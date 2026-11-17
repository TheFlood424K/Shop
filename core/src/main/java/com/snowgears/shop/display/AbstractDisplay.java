package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ArmorStandData;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.UtilMethods;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Paper entity-backed shop display.
 *
 * <p>The main display is shared by all viewers of a shop. Every main entity is
 * created hidden by default and is revealed with Paper's per-player visibility
 * API. Tags are intentionally player-specific entities.</p>
 */
public abstract class AbstractDisplay {

    protected final Location shopSignLocation;
    protected volatile DisplayType type;

    private final Object sharedLock = new Object();
    private final Object tagLock = new Object();

    private final Set<UUID> visiblePlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Set<Entity>> tagEntities = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TagRequest> pendingTagRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerTagEpochs = new HashMap<>();

    private volatile SharedGeneration sharedGeneration;
    private long sharedGenerationCounter;
    private long globalTagEpoch;

    public AbstractDisplay(Location shopSignLocation) {
        this.shopSignLocation = shopSignLocation == null ? null : shopSignLocation.clone();
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean isChunkLoaded() {
        return shopSignLocation != null && UtilMethods.isChunkLoaded(shopSignLocation);
    }

    /**
     * Reveals this shop's shared display to one player, or to every eligible
     * player in the shop world when {@code player} is {@code null}.
     */
    public void spawn(Player player) {
        if (!isEnabled() || shopSignLocation == null || shopSignLocation.getWorld() == null) {
            return;
        }

        AbstractShop shop = getShop();
        if (shop == null || shop.getItemStack() == null || shop.getChestLocation() == null) {
            return;
        }

        if (player != null) {
            if (!player.isOnline() || !isSameWorld(player)) {
                return;
            }
            visiblePlayers.add(player.getUniqueId());
        } else {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.isOnline() && isSameWorld(onlinePlayer)) {
                    visiblePlayers.add(onlinePlayer.getUniqueId());
                }
            }
        }

        DisplayType displayType = getType();
        if (displayType == null) {
            displayType = Shop.getPlugin().getDisplayType();
        }

        ensureSharedDisplay(displayType, shop);

        SharedGeneration generation = sharedGeneration;
        if (generation != null && generation.complete) {
            if (player == null) {
                revealSharedGeneration(generation);
            } else {
                revealSharedEntity(generation, player);
            }
        }
    }

    /**
     * Removes one player's tag entities and visibility exception. Shared main
     * entities remain available to other viewers. Passing {@code null} destroys
     * the shared main entities and all tags for every viewer.
     */
    public void remove(Player player) {
        try {
            removeDisplayEntities(player, false);
            removeDisplayEntities(player, true);
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to remove display entities: " + exception.getMessage());
        }
    }

    /**
     * Explicit logout/player-cleanup entry point. This is equivalent to
     * {@link #remove(Player)} and never destroys shared entities needed by
     * other players.
     */
    public void removePlayer(Player player) {
        remove(player);
    }

    /**
     * Removes main display visibility for a player, or all shared entities when
     * {@code player} is {@code null}. When {@code onlyDisplayTags} is true, only
     * that player's tags (or all tags for {@code null}) are removed.
     */
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        if (!isEnabled()) {
            return;
        }

        try {
            if (onlyDisplayTags) {
                removeTagEntities(player);
            } else if (player == null) {
                removeSharedEntities();
            } else {
                removePlayerVisibility(player);
            }
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to remove display entities: " + exception.getMessage());
        }
    }

    /** Returns whether this display has been requested for the player. */
    public boolean hasActiveDisplay(Player player) {
        return player != null && visiblePlayers.contains(player.getUniqueId());
    }

    /** Returns whether the player currently owns visible tag entities or a pending tag request. */
    public boolean hasActiveDisplayTags(Player player) {
        return player != null && displayTagsVisible(player);
    }

    private void ensureSharedDisplay(DisplayType displayType, AbstractShop shop) {
        SharedGeneration previous;
        SharedGeneration next;

        synchronized (sharedLock) {
            SharedGeneration current = sharedGeneration;
            if (current != null && current.displayType == displayType && !current.failed) {
                return;
            }

            previous = current;
            next = new SharedGeneration(++sharedGenerationCounter, displayType);
            sharedGeneration = next;
        }

        if (previous != null) {
            removeEntities(previous.entities);
        }

        if (displayType == DisplayType.NONE) {
            next.complete = true;
            next.expectedEntityCount = 0;
            return;
        }

        Location chestLocation = shop.getChestLocation().clone();
        if (!executeAtLocation(chestLocation, () -> buildSharedGeneration(next, shop))) {
            next.complete = true;
            next.failed = true;
        }
    }

    private void buildSharedGeneration(SharedGeneration generation, AbstractShop shop) {
        if (!isCurrentGeneration(generation)) {
            return;
        }

        List<MainEntitySpec> specs;
        try {
            specs = createMainEntitySpecs(shop, generation.displayType);
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to prepare shop display entities: " + exception.getMessage());
            generation.complete = true;
            generation.failed = true;
            return;
        }

        generation.expectedEntityCount = specs.size();
        if (specs.isEmpty()) {
            generation.complete = true;
            revealSharedGeneration(generation);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(specs.size());
        for (MainEntitySpec spec : specs) {
            if (!isCurrentGeneration(generation)) {
                removeEntities(generation.entities);
                return;
            }

            Location entityLocation = spec.location.clone();
            if (!executeAtLocation(entityLocation, () -> {
                try {
                    if (!isCurrentGeneration(generation)) {
                        return;
                    }

                    Entity entity = spawnMainEntity(spec);
                    if (entity != null) {
                        generation.entities.add(entity);
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        generation.complete = true;
                        revealSharedGeneration(generation);
                    }
                }
            })) {
                if (remaining.decrementAndGet() == 0) {
                    generation.complete = true;
                    revealSharedGeneration(generation);
                }
            }
        }
    }

    private boolean isCurrentGeneration(SharedGeneration generation) {
        return sharedGeneration == generation;
    }

    private List<MainEntitySpec> createMainEntitySpecs(AbstractShop shop, DisplayType displayType) {
        List<MainEntitySpec> specs = new ArrayList<>();

        ItemStack item = shop.getItemStack().clone();
        item.setAmount(1);
        ItemStack barterItem = null;
        if (shop.getSecondaryItemStack() != null) {
            barterItem = shop.getSecondaryItemStack().clone();
            barterItem.setAmount(1);
        }

        switch (displayType) {
            case NONE -> {
                // No main entities.
            }
            case ITEM -> {
                Location itemLocation = getItemDropLocation(shop, false);
                if (itemLocation != null) {
                    specs.add(MainEntitySpec.item(itemLocation, item));
                }
                if (barterItem != null) {
                    Location barterLocation = getItemDropLocation(shop, true);
                    if (barterLocation != null) {
                        specs.add(MainEntitySpec.item(barterLocation, barterItem));
                    }
                }
            }
            case LARGE_ITEM -> {
                if (shop.getFacing() != null) {
                    Location leftLocation = shop.getChestLocation().clone().add(0, 1, 0);
                    leftLocation.add(getLargeItemBarterOffset(shop, false));
                    specs.add(MainEntitySpec.armorStand(
                            DisplayUtil.getArmorStandData(item, leftLocation, shop.getFacing(), false)));

                    if (barterItem != null) {
                        Location rightLocation = shop.getChestLocation().clone().add(0, 1, 0);
                        rightLocation.add(getLargeItemBarterOffset(shop, true));
                        specs.add(MainEntitySpec.armorStand(
                                DisplayUtil.getArmorStandData(barterItem, rightLocation, shop.getFacing(), false)));
                    }
                }
            }
            case GLASS_CASE -> {
                if (shop.getFacing() != null) {
                    Location caseLocation = shop.getChestLocation().clone().add(0, 1, 0);
                    specs.add(MainEntitySpec.armorStand(
                            DisplayUtil.getArmorStandData(new ItemStack(Material.GLASS), caseLocation, shop.getFacing(), true)));
                }

                Location itemLocation = getItemDropLocation(shop, false);
                if (itemLocation != null) {
                    specs.add(MainEntitySpec.item(itemLocation, item));
                }
                if (barterItem != null) {
                    Location barterLocation = getItemDropLocation(shop, true);
                    if (barterLocation != null) {
                        specs.add(MainEntitySpec.item(barterLocation, barterItem));
                    }
                }
            }
            case ITEM_FRAME -> {
                if (barterItem == null) {
                    Location frameLocation = getItemFrameLocation(shop);
                    if (frameLocation != null) {
                        specs.add(MainEntitySpec.itemFrame(
                                frameLocation,
                                shop.getItemStack(),
                                shop.getFacing(),
                                Shop.getPlugin().getGlowingItemFrame()));
                    }
                }
            }
        }

        return specs;
    }

    private Location getItemFrameLocation(AbstractShop shop) {
        Location chestLocation = shop.getChestLocation();
        if (chestLocation == null) {
            return null;
        }

        if (!isChunkLoaded()) {
            return chestLocation.clone().add(0, 1, 0);
        }

        Block aboveShop = chestLocation.getBlock().getRelative(BlockFace.UP);
        Location frameLocation = aboveShop.getLocation();
        if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
            if (shop.getFacing() == null) {
                return frameLocation;
            }
            frameLocation = aboveShop.getRelative(shop.getFacing()).getLocation();
        }
        return frameLocation;
    }

    private Entity spawnMainEntity(MainEntitySpec spec) {
        if (spec.location == null || spec.location.getWorld() == null) {
            return null;
        }

        try {
            return switch (spec.kind) {
                case ITEM -> spec.location.getWorld().spawn(spec.location, Item.class,
                        item -> configureItem(item, spec.itemStack));
                case ARMOR_STAND -> spec.location.getWorld().spawn(spec.location, ArmorStand.class,
                        armorStand -> configureArmorStand(armorStand, spec.armorStandData));
                case ITEM_FRAME -> spec.location.getWorld().spawn(spec.location, ItemFrame.class,
                        itemFrame -> configureItemFrame(itemFrame, spec.itemStack, spec.facing));
                case GLOW_ITEM_FRAME -> spec.location.getWorld().spawn(spec.location, GlowItemFrame.class,
                        itemFrame -> configureItemFrame(itemFrame, spec.itemStack, spec.facing));
            };
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to spawn shop display entity: " + exception.getMessage());
            return null;
        }
    }

    private void configureItem(Item item, ItemStack itemStack) {
        item.setItemStack(itemStack);
        item.setUnlimitedLifetime(true);
        item.setWillAge(false);
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setCanPlayerPickup(false);
        item.setCanMobPickup(false);
        item.setVelocity(new Vector());
        configureBaseEntity(item);
    }

    private void configureArmorStand(ArmorStand armorStand, ArmorStandData data) {
        armorStand.setVisible(false);
        armorStand.setBasePlate(false);
        armorStand.setMarker(true);
        armorStand.setGravity(false);
        armorStand.setPersistent(false);
        armorStand.setInvulnerable(true);
        armorStand.setVisibleByDefault(false);
        armorStand.setCanMove(false);
        armorStand.setCanTick(false);
        armorStand.setAI(false);
        armorStand.setCollidable(false);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomNameVisible(false);
        armorStand.setSilent(true);
        armorStand.setSmall(data.isSmall());
        armorStand.setRotation((float) data.getYaw(), 0.0f);

        if (data.getRightArmPose() != null) {
            armorStand.setRightArmPose(data.getRightArmPose());
        }

        ItemStack equipment = data.getEquipment();
        if (equipment != null) {
            switch (data.getEquipmentSlot()) {
                case HEAD -> armorStand.getEquipment().setHelmet(equipment);
                case CHEST -> armorStand.getEquipment().setChestplate(equipment);
                case LEGS -> armorStand.getEquipment().setLeggings(equipment);
                case FEET -> armorStand.getEquipment().setBoots(equipment);
                case OFF_HAND -> armorStand.getEquipment().setItemInOffHand(equipment);
                case HAND -> armorStand.getEquipment().setItemInMainHand(equipment);
            }
        }

        PaperDisplayMarker.mark(armorStand);
    }

    private void configureItemFrame(ItemFrame itemFrame, ItemStack itemStack, BlockFace facing) {
        itemFrame.setItem(itemStack);
        if (facing != null) {
            itemFrame.setFacingDirection(facing, true);
        }
        itemFrame.setFixed(true);
        itemFrame.setItemDropChance(0.0f);
        itemFrame.setGravity(false);
        itemFrame.setPersistent(false);
        itemFrame.setInvulnerable(true);
        itemFrame.setVisibleByDefault(false);
        itemFrame.setSilent(true);
        PaperDisplayMarker.mark(itemFrame);
    }

    private void configureBaseEntity(Entity entity) {
        entity.setGravity(false);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setVisibleByDefault(false);
        entity.setSilent(true);
        PaperDisplayMarker.mark(entity);
    }

    private void revealSharedGeneration(SharedGeneration generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }

        for (UUID playerId : new HashSet<>(visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && isSameWorld(player)) {
                revealSharedEntity(generation, player);
            }
        }
    }

    private void revealSharedEntity(SharedGeneration generation, Player player) {
        for (Entity entity : new HashSet<>(generation.entities)) {
            executeOnEntity(entity, () -> {
                if (!isCurrentGeneration(generation)
                        || !generation.entities.contains(entity)
                        || !visiblePlayers.contains(player.getUniqueId())
                        || !player.isOnline()) {
                    return;
                }

                try {
                    player.showEntity(Shop.getPlugin(), entity);
                } catch (RuntimeException exception) {
                    Shop.getPlugin().getLogger().fine("Unable to reveal shop display entity: " + exception.getMessage());
                }
            }, null);
        }
    }

    private void removePlayerVisibility(Player player) {
        visiblePlayers.remove(player.getUniqueId());

        SharedGeneration generation = sharedGeneration;
        if (generation == null) {
            return;
        }

        for (Entity entity : new HashSet<>(generation.entities)) {
            executeOnEntity(entity, () -> {
                if (visiblePlayers.contains(player.getUniqueId())) {
                    return;
                }
                try {
                    player.hideEntity(Shop.getPlugin(), entity);
                } catch (RuntimeException exception) {
                    Shop.getPlugin().getLogger().fine("Unable to hide shop display entity: " + exception.getMessage());
                }
            }, null);
        }
    }

    private void removeSharedEntities() {
        SharedGeneration generation;
        synchronized (sharedLock) {
            generation = sharedGeneration;
            sharedGeneration = null;
            sharedGenerationCounter++;
        }
        visiblePlayers.clear();

        if (generation != null) {
            removeEntities(generation.entities);
        }
    }

    private void removeEntities(Set<Entity> entities) {
        for (Entity entity : new HashSet<>(entities)) {
            executeOnEntity(entity, () -> {
                try {
                    entity.remove();
                } catch (RuntimeException exception) {
                    Shop.getPlugin().getLogger().fine("Unable to remove shop display entity: " + exception.getMessage());
                }
            }, null);
        }
    }

    // Display tags

    public void showDisplayTags(Player player) {
        if (!isEnabled() || player == null || !player.isOnline() || !isSameWorld(player)) {
            return;
        }

        AbstractShop shop = getShop();
        if (shop == null || !shop.isInitialized() || shop.getChestLocation() == null || shop.getFacing() == null) {
            return;
        }

        long globalEpoch;
        long playerEpoch;
        TagRequest request;
        synchronized (tagLock) {
            if (displayTagsVisible(player)) {
                return;
            }
            globalEpoch = this.globalTagEpoch;
            playerEpoch = playerTagEpochs.getOrDefault(player.getUniqueId(), 0L);
            request = new TagRequest(player.getUniqueId(), globalEpoch, playerEpoch);
            if (pendingTagRequests.putIfAbsent(player.getUniqueId(), request) != null) {
                return;
            }
        }

        Location chestLocation = shop.getChestLocation().clone();
        boolean scheduled = executeAtLocation(chestLocation, () -> {
            try {
                if (!isCurrentTagRequest(player, request)) {
                    return;
                }

                AbstractShop currentShop = getShop();
                if (currentShop == null
                        || !currentShop.isInitialized()
                        || currentShop.getChestLocation() == null
                        || currentShop.getFacing() == null
                        || Shop.getPlugin().getDisplayTagOption() == DisplayTagOption.NONE) {
                    return;
                }

                List<String> displayTags = ShopMessage.getDisplayTags(currentShop, currentShop.getType());
                if (displayTags == null || displayTags.isEmpty()) {
                    return;
                }

                Location lowerTagLocation = currentShop.getChestLocation().clone().add(0, 1, 0);
                lowerTagLocation.add(0.5, 0.5, 0.5);
                lowerTagLocation = UtilMethods.pushLocationInDirection(lowerTagLocation, currentShop.getFacing(), 0.2);

                Block displayBlock = lowerTagLocation.getBlock();
                if (isChunkLoaded()) {
                    if (displayBlock.getType() == Material.BARREL
                            || displayBlock.getRelative(BlockFace.DOWN).getType() == Material.BARREL) {
                        lowerTagLocation.add(0, 0.25, 0);
                    }
                    if (currentShop.getChestLocation().clone().add(0, 2, 0).getBlock().getType() != Material.AIR) {
                        lowerTagLocation = UtilMethods.pushLocationInDirection(
                                lowerTagLocation, currentShop.getFacing(), 0.35);
                    }
                }

                List<TagData> tagData = new ArrayList<>();
                double verticalAddition = 0;
                for (int index = displayTags.size() - 1; index >= 0; index--) {
                    Location tagLocation = lowerTagLocation.clone();
                    String tagLine = displayTags.get(index);

                    if (tagLine.contains("[lshift]")) {
                        tagLocation.add(getShiftOffset(currentShop, true, false));
                        tagLine = tagLine.replace("[lshift]", "");
                    }
                    if (tagLine.contains("[rshift]")) {
                        tagLocation.add(getShiftOffset(currentShop, false, true));
                        tagLine = tagLine.replace("[rshift]", "");
                    }

                    tagLocation.add(0, verticalAddition, 0);
                    tagData.add(new TagData(tagLine, tagLocation));
                    verticalAddition += 0.3;
                }

                request.expectedCount = tagData.size();
                for (int index = tagData.size() - 1; index >= 0; index--) {
                    TagData data = tagData.get(index);
                    if (!scheduleTagEntity(player, data.text, data.location, request)) {
                        request.completedCount.incrementAndGet();
                    }
                }
                request.completeIfFinished();

                Shop.getPlugin().getShopHandler().addActiveShopDisplayTag(player, shopSignLocation);
                removeDisplayTagsDelayedTask(player);
            } catch (RuntimeException exception) {
                Shop.getPlugin().getLogger().fine("Unable to create shop display tags: " + exception.getMessage());
            } finally {
                if (request.expectedCount == 0) {
                    pendingTagRequests.remove(player.getUniqueId(), request);
                }
            }
        });
        if (!scheduled) {
            pendingTagRequests.remove(player.getUniqueId(), request);
        }
    }

    public void updateDisplayTags() {
        Set<UUID> playerIds = new HashSet<>(tagEntities.keySet());
        playerIds.addAll(pendingTagRequests.keySet());
        if (playerIds.isEmpty()) {
            return;
        }

        for (UUID playerId : new HashSet<>(playerIds)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !isSameWorld(player) || !displayTagsVisible(player)) {
                continue;
            }

            removeDisplayEntities(player, true);
            showDisplayTags(player);
        }
    }

    public void createTagEntity(Player player, String text, Location location) {
        if (!isEnabled() || player == null || text == null || location == null || location.getWorld() == null) {
            return;
        }

        long globalEpoch;
        long playerEpoch;
        synchronized (tagLock) {
            globalEpoch = this.globalTagEpoch;
            playerEpoch = playerTagEpochs.getOrDefault(player.getUniqueId(), 0L);
        }
        createTagEntity(player, text, location, globalEpoch, playerEpoch);
    }

    /**
     * Creates one player-specific floating text entity. This overload is used
     * by the independent shop creation display and does not participate in the
     * shop tag request lifecycle.
     */
    private void createTagEntity(Player player, String text, Location location, long globalEpoch, long playerEpoch) {
        if (!isCurrentTagRequest(player, globalEpoch, playerEpoch)) {
            return;
        }

        executeAtLocation(location, () -> createTagEntityAtLocation(player, text, location, globalEpoch, playerEpoch));
    }

    private boolean scheduleTagEntity(Player player, String text, Location location, TagRequest request) {
        if (!isCurrentTagRequest(player, request)) {
            return false;
        }

        return executeAtLocation(location, () -> {
            try {
                createTagEntityAtLocation(player, text, location, request);
            } finally {
                request.completedCount.incrementAndGet();
                request.completeIfFinished();
            }
        });
    }

    private void createTagEntityAtLocation(Player player, String text, Location location,
                                           long globalEpoch, long playerEpoch) {
        createTagEntityAtLocationInternal(player, text, location, globalEpoch, playerEpoch, null, false);
    }

    private void createTagEntityAtLocation(Player player, String text, Location location, TagRequest request) {
        createTagEntityAtLocationInternal(
                player, text, location, request.globalEpoch, request.playerEpoch, request, true);
    }

    private void createTagEntityAtLocationInternal(Player player, String text, Location location,
                                                   long globalEpoch, long playerEpoch, TagRequest request,
                                                   boolean trackedRequest) {
        if (trackedRequest) {
            if (!isCurrentTagRequest(player, request)) {
                return;
            }
        } else if (!isCurrentTagRequest(player, globalEpoch, playerEpoch)) {
            return;
        }

        Component component;
        try {
            component = LegacyComponentSerializer.legacySection().deserialize(text);
        } catch (RuntimeException exception) {
            component = Component.text(text);
        }

        TextDisplay tag;
        try {
            Component tagComponent = component;
            tag = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
                textDisplay.text(tagComponent);
                textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
                textDisplay.setDefaultBackground(false);
                textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                textDisplay.setGravity(false);
                textDisplay.setPersistent(false);
                textDisplay.setInvulnerable(true);
                textDisplay.setVisibleByDefault(false);
                textDisplay.setSilent(true);
                PaperDisplayMarker.mark(textDisplay);
            });
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to spawn shop display tag: " + exception.getMessage());
            return;
        }

        boolean stale;
        synchronized (tagLock) {
            stale = trackedRequest
                    ? !isCurrentTagRequestLocked(player, request)
                    : !isCurrentTagRequestLocked(player, globalEpoch, playerEpoch);
            if (!stale) {
                tagEntities.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(tag);
            }
        }

        if (stale) {
            removeEntity(tag);
            return;
        }

        executeOnEntity(tag, () -> {
            if (!player.isOnline() || !tagEntities.getOrDefault(player.getUniqueId(), Set.of()).contains(tag)) {
                return;
            }
            try {
                player.showEntity(Shop.getPlugin(), tag);
            } catch (RuntimeException exception) {
                Shop.getPlugin().getLogger().fine("Unable to reveal shop display tag: " + exception.getMessage());
            }
        }, null);
    }

    private void removeTagEntities(Player player) {
        Set<Entity> entitiesToRemove = new HashSet<>();

        synchronized (tagLock) {
            if (player == null) {
                globalTagEpoch++;
                for (Set<Entity> entities : tagEntities.values()) {
                    entitiesToRemove.addAll(entities);
                }
                tagEntities.clear();
                for (TagRequest request : pendingTagRequests.values()) {
                    request.cancel();
                }
                pendingTagRequests.clear();
                for (UUID playerId : new HashSet<>(playerTagEpochs.keySet())) {
                    playerTagEpochs.put(playerId, playerTagEpochs.get(playerId) + 1);
                }
            } else {
                UUID playerId = player.getUniqueId();
                playerTagEpochs.put(playerId, playerTagEpochs.getOrDefault(playerId, 0L) + 1);
                Set<Entity> entities = tagEntities.remove(playerId);
                if (entities != null) {
                    entitiesToRemove.addAll(entities);
                }
                TagRequest request = pendingTagRequests.remove(playerId);
                if (request != null) {
                    request.cancel();
                }
            }
        }

        for (Entity entity : entitiesToRemove) {
            removeEntity(entity);
        }
    }

    private void removeEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        executeOnEntity(entity, () -> {
            try {
                entity.remove();
            } catch (RuntimeException exception) {
                Shop.getPlugin().getLogger().fine("Unable to remove shop display tag: " + exception.getMessage());
            }
        }, null);
    }

    private boolean isCurrentTagRequest(Player player, long globalEpoch, long playerEpoch) {
        synchronized (tagLock) {
            return isCurrentTagRequestLocked(player, globalEpoch, playerEpoch);
        }
    }

    private boolean isCurrentTagRequest(Player player, TagRequest request) {
        synchronized (tagLock) {
            return isCurrentTagRequestLocked(player, request);
        }
    }

    private boolean isCurrentTagRequest(Player player, TagRequest request, boolean trackedRequest) {
        synchronized (tagLock) {
            return trackedRequest
                    ? isCurrentTagRequestLocked(player, request)
                    : isCurrentTagRequestLocked(player, request.globalEpoch, request.playerEpoch);
        }
    }

    private boolean isCurrentTagRequestLocked(Player player, long globalEpoch, long playerEpoch) {
        return globalEpoch == this.globalTagEpoch
                && playerEpoch == playerTagEpochs.getOrDefault(player.getUniqueId(), 0L);
    }

    private boolean isCurrentTagRequestLocked(Player player, TagRequest request) {
        return pendingTagRequests.get(player.getUniqueId()) == request
                && isCurrentTagRequestLocked(player, request.globalEpoch, request.playerEpoch);
    }

    protected boolean displayTagsVisible(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Set<Entity> entities = tagEntities.get(playerId);
        return (entities != null && !entities.isEmpty()) || pendingTagRequests.containsKey(playerId);
    }

    public void addRemoveDisplayTask(Player player) {
        removeDisplayTagsDelayedTask(player);
    }

    protected void removeDisplayTagsDelayedTask(Player player) {
        if (!isEnabled() || player == null) {
            return;
        }

        try {
            EntityScheduler scheduler = player.getScheduler();
            scheduler.execute(Shop.getPlugin(), () -> {
                if (!player.isOnline() || !displayTagsVisible(player)) {
                    removeDisplayEntities(player, true);
                    return;
                }
                if (playerIsLookingTowardShop(player)) {
                    removeDisplayTagsDelayedTask(player);
                } else {
                    removeDisplayEntities(player, true);
                }
            }, () -> removeDisplayEntities(player, true), 20L);
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to schedule shop display tag cleanup: " + exception.getMessage());
        }
    }

    protected boolean playerIsLookingTowardShop(Player player) {
        try {
            if (player.getLocation().distanceSquared(this.shopSignLocation) > 64) {
                return false;
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }

        AbstractShop shop = getShop();
        if (shop == null || shop.getChestLocation() == null) {
            return false;
        }

        Vector lookDirection = player.getEyeLocation().getDirection();
        Location displayLocation = shop.getChestLocation().clone().add(0.5, 0.9, 0.5);
        Vector blockDirection = displayLocation.subtract(player.getEyeLocation()).toVector().normalize();
        return blockDirection.angle(lookDirection) < 1;
    }

    public DisplayType getType() {
        return type;
    }

    public AbstractShop getShop() {
        return Shop.getPlugin().getShopHandler().getShop(this.shopSignLocation);
    }

    public void setType(DisplayType displayType, boolean checkDisplayBlock) {
        if (displayType == null) {
            return;
        }

        if (!checkDisplayBlock) {
            this.type = displayType;
            return;
        }

        AbstractShop shop = getShop();
        if (shop == null || shop.getChestLocation() == null) {
            return;
        }

        Location chestLocation = shop.getChestLocation().clone();
        executeAtLocation(chestLocation, () -> {
            if (hasDisplayRoom(shop, displayType)) {
                this.type = displayType;
            }
        });
    }

    private boolean hasDisplayRoom(AbstractShop shop, DisplayType displayType) {
        DisplayType oldType = this.type;
        boolean roomRequired = (oldType == DisplayType.NONE && displayType != DisplayType.ITEM_FRAME)
                || (oldType == DisplayType.ITEM_FRAME && displayType != DisplayType.NONE);
        if (!roomRequired || !isChunkLoaded()) {
            return true;
        }

        Block aboveShop = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
        return UtilMethods.materialIsNonIntrusive(aboveShop.getType());
    }

    public void cycleType(Player player) {
        AbstractShop shop = getShop();
        if (shop == null || shop.getChestLocation() == null) {
            return;
        }

        Location chestLocation = shop.getChestLocation().clone();
        executeAtLocation(chestLocation, () -> cycleTypeAtLocation(player, shop));
    }

    private void cycleTypeAtLocation(Player player, AbstractShop shop) {
        if (shop.getFacing() == null) {
            return;
        }

        DisplayType[] cycle = Shop.getPlugin().getDisplayCycle();
        if (cycle == null || cycle.length == 0) {
            return;
        }

        DisplayType displayType = this.type;
        if (displayType == null) {
            displayType = Shop.getPlugin().getDisplayType();
        }

        int index = -1;
        boolean blocked = isChunkLoaded()
                && !UtilMethods.materialIsNonIntrusive(
                        shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getType());

        if (displayType == DisplayType.NONE && blocked) {
            for (int cycleIndex = 0; cycleIndex < cycle.length; cycleIndex++) {
                if (cycle[cycleIndex] == DisplayType.ITEM_FRAME) {
                    index = cycleIndex;
                }
            }
            if (index == -1) {
                return;
            }
        } else if (displayType == DisplayType.ITEM_FRAME && blocked) {
            for (int cycleIndex = 0; cycleIndex < cycle.length; cycleIndex++) {
                if (cycle[cycleIndex] == DisplayType.NONE) {
                    index = cycleIndex;
                }
            }
            if (index == -1) {
                return;
            }
        } else {
            index = 0;
            for (int cycleIndex = 0; cycleIndex < cycle.length; cycleIndex++) {
                if (cycle[cycleIndex] == displayType) {
                    index = cycleIndex + 1;
                }
            }
            if (index >= cycle.length) {
                index = 0;
            }
        }

        if (cycle[index] == DisplayType.ITEM_FRAME) {
            boolean skip = shop.getType() == ShopType.BARTER;
            if (!skip) {
                for (Entity entity : shop.getChestLocation().getWorld().getNearbyEntities(
                        getItemDropLocation(shop, false), 1, 1, 1)) {
                    if (entity.getType() == EntityType.ITEM_FRAME
                            && entity instanceof ItemFrame itemFrame
                            && shop.getFacing() != null
                            && itemFrame.getAttachedFace() == shop.getFacing().getOppositeFace()) {
                        skip = true;
                        break;
                    }
                }
            }

            if (skip) {
                index++;
                if (index >= cycle.length) {
                    index = 0;
                }
            }
        }

        this.type = cycle[index];
        this.spawn(player);
        Shop.getPlugin().getShopHandler().addActiveShopDisplay(player, this.shopSignLocation);
        shop.setNeedsSave(true);
    }

    public Vector getShiftOffset(boolean isLeftShift, boolean isRightShift) {
        AbstractShop shop = getShop();
        return shop == null ? new Vector() : getShiftOffset(shop, isLeftShift, isRightShift);
    }

    private Vector getShiftOffset(AbstractShop shop, boolean isLeftShift, boolean isRightShift) {
        Vector offset = new Vector(0, 0, 0);
        double space = 0.48;

        if (shop.getFacing() == null) {
            return offset;
        }

        switch (shop.getFacing()) {
            case NORTH -> {
                if (isRightShift) {
                    offset.setX(-space);
                } else if (isLeftShift) {
                    offset.setX(space);
                }
            }
            case EAST -> {
                if (isRightShift) {
                    offset.setZ(-space);
                } else if (isLeftShift) {
                    offset.setZ(space);
                }
            }
            case SOUTH -> {
                if (isRightShift) {
                    offset.setX(space);
                } else if (isLeftShift) {
                    offset.setX(-space);
                }
            }
            case WEST -> {
                if (isRightShift) {
                    offset.setZ(-space);
                } else if (isLeftShift) {
                    offset.setZ(space);
                }
            }
            default -> {
                // No offset.
            }
        }
        return offset;
    }

    private Vector getLargeItemBarterOffset(AbstractShop shop, boolean isBarterItem) {
        Vector offset = new Vector(0, 0, 0);
        double space = 0.24;

        if (shop.getType() == ShopType.BARTER && shop.getFacing() != null) {
            switch (shop.getFacing()) {
                case NORTH -> offset.setX(isBarterItem ? -space : space);
                case EAST -> offset.setZ(isBarterItem ? -space : space);
                case SOUTH -> offset.setX(isBarterItem ? space : -space);
                case WEST -> offset.setZ(isBarterItem ? space : -space);
                default -> {
                    // No offset.
                }
            }
        }
        return offset;
    }

    private Location getItemDropLocation(AbstractShop shop, boolean isBarterItem) {
        if (shop == null || shop.getFacing() == null || shop.getChestLocation() == null) {
            return null;
        }

        double dropY = 0.98;
        Material blockType = shop.getChestLocation().getBlock().getType();
        if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST) {
            dropY = 0.9;
        }

        double dropX = 0.5;
        double dropZ = 0.5;
        if (shop.getType() == ShopType.BARTER) {
            switch (shop.getFacing()) {
                case NORTH -> dropX = isBarterItem ? 0.3 : 0.7;
                case EAST -> dropZ = isBarterItem ? 0.3 : 0.7;
                case SOUTH -> dropX = isBarterItem ? 0.7 : 0.3;
                case WEST -> dropZ = isBarterItem ? 0.7 : 0.3;
                default -> {
                    // Centered drop.
                }
            }
        }
        return shop.getChestLocation().clone().add(dropX, dropY, dropZ);
    }

    protected boolean isSameWorld(Player player) {
        return player != null
                && shopSignLocation != null
                && shopSignLocation.getWorld() != null
                && player.getWorld().getUID().equals(shopSignLocation.getWorld().getUID());
    }

    /**
     * Compatibility method retained for callers compiled against the old
     * display API. It now uses public Paper item metadata only.
     */
    public String getItemNameNMS(ItemStack item) {
        if (item == null) {
            return "";
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            if (itemMeta.hasDisplayName()) {
                return itemMeta.getDisplayName();
            }
            if (itemMeta.hasItemName()) {
                return itemMeta.getItemName();
            }
        }
        return item.getType().getKey().getKey();
    }

    private boolean executeAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {
            Bukkit.getRegionScheduler().execute(Shop.getPlugin(), location, task);
            return true;
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to schedule shop display work: " + exception.getMessage());
            return false;
        }
    }

    private void executeOnEntity(Entity entity, Runnable task, Runnable retiredTask) {
        if (entity == null) {
            return;
        }

        try {
            entity.getScheduler().execute(Shop.getPlugin(), task, retiredTask, 1L);
        } catch (RuntimeException exception) {
            Shop.getPlugin().getLogger().fine("Unable to schedule shop display entity work: " + exception.getMessage());
        }
    }

    private static final class SharedGeneration {
        private final long id;
        private final DisplayType displayType;
        private final Set<Entity> entities = ConcurrentHashMap.newKeySet();
        private volatile boolean complete;
        private volatile boolean failed;
        private volatile int expectedEntityCount;

        private SharedGeneration(long id, DisplayType displayType) {
            this.id = id;
            this.displayType = displayType;
        }
    }

    private enum MainEntityKind {
        ITEM,
        ARMOR_STAND,
        ITEM_FRAME,
        GLOW_ITEM_FRAME
    }

    private static final class MainEntitySpec {
        private final MainEntityKind kind;
        private final Location location;
        private final ItemStack itemStack;
        private final ArmorStandData armorStandData;
        private final BlockFace facing;

        private MainEntitySpec(MainEntityKind kind, Location location, ItemStack itemStack,
                              ArmorStandData armorStandData, BlockFace facing) {
            this.kind = kind;
            this.location = location;
            this.itemStack = itemStack;
            this.armorStandData = armorStandData;
            this.facing = facing;
        }

        private static MainEntitySpec item(Location location, ItemStack itemStack) {
            return new MainEntitySpec(MainEntityKind.ITEM, location, itemStack, null, null);
        }

        private static MainEntitySpec armorStand(ArmorStandData armorStandData) {
            return new MainEntitySpec(MainEntityKind.ARMOR_STAND, armorStandData.getLocation(), null,
                    armorStandData, null);
        }

        private static MainEntitySpec itemFrame(Location location, ItemStack itemStack, BlockFace facing,
                                                boolean glowing) {
            return new MainEntitySpec(glowing ? MainEntityKind.GLOW_ITEM_FRAME : MainEntityKind.ITEM_FRAME,
                    location, itemStack, null, facing);
        }
    }

    private record TagData(String text, Location location) {
    }

    private final class TagRequest {
        private final UUID playerId;
        private final long globalEpoch;
        private final long playerEpoch;
        private final AtomicInteger completedCount = new AtomicInteger();
        private volatile int expectedCount;

        private TagRequest(UUID playerId, long globalEpoch, long playerEpoch) {
            this.playerId = playerId;
            this.globalEpoch = globalEpoch;
            this.playerEpoch = playerEpoch;
        }

        private void completeIfFinished() {
            if (expectedCount > 0 && completedCount.get() >= expectedCount) {
                pendingTagRequests.remove(playerId, this);
            }
        }

        private void cancel() {
            expectedCount = 0;
            pendingTagRequests.remove(playerId, this);
        }
    }
}

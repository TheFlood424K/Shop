package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopStorage;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.DisplayUtil;
import com.theflood424k.shoprevanced.util.UtilMethods;
import org.bukkit.Location;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages ShopRevanced display lifecycle for players.
 * <p>
 * Tracks active displays per player, handles display spawn/removal,
 * movement-based display updates, and teleport cooldowns.
 * All region-bound operations are scheduled via FoliaLib at the correct region.
 */
public class ShopDisplayManager {

    private final ShopRevanced plugin;
    private final ShopStorage storage;

    private final java.util.concurrent.ConcurrentHashMap<UUID, java.util.HashSet<Location>> playersWithActiveShopDisplays = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> playersProcessingShopDisplays = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Location> playersActiveShopDisplayTag = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Location> lastProcessedLocations = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Long> teleportCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TELEPORT_COOLDOWN_MS = 500;

    public ShopDisplayManager(ShopRevanced plugin, ShopStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * Creates a Paper-backed display instance at the specified location.
     *
     * @param loc The location to create the display at
     * @return A new display instance
     */
    public com.theflood424k.shoprevanced.display.AbstractDisplay createDisplay(Location loc) {
        if (plugin.isMockBukkit()) {
            return new com.theflood424k.shoprevanced.display.DisplayDisabled(loc);
        }
        return new com.theflood424k.shoprevanced.display.Display(loc);
    }

    /**
     * Processes ShopRevanced displays near the specified player.
     *
     * @param player The player to update displays for
     */
    public void processShopDisplaysNearPlayer(Player player) {
        if (playersProcessingShopDisplays.contains(player.getUniqueId())) {
            return;
        }

        Location currentLocation = player.getLocation();
        double movementThreshold = plugin.getDisplayMovementThreshold();
        Location lastLocation = lastProcessedLocations.get(player.getUniqueId());

        if (lastLocation != null &&
                lastLocation.getWorld().equals(currentLocation.getWorld()) &&
                lastLocation.distanceSquared(currentLocation) < (movementThreshold * movementThreshold)) {
            return;
        }

        playersProcessingShopDisplays.add(player.getUniqueId());

        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            try {
                Location playerLocation = player.getLocation();
                lastProcessedLocations.put(player.getUniqueId(), playerLocation.clone());

                java.util.HashSet<Location> nearbyShopLocations = storage.getShopLocationsNearLocationWithinDistance(
                        playerLocation,
                        plugin.getShopSearchRadius(),
                        plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance()
                );

                processBatchDisplayUpdates(player, playerLocation, nearbyShopLocations);

            } catch (Exception e) {
                plugin.getLogger().warning("Error processing ShopRevanced displays for player " + player.getName());
                e.printStackTrace();
            } finally {
                playersProcessingShopDisplays.remove(player.getUniqueId());
            }
        }, 1);
    }

    private void processBatchDisplayUpdates(Player player, Location playerLocation, java.util.HashSet<Location> shopLocations) {
        if (!player.isOnline()) return;

        plugin.getLogger().debug("Processing batch display update for " + player.getName() +
                " at " + playerLocation.getWorld().getName() +
                " [" + playerLocation.getBlockX() + "," + playerLocation.getBlockY() + "," + playerLocation.getBlockZ() + "]" +
                " with " + shopLocations.size() + " nearby shops");

        java.util.HashSet<Location> displaysToShow = new java.util.HashSet<>();
        java.util.HashSet<Location> displaysToRemove = new java.util.HashSet<>();

        for (Location shopLocation : shopLocations) {
            AbstractShop shop = storage.getShop(shopLocation);
            if (shop == null) continue;

            double distance = playerLocation.distance(shop.getSignLocation());

            if (distance < plugin.getMaxShopDisplayDistance()) {
                displaysToShow.add(shopLocation);
            } else {
                displaysToRemove.add(shopLocation);
            }
        }

        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            java.util.HashSet<Location> activeDisplays = new java.util.HashSet<>(playersWithActiveShopDisplays.get(player.getUniqueId()));
            for (Location displayLocation : activeDisplays) {
                if (!shopLocations.contains(displayLocation)) {
                    displaysToRemove.add(displayLocation);
                }
            }
        }

        for (Location locationToRemove : displaysToRemove) {
            AbstractShop shop = storage.getShop(locationToRemove);
            if (shop != null) {
                shop.getDisplay().remove(player);
                removeActiveShopDisplay(player, locationToRemove);
            }
        }

        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            java.util.List<Map.Entry<Location, Double>> sortedLocations = new ArrayList<>();

            for (Location locationToShow : displaysToShow) {
                if (!hasActiveDisplay(player, locationToShow)) {
                    double distance = playerLocation.distance(locationToShow);
                    sortedLocations.add(new AbstractMap.SimpleEntry<>(locationToShow, distance));
                }
            }

            sortedLocations.sort(java.util.Comparator.comparing(Map.Entry::getValue));

            int batchSize = plugin.getDisplayBatchSize();
            int batchDelay = plugin.getDisplayBatchDelay();
            int totalBatches = (sortedLocations.size() + batchSize - 1) / batchSize;

            plugin.getLogger().debug("Creating " + sortedLocations.size() + " displays in " + totalBatches + " batches for " + player.getName());

            for (int batch = 0; batch < totalBatches; batch++) {
                final int currentBatch = batch;

                plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
                    if (!player.isOnline()) return;

                    int startIndex = currentBatch * batchSize;
                    int endIndex = Math.min(startIndex + batchSize, sortedLocations.size());

                    for (int i = startIndex; i < endIndex; i++) {
                        Location locationToShow = sortedLocations.get(i).getKey();
                        AbstractShop shop = storage.getShop(locationToShow);

                        if (shop != null && player.isOnline()) {
                            shop.getDisplay().spawn(player);
                            addActiveShopDisplay(player, locationToShow);
                        }
                    }
                }, batch * batchDelay);
            }
        }, 2);
    }

    /**
     * Clears all ShopRevanced displays near the specified player.
     *
     * @param player The player
     */
    public void clearShopDisplaysNearPlayer(Player player) {
        playersWithActiveShopDisplays.remove(player.getUniqueId());
        lastProcessedLocations.remove(player.getUniqueId());
        playersProcessingShopDisplays.remove(player.getUniqueId());
        teleportCooldowns.remove(player.getUniqueId());
    }

    /**
     * Force ShopRevanced display processing for a player, ignoring movement threshold checks.
     *
     * @param player The player to process ShopRevanced displays for
     */
    public void forceProcessShopDisplaysNearPlayer(Player player) {
        Long lastTeleport = teleportCooldowns.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        if (lastTeleport != null && currentTime - lastTeleport < TELEPORT_COOLDOWN_MS) {
            plugin.getLogger().debug("Skipping display update for " + player.getName() + " - on teleport cooldown");
            return;
        }

        teleportCooldowns.put(player.getUniqueId(), currentTime);
        playersProcessingShopDisplays.remove(player.getUniqueId());
        lastProcessedLocations.remove(player.getUniqueId());

        plugin.getLogger().debug("Force processing ShopRevanced displays for " + player.getName() + " after teleport");

        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline()) {
                if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
                    java.util.HashSet<Location> displays = playersWithActiveShopDisplays.get(player.getUniqueId());
                    if (displays != null) {
                        plugin.getLogger().debug("Removing " + displays.size() + " existing displays for " + player.getName());
                        for (Location displayLoc : new java.util.HashSet<>(displays)) {
                            AbstractShop shop = storage.getShop(displayLoc);
                            if (shop != null) {
                                shop.getDisplay().remove(player);
                            }
                        }
                    }
                    playersWithActiveShopDisplays.remove(player.getUniqueId());
                }

                plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
                    if (player.isOnline()) {
                        processShopDisplaysNearPlayer(player);
                    }
                }, 10);
            }
        }, 1);
    }

    /**
     * Checks if a player has an active display at the specified ShopRevanced location.
     *
     * @param player           The player to check
     * @param shopSignLocation The ShopRevanced sign location
     * @return true if the player has an active display
     */
    public boolean hasActiveDisplay(Player player, Location shopSignLocation) {
        java.util.HashSet<Location> shops = playersWithActiveShopDisplays.get(player.getUniqueId());
        return shops != null && shops.contains(shopSignLocation);
    }

    /**
     * Adds an active ShopRevanced display for a player.
     *
     * @param player           The player
     * @param shopSignLocation The ShopRevanced sign location
     */
    public void addActiveShopDisplay(Player player, Location shopSignLocation) {
        java.util.HashSet<Location> shops;
        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
        } else {
            shops = new java.util.HashSet<>();
        }
        shops.add(shopSignLocation);
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    /**
     * Removes an active ShopRevanced display for a player.
     *
     * @param player           The player
     * @param shopSignLocation The ShopRevanced sign location
     */
    public void removeActiveShopDisplay(Player player, Location shopSignLocation) {
        java.util.HashSet<Location> shops;
        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
            shops.remove(shopSignLocation);
        } else {
            shops = new java.util.HashSet<>();
        }
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    /**
     * Adds an active ShopRevanced display tag for a player.
     *
     * @param player           The player
     * @param shopSignLocation The ShopRevanced sign location
     */
    public void addActiveShopDisplayTag(Player player, Location shopSignLocation) {
        if (playersActiveShopDisplayTag.containsKey(player.getUniqueId())) {
            Location oldShopSignLocation = playersActiveShopDisplayTag.get(player.getUniqueId());

            if (!oldShopSignLocation.equals(shopSignLocation)) {
                AbstractShop oldShop = storage.getShop(oldShopSignLocation);
                if (oldShop != null && oldShop.getDisplay() != null) {
                    plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
                        if (player.isOnline()) {
                            oldShop.getDisplay().removeDisplayEntities(player, true);
                        }
                    }, 1);
                }
            }
        }

        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline()) {
                playersActiveShopDisplayTag.put(player.getUniqueId(), shopSignLocation);
            }
        }, 2);
    }

    /**
     * Removes all ShopRevanced displays for the specified player.
     * <p>
     * Only iterates shops the player actually has active displays for,
     * tracked via {@link #playersWithActiveShopDisplays}, instead of
     * scanning every shop on the server. When {@code player} is {@code null}
     * (e.g. during {@code onDisable}), falls back to all shops.
     *
     * @param player The player, or null to remove all displays for all players
     */
    public void removeAllDisplays(Player player) {
        if (player != null) {
            java.util.HashSet<Location> activeDisplays = playersWithActiveShopDisplays.get(player.getUniqueId());
            if (activeDisplays != null) {
                for (Location displayLoc : new java.util.HashSet<>(activeDisplays)) {
                    AbstractShop shop = storage.getShop(displayLoc);
                    if (shop != null) {
                        shop.getDisplay().remove(player);
                    }
                }
                playersWithActiveShopDisplays.remove(player.getUniqueId());
            }
            return;
        }
        // null player: full cleanup (e.g. onDisable)
        for (AbstractShop shop : storage.getAllShops()) {
            shop.getDisplay().remove(null);
        }
    }

    /**
     * Removes legacy display entities.
     * <p>
     * Instead of scanning every entity in every world (O(worlds × entities)),
     * only cleans up display entities belonging to shops we track, plus signs
     * for shops in loaded chunks.
     */
    public void removeLegacyDisplays() {
            // Update signs for shops in loaded chunks.
        for (AbstractShop shop : storage.getAllShops()) {
            if (UtilMethods.isChunkLoaded(shop.getChestLocation())) {
                shop.updateSign();
            }
        }
        for (AbstractShop shop : storage.getAllShops()) {
            shop.getDisplay().remove(null);
        }

            // Server-wide sweep for orphaned legacy display entities/items left behind by
            // older plugin versions. Runs off the main thread via the region-safe scheduler
            // so it never blocks the caller or accesses entities outside a region context.
            // This is intentionally only invoked explicitly (admin-only /shoprevanced itemrefresh
            // and reload), not on every tick.
            plugin.getFoliaLib().getScheduler().runAsync(task -> {
                for (World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (DisplayUtil.isDisplay(entity)) {
                            entity.remove();
                        } else if (entity.getType() == org.bukkit.entity.EntityType.ITEM) {
                            ItemMeta itemMeta = ((Item) entity).getItemStack().getItemMeta();
                            if (itemMeta != null && UtilMethods.stringStartsWithUUID(itemMeta.getDisplayName())) {
                            entity.remove();
                        }
                    }
                }
                }
            });
    }

    /**
     * Rebuilds all ShopRevanced displays in a chunk for nearby players.
     * Called after a chunk has been loaded to ensure displays are shown.
     *
     * @param chunk The chunk that was loaded
     */
    public void rebuildDisplaysInChunk(org.bukkit.Chunk chunk) {
        String chunkKey = UtilMethods.getChunkKey(chunk);
        java.util.List<Location> shopLocations = storage.getShopLocations(chunkKey);

        if (shopLocations.isEmpty()) {
            return;
        }

        for (Player player : chunk.getWorld().getPlayers()) {
            Long lastTeleport = teleportCooldowns.get(player.getUniqueId());
            if (lastTeleport != null && System.currentTimeMillis() - lastTeleport < TELEPORT_COOLDOWN_MS) {
                plugin.getLogger().debug("Skipping chunk display update for " + player.getName() + " - on teleport cooldown");
                continue;
            }

            if (isPlayerNearChunk(player, chunk, plugin.getMaxShopDisplayDistance())) {
                plugin.getLogger().debug("Rebuilding ShopRevanced displays for " + player.getName() + " in chunk " + chunkKey);
                processShopDisplaysNearPlayer(player);
            }
        }
    }

    private boolean isPlayerNearChunk(Player player, org.bukkit.Chunk chunk, double maxDistance) {
        if (!player.getWorld().equals(chunk.getWorld())) {
            return false;
        }

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        Location playerLoc = player.getLocation();
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        double distance;

        if (playerX < minX) {
            distance = minX - playerX;
        } else if (playerX > maxX) {
            distance = playerX - maxX;
        } else {
            distance = 0;
        }

        if (playerZ < minZ) {
            double zDistance = minZ - playerZ;
            distance = Math.sqrt(distance * distance + zDistance * zDistance);
        } else if (playerZ > maxZ) {
            double zDistance = playerZ - maxZ;
            distance = Math.sqrt(distance * distance + zDistance * zDistance);
        }

        return distance <= maxDistance;
    }
}

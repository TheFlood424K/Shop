package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.UtilMethods;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages shop storage, lookup, and tracking across the server.
 * <p>
 * Holds all shops in concurrent maps keyed by sign location, player UUID,
 * and chunk key. Provides lookup methods for shops by location, chest,
 * touching block, player, chunk, item type, and proximity.
 */
public class ShopStorage {

    private final ShopRevanced plugin;

    private final java.util.concurrent.ConcurrentHashMap<Location, AbstractShop> allShops = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.CopyOnWriteArrayList<Location>> playerShops = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<Location>> chunkShops = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Location>> unloadedShopsByChunk = new java.util.concurrent.ConcurrentHashMap<>();
    private final UUID adminUUID;
    private final BlockFace[] directions = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public ShopStorage(ShopRevanced plugin) {
        this.plugin = plugin;
        this.adminUUID = UUID.randomUUID();
    }

    /**
     * Gets a ShopRevanced by its sign location.
     *
     * @param loc The sign location of the ShopRevanced
     * @return The ShopRevanced at the location, or null if none exists
     */
    public AbstractShop getShop(Location loc) {
        return allShops.get(loc);
    }

    /**
     * Gets a ShopRevanced by its chest block.
     *
     * @param shopChest The chest block of the ShopRevanced
     * @return The ShopRevanced associated with the chest, or null if none exists
     */
    public AbstractShop getShopByChest(Block shopChest) {
        try {
            if (isChest(shopChest)) {
                AbstractShop shop = null;

                if (shopChest.getState() instanceof Chest) {
                    Chest chest = (Chest) shopChest.getState();
                    Object ih = chest.getInventory().getHolder();

                    if (ih instanceof DoubleChest) {
                        DoubleChest dc = (DoubleChest) ih;
                        Chest leftChest = (Chest) dc.getLeftSide();
                        Chest rightChest = (Chest) dc.getRightSide();

                        for (BlockFace direction : directions) {
                            shop = getShop(leftChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                if (leftChest.getLocation().equals(shop.getChestLocation()) || rightChest.getLocation().equals(shop.getChestLocation()))
                                    return shop;
                            }
                            shop = getShop(rightChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                if (shop.getChestLocation().equals(leftChest.getLocation()) || shop.getChestLocation().equals(rightChest.getLocation()))
                                    return shop;
                            }
                        }
                        return null;
                    }
                }

                for (BlockFace direction : directions) {
                    shop = getShop(shopChest.getRelative(direction).getLocation());
                    if (shop != null) {
                        if (shopChest.getLocation().equals(shop.getChestLocation()))
                            return shop;
                    }
                }
                return null;
            }
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warning("NoClassDefFoundError caught in getShopByChest: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Exception caught in getShopByChest: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets a ShopRevanced touching the specified block.
     *
     * @param block The block to check for adjacent shops
     * @return The ShopRevanced touching the block, or null if none exists
     */
    public AbstractShop getShopTouchingBlock(Block block) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            if (isChest(block.getRelative(face))) {
                Block shopChest = block.getRelative(face);
                for (BlockFace newFace : faces) {
                    if (shopChest.getRelative(newFace).getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
                        AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
                        if (shop != null)
                            return shop;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Adds a ShopRevanced to the handler's tracking structures.
     *
     * @param shop The ShopRevanced to add
     */
    public void addShop(AbstractShop shop) {
        AbstractShop s = getShop(shop.getSignLocation());
        if (s != null) {
            return;
        }
        allShops.put(shop.getSignLocation(), shop);

        java.util.concurrent.CopyOnWriteArrayList<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
        if (!playerShopLocations.contains(shop.getSignLocation())) {
            playerShopLocations.add(shop.getSignLocation());
            playerShops.put(shop.getOwnerUUID(), playerShopLocations);
        }

        String chunkKey = UtilMethods.getChunkKey(shop.getSignLocation());
        java.util.concurrent.CopyOnWriteArrayList<Location> chunkShopLocations = getShopLocations(chunkKey);
        if (!chunkShopLocations.contains(shop.getSignLocation())) {
            chunkShopLocations.add(shop.getSignLocation());
            chunkShops.put(chunkKey, chunkShopLocations);
        }

        plugin.getGuiHandler().reloadPlayerHeadIcon(shop);
    }

    /**
     * Removes a ShopRevanced from the handler's tracking structures.
     *
     * @param shop      The ShopRevanced to remove
     * @param forceSave Whether to force immediate save
     */
    public void removeShop(AbstractShop shop, boolean forceSave) {
        boolean changed = false;
        if (allShops.containsKey(shop.getSignLocation())) {
            allShops.remove(shop.getSignLocation());
            changed = true;
        }
        if (playerShops.containsKey(shop.getOwnerUUID())) {
            java.util.concurrent.CopyOnWriteArrayList<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
            if (playerShopLocations.contains(shop.getSignLocation())) {
                playerShopLocations.remove(shop.getSignLocation());
                if (playerShopLocations.isEmpty()) {
                    playerShops.remove(shop.getOwnerUUID());
                } else {
                    playerShops.put(shop.getOwnerUUID(), playerShopLocations);
                }
                changed = true;
            }
        }
        String chunkKey = UtilMethods.getChunkKey(shop.getSignLocation());
        if (chunkShops.containsKey(chunkKey)) {
            java.util.concurrent.CopyOnWriteArrayList<Location> chunkShopLocations = getShopLocations(chunkKey);
            if (chunkShopLocations.contains(shop.getSignLocation())) {
                chunkShopLocations.remove(shop.getSignLocation());
                if (chunkShopLocations.isEmpty()) {
                    chunkShops.remove(chunkKey);
                } else {
                    chunkShops.put(chunkKey, chunkShopLocations);
                }
                changed = true;
            }
        }

        if (changed) {
            plugin.getLogger().debug("Removed ShopRevanced internally from ShopRevancedHandler: " + shop);
        }
    }

    /**
     * Processes all unloaded shops in the specified chunk.
     *
     * @param chunk The chunk containing unloaded shops
     */
    public void processUnloadedShopsInChunk(org.bukkit.Chunk chunk) {
        String key = UtilMethods.getChunkKey(chunk);
        if (unloadedShopsByChunk.containsKey(key)) {
            List<UUID> playerUUIDs = new ArrayList<>();
            List<Location> shopLocations = getUnloadedShopsByChunk(key);
            for (Location shopLocation : shopLocations) {
                AbstractShop shop = getShop(shopLocation);
                if (shop != null) {
                    plugin.getFoliaLib().getScheduler().runAtLocation(shopLocation, task -> {
                        boolean loadSuccess = shop.load();
                        if (loadSuccess) {
                            if (!playerUUIDs.contains(shop.getOwnerUUID())) {
                                playerUUIDs.add(shop.getOwnerUUID());
                            }
                        }
                    });
                }
            }
            unloadedShopsByChunk.remove(key);
        }
    }

    /**
     * Adds a ShopRevanced to the unloaded shops list for its chunk.
     *
     * @param shop The ShopRevanced to add
     */
    public void addUnloadedShopToChunkList(AbstractShop shop) {
        String chunkKey = UtilMethods.getChunkKey(shop.getSignLocation());
        List<Location> shopLocations = getUnloadedShopsByChunk(chunkKey);
        if (!shopLocations.contains(shop.getSignLocation())) {
            shopLocations.add(shop.getSignLocation());
            unloadedShopsByChunk.put(chunkKey, shopLocations);
        }
    }

    /**
     * Gets all shops on the server.
     *
     * @return List of all shops
     */
    public List<AbstractShop> getAllShops() {
        return allShops.values().stream().collect(
                Collectors.toCollection(ArrayList::new)
        );
    }

    /**
     * Gets all shops owned by the specified player.
     *
     * @param player The player's UUID
     * @return List of shops owned by the player
     */
    public List<AbstractShop> getShops(UUID player) {
        List<AbstractShop> shops = new ArrayList<>();
        for (Location shopSign : getShopLocations(player)) {
            AbstractShop shop = getShop(shopSign);
            if (shop != null)
                shops.add(shop);
        }
        return shops;
    }

    /**
     * Gets all shops selling or buying the specified item.
     *
     * @param itemStack The item to search for
     * @return List of shops dealing with the item
     */
    public List<AbstractShop> getShopsByItem(ItemStack itemStack) {
        List<AbstractShop> shops = new ArrayList<>();
        for (AbstractShop shop : allShops.values()) {
            if (shop.getItemStack() != null && shop.getItemStack().getType() == itemStack.getType())
                shops.add(shop);
            else if (shop.getSecondaryItemStack() != null && shop.getSecondaryItemStack().getType() == itemStack.getType())
                shops.add(shop);
        }
        return shops;
    }

    /**
     * Gets all offline players that own shops.
     *
     * @return List of ShopRevanced owners
     */
    public List<OfflinePlayer> getShopOwners() {
        ArrayList<OfflinePlayer> owners = new ArrayList<>();
        for (UUID player : playerShops.keySet()) {
            owners.add(Bukkit.getOfflinePlayer(player));
        }
        return owners;
    }

    /**
     * Gets all UUIDs of players that own shops.
     *
     * @return List of ShopRevanced owner UUIDs
     */
    public List<UUID> getShopOwnerUUIDs() {
        ArrayList<UUID> owners = new ArrayList<>();
        for (UUID player : playerShops.keySet()) {
            owners.add(player);
        }
        return owners;
    }

    /**
     * Gets ShopRevanced locations for a player.
     *
     * @param player The player's UUID
     * @return List of ShopRevanced locations
     */
    java.util.concurrent.CopyOnWriteArrayList<Location> getShopLocations(UUID player) {
        java.util.concurrent.CopyOnWriteArrayList<Location> shopLocations;
        if (playerShops.containsKey(player)) {
            shopLocations = playerShops.get(player);
        } else {
            shopLocations = new java.util.concurrent.CopyOnWriteArrayList<>();
        }
        return shopLocations;
    }

    /**
     * Gets ShopRevanced locations for a chunk.
     *
     * @param chunkKey The chunk key
     * @return List of ShopRevanced locations
     */
    java.util.concurrent.CopyOnWriteArrayList<Location> getShopLocations(String chunkKey) {
        java.util.concurrent.CopyOnWriteArrayList<Location> shopLocations;
        if (chunkShops.containsKey(chunkKey)) {
            shopLocations = chunkShops.get(chunkKey);
        } else {
            shopLocations = new java.util.concurrent.CopyOnWriteArrayList<>();
        }
        return shopLocations;
    }

    /**
     * Gets ShopRevanced locations near a specific location within a default radius of 1 chunk.
     *
     * @param location The center location to search around
     * @return HashSet of ShopRevanced locations in the surrounding chunks
     */
    public java.util.HashSet<Location> getShopLocationsNearLocation(Location location) {
        return getShopLocationsNearLocation(location, plugin.getShopSearchRadius());
    }

    /**
     * Gets ShopRevanced locations near a specific location within a specified chunk radius.
     *
     * @param location   The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     * @return HashSet of ShopRevanced locations in the surrounding chunks
     */
    public java.util.HashSet<Location> getShopLocationsNearLocation(Location location, int chunkRadius) {
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("Chunk radius cannot be negative");
        }

        int chunkX = UtilMethods.getChunkX(location);
        int chunkZ = UtilMethods.getChunkZ(location);
        String worldName = location.getWorld().getName();

        java.util.HashSet<Location> shopsNearLocation = new java.util.HashSet<>();

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                String chunkKey = UtilMethods.createChunkKey(worldName, chunkX + x, chunkZ + z);
                List<Location> shopLocations = getShopLocations(chunkKey);
                shopsNearLocation.addAll(shopLocations);
            }
        }

        return shopsNearLocation;
    }

    /**
     * Gets actual ShopRevanced objects near a specific location within the default radius.
     *
     * @param location The center location to search around
     * @return List of shops in the surrounding chunks
     */
    public List<AbstractShop> getShopsNearLocation(Location location) {
        return getShopsNearLocation(location, plugin.getShopSearchRadius());
    }

    /**
     * Gets actual ShopRevanced objects near a specific location within a specified chunk radius.
     *
     * @param location   The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     * @return List of shops in the surrounding chunks
     */
    public List<AbstractShop> getShopsNearLocation(Location location, int chunkRadius) {
        List<AbstractShop> shopsNearLocation = new ArrayList<>();

        for (Location shopLocation : getShopLocationsNearLocation(location, chunkRadius)) {
            AbstractShop shop = getShop(shopLocation);
            if (shop != null) {
                shopsNearLocation.add(shop);
            }
        }

        return shopsNearLocation;
    }

    /**
     * Gets ShopRevanced locations near a specific location within a specified chunk radius,
     * filtered by maximum distance in blocks.
     *
     * @param location          The center location to search around
     * @param chunkRadius       The radius (in chunks) to search around the center location
     * @param maxDistanceSquared The maximum squared distance (in blocks) to include shops
     * @return HashSet of ShopRevanced locations within the distance limit
     */
    public java.util.HashSet<Location> getShopLocationsNearLocationWithinDistance(Location location, int chunkRadius, double maxDistanceSquared) {
        java.util.HashSet<Location> nearbyLocations = getShopLocationsNearLocation(location, chunkRadius);
        java.util.HashSet<Location> filteredLocations = new java.util.HashSet<>();

        for (Location shopLocation : nearbyLocations) {
            try {
                if (location.distanceSquared(shopLocation) <= maxDistanceSquared) {
                    filteredLocations.add(shopLocation);
                }
            } catch (Exception e) {
                // distanceSquared does not exist in MockBukkit
            }
        }

        return filteredLocations;
    }

    /**
     * Gets actual ShopRevanced objects near a specific location within a specified radius in blocks.
     *
     * @param location   The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     * @param maxDistance The maximum distance (in blocks) to include shops
     * @return List of shops within the distance limit
     */
    public List<AbstractShop> getShopsNearLocationWithinDistance(Location location, int chunkRadius, double maxDistance) {
        List<AbstractShop> shops = new ArrayList<>();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (Location shopLocation : getShopLocationsNearLocationWithinDistance(location, chunkRadius, maxDistanceSquared)) {
            AbstractShop shop = getShop(shopLocation);
            if (shop != null) {
                shops.add(shop);
            }
        }

        return shops;
    }

    List<Location> getUnloadedShopsByChunk(String chunkKey) {
        List<Location> unloadedShopsInChunk;
        if (unloadedShopsByChunk.containsKey(chunkKey)) {
            unloadedShopsInChunk = unloadedShopsByChunk.get(chunkKey);
        } else {
            unloadedShopsInChunk = new ArrayList<>();
        }
        return unloadedShopsInChunk;
    }

    /**
     * Gets the total number of shops.
     *
     * @return total number of shops
     */
    public int getNumberOfShops() {
        return allShops.size();
    }

    /**
     * Gets the number of shops owned by the specified player.
     *
     * @param player The player
     * @return number of shops owned by the player
     */
    public int getNumberOfShops(org.bukkit.entity.Player player) {
        return getShopLocations(player.getUniqueId()).size();
    }

    /**
     * Gets the number of shops owned by the player with the specified UUID.
     *
     * @param playerUUID The player's UUID
     * @return number of shops owned by the player
     */
    public int getNumberOfShops(UUID playerUUID) {
        return getShopLocations(playerUUID).size();
    }

    /**
     * Gets the number of shops of the specified type.
     *
     * @param shopType The ShopRevanced type
     * @return number of shops with the specified type
     */
    public int getNumberOfShops(ShopType shopType) {
        int shopsWithType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getType() == shopType) {
                shopsWithType++;
            }
        }
        return shopsWithType;
    }

    /**
     * Gets the number of shops using the specified display type.
     *
     * @param displayType The display type
     * @return number of shops with the specified display type
     */
    public int getNumberOfShopDisplayTypes(DisplayType displayType) {
        int shopsWithDisplayType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getDisplay().getType() == displayType) {
                shopsWithDisplayType++;
            }
        }
        return shopsWithDisplayType;
    }

    /**
     * Gets counts of shops grouped by container type.
     *
     * @return map of container type names to counts
     */
    public Map<String, Integer> getShopContainerCounts() {
        int chestShops = 0;
        int barrelShops = 0;
        int shulkerBoxShops = 0;
        for (AbstractShop shop : allShops.values()) {
            org.bukkit.Material containerType = shop.getContainerType();
            if (containerType == null) continue;
            if (containerType == org.bukkit.Material.CHEST || containerType == org.bukkit.Material.TRAPPED_CHEST
                    || containerType.name().endsWith("COPPER_CHEST")) {
                chestShops++;
            }
            if (containerType == org.bukkit.Material.BARREL) {
                barrelShops++;
            }
            if (containerType.name().endsWith("SHULKER_BOX")) {
                shulkerBoxShops++;
            }
        }
        Map<String, Integer> containerTypes = new HashMap<>();
        containerTypes.put("Chest Shops", chestShops);
        containerTypes.put("Barrel Shops", barrelShops);
        containerTypes.put("Shulker Box Shops", shulkerBoxShops);
        return containerTypes;
    }

    /**
     * Gets the admin UUID.
     *
     * @return the admin UUID
     */
    public UUID getAdminUUID() {
        return adminUUID;
    }

    /**
     * Gets the number of shops owned by the specified player that need saving.
     *
     * @param playerUUID The player's UUID
     * @return number of shops that need saving
     */
    public int numShopsNeedSave(UUID playerUUID) {
        int needToBeSaved = 0;
        for (Location shopSign : getShopLocations(playerUUID)) {
            AbstractShop shop = getShop(shopSign);
            if (shop != null && shop.needsSave()) {
                needToBeSaved++;
            }
        }
        return needToBeSaved;
    }

    /**
     * Checks if a block is a chest/container that can be used for shops.
     *
     * @param b The block
     * @return true if the block is a valid ShopRevanced container
     */
    public boolean isChest(Block b) {
        return plugin.getEnabledContainers().contains(b.getType());
    }

    // Package-private accessor for ShopSaveLoad to use saveShops
    void saveShops(UUID player, boolean force) {
        // Delegated to ShopSaveLoad via ShopRevancedHandler
    }
}

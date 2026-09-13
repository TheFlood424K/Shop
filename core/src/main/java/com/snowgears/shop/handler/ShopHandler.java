package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ItemListType;
import com.snowgears.shop.util.ItemStackUtils;
import com.snowgears.shop.util.PlayerNameCache;
import com.snowgears.shop.util.ShopLogger;
import com.snowgears.shop.util.UtilMethods;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


public class ShopHandler {

    public Shop plugin;
    private Class<?> displayClass;

    private ConcurrentHashMap<Location, AbstractShop> allShops = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, List<Location>> playerShops = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<Location>> chunkShops = new ConcurrentHashMap<>(); //String key = world_x_z
    private ConcurrentHashMap<UUID, HashSet<Location>> playersWithActiveShopDisplays = new ConcurrentHashMap<>();
    private Set<UUID> playersProcessingShopDisplays = ConcurrentHashMap.newKeySet();
    private ConcurrentHashMap<UUID, Location> playersActiveShopDisplayTag = new ConcurrentHashMap<>();

    //all loading of shops happens async at onEnable()
    //shops that still need to calculate their facing direction based on sign are considered "unloaded"
    //we will be loading these shops at time of chunkload and resaving them so they are saved with the 'facing' variable
    private ConcurrentHashMap<String, List<Location>> unloadedShopsByChunk = new ConcurrentHashMap<>();
    private UUID adminUUID;
    private BlockFace[] directions = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private ArrayList<ItemStack> itemListItems = new ArrayList<>();

    // Map to track player last processed locations for movement-based display updates
    private ConcurrentHashMap<UUID, Location> lastProcessedLocations = new ConcurrentHashMap<>();

    // Teleport cooldown map to prevent multiple display updates during teleportation
    private ConcurrentHashMap<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    // Cooldown time in milliseconds (500ms = half a second)
    private static final long TELEPORT_COOLDOWN_MS = 500;

    public ShopHandler(Shop instance) {
        plugin = instance;
        adminUUID = UUID.randomUUID();
        initDisplayClass();
        initItemList();

        plugin.getFoliaLib().getScheduler().runLater(() -> {
            loadShops();
        }, 10);
    }

    public void disableDisplayClass() {
        try {
            final Class<?> clazz = Class.forName("com.snowgears.shop.display.DisplayDisabled");
            if (AbstractDisplay.class.isAssignableFrom(clazz))
                this.displayClass = clazz;
        } catch (final Exception e) {
            Shop.getPlugin().getLogger().severe("Failed to load DisplayDisabled class.");
            Shop.getPlugin().onDisable();
        } catch (Error e) {
            Shop.getPlugin().getLogger().severe("Failed to load DisplayDisabled class.");
            Shop.getPlugin().onDisable();
        }
    }

    private boolean initDisplayClass(){
        try {
            Shop.getPlugin().getLogger().info("Using item display handler - com.snowgears.shop.display.Display");
            final Class<?> clazz = Class.forName("com.snowgears.shop.display.Display");
            if (AbstractDisplay.class.isAssignableFrom(clazz)) {
                this.displayClass = clazz;
                return true;
            }
        } catch (final Exception e) {
            Shop.getPlugin().getLogger().severe("Error while loading 'com.snowgears.shop.display.Display'. " + e.getMessage());
            e.printStackTrace();
            disableDisplayClass();
            return false;
        } catch (Error e) {
            Shop.getPlugin().getLogger().severe("Error while loading 'com.snowgears.shop.display.Display'. " + e.getMessage());
            e.printStackTrace();
            disableDisplayClass();
            return false;
        }
        Shop.getPlugin().getLogger().severe("Unknown issue loading display class, disabling display features.");
        disableDisplayClass();
        return false;
    }

    public AbstractDisplay createDisplay(Location loc){
        try {
            AbstractDisplay display = (AbstractDisplay) displayClass.getConstructor(Location.class).newInstance(loc);
            return display;
        } catch (Exception e){
            plugin.getLogger().warning("Error creating display at | World: " + loc.getWorld().getName() + " at " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ());
        }
        return null;
    }

    public AbstractShop getShop(Location loc) {
        return allShops.get(loc);
    }

    public AbstractShop getShopByChest(Block shopChest) {

        try {
            if(isChest(shopChest)) {

                AbstractShop shop = null;
                InventoryHolder ih = null;

                //if the shop is a single chest or double chest, add the chest blocks to check
                if (shopChest.getState() instanceof Chest) {
                    Chest chest = (Chest) shopChest.getState();
                    ih = chest.getInventory().getHolder();

                    if (ih instanceof DoubleChest) {

                        DoubleChest dc = (DoubleChest) ih;
                        Chest leftChest = (Chest) dc.getLeftSide();
                        Chest rightChest = (Chest) dc.getRightSide();

                        for (BlockFace direction : directions) {
                            shop = this.getShop(leftChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                //make sure the shop sign you found is actually attached to the correct shop
                                if (leftChest.getLocation().equals(shop.getChestLocation()) || rightChest.getLocation().equals(shop.getChestLocation()))
                                    return shop;
                            }
                            shop = this.getShop(rightChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                //make sure the shop sign you found is actually attached to the correct shop
                                if (shop.getChestLocation().equals(leftChest.getLocation()) || shop.getChestLocation().equals(rightChest.getLocation()))
                                    return shop;
                            }
                        }
                        return null;
                    }
                }

                for (BlockFace direction : directions) {
                    shop = this.getShop(shopChest.getRelative(direction).getLocation());
                    if (shop != null) {
                        //make sure the shop sign you found is actually attached to the correct shop
                        if (shopChest.getLocation().equals(shop.getChestLocation()))
                            return shop;
                    }
                }
                return null;
            }
        } catch (NoClassDefFoundError e) {}

        return null;
    }

    public AbstractShop getShopTouchingBlock(Block block){
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for(BlockFace face : faces){
            if(this.isChest(block.getRelative(face))){
                Block shopChest = block.getRelative(face);
                for(BlockFace newFace : faces){
                    if(shopChest.getRelative(newFace).getBlockData() instanceof WallSign){
                        AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
                        if(shop != null)
                            return shop;
                    }
                }
            }
        }
        return null;
    }

    public void addShop(AbstractShop shop) {

        //this is to remove a bug that caused one shop to be saved to multiple files at one point
        AbstractShop s = getShop(shop.getSignLocation());
        if(s != null) {
            return;
        }
        allShops.put(shop.getSignLocation(), shop);

        List<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
        if(!playerShopLocations.contains(shop.getSignLocation())) {
            playerShopLocations.add(shop.getSignLocation());
            playerShops.put(shop.getOwnerUUID(), playerShopLocations);
        }

        String chunkKey = UtilMethods.getChunkKey(shop.getSignLocation());
        List<Location> chunkShopLocations = getShopLocations(chunkKey);
        if(!chunkShopLocations.contains(shop.getSignLocation())) {
            chunkShopLocations.add(shop.getSignLocation());
            chunkShops.put(chunkKey, chunkShopLocations);
        }

        plugin.getGuiHandler().reloadPlayerHeadIcon(shop);
    }

    //This method should only be used by AbstractShop object to delete
    public void removeShop(AbstractShop shop, boolean forceSave) {
        boolean changed = false;
        if (allShops.containsKey(shop.getSignLocation())) {
            allShops.remove(shop.getSignLocation());
            changed = true;
        }
        if(playerShops.containsKey(shop.getOwnerUUID())){
            List<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
            if(playerShopLocations.contains(shop.getSignLocation())) {
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
        if(chunkShops.containsKey(chunkKey)){
            List<Location> chunkShopLocations = getShopLocations(chunkKey);
            if(chunkShopLocations.contains(shop.getSignLocation())) {
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
            Shop.getPlugin().getLogger().debug("Removed Shop internally from ShopHandler: " + shop);
            // Immediate force save if there were any changes since we deleted a shop 
            // Note that we don't pass forceSave down, it is only a flag on if we should trigger the save attempt immediately
            // we only hold off on doing this if we are bulk deleting shops for users to prevent repeated saves.
            // The forceSave flag should rarely be `false`, and you should be careful when setting it to false.
            if (forceSave) {
                this.saveShops(shop.getOwnerUUID(), true);
            }
        }
    }

    public void processUnloadedShopsInChunk(Chunk chunk){
        String key = UtilMethods.getChunkKey(chunk);
        if(unloadedShopsByChunk.containsKey(key)){
            List<UUID> playerUUIDs = new ArrayList<>();
            List<Location> shopLocations = getUnloadedShopsByChunk(key);
            for(Location shopLocation : shopLocations) {
                AbstractShop shop = getShop(shopLocation);
                if(shop != null){
                    // Run at the shop's location to ensure it works in the correct region in Folia
                    plugin.getFoliaLib().getScheduler().runAtLocation(shopLocation, task -> {
                        boolean loadSuccess = shop.load();
                        if(loadSuccess) {
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

    public void addUnloadedShopToChunkList(AbstractShop shop){
        String chunkKey = UtilMethods.getChunkKey(shop.getSignLocation());
        List<Location> shopLocations = getUnloadedShopsByChunk(chunkKey);
        if(!shopLocations.contains(shop.getSignLocation())) {
            shopLocations.add(shop.getSignLocation());
            unloadedShopsByChunk.put(chunkKey, shopLocations);
        }
    }

    public List<AbstractShop> getAllShops(){
        return allShops.values().stream().collect(
                Collectors.toCollection(ArrayList::new)
        );
    }

    public List<AbstractShop> getShops(UUID player){
        List<AbstractShop> shops = new ArrayList<>();
        for(Location shopSign : getShopLocations(player)){
            AbstractShop shop = getShop(shopSign);
            if(shop != null)
                shops.add(shop);
        }
        return shops;
    }

    public int numShopsNeedSave(UUID player){
        List<AbstractShop> shops = getShops(player);

        // Default does not need to be saved;
        int needToBeSaved = 0;
        for (AbstractShop shop : shops) {
            if (shop.needsSave()) { needToBeSaved++; }
        }

        return needToBeSaved;
    }

    public List<AbstractShop> getShopsByItem(ItemStack itemStack){
        List<AbstractShop> shops = new ArrayList<>();
        for(AbstractShop shop : allShops.values()){
            if(shop.getItemStack() != null && shop.getItemStack().getType() == itemStack.getType())
                shops.add(shop);
            else if(shop.getSecondaryItemStack() != null && shop.getSecondaryItemStack().getType() == itemStack.getType())
                shops.add(shop);
        }
        return shops;
    }

    // Note: this is resource intensive on large servers, maybe refactor at some point
    public List<OfflinePlayer> getShopOwners(){
        ArrayList<OfflinePlayer> owners = new ArrayList<>();
        for(UUID player : playerShops.keySet()) {
            owners.add(Bukkit.getOfflinePlayer(player));
        }
        return owners;
    }

    public List<UUID> getShopOwnerUUIDs(){
        ArrayList<UUID> owners = new ArrayList<>();
        for(UUID player : playerShops.keySet()) {
            owners.add(player);
        }
        return owners;
    }

    private List<Location> getShopLocations(UUID player){
        List<Location> shopLocations;
        if(playerShops.containsKey(player)) {
            shopLocations = playerShops.get(player);
        }
        else
            shopLocations = new ArrayList<>();
        return shopLocations;
    }

    private List<Location> getShopLocations(String chunkKey){
        List<Location> shopLocations;
        if(chunkShops.containsKey(chunkKey)) {
            shopLocations = chunkShops.get(chunkKey);
        }
        else {
            shopLocations = new ArrayList<>();
        }
        return shopLocations;
    }

    /**
     * Gets shop locations near a specific location within a default radius of 1 chunk
     * (which covers a 3x3 chunk area)
     * 
     * @param location The center location to search around
     * @return HashSet of shop locations in the surrounding chunks
     */
    public HashSet<Location> getShopLocationsNearLocation(Location location) {
        return getShopLocationsNearLocation(location, plugin.getShopSearchRadius());
    }

    /**
     * Gets shop locations near a specific location within a specified chunk radius
     * 
     * @param location The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     *                    A radius of 1 means a 3x3 chunk area, 2 means 5x5, etc.
     * @return HashSet of shop locations in the surrounding chunks
     */
    public HashSet<Location> getShopLocationsNearLocation(Location location, int chunkRadius) {
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("Chunk radius cannot be negative");
        }
        
        int chunkX = UtilMethods.getChunkX(location);
        int chunkZ = UtilMethods.getChunkZ(location);
        String worldName = location.getWorld().getName();
        
        HashSet<Location> shopsNearLocation = new HashSet<>();
        
        // Loop through all chunks in the specified radius
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
     * Gets actual shop objects near a specific location within the default radius
     * 
     * @param location The center location to search around
     * @return List of shops in the surrounding chunks
     */
    public List<AbstractShop> getShopsNearLocation(Location location) {
        return getShopsNearLocation(location, plugin.getShopSearchRadius());
    }

    /**
     * Gets actual shop objects near a specific location within a specified chunk radius
     * 
     * @param location The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     * @return List of shops in the surrounding chunks
     */
    public List<AbstractShop> getShopsNearLocation(Location location, int chunkRadius) {
        List<AbstractShop> shopsNearLocation = new ArrayList<>();
        
        // Get shop locations in the specified radius
        for (Location shopLocation : getShopLocationsNearLocation(location, chunkRadius)) {
            AbstractShop shop = getShop(shopLocation);
            if (shop != null) {
                shopsNearLocation.add(shop);
            }
        }
        
        return shopsNearLocation;
    }

    /**
     * Gets shop locations near a specific location within a specified chunk radius,
     * filtered by maximum distance in blocks.
     * 
     * @param location The center location to search around
     * @param chunkRadius The radius (in chunks) to search around the center location
     * @param maxDistanceSquared The maximum squared distance (in blocks) to include shops
     *                          Using squared distance avoids expensive square root calculations
     * @return HashSet of shop locations within the distance limit
     */
    public HashSet<Location> getShopLocationsNearLocationWithinDistance(Location location, int chunkRadius, double maxDistanceSquared) {
        HashSet<Location> nearbyLocations = getShopLocationsNearLocation(location, chunkRadius);
        HashSet<Location> filteredLocations = new HashSet<>();
        
        // Filter by distance
        for (Location shopLocation : nearbyLocations) {
            // Using distanceSquared is more efficient than distance
            try {
                if (location.distanceSquared(shopLocation) <= maxDistanceSquared) {
                    filteredLocations.add(shopLocation);
                }
            } catch (Exception e) {
                // distanceSquared does not exist in MockBukkit and this is the easiest way to disable it
            }
        }
        
        return filteredLocations;
    }

    /**
     * Gets actual shop objects near a specific location within a specified radius in blocks
     * 
     * @param location The center location to search around
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

    /**
     * Checks whether {@code item} is permitted by the configured item list.
     *
     * <p>Before comparing, both the candidate item and each list entry are passed through
     * {@link ItemStackUtils#stripFontFromItem(ItemStack)} so that items whose display-name
     * components carry a custom Adventure font key are not accidentally blocked when the
     * list entry was saved without a font.  If the list entry has no {@link ItemMeta} at
     * all, the comparison falls back to a plain material-type check.
     *
     * <p>When the item list is empty the method always returns {@code true} (no restriction).
     * The list acts as an allowlist when {@link ItemListType#WHITELIST} is configured, and as
     * a blocklist when {@link ItemListType#BLACKLIST} is configured.
     *
     * @param item the item being checked — may be {@code null}, in which case {@code false} is returned
     * @return {@code true} if the item passes the list check
     */
    public boolean passesItemListCheck(ItemStack item) {
        if (item == null) return false;
        if (itemListItems.isEmpty()) return true;

        // Normalise the candidate: strip any custom font so comparisons are font-agnostic.
        ItemStack normCandidate = ItemStackUtils.stripFontFromItem(item);

        boolean foundMatch = false;
        for (ItemStack listEntry : itemListItems) {
            if (listEntry == null) continue;

            ItemStack normEntry = ItemStackUtils.stripFontFromItem(listEntry);

            if (normEntry.getItemMeta() != null) {
                // Full meta comparison (type + display name + lore + enchants etc.),
                // but with fonts stripped from both sides.
                if (normEntry.isSimilar(normCandidate)) {
                    foundMatch = true;
                    break;
                }
            } else {
                // List entry has no meta — type-only match is sufficient.
                if (normEntry.getType() == normCandidate.getType()) {
                    foundMatch = true;
                    break;
                }
            }
        }

        ItemListType listType = plugin.getItemListType();
        if (listType == ItemListType.WHITELIST) {
            return foundMatch;
        } else {
            // BLACKLIST: item passes if it was NOT found in the list
            return !foundMatch;
        }
    }

    public void processShopDisplaysNearPlayer(Player player){
        // If the player is already being processed, don't start another process
        if (playersProcessingShopDisplays.contains(player.getUniqueId())) {
            return;
        }
        
        // Get current player location
        Location currentLocation = player.getLocation();
        
        // Check if player has moved enough to warrant processing
        Location lastLocation = lastProcessedLocations.get(player.getUniqueId());
        double movementThreshold = plugin.getDisplayMovementThreshold();
        
        // Skip processing if player hasn't moved enough and this isn't the first check
        if (lastLocation != null && 
            lastLocation.getWorld().equals(currentLocation.getWorld()) && 
            lastLocation.distanceSquared(currentLocation) < (movementThreshold * movementThreshold)) {
            return;
        }
        
        // Mark player as being processed to prevent concurrent processing
        playersProcessingShopDisplays.add(player.getUniqueId());
        
        // Schedule display processing task at the player's entity
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            try {
                // Use a local variable for current location to avoid race conditions
                Location playerLocation = player.getLocation();
                
                // Update the last processed location immediately to prevent multiple processings
                lastProcessedLocations.put(player.getUniqueId(), playerLocation.clone());
                
                // Get all shop locations within the maximum display distance in one batch
                HashSet<Location> nearbyShopLocations = getShopLocationsNearLocationWithinDistance(
                    playerLocation, 
                    plugin.getShopSearchRadius(), 
                    plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance()
                );
                
                // Create a batch operation for all displays to minimize interference
                // This helps prevent the "bouncing" effect when displays are created one by one
                processBatchDisplayUpdates(player, playerLocation, nearbyShopLocations);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing shop displays for player " + player.getName());
                e.printStackTrace();
            } finally {
                // Always ensure player is removed from processing list
                playersProcessingShopDisplays.remove(player.getUniqueId());
            }
        }, 1);
    }

    /**
     * Process all shop displays in a single coordinated batch to minimize visual artifacts
     * @param player The player to update displays for
     * @param playerLocation The player's current location
     * @param shopLocations Set of shop locations to process
     */
    private void processBatchDisplayUpdates(Player player, Location playerLocation, HashSet<Location> shopLocations) {
        if (!player.isOnline()) return;
        
        // Log the processing if in debug mode
        plugin.getLogger().debug("Processing batch display update for " + player.getName() + 
            " at " + playerLocation.getWorld().getName() + 
            " [" + playerLocation.getBlockX() + "," + playerLocation.getBlockY() + "," + playerLocation.getBlockZ() + "]" +
            " with " + shopLocations.size() + " nearby shops");
        
        // First, collect all displays that need to be shown and those that need to be removed
        HashSet<Location> displaysToShow = new HashSet<>();
        HashSet<Location> displaysToRemove = new HashSet<>();
        
        // Determine which displays to show and which to remove
        for (Location shopLocation : shopLocations) {
            AbstractShop shop = getShop(shopLocation);
            if (shop == null) continue;
            
            double distance = playerLocation.distance(shop.getSignLocation());
            
            if (distance < plugin.getMaxShopDisplayDistance()) {
                // Within display distance, should be shown
                displaysToShow.add(shopLocation);
            } else {
                // Too far, should be removed
                displaysToRemove.add(shopLocation);
            }
        }
        
        // Also identify any current displays that are no longer in range
        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            HashSet<Location> activeDisplays = new HashSet<>(playersWithActiveShopDisplays.get(player.getUniqueId()));
            for (Location displayLocation : activeDisplays) {
                if (!shopLocations.contains(displayLocation)) {
                    displaysToRemove.add(displayLocation);
                }
            }
        }
        
        // Process removals first to prevent interference with new spawns
        for (Location locationToRemove : displaysToRemove) {
            AbstractShop shop = getShop(locationToRemove);
            if (shop != null) {
                shop.getDisplay().remove(player);
                removeActiveShopDisplay(player, locationToRemove);
            }
        }
        
        // Short delay before processing additions to ensure removals are complete
        // This helps prevent the visual "refresh" effect
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            // Now process additions in priority order (closest first)
            List<Map.Entry<Location, Double>> sortedLocations = new ArrayList<>();
            
            for (Location locationToShow : displaysToShow) {
                if (!hasActiveDisplay(player, locationToShow)) {
                    double distance = playerLocation.distance(locationToShow);
                    sortedLocations.add(new SimpleEntry<>(locationToShow, distance));
                }
            }
            
            // Sort by distance (closest first)
            sortedLocations.sort(Comparator.comparing(Map.Entry::getValue));
            
            // Process in distance order with small delays between batches to reduce visual clutter
            // Use configurable batch size from config
            int batchSize = plugin.getDisplayBatchSize();
            int batchDelay = plugin.getDisplayBatchDelay();
            int totalBatches = (sortedLocations.size() + batchSize - 1) / batchSize;
            
            plugin.getLogger().debug("Creating " + sortedLocations.size() + " displays in " + totalBatches + " batches for " + player.getName());
            
            for (int batch = 0; batch < totalBatches; batch++) {
                final int currentBatch = batch;
                
                // Add a configurable delay between batches
                plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
                    if (!player.isOnline()) return;
                    
                    int startIndex = currentBatch * batchSize;
                    int endIndex = Math.min(startIndex + batchSize, sortedLocations.size());
                    
                    for (int i = startIndex; i < endIndex; i++) {
                        Location locationToShow = sortedLocations.get(i).getKey();
                        AbstractShop shop = getShop(locationToShow);
                        
                        if (shop != null && player.isOnline()) {
                            shop.getDisplay().spawn(player);
                            addActiveShopDisplay(player, locationToShow);
                        }
                    }
                }, batch * batchDelay); // Configurable delay between batches
            }
        }, 2); // 2 tick delay after removals
    }

    public void clearShopDisplaysNearPlayer(Player player){
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId()))
            playersWithActiveShopDisplays.remove(player.getUniqueId());
        
        // Also remove player from last processed locations
        lastProcessedLocations.remove(player.getUniqueId());
        
        // Also remove from processing list to avoid any potential deadlocks
        playersProcessingShopDisplays.remove(player.getUniqueId());
        
        // Clear teleport cooldown as well
        teleportCooldowns.remove(player.getUniqueId());

    }

    /**
     * Force shop display processing for a player, ignoring movement threshold checks.
     * This should be called after teleportation or world changes.
     * 
     * @param player The player to process shop displays for
     */
    public void forceProcessShopDisplaysNearPlayer(Player player) {
        // Check if player is on teleport cooldown
        Long lastTeleport = teleportCooldowns.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();
        
        // If player is on cooldown, skip this update
        if (lastTeleport != null && currentTime - lastTeleport < TELEPORT_COOLDOWN_MS) {
            plugin.getLogger().debug("Skipping display update for " + player.getName() + " - on teleport cooldown");
            return;
        }
        
        // Set teleport cooldown
        teleportCooldowns.put(player.getUniqueId(), currentTime);
        
        // Remove from processing list if somehow still in there
        playersProcessingShopDisplays.remove(player.getUniqueId());
        
        // Remove any previous location tracking to force a fresh distance calculation
        lastProcessedLocations.remove(player.getUniqueId());
        
        // Now process normally - the missing last location will trigger a fresh update
        processShopDisplaysNearPlayer(player);
    }

    public boolean hasActiveDisplay(Player player, Location shopLocation) {
        if (!playersWithActiveShopDisplays.containsKey(player.getUniqueId()))
            return false;
        return playersWithActiveShopDisplays.get(player.getUniqueId()).contains(shopLocation);
    }

    public void addActiveShopDisplay(Player player, Location shopLocation) {
        HashSet<Location> activeDisplays = playersWithActiveShopDisplays.getOrDefault(player.getUniqueId(), new HashSet<>());
        activeDisplays.add(shopLocation);
        playersWithActiveShopDisplays.put(player.getUniqueId(), activeDisplays);
    }

    public void removeActiveShopDisplay(Player player, Location shopLocation) {
        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            playersWithActiveShopDisplays.get(player.getUniqueId()).remove(shopLocation);
        }
    }

    public boolean isChest(Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST;
    }

    public UUID getAdminUUID() {
        return adminUUID;
    }

    public ArrayList<ItemStack> getItemListItems() {
        return itemListItems;
    }

    private List<Location> getUnloadedShopsByChunk(String chunkKey) {
        List<Location> shopLocations;
        if (unloadedShopsByChunk.containsKey(chunkKey)) {
            shopLocations = unloadedShopsByChunk.get(chunkKey);
        } else {
            shopLocations = new ArrayList<>();
        }
        return shopLocations;
    }

    private void initItemList() {
        itemListItems.clear();
        File itemListFile = new File(plugin.getDataFolder(), "itemList.yml");
        if (!itemListFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemListFile);
        List<?> rawList = config.getList("items");
        if (rawList == null) return;

        for (Object obj : rawList) {
            if (obj instanceof ItemStack) {
                itemListItems.add((ItemStack) obj);
            }
        }
    }

    public void loadShops() {
        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        if (!shopsFolder.exists() || !shopsFolder.isDirectory()) return;

        File[] playerFolders = shopsFolder.listFiles(File::isDirectory);
        if (playerFolders == null) return;

        for (File playerFolder : playerFolders) {
            UUID playerUUID;
            try {
                playerUUID = UUID.fromString(playerFolder.getName());
            } catch (IllegalArgumentException e) {
                continue;
            }

            File[] shopFiles = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (shopFiles == null) continue;

            for (File shopFile : shopFiles) {
                try {
                    AbstractShop shop = AbstractShop.loadFromFile(plugin, shopFile, playerUUID);
                    if (shop != null) {
                        if (shop.isMissingData()) {
                            addUnloadedShopToChunkList(shop);
                        }
                        addShop(shop);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error loading shop from file: " + shopFile.getName(), e);
                }
            }
        }
    }

    public void saveShops(UUID playerUUID, boolean immediate) {
        List<AbstractShop> shops = getShops(playerUUID);
        if (shops.isEmpty()) return;

        if (immediate) {
            for (AbstractShop shop : shops) {
                if (shop.needsSave()) {
                    shop.saveToFile();
                }
            }
        } else {
            plugin.getFoliaLib().getScheduler().runLater(() -> {
                for (AbstractShop shop : shops) {
                    if (shop.needsSave()) {
                        shop.saveToFile();
                    }
                }
            }, 1);
        }
    }

    public void saveAllShops() {
        for (AbstractShop shop : getAllShops()) {
            if (shop.needsSave()) {
                shop.saveToFile();
            }
        }
    }

    public void addInventoryToItemList(PlayerInventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !itemListItems.contains(item)) {
                itemListItems.add(item.clone());
            }
        }
    }

    public void removeInventoryFromItemList(PlayerInventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                itemListItems.removeIf(listItem -> listItem != null && listItem.getType() == item.getType());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shop count helpers — used by ShopMessage placeholder lambdas
    // -------------------------------------------------------------------------

    /** Returns the total number of shops currently loaded across all players. */
    public int getNumberOfShops() {
        return allShops.size();
    }

    /** Returns the number of shops owned by the given player UUID. */
    public int getNumberOfShops(UUID playerUUID) {
        List<Location> locs = playerShops.get(playerUUID);
        return locs != null ? locs.size() : 0;
    }

    /** Convenience overload — returns the number of shops owned by the given online player. */
    public int getNumberOfShops(Player player) {
        return getNumberOfShops(player.getUniqueId());
    }
}

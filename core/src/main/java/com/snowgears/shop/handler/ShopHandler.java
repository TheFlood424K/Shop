package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.BarterShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ItemListType;
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
import org.bukkit.inventory.ItemMeta;
import org.bukkit.inventory.PlayerInventory;

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

    /**
     * Rebuild all shop displays in a given chunk (re-spawns displays for online players).
     * Called from ShopListener when a chunk is loaded or a player enters a chunk.
     */
    public void rebuildDisplaysInChunk(Chunk chunk) {
        String key = UtilMethods.getChunkKey(chunk);
        List<Location> shopLocations = getShopLocations(key);
        if (shopLocations.isEmpty()) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (Location loc : shopLocations) {
                AbstractShop shop = getShop(loc);
                if (shop == null) continue;
                // Only act on this shop if the player is within display range
                try {
                    if (player.getWorld().equals(loc.getWorld()) &&
                            player.getLocation().distanceSquared(loc) <=
                            plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance()) {
                        if (!hasActiveDisplay(player, loc)) {
                            shop.getDisplay().spawn(player);
                            addActiveShopDisplay(player, loc);
                        }
                    }
                } catch (Exception ignored) {}
            }
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

    public HashSet<Location> getShopLocationsNearLocation(Location location) {
        return getShopLocationsNearLocation(location, plugin.getShopSearchRadius());
    }

    public HashSet<Location> getShopLocationsNearLocation(Location location, int chunkRadius) {
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("Chunk radius cannot be negative");
        }
        
        int chunkX = UtilMethods.getChunkX(location);
        int chunkZ = UtilMethods.getChunkZ(location);
        String worldName = location.getWorld().getName();
        
        HashSet<Location> shopsNearLocation = new HashSet<>();
        
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                String chunkKey = UtilMethods.createChunkKey(worldName, chunkX + x, chunkZ + z);
                List<Location> shopLocations = getShopLocations(chunkKey);
                shopsNearLocation.addAll(shopLocations);
            }
        }
        
        return shopsNearLocation;
    }

    public List<AbstractShop> getShopsNearLocation(Location location) {
        return getShopsNearLocation(location, plugin.getShopSearchRadius());
    }

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

    public HashSet<Location> getShopLocationsNearLocationWithinDistance(Location location, int chunkRadius, double maxDistanceSquared) {
        HashSet<Location> nearbyLocations = getShopLocationsNearLocation(location, chunkRadius);
        HashSet<Location> filteredLocations = new HashSet<>();
        
        for (Location shopLocation : nearbyLocations) {
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

    public void processShopDisplaysNearPlayer(Player player){
        if (playersProcessingShopDisplays.contains(player.getUniqueId())) {
            return;
        }
        
        Location currentLocation = player.getLocation();
        
        Location lastLocation = lastProcessedLocations.get(player.getUniqueId());
        double movementThreshold = plugin.getDisplayMovementThreshold();
        
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
                
                HashSet<Location> nearbyShopLocations = getShopLocationsNearLocationWithinDistance(
                    playerLocation, 
                    plugin.getShopSearchRadius(), 
                    plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance()
                );
                
                processBatchDisplayUpdates(player, playerLocation, nearbyShopLocations);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing shop displays for player " + player.getName());
                e.printStackTrace();
            } finally {
                playersProcessingShopDisplays.remove(player.getUniqueId());
            }
        }, 1);
    }

    private void processBatchDisplayUpdates(Player player, Location playerLocation, HashSet<Location> shopLocations) {
        if (!player.isOnline()) return;
        
        plugin.getLogger().debug("Processing batch display update for " + player.getName() + 
            " at " + playerLocation.getWorld().getName() + 
            " [" + playerLocation.getBlockX() + "," + playerLocation.getBlockY() + "," + playerLocation.getBlockZ() + "]" +
            " with " + shopLocations.size() + " nearby shops");
        
        HashSet<Location> displaysToShow = new HashSet<>();
        HashSet<Location> displaysToRemove = new HashSet<>();
        
        for (Location shopLocation : shopLocations) {
            AbstractShop shop = getShop(shopLocation);
            if (shop == null) continue;
            
            double distance = playerLocation.distance(shop.getSignLocation());
            
            if (distance < plugin.getMaxShopDisplayDistance()) {
                displaysToShow.add(shopLocation);
            } else {
                displaysToRemove.add(shopLocation);
            }
        }
        
        if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
            HashSet<Location> activeDisplays = new HashSet<>(playersWithActiveShopDisplays.get(player.getUniqueId()));
            for (Location displayLocation : activeDisplays) {
                if (!shopLocations.contains(displayLocation)) {
                    displaysToRemove.add(displayLocation);
                }
            }
        }
        
        for (Location locationToRemove : displaysToRemove) {
            AbstractShop shop = getShop(locationToRemove);
            if (shop != null) {
                shop.getDisplay().remove(player);
                removeActiveShopDisplay(player, locationToRemove);
            }
        }
        
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            List<Map.Entry<Location, Double>> sortedLocations = new ArrayList<>();
            
            for (Location locationToShow : displaysToShow) {
                if (!hasActiveDisplay(player, locationToShow)) {
                    double distance = playerLocation.distance(locationToShow);
                    sortedLocations.add(new SimpleEntry<>(locationToShow, distance));
                }
            }
            
            sortedLocations.sort(Comparator.comparing(Map.Entry::getValue));
            
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
                        AbstractShop shop = getShop(locationToShow);
                        
                        if (shop != null && player.isOnline()) {
                            shop.getDisplay().spawn(player);
                            addActiveShopDisplay(player, locationToShow);
                        }
                    }
                }, batch * batchDelay);
            }
        }, 2);
    }

    public void clearShopDisplaysNearPlayer(Player player){
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId()))
            playersWithActiveShopDisplays.remove(player.getUniqueId());
        
        lastProcessedLocations.remove(player.getUniqueId());
        playersProcessingShopDisplays.remove(player.getUniqueId());
        teleportCooldowns.remove(player.getUniqueId());
    }

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
        
        plugin.getLogger().debug("Force processing shop displays for " + player.getName() + " after teleport");
        
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline()) {
                if (playersWithActiveShopDisplays.containsKey(player.getUniqueId())) {
                    HashSet<Location> displays = playersWithActiveShopDisplays.get(player.getUniqueId());
                    if (displays != null) {
                        plugin.getLogger().debug("Removing " + displays.size() + " existing displays for " + player.getName());
                        for (Location displayLoc : new HashSet<>(displays)) {
                            AbstractShop shop = getShop(displayLoc);
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

    public boolean hasActiveDisplay(Player player, Location shopSignLocation) { 
        HashSet<Location> shops = playersWithActiveShopDisplays.get(player.getUniqueId());
        return shops != null && shops.contains(shopSignLocation);
    }

    public void addActiveShopDisplay(Player player, Location shopSignLocation){
        HashSet<Location> shops;
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId())){
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
        }
        else{
            shops = new HashSet<>();
        }
        shops.add(shopSignLocation);
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    public void removeActiveShopDisplay(Player player, Location shopSignLocation){
        HashSet<Location> shops;
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId())){
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
            shops.remove(shopSignLocation);
        }
        else{
            shops = new HashSet<>();
        }
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    public void addActiveShopDisplayTag(Player player, Location shopSignLocation) {
        if (playersActiveShopDisplayTag.containsKey(player.getUniqueId())) {
            Location oldShopSignLocation = playersActiveShopDisplayTag.get(player.getUniqueId());

            if (!oldShopSignLocation.equals(shopSignLocation)) {
                AbstractShop oldShop = getShop(oldShopSignLocation);
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

    private List<Location> getUnloadedShopsByChunk(String chunkKey){
        List<Location> unloadedShopsInChunk;
        if(unloadedShopsByChunk.containsKey(chunkKey)) {
            unloadedShopsInChunk = unloadedShopsByChunk.get(chunkKey);
        }
        else
            unloadedShopsInChunk = new ArrayList<>();
        return unloadedShopsInChunk;
    }

    public int getNumberOfShops() {
        return allShops.size();
    }

    public int getNumberOfShops(Player player) {
        return getShopLocations(player.getUniqueId()).size();
    }

    public int getNumberOfShops(UUID playerUUID) {
        return getShopLocations(playerUUID).size();
    }

    public int getNumberOfShops(ShopType shopType) {
        int shopsWithType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getType() == shopType) { shopsWithType++; }
        }
        return shopsWithType;
    }

    public int getNumberOfShopDisplayTypes(DisplayType displayType) {
        int shopsWithDisplayType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getDisplay().getType() == displayType) { shopsWithDisplayType++; }
        }
        return shopsWithDisplayType;
    }

    public Map<String, Integer> getShopContainerCounts() {
        int chestShops = 0;
        int barrelShops = 0;
        int shulkerBoxShops = 0;
        for (AbstractShop shop : allShops.values()) {
            Material containerType = shop.getContainerType();
            if (containerType == null) continue;
            if (containerType == Material.CHEST || containerType == Material.TRAPPED_CHEST
                    || containerType.name().endsWith("COPPER_CHEST")) { chestShops++; }
            if (containerType == Material.BARREL) { barrelShops++; }
            if (containerType.name().endsWith("SHULKER_BOX")) { shulkerBoxShops++; }
        }
        Map<String, Integer> containerTypes = new HashMap<>();
        containerTypes.put("Chest Shops", chestShops);
        containerTypes.put("Barrel Shops", barrelShops);
        containerTypes.put("Shulker Box Shops", shulkerBoxShops);
        return containerTypes;
    }

    public void removeAllDisplays(Player player) {
        for (AbstractShop shop : allShops.values()) {
            shop.getDisplay().remove(player);
        }
    }

    public void removeLegacyDisplays(){
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if(DisplayUtil.isDisplay(entity)){
                    entity.remove();
                }
                //make to sure to clear items from old version of plugin too
                else if (entity.getType() == EntityType.ITEM) {
                    ItemMeta itemMeta = ((Item) entity).getItemStack().getItemMeta();
                    if (UtilMethods.stringStartsWithUUID(itemMeta.getDisplayName())) {
                        entity.remove();
                    }
                }
            }
        }
        for(UUID shopOwnerUUID : plugin.getShopHandler().getShopOwnerUUIDs()){
            for(AbstractShop shop : plugin.getShopHandler().getShops(shopOwnerUUID)){
                if(UtilMethods.isChunkLoaded(shop.getChestLocation())) {
                    plugin.getLogger().debug("[ShopHander.removeLegacyDisplays] updateSign");
                    shop.updateSign();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Item-list helpers used by CommandHandler and ShopCreationUtil
    // -----------------------------------------------------------------------

    /**
     * Returns true when the given ItemStack is allowed by the item list
     * (or when the item list feature is disabled).
     */
    public boolean passesItemListCheck(ItemStack item) {
        if (plugin.getItemListType() == ItemListType.NONE) return true;
        if (itemListItems.isEmpty()) return plugin.getItemListType() == ItemListType.BLACKLIST;
        boolean inList = false;
        for (ItemStack listItem : itemListItems) {
            if (listItem != null && listItem.getType() == item.getType()) {
                inList = true;
                break;
            }
        }
        // WHITELIST: must be in list.  BLACKLIST: must NOT be in list.
        return plugin.getItemListType() == ItemListType.WHITELIST ? inList : !inList;
    }

    /**
     * Adds all unique item types from the given inventory to the item list file
     * and reloads the in-memory list.
     */
    public void addInventoryToItemList(PlayerInventory inventory) {
        String itemListPath = plugin.getItemListPath();
        if (itemListPath == null || itemListPath.isEmpty()) return;
        File itemListFile = new File(plugin.getDataFolder(), itemListPath);
        YamlConfiguration config = itemListFile.exists()
                ? YamlConfiguration.loadConfiguration(itemListFile)
                : new YamlConfiguration();
        int nextKey = config.contains("items") ? config.getConfigurationSection("items").getKeys(false).size() + 1 : 1;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            boolean already = false;
            if (config.contains("items")) {
                for (String k : config.getConfigurationSection("items").getKeys(false)) {
                    ItemStack existing = config.getItemStack("items." + k);
                    if (existing != null && existing.getType() == stack.getType()) { already = true; break; }
                }
            }
            if (!already) {
                ItemStack toSave = stack.clone(); toSave.setAmount(1);
                config.set("items." + nextKey, toSave);
                nextKey++;
            }
        }
        try { config.save(itemListFile); } catch (IOException e) { plugin.getLogger().warning("Could not save item list: " + e.getMessage()); }
        initItemList();
    }

    /**
     * Removes all item types found in the given inventory from the item list file
     * and reloads the in-memory list.
     */
    public void removeInventoryFromItemList(PlayerInventory inventory) {
        String itemListPath = plugin.getItemListPath();
        if (itemListPath == null || itemListPath.isEmpty()) return;
        File itemListFile = new File(plugin.getDataFolder(), itemListPath);
        if (!itemListFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemListFile);
        if (!config.contains("items")) return;
        Set<String> keys = new HashSet<>(config.getConfigurationSection("items").getKeys(false));
        for (String k : keys) {
            ItemStack existing = config.getItemStack("items." + k);
            if (existing == null) continue;
            for (ItemStack stack : inventory.getContents()) {
                if (stack != null && stack.getType() == existing.getType()) {
                    config.set("items." + k, null);
                    break;
                }
            }
        }
        try { config.save(itemListFile); } catch (IOException e) { plugin.getLogger().warning("Could not save item list: " + e.getMessage()); }
        initItemList();
    }

    // -----------------------------------------------------------------------

    private boolean immediateShutdown = false;
    public int saveShops(final UUID player){ return saveShops(player, false); }
    public int saveShops(final UUID player, boolean force){
        if (this.immediateShutdown) return -5;

        String playerName = player == this.getAdminUUID() ? "admin" : plugin.getServer().getOfflinePlayer(player).getName();
        int numWantingToUpdate = numShopsNeedSave(player);
        if (!force && numWantingToUpdate == 0 && getNumberOfShops(player) > 0) {
            plugin.getLogger().trace("save shops for player (" + playerName + ") was called, but no shops for player need updating! " + player.toString());
            return 0;
        }

        plugin.getLogger().debug("attempting to save shops for player " + playerName + " (" + player.toString() + ") isAdmin: " + (player == Shop.getPlugin().getShopHandler().getAdminUUID()));
        File currentFile = null;
        try {

            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            if (!fileDirectory.exists())
                fileDirectory.mkdir();

            String owner = null;
            if(player.equals(adminUUID)) {
                owner = "admin";
                currentFile = new File(fileDirectory + "/admin.yml");
            }
            else {
                owner = player.toString();
                currentFile = new File(fileDirectory + "/" + player.toString() + ".yml");
            }

            plugin.getLogger().trace("    current file " + currentFile);

            YamlConfiguration config = new YamlConfiguration();
            plugin.getLogger().trace("    preparing yaml for " + currentFile);

            List<AbstractShop> shopList = getShops(player);
            if (shopList.isEmpty()) {
                currentFile.delete();
                plugin.getLogger().debug("    no shops exist for player (" + playerName + "), deleting file... " + currentFile);
                return -1;
            }

            int shopNumber = 0;
            for (AbstractShop shop : shopList) {
                if(!shop.getOwnerUUID().equals(player))
                    continue;

                if (shop.isInitialized()) {
                    shopNumber++;
                    config.set("shops." + owner + "." + shopNumber + ".id", shop.getId().toString());
                    config.set("shops." + owner + "." + shopNumber + ".location", locationToString(shop.getSignLocation()));
                    if(shop.getFacing() != null)
                        config.set("shops." + owner + "." + shopNumber + ".facing", shop.getFacing().toString());
                    config.set("shops." + owner + "." + shopNumber + ".price", shop.getPrice());
                    if(shop.getType() == ShopType.COMBO){
                        config.set("shops." + owner + "." + shopNumber + ".priceSell", ((ComboShop)shop).getPriceSell());
                    }
                    config.set("shops." + owner + "." + shopNumber + ".amount", shop.getAmount());
                    String type = "";
                    if (shop.isAdmin())
                        type = "admin ";
                    type = type + shop.getType().toString();
                    config.set("shops." + owner + "." + shopNumber + ".type", type);
                    if(shop.getDisplay().getType() != null) {
                        config.set("shops." + owner + "." + shopNumber + ".displayType", shop.getDisplay().getType().toString());
                    }
                    else{
                        config.set("shops." + owner + "." + shopNumber + ".displayType", null);
                    }
                    if(shop.isFakeSign()){
                        config.set("shops." + owner + "." + shopNumber + ".fakeSign", shop.isFakeSign());
                    }

                    config.set("shops." + owner + "." + shopNumber + ".stock", shop.getStock());

                    ItemStack itemStack = shop.getItemStack();
                    itemStack.setAmount(1);
                    if(shop.getType() == ShopType.GAMBLE)
                        itemStack = new ItemStack(Material.AIR);
                    config.set("shops." + owner + "." + shopNumber + ".item", itemStack);

                    if (shop.getType() == ShopType.BARTER) {
                        ItemStack barterItemStack = shop.getSecondaryItemStack();
                        barterItemStack.setAmount(1);
                        config.set("shops." + owner + "." + shopNumber + ".itemBarter", barterItemStack);
                    }

                    shop.setNeedsSave(false);
                }
                else {
                    plugin.getLogger().debug("    shop " + shop + " is not initialized, skipping...");
                }
            }
            
            if (plugin.getLogger().isLevelEnabled(ShopLogger.SPAM)) {
                plugin.getLogger().spam("    built config to save... \n" + config.saveToString());
            }
            
            Path targetPath = currentFile.toPath();
            Path tempFile = Files.createTempFile(targetPath.getParent(), owner + "_", ".tmp");
            config.save(tempFile.toFile());
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                plugin.getLogger().helpful("Saved " + shopNumber + " Shops for Player " + playerName + " to file: " + currentFile);
                return shopNumber;
            } catch (Error | Exception ex) {
                plugin.getLogger().debug("Error during atomic move", ex);
                plugin.getLogger().debug("Filesystem does not support atomic move; using manual two-step replacement with backup...");
                Path backupPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".bak");
                try {
                    if (Files.exists(targetPath)) {
                        plugin.getLogger().debug("Backing up existing shop file for " + playerName + " from (" + targetPath + ") to (" + backupPath + ")...");
                        Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().debug("Successfully backed up existing shop file for " + playerName + " from (" + targetPath + ") to (" + backupPath + ")");
                    }
                    plugin.getLogger().debug("Moving new shop file for " + playerName + " from (" + tempFile + ") to (" + targetPath + ")");
                    Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().debug("Successfully moved new shop file for " + playerName + " from (" + tempFile + ") to (" + targetPath + ")!");
                    if (Files.exists(backupPath)) {
                        plugin.getLogger().debug("Deleting temporary backup of old shop file for " + playerName + " from (" + backupPath + ")");
                        Files.deleteIfExists(backupPath);
                        plugin.getLogger().debug("Successfully deleted temporary backup of old shop file for " + playerName + " from (" + backupPath + ")!");
                    }

                    plugin.getLogger().helpful("Saved " + shopNumber + " Shops for Player " + playerName + " to file: " + currentFile);
                    return shopNumber;
                } catch (Error | Exception moveEx) {
                    plugin.getLogger().severe("Critical error writing updated shop file for (" + playerName + ") to (" + targetPath + ")! This issue should not be ignored! Error message: " + moveEx.getMessage());
                    try {
                        if (Files.exists(targetPath)) {
                            plugin.getLogger().warning("Original file was left untouched. Player shop updates were not saved!");
                        } else if (Files.exists(backupPath)) {
                            plugin.getLogger().warning("Restoring backup player shop file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")");
                            Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            plugin.getLogger().info("Successfully restored backup player shop file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")!");
                        }
                    } catch (Error | Exception restoreEx) {
                        plugin.getLogger().severe("Failed to restore backup player shop file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")! Exception: " + restoreEx.getMessage());
                    }
                    if (Files.exists(targetPath)) {
                        plugin.getLogger().warning("Original file was left untouched. Player shop updates were not saved!");
                        return -2;
                    }
                    else if (Files.exists(backupPath)) {
                        plugin.getLogger().severe("Failed to restore backup player shop file for " + playerName);
                        plugin.getLogger().severe("You will need to manually restore this players backup file from (" + backupPath + ") to (" + targetPath + ")!");
                        return -3;
                    } else {
                        plugin.getLogger().severe("Possible data loss detected! Original file does not exist and Backup file does not exist for player (" + playerName + ")! Original MISSING: (" + targetPath + "), Backup MISSING: (" + backupPath + ")!!!");
                        plugin.getLogger().severe("Do not startup the plugin again until you have traced and fixed the issue! You may delete a new player file with each startup if the issue is not fixed!");
                        plugin.getLogger().severe("Shutting down plugin immediately to prevent Shop save data loss...");
                        Bukkit.getPluginManager().disablePlugin(plugin);
                        this.immediateShutdown = true;
                        return -5;
                    }
                }
            }
        } catch (Error | Exception e){
            plugin.getLogger().severe("Unable to update/save player shop file for (" + playerName + ") at (" + currentFile + ")! Original file was left untouched. Error message: " + e.getMessage());
            plugin.getLogger().warning("Are these Shop player files from an older version of the Minecraft? You can run into issues with Item NBT data not migrating correctly if you jump forward/skip too many MC versions at a time. You might be able to fix this error by copying the affected player(s) file(s) to a new test server (you do not have to copy the world, but should if you are able to) and starting up the server in each 'skipped' version of Minecraft with the Shop plugin's `debug_forceResaveAll` config option set to `true`. This will force a resave of all Shop files and will update any NBT changes between the last run version of Minecraft and the new one you are trying to use.");
            plugin.getLogger().severe("If you are unable to fix this error, you will need to delete or manually fix the affected player shop file at (" + currentFile + ") in order to allow them to create new Shops and make this error go away. This will delete all Shops for the player and will require the player to re-add their shops.");
            plugin.getLogger().debug("Stacktrace: ", e);
            return -2;
        }
    }

    public int saveAllShops() {
        HashMap<UUID, Boolean> allPlayersWithShops = new HashMap<>();
        for (AbstractShop shop : allShops.values()) {
            allPlayersWithShops.put(shop.getOwnerUUID(), true);
        }

        int numberUpdated = 0;
        int playersWithUpdate = 0;
        for (UUID player : allPlayersWithShops.keySet()) {
            int shopsUpdated = saveShops(player);

            if (shopsUpdated > 0) {
                numberUpdated += shopsUpdated;
                playersWithUpdate++;
            }
        }
        if (playersWithUpdate > 0) plugin.getLogger().info("Saved " + playersWithUpdate + " Player Shop file updates for " + numberUpdated + " total shops.");
        return numberUpdated;
    }

    private String locationToString(Location loc){
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void loadShops(){
        plugin.getFoliaLib().getScheduler().runAsync(task -> {
            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            if (!fileDirectory.exists()) return;

            File[] files = fileDirectory.listFiles();
            if (files == null) return;

            plugin.getLogger().info("Loading shops from " + files.length + " player files...");

            int totalShopsLoaded = 0;
            int totalFilesLoaded = 0;
            int totalFilesSkipped = 0;

            for (File file : files) {
                if (!file.getName().endsWith(".yml")) continue;
                if (file.getName().endsWith(".bak.yml")) continue;

                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String owner = file.getName().replace(".yml", "");

                UUID ownerUUID;
                if (owner.equals("admin")) {
                    ownerUUID = adminUUID;
                } else {
                    try {
                        ownerUUID = UUID.fromString(owner);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skipping file with invalid UUID: " + file.getName());
                        totalFilesSkipped++;
                        continue;
                    }
                }

                if (!config.contains("shops." + owner)) {
                    plugin.getLogger().debug("Skipping file with no shops: " + file.getName());
                    totalFilesSkipped++;
                    continue;
                }

                int shopsLoaded = 0;
                for (String shopNumber : config.getConfigurationSection("shops." + owner).getKeys(false)) {
                    String path = "shops." + owner + "." + shopNumber;

                    try {
                        String locationString = config.getString(path + ".location");
                        Location signLocation = getLocationFromString(locationString);
                        if (signLocation == null) {
                            plugin.getLogger().warning("Skipping shop with invalid location: " + locationString);
                            continue;
                        }

                        String typeString = config.getString(path + ".type");
                        boolean isAdmin = false;
                        if (typeString != null && typeString.startsWith("admin ")) {
                            isAdmin = true;
                            typeString = typeString.substring(6);
                        }
                        ShopType shopType;
                        try {
                            shopType = ShopType.valueOf(typeString);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Skipping shop with invalid type: " + typeString);
                            continue;
                        }

                        double price = config.getDouble(path + ".price");
                        double priceSell = config.getDouble(path + ".priceSell", -1);
                        int amount = config.getInt(path + ".amount");
                        int stock = config.getInt(path + ".stock", -1);
                        ItemStack item = config.getItemStack(path + ".item");
                        ItemStack barterItem = config.getItemStack(path + ".itemBarter");
                        boolean fakeSign = config.getBoolean(path + ".fakeSign", false);

                        String facingString = config.getString(path + ".facing");
                        BlockFace facing = null;
                        if (facingString != null) {
                            try { facing = BlockFace.valueOf(facingString); } catch (IllegalArgumentException e) { }
                        }

                        String idString = config.getString(path + ".id");
                        UUID shopId = null;
                        if (idString != null) {
                            try { shopId = UUID.fromString(idString); } catch (IllegalArgumentException e) { }
                        }

                        String displayTypeString = config.getString(path + ".displayType");
                        DisplayType displayType = null;
                        if (displayTypeString != null) {
                            try { displayType = DisplayType.valueOf(displayTypeString); } catch (IllegalArgumentException e) { }
                        }

                        // Use the existing AbstractShop.create() factory — priceSell is the combo buy price.
                        // Passing -1 for priceSell on non-combo shops is harmless; ComboShop ignores it.
                        double comboPriceSell = (shopType == ShopType.COMBO && priceSell >= 0) ? priceSell : 0;
                        AbstractShop shop = AbstractShop.create(
                            signLocation, ownerUUID, price, comboPriceSell, amount, isAdmin, shopType, facing
                        );

                        if (shop == null) continue;

                        if (shopId != null) shop.setId(shopId);
                        // If facing was null we don't know the direction yet — defer to chunk-load
                        if (facing == null) { addUnloadedShopToChunkList(shop); }
                        // Restore saved stock without triggering a chest scan (chest may be unloaded)
                        if (stock >= 0) shop.setStockOnLoad(stock);
                        if (fakeSign) shop.setFakeSign(true);
                        // setType(type, announce) — false = silent, no display rebuild
                        if (displayType != null) shop.getDisplay().setType(displayType, false);
                        if (shopType == ShopType.BARTER && barterItem != null) {
                            ((BarterShop) shop).setSecondaryItemStack(barterItem);
                        }
                        if (item != null) shop.setItemStack(item);

                        addShop(shop);
                        shopsLoaded++;

                    } catch (Exception e) {
                        plugin.getLogger().warning("Error loading shop from file " + file.getName() + ": " + e.getMessage());
                        plugin.getLogger().debug("Stacktrace: ", e);
                    }
                }

                totalShopsLoaded += shopsLoaded;
                totalFilesLoaded++;
                plugin.getLogger().debug("Loaded " + shopsLoaded + " shops from file: " + file.getName());
            }

            plugin.getLogger().info("Finished loading shops. Loaded " + totalShopsLoaded + " shops from " + totalFilesLoaded + " files. Skipped " + totalFilesSkipped + " files.");
        });
    }

    private Location getLocationFromString(String locationString) {
        if (locationString == null) return null;
        String[] parts = locationString.split(",");
        if (parts.length < 4) return null;
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) { return null; }
    }

    public boolean isChest(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL
            || type.name().endsWith("_SHULKER_BOX")
            || type.name().endsWith("COPPER_CHEST");
    }

    public UUID getAdminUUID() { return adminUUID; }

    public int getItemListSize() { return itemListItems.size(); }
    public ArrayList<ItemStack> getItemListItems() { return itemListItems; }

    private void initItemList() {
        itemListItems.clear();
        if (plugin.getItemListType() == ItemListType.NONE) return;
        String itemListPath = plugin.getItemListPath();
        if (itemListPath == null || itemListPath.isEmpty()) return;
        File itemListFile = new File(plugin.getDataFolder(), itemListPath);
        if (!itemListFile.exists()) {
            plugin.getLogger().warning("Item list file not found: " + itemListFile.getAbsolutePath());
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemListFile);
        if (!config.contains("items")) return;
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            ItemStack item = config.getItemStack("items." + key);
            if (item != null) itemListItems.add(item);
        }
        plugin.getLogger().info("Loaded " + itemListItems.size() + " items from item list.");
    }
}

package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ComboShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.util.PlayerNameCache;
import com.theflood424k.shoprevanced.util.ShopRevancedLogger;
import com.theflood424k.shoprevanced.util.UtilMethods;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles ShopRevanced save and load operations.
 * <p>
 * All shop persistence happens through this class: saving player shops to YAML,
 * loading shops from disk at startup, and converting legacy save formats.
 */
public class ShopSaveLoad {

    private final ShopRevanced plugin;
    private final ShopStorage storage;

    private boolean immediateShutdown = false;

    public ShopSaveLoad(ShopRevanced plugin, ShopStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * Saves shops for the specified player.
     *
     * @param player The player's UUID
     * @return result code indicating save status
     */
    public int saveShops(final UUID player) {
        return saveShops(player, false);
    }

    /**
     * Saves shops for the specified player.
     *
     * @param player The player's UUID
     * @param force  Whether to force save even if no shops need updating
     * @return result code indicating save status
     */
    public int saveShops(final UUID player, boolean force) {
        if (this.immediateShutdown) return -5;

        int numWantingToUpdate = storage.numShopsNeedSave(player);
        if (!force && numWantingToUpdate == 0 && storage.getNumberOfShops(player) > 0) {
            String playerName = player == storage.getAdminUUID() ? "admin" : plugin.getServer().getOfflinePlayer(player).getName();
            plugin.getLogger().trace("save shops for player (" + playerName + ") was called, but no shops for player need updating! " + player.toString());
            return 0;
        }

        String playerName = player == storage.getAdminUUID() ? "admin" : plugin.getServer().getOfflinePlayer(player).getName();
        plugin.getLogger().debug("attempting to save shops for player " + playerName + " (" + player.toString() + ") isAdmin: " + (player == storage.getAdminUUID()));
        File currentFile = null;
        try {

            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            if (!fileDirectory.exists()) {
                fileDirectory.mkdir();
            }

            String owner = null;
            if (player.equals(storage.getAdminUUID())) {
                owner = "admin";
                currentFile = new File(fileDirectory + "/admin.yml");
            } else {
                owner = player.toString();
                currentFile = new File(fileDirectory + "/" + player.toString() + ".yml");
            }

            plugin.getLogger().trace("    current file " + currentFile);

            YamlConfiguration config = new YamlConfiguration();
            plugin.getLogger().trace("    preparing yaml for " + currentFile);

            List<AbstractShop> shopList = storage.getShops(player);
            if (shopList.isEmpty()) {
                currentFile.delete();
                plugin.getLogger().debug("    no shops exist for player (" + playerName + "), deleting file... " + currentFile);
                return -1;
            }

            int shopNumber = 0;
            for (AbstractShop shop : shopList) {
                if (!shop.getOwnerUUID().equals(player)) continue;

                if (shop.isInitialized()) {
                    shopNumber++;
                    config.set("shops." + owner + "." + shopNumber + ".id", shop.getId().toString());
                    config.set("shops." + owner + "." + shopNumber + ".location", locationToString(shop.getSignLocation()));
                    if (shop.getFacing() != null) {
                        config.set("shops." + owner + "." + shopNumber + ".facing", shop.getFacing().toString());
                    }
                    config.set("shops." + owner + "." + shopNumber + ".price", shop.getPrice());
                    if (shop.getType() == ShopType.COMBO) {
                        config.set("shops." + owner + "." + shopNumber + ".priceSell", ((ComboShop) shop).getPriceSell());
                    }
                    config.set("shops." + owner + "." + shopNumber + ".amount", shop.getAmount());
                    String type = "";
                    if (shop.isAdmin()) type = "admin ";
                    type = type + shop.getType().toString();
                    config.set("shops." + owner + "." + shopNumber + ".type", type);
                    if (shop.getDisplay().getType() != null) {
                        config.set("shops." + owner + "." + shopNumber + ".displayType", shop.getDisplay().getType().toString());
                    } else {
                        config.set("shops." + owner + "." + shopNumber + ".displayType", null);
                    }
                    if (shop.isFakeSign()) {
                        config.set("shops." + owner + "." + shopNumber + ".fakeSign", shop.isFakeSign());
                    }

                    config.set("shops." + owner + "." + shopNumber + ".stock", shop.getStock());

                    ItemStack itemStack = shop.getItemStack();
                    itemStack.setAmount(1);
                    if (shop.getType() == ShopType.GAMBLE) {
                        itemStack = new ItemStack(org.bukkit.Material.AIR);
                    }
                    config.set("shops." + owner + "." + shopNumber + ".item", itemStack);

                    if (shop.getType() == ShopType.BARTER) {
                        ItemStack barterItemStack = shop.getSecondaryItemStack();
                        barterItemStack.setAmount(1);
                        config.set("shops." + owner + "." + shopNumber + ".itemBarter", barterItemStack);
                    }

                    shop.setNeedsSave(false);
                } else {
                    plugin.getLogger().debug("    ShopRevanced " + shop + " is not initialized, skipping...");
                }
            }

            if (plugin.getLogger().isLevelEnabled(ShopRevancedLogger.SPAM)) {
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
                        plugin.getLogger().debug("Backing up existing ShopRevanced file for " + playerName + " from (" + targetPath + ") to (" + backupPath + ")...");
                        Files.move(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().debug("Successfully backed up existing ShopRevanced file for " + playerName + " from (" + targetPath + ") to (" + backupPath + ")");
                    }
                    plugin.getLogger().debug("Moving new ShopRevanced file for " + playerName + " from (" + tempFile + ") to (" + targetPath + ")");
                    Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().debug("Successfully moved new ShopRevanced file for " + playerName + " from (" + tempFile + ") to (" + targetPath + ")!");
                    if (Files.exists(backupPath)) {
                        plugin.getLogger().debug("Deleting temporary backup of old ShopRevanced file for " + playerName + " from (" + backupPath + ")...");
                        Files.deleteIfExists(backupPath);
                        plugin.getLogger().debug("Successfully deleted temporary backup of old ShopRevanced file for " + playerName + " from (" + backupPath + ")!");
                    }

                    plugin.getLogger().helpful("Saved " + shopNumber + " Shops for Player " + playerName + " to file: " + currentFile);
                    return shopNumber;
                } catch (Error | Exception moveEx) {
                    plugin.getLogger().severe("Critical error writing updated ShopRevanced file for (" + playerName + ") to (" + targetPath + ")! This issue should not be ignored! Error message: " + moveEx.getMessage());
                    try {
                        if (Files.exists(targetPath)) {
                            plugin.getLogger().warning("Original file was left untouched. Player ShopRevanced updates were not saved!");
                        } else if (Files.exists(backupPath)) {
                            plugin.getLogger().warning("Restoring backup player ShopRevanced file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")");
                            Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            plugin.getLogger().info("Successfully restored backup player ShopRevanced file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")!");
                        }
                    } catch (Error | Exception restoreEx) {
                        plugin.getLogger().severe("Failed to restore backup player ShopRevanced file for " + playerName + " from (" + backupPath + ") to (" + targetPath + ")! Exception: " + restoreEx.getMessage());
                    }
                    if (Files.exists(targetPath)) {
                        plugin.getLogger().warning("Original file was left untouched. Player ShopRevanced updates were not saved!");
                        return -2;
                    } else if (Files.exists(backupPath)) {
                        plugin.getLogger().severe("Failed to restore backup player ShopRevanced file for " + playerName);
                        plugin.getLogger().severe("You will need to manually restore this players backup file from (" + backupPath + ") to (" + targetPath + ")!");
                        return -3;
                    } else {
                        plugin.getLogger().severe("Possible data loss detected! Original file does not exist and Backup file does not exist for player (" + playerName + ")! Original MISSING: (" + targetPath + "), Backup MISSING: (" + backupPath + ")!!!");
                        plugin.getLogger().severe("Do not startup the plugin again until you have traced and fixed the issue! You may delete a new player file with each startup if the issue is not fixed!");
                        plugin.getLogger().severe("Shutting down plugin immediately to prevent ShopRevanced save data loss...");
                        Bukkit.getPluginManager().disablePlugin(plugin);
                        this.immediateShutdown = true;
                        return -5;
                    }
                }
            }
        } catch (Error | Exception e) {
            String playerName2 = player == storage.getAdminUUID() ? "admin" : plugin.getServer().getOfflinePlayer(player).getName();
            plugin.getLogger().severe("Unable to update/save player ShopRevanced file for (" + playerName2 + ") at (" + currentFile + ")! Original file was left untouched. Error message: " + e.getMessage());
            plugin.getLogger().warning("Are these ShopRevanced player files from an older version of the Minecraft? You can run into issues with Item NBT data not migrating correctly if you jump forward/skip too many MC versions at a time. You might be able to fix this error by copying the affected player(s) file(s) to a new test server (you do not have to copy the world, but should if you are able to) and starting up the server in each 'skipped' version of Minecraft with the ShopRevanced plugin's `debug_forceResaveAll` config option set to `true`. This will force a resave of all ShopRevanced files and will update any NBT changes between the last run version of Minecraft and the new one you are trying to use.");
            plugin.getLogger().severe("If you are unable to fix this error, you will need to delete or manually fix the affected player ShopRevanced file at (" + currentFile + ") in order to allow them to create new Shops and make this error go away. This will delete all Shops for the player and will require the player to re-add their shops.");
            plugin.getLogger().debug("Stacktrace: ", e);
            return -2;
        }
    }

    /**
     * Saves all shops to disk.
     *
     * @return The number of shops saved
     */
    public int saveAllShops() {
        java.util.concurrent.ConcurrentHashMap<UUID, Boolean> allPlayersWithShops = new java.util.concurrent.ConcurrentHashMap<>();
        for (AbstractShop shop : storage.getAllShops()) {
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
        if (playersWithUpdate > 0) plugin.getLogger().info("Saved " + playersWithUpdate + " Player ShopRevanced file updates to disk");
        return numberUpdated;
    }

    /**
     * Converts legacy ShopRevanced save format to new format.
     */
    public void convertLegacyShopSaves() {
        saveAllShops();

        File fileDirectory = new File(plugin.getDataFolder(), "Data");
        if (!fileDirectory.exists()) return;

        for (File file : fileDirectory.listFiles()) {
            if (file.isFile()) {
                if (file.getName().endsWith(".yml")
                        && !file.getName().contains("itemCurrency")
                        && !file.getName().contains("gambleDisplay")) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    boolean isLegacyConfig = false;
                }
            }
        }
    }

    /**
     * Loads all shops from disk.
     */
    public void loadShops() {
        plugin.getFoliaLib().getScheduler().runLater(() -> {
            int numShopsLoaded = 0;

            boolean convertLegacySaves = false;
            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            if (!fileDirectory.exists()) return;

            PlayerNameCache.initialize();

            File[] files = fileDirectory.listFiles();
            if (files == null) return;

            for (File file : files) {
                ShopRevanced.getPlugin().getLogger().debug("Loading player shops from file: " + file.getName());
                try {
                    if (file.isFile()) {
                        if (file.getName().endsWith(".yml")
                                && !file.getName().contains("itemCurrency")
                                && !file.getName().contains("gambleDisplay")
                                && !file.getName().contains("playerNameCache")) {
                            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                            boolean isLegacyConfig = false;
                            UUID playerUUID = null;
                            String fileNameNoExt = null;
                            try {
                                int dotIndex = file.getName().lastIndexOf('.');
                                fileNameNoExt = file.getName().substring(0, dotIndex);

                                if (!fileNameNoExt.equals("admin")) {
                                    playerUUID = UUID.fromString(fileNameNoExt);
                                } else {
                                    playerUUID = storage.getAdminUUID();
                                }
                            } catch (IllegalArgumentException iae) {
                                isLegacyConfig = true;
                                convertLegacySaves = true;
                                playerUUID = uidFromString(fileNameNoExt);
                            }
                            numShopsLoaded += loadShopsFromConfig(config, isLegacyConfig);
                            if (isLegacyConfig) {
                                saveShops(playerUUID, true);
                                file.delete();
                            }
                            if (plugin.getDebug_forceResaveAll()) {
                                saveShops(playerUUID, true);
                            }
                        }
                    }
                } catch (Exception e) {
                    ShopRevanced.getPlugin().getLogger().log(Level.SEVERE, "Error while loading ShopRevanced from player file (" + file.getName() + "), please report this error to the ShopRevanced developers: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if (convertLegacySaves) {
                convertLegacyShopSaves();
            }

            ShopRevanced.getPlugin().getLogger().log(Level.INFO, "Loaded " + numShopsLoaded + " Shops!");
        }, 1);
    }

    private int loadShopsFromConfig(YamlConfiguration config, boolean isLegacy) {
        if (config.getConfigurationSection("shops") == null) return 0;

        int numShopsLoaded = 0;
        Set<String> allShopOwners = config.getConfigurationSection("shops").getKeys(false);

        for (String shopOwner : allShopOwners) {
            UUID owner = null;

            Set<String> allShopNumbers = config.getConfigurationSection("shops." + shopOwner).getKeys(false);
            int playerLoadedShops = 0;
            for (String shopNumber : allShopNumbers) {
                Location signLoc = locationFromString(config.getString("shops." + shopOwner + "." + shopNumber + ".location"));
                if (signLoc != null) {
                    try {
                        String idString = config.getString("shops." + shopOwner + "." + shopNumber + ".id");
                        UUID id = null;
                        if (idString != null && !idString.isEmpty()) {
                            id = UUID.fromString(idString);
                        }

                        if (shopOwner.equals("admin")) {
                            owner = storage.getAdminUUID();
                        } else if (isLegacy) {
                            owner = uidFromString(shopOwner);
                        } else {
                            owner = UUID.fromString(shopOwner);
                        }

                        BlockFace facing = null;
                        String facingStr = config.getString("shops." + shopOwner + "." + shopNumber + ".facing");
                        if (facingStr != null) {
                            facing = BlockFace.valueOf(facingStr);
                        }

                        String type = config.getString("shops." + shopOwner + "." + shopNumber + ".type");
                        double price = Double.parseDouble(config.getString("shops." + shopOwner + "." + shopNumber + ".price"));
                        double priceSell = 0;
                        if (config.getString("shops." + shopOwner + "." + shopNumber + ".priceSell") != null) {
                            priceSell = Double.parseDouble(config.getString("shops." + shopOwner + "." + shopNumber + ".priceSell"));
                        }
                        int amount = Integer.parseInt(config.getString("shops." + shopOwner + "." + shopNumber + ".amount"));

                        boolean isAdmin = shopOwner.equals("admin");

                        ShopType shopType = typeFromString(type);

                        ItemStack itemStack = config.getItemStack("shops." + shopOwner + "." + shopNumber + ".item");
                        if (shopType == ShopType.GAMBLE) {
                            itemStack = plugin.getGambleDisplayItem();
                        }

                        if (itemStack == null) {
                            plugin.getLogger().log(Level.WARNING, "Unable to load ShopRevanced #" + shopNumber + " for owner '" + shopOwner + "'! Skipping!");
                            continue;
                        }

                        AbstractShop shop = AbstractShop.create(signLoc, owner, price, priceSell, amount, isAdmin, shopType, facing);
                        if (id != null) shop.setId(id);

                        int stock = config.getInt("shops." + shopOwner + "." + shopNumber + ".stock");
                        shop.setStockOnLoad(stock);

                        shop.setItemStack(itemStack);
                        if (shop.getType() == ShopType.BARTER) {
                            ItemStack barterItemStack = config.getItemStack("shops." + shopOwner + "." + shopNumber + ".itemBarter");
                            shop.setSecondaryItemStack(barterItemStack);
                        }
                        String displayType = config.getString("shops." + shopOwner + "." + shopNumber + ".displayType");
                        if (displayType != null) {
                            shop.getDisplay().setType(DisplayType.valueOf(displayType), false);
                        }

                        boolean isFakeSign = config.getBoolean("shops." + shopOwner + "." + shopNumber + ".fakeSign");
                        if (isFakeSign) {
                            shop.setFakeSign(true);
                        }

                        shop.refreshGuiIcon();

                        if (isLegacy) {
                            shop.setNeedsSave(true);
                        }
                        if (idString == null || idString.isEmpty()) {
                            shop.setNeedsSave(true);
                        }

                        if (shop.isChunkLoaded()) {
                            plugin.getFoliaLib().getScheduler().runAtLocation(shop.getSignLocation(), task -> {
                                boolean loadSuccess = shop.load();
                                if (loadSuccess) {
                            storage.addShop(shop);
                                }
                            });
                        } else {
                            plugin.getLogger().debug("Chunk not loaded. Adding ShopRevanced to unloadedList to load later: " + shop);
                            storage.addUnloadedShopToChunkList(shop);
                            storage.addShop(shop);
                        }

                        numShopsLoaded++;
                        playerLoadedShops++;
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }
            String ownerName = shopOwner.equals("admin") ? "admin" : plugin.getServer().getOfflinePlayer(UUID.fromString(shopOwner)).getName();
            plugin.getLogger().helpful("Loaded (" + playerLoadedShops + ") shops for Player " + ownerName + " from: " + shopOwner + ".yml");
        }

        return numShopsLoaded;
    }

    /**
     * Converts a location to a string representation.
     *
     * @param loc The location
     * @return string representation of the location
     */
    private String locationToString(Location loc) {
        String worldName = loc.getWorld() == null ? "unknown_world" : loc.getWorld().getName();
        return worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * Converts a string representation to a Location.
     *
     * @param locString The string representation
     * @return the Location
     */
    private Location locationFromString(String locString) {
        String[] parts = locString.split(",");
        return new Location(plugin.getServer().getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }

    /**
     * Converts a string representation to a UUID.
     *
     * @param ownerString The string containing the UUID
     * @return the UUID
     */
    private UUID uidFromString(String ownerString) {
        int index = ownerString.indexOf("(");
        String uidString = ownerString.substring(index + 1, ownerString.length() - 1);
        return UUID.fromString(uidString);
    }

    /**
     * Converts a string representation to a ShopRevanced type.
     *
     * @param typeString The string representation
     * @return the ShopRevanced type
     */
    private ShopType typeFromString(String typeString) {
        if (typeString.contains("sell")) return ShopType.SELL;
        else if (typeString.contains("buy")) return ShopType.BUY;
        else if (typeString.contains("barter")) return ShopType.BARTER;
        else if (typeString.contains("combo")) return ShopType.COMBO;
        else return ShopType.GAMBLE;
    }
}

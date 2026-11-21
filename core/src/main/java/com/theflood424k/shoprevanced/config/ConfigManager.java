package com.theflood424k.shoprevanced.config;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.DisplayUtil;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.UtilMethods;
import com.theflood424k.shoprevanced.util.ConfigUpdater;
import com.theflood424k.shoprevanced.integration.WorldGuardHook.WorldGuardConfig;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized configuration manager for ShopRevanced.
 *
 * <p>Owns all YAML config file loading, {@link ConfigUpdater} migrations, and
 * configuration field accessors. ShopRevanced delegates to this class so the
 * main plugin class no longer holds ~80 config fields directly.</p>
 *
 * <p>Config keys are preserved exactly as they were in ShopRevanced so no
 * behavior or config-file changes occur.</p>
 */
public class ConfigManager {

    private final ShopRevanced plugin;

    // Raw YAML configurations
    private YamlConfiguration config;
    private YamlConfiguration chatConfig;
    private YamlConfiguration signConfig;
    private YamlConfiguration displayConfig;

    // Config file references
    private File configFile;
    private File chatConfigFile;
    private File signConfigFile;
    private File displayConfigFile;

    // Config-section world-guard keys (kept as constants for stable lookups)
    private static final String CONFIG_PATH_WORLD_GUARD_ENABLED = "worldGuard.enabled";
    private static final String CONFIG_PATH_LWC_ENABLED = "lwc.enabled";
    private static final String CONFIG_PATH_BENTO_BOX_ENABLED = "bentoBox.enabled";
    private static final String CONFIG_PATH_ADVANCED_REGION_MARKET_ENABLED = "advancedRegionMarket.enabled";
    private static final String CONFIG_PATH_PLOT_SQUARED_ENABLED = "plotSquared.enabled";
    private static final String CONFIG_PATH_BOLT_TRUST_INTEGRATION_ENABLED = "bolt.trustIntegration.enabled";
    private static final String CONFIG_PATH_BLOCK_PROT_TRUST_INTEGRATION_ENABLED = "blockProt.trustIntegration.enabled";
    private static final String CONFIG_PATH_CASHIER_LOCK_TRUST_INTEGRATION_ENABLED = "cashierLock.trustIntegration.enabled";
    private static final String CONFIG_PATH_GRIEF_PREVENTION_ENABLED = "griefprevention.enabled";
    private static final String CONFIG_PATH_GEYSER_ENABLED = "geyser.enabled";
    private static final String CONFIG_PATH_EZTAX_ENABLED = "ezTax.enabled";

    // ========================================================================
    // Configuration fields (mirrors of former ShopRevanced fields)
    // ========================================================================

    private boolean usePerms;
    private boolean checkUpdates;
    private boolean enableGUI;

    private WorldGuardConfig worldGuardConfig;

    private boolean hookTowny;
    private String commandAlias;
    private com.theflood424k.shoprevanced.display.DisplayType displayType;
    private com.theflood424k.shoprevanced.display.DisplayTagOption displayTagOption;
    private com.theflood424k.shoprevanced.display.DisplayType[] displayCycle;
    private boolean checkItemDurability;
    private boolean ignoreItemRepairCost;
    private boolean allowCreativeSelection;
    private boolean forceDisplayToNoneIfBlocked;
    private int displayLightLevel;
    private boolean setGlowingItemFrame;
    private boolean setGlowingSignText;
    private NavigableMap<Double, String> priceSuffixes;
    private Double priceSuffixMinimumValue;
    private boolean destroyShopRequiresSneak;
    private int hoursOfflineToRemoveShops;
    private boolean playSounds;
    private boolean playEffects;
    private boolean allowCreateMethodSign;
    private boolean allowCreateMethodChest;
    private int shopCreationTimeout;
    private ItemStack gambleDisplayItem;
    private ItemStack itemCurrency;
    private CurrencyType currencyType;
    private String currencyName = "";
    private String currencyFormat = "";
    private boolean allowFractionalCurrency = false;
    private Economy econ;
    private List<Material> enabledContainers;
    private boolean inverseComboShops;
    private double creationCost;
    private double destructionCost;
    private double teleportCost;
    private double teleportCooldown;
    private boolean returnCreationCost;
    private boolean allowPartialSales;
    private double taxPercent;
    private boolean offlinePurchaseNotificationsEnabled;
    private ItemListType itemListType;
    private List<String> worldBlackList;
    private Map<com.theflood424k.shoprevanced.util.ShopClickType, com.theflood424k.shoprevanced.util.ShopAction> clickTypeActionMap;
    private NamespacedKey signLocationNameSpacedKey;
    private NamespacedKey playerUUIDNameSpacedKey;

    // ShopRevanced display optimization settings
    private double displayProcessInterval;
    private double displayMovementThreshold;
    private double maxShopDisplayDistance;
    private int shopSearchRadius;
    private int displayBatchSize;
    private int displayBatchDelay;

    // Debug settings
    private boolean debug_allowUseOwnShop;
    private boolean debug_transactionDebugLogs;
    private int debug_shopCreateCooldown;
    private boolean debug_forceResaveAll;

    // Integration enable flags
    private boolean worldGuardIntegrationEnabled;
    private boolean lwcIntegrationEnabled;
    private boolean bentoBoxIntegrationEnabled;
    private boolean advancedRegionMarketIntegrationEnabled;
    private boolean plotSquaredIntegrationEnabled;
    private boolean boltTrustIntegrationEnabled;
    private boolean blockProtTrustIntegrationEnabled;
    private boolean cashierLockTrustIntegrationEnabled;
    private boolean griefPreventionIntegrationEnabled;
    private boolean geyserIntegrationEnabled;
    private boolean ezTaxIntegrationEnabled;

    // WorldGuard / Arclight detection (resolved at runtime)
    private boolean worldGuardExists;
    private Boolean arclightExists;

    public ConfigManager(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    // Lifecycle: load, reload, save
    // ========================================================================

    /**
     * Loads all YAML config files and runs {@link ConfigUpdater} migrations.
     * Called during {@code onEnable} before {@link #reloadConfig()}.
     */
    public void loadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("config.yml"), configFile);
        }

        chatConfigFile = new File(plugin.getDataFolder(), "chatConfig.yml");
        if (!chatConfigFile.exists()) {
            chatConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("chatConfig.yml"), chatConfigFile);
        }

        signConfigFile = new File(plugin.getDataFolder(), "signConfig.yml");
        if (!signConfigFile.exists()) {
            signConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("signConfig.yml"), signConfigFile);
        }

        displayConfigFile = new File(plugin.getDataFolder(), "displayConfig.yml");
        if (!displayConfigFile.exists()) {
            displayConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("displayConfig.yml"), displayConfigFile);
        }

        try {
            runConfigMigrations(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "chatConfig.yml", chatConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "signConfig.yml", signConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "displayConfig.yml", displayConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reloads configuration values from the YAML files into memory.
     * Called during {@code onEnable} and {@code reload()}.
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);
        signConfig = YamlConfiguration.loadConfiguration(signConfigFile);
        displayConfig = YamlConfiguration.loadConfiguration(displayConfigFile);

        // Load logger values
        plugin.getLogger().setLogLevel(config.getString("logLevel"));
        plugin.getLogger().enableColor(config.getBoolean("enableLogColor"));

        signLocationNameSpacedKey = new NamespacedKey(plugin, "signLocation");
        playerUUIDNameSpacedKey = new NamespacedKey(plugin, "playerUUID");

        allowCreateMethodSign = config.getBoolean("creationMethod.placeSign");
        allowCreateMethodChest = config.getBoolean("creationMethod.hitChest");
        // Default keeps pre-existing configs (which lack this key) working instead of
        // collapsing to 0 seconds, which would time creation out immediately.
        shopCreationTimeout = config.getInt("shopCreationTimeout", 120);

        usePerms = config.getBoolean("usePermissions");
        checkUpdates = config.getBoolean("checkUpdates");
        enableGUI = config.getBoolean("enableGUI");

        worldGuardIntegrationEnabled = config.getBoolean(CONFIG_PATH_WORLD_GUARD_ENABLED, true);
        lwcIntegrationEnabled = config.getBoolean(CONFIG_PATH_LWC_ENABLED, true);
        bentoBoxIntegrationEnabled = config.getBoolean(CONFIG_PATH_BENTO_BOX_ENABLED, true);
        advancedRegionMarketIntegrationEnabled = config.getBoolean(CONFIG_PATH_ADVANCED_REGION_MARKET_ENABLED, true);
        plotSquaredIntegrationEnabled = config.getBoolean(CONFIG_PATH_PLOT_SQUARED_ENABLED, true);
        boltTrustIntegrationEnabled = config.getBoolean(CONFIG_PATH_BOLT_TRUST_INTEGRATION_ENABLED, true);
        blockProtTrustIntegrationEnabled = config.getBoolean(CONFIG_PATH_BLOCK_PROT_TRUST_INTEGRATION_ENABLED, true);
        cashierLockTrustIntegrationEnabled = config.getBoolean(CONFIG_PATH_CASHIER_LOCK_TRUST_INTEGRATION_ENABLED, true);
        griefPreventionIntegrationEnabled = config.getBoolean(CONFIG_PATH_GRIEF_PREVENTION_ENABLED, true);
        geyserIntegrationEnabled = config.getBoolean(CONFIG_PATH_GEYSER_ENABLED, true);
        ezTaxIntegrationEnabled = config.getBoolean(CONFIG_PATH_EZTAX_ENABLED, false);

        hookTowny = config.getBoolean("hookTowny");

        try {
            displayType = com.theflood424k.shoprevanced.display.DisplayType.valueOf(config.getString("displayType"));
        } catch (Exception e) {
            displayType = com.theflood424k.shoprevanced.display.DisplayType.ITEM;
        }

        try {
            displayTagOption = com.theflood424k.shoprevanced.display.DisplayTagOption.valueOf(config.getString("displayNameTags"));
        } catch (Exception e) {
            displayTagOption = com.theflood424k.shoprevanced.display.DisplayTagOption.NONE;
        }

        try {
            List<String> cycle = config.getStringList("displayCycle");
            if (cycle.isEmpty()) {
                for (com.theflood424k.shoprevanced.display.DisplayType dt : com.theflood424k.shoprevanced.display.DisplayType.values()) {
                    cycle.add(dt.name());
                }
            }

            displayCycle = new com.theflood424k.shoprevanced.display.DisplayType[cycle.size()];
            for (int i = 0; i < cycle.size(); i++) {
                displayCycle[i] = com.theflood424k.shoprevanced.display.DisplayType.valueOf(cycle.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        checkItemDurability = config.getBoolean("checkItemDurability");
        ignoreItemRepairCost = config.getBoolean("ignoreItemRepairCost");
        allowCreativeSelection = config.getBoolean("allowCreativeSelection");
        forceDisplayToNoneIfBlocked = config.getBoolean("forceDisplayToNoneIfBlocked");
        displayLightLevel = config.getInt("displayLightLevel");
        setGlowingItemFrame = config.getBoolean("setGlowingItemFrame");
        hoursOfflineToRemoveShops = config.getInt("deletePlayerShopsAfterXHoursOffline");
        playSounds = config.getBoolean("playSounds");
        playEffects = config.getBoolean("playEffects");
        setGlowingSignText = config.getBoolean("setGlowingSignText");

        priceSuffixes = new TreeMap<>();
        for (String suffixKey : config.getConfigurationSection("priceSuffixes").getKeys(false)) {
            if (suffixKey.equals("minimumValue")) {
                priceSuffixMinimumValue = config.getDouble("priceSuffixes.minimumValue");
            } else {
                boolean enabled = config.getBoolean("priceSuffixes." + suffixKey + ".enabled");
                if (enabled) {
                    Double suffixValue = config.getDouble("priceSuffixes." + suffixKey + ".value");
                    priceSuffixes.put(suffixValue, suffixKey);
                }
            }
        }

        destroyShopRequiresSneak = config.getBoolean("destroyShopRequiresSneak");

        try {
            currencyType = CurrencyType.valueOf(config.getString("currency.type"));
        } catch (Exception e) {
            currencyType = CurrencyType.ITEM;
        }

        if (currencyType == CurrencyType.VAULT) {
            allowFractionalCurrency = config.getBoolean("allowFractionalCurrency");
        }

        offlinePurchaseNotificationsEnabled = config.getBoolean("offlinePurchaseNotifications.enabled");

        if (offlinePurchaseNotificationsEnabled && config.getString("logging.type").toUpperCase().equals("OFF")) {
            plugin.getLogger().warning("Offline purchase notifications are enabled in `config.yml` but DB logging is set to `OFF`. Offline purchase notifications will be disabled.");
            plugin.getLogger().warning("Please set `logging.type` to `FILE` or setup a database in `config.yml` to enable offline purchase notifications.");
            offlinePurchaseNotificationsEnabled = false;
        }

        // Loading the itemCurrency from a file makes it easier to allow servers to use detailed itemstacks as the server's economy item
        File fileDirectory = new File(plugin.getDataFolder(), "Data");
        if (!fileDirectory.exists()) {
            boolean success = fileDirectory.mkdirs();
            if (!success) {
                plugin.getLogger().severe("[ShopRevanced] Data folder could not be created!");
            }
        }

        File itemCurrencyFile = new File(fileDirectory, "itemCurrency.yml");
        if (itemCurrencyFile.exists()) {
            YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
            itemCurrency = currencyConfig.getItemStack("item");
            if (itemCurrency != null) {
                itemCurrency.setAmount(1);
            }
        } else {
            try {
                itemCurrency = new ItemStack(Material.EMERALD);
                itemCurrencyFile.createNewFile();

                YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
                currencyConfig.set("item", itemCurrency);
                currencyConfig.save(itemCurrencyFile);
            } catch (Exception e) {
                // ignored - fall back to default emerald
            }
        }

        // Load the gamble display item from its file
        File gambleDisplayFile = new File(fileDirectory, "gambleDisplayItem.yml");
        if (!gambleDisplayFile.exists()) {
            gambleDisplayFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("GAMBLE_DISPLAY.yml"), gambleDisplayFile);
        }
        try {
            YamlConfiguration gambleItemConfig = YamlConfiguration.loadConfiguration(gambleDisplayFile);
            gambleDisplayItem = gambleItemConfig.getItemStack("GAMBLE_DISPLAY");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Error loading gamble display item from file: " + gambleDisplayFile.getAbsolutePath());
            gambleDisplayItem = new ItemStack(Material.DIAMOND);
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading gamble display item from file: " + gambleDisplayFile.getAbsolutePath());
            gambleDisplayItem = new ItemStack(Material.DIAMOND);
        } catch (Error e) {
            plugin.getLogger().warning("Error loading gamble display item from file: " + gambleDisplayFile.getAbsolutePath());
            gambleDisplayItem = new ItemStack(Material.DIAMOND);
        }

        if (gambleDisplayItem == null) {
            plugin.getLogger().severe("Error loading gamble display item from file: " + gambleDisplayFile.getAbsolutePath());
            gambleDisplayItem = new ItemStack(Material.DIAMOND);
        }

        currencyName = config.getString("currency.name");
        currencyFormat = config.getString("currency.format");

        enabledContainers = new ArrayList<>();
        for (String materialString : config.getStringList("enabledContainers")) {
            try {
                enabledContainers.add(Material.valueOf(materialString));
            } catch (IllegalArgumentException e) {
                // skip invalid material
            }
        }

        inverseComboShops = config.getBoolean("inverseComboShops");

        creationCost = config.getDouble("creationCost");
        destructionCost = config.getDouble("destructionCost");
        teleportCost = config.getDouble("teleportCost");
        teleportCooldown = config.getDouble("teleportCooldown");
        returnCreationCost = config.getBoolean("returnCreationCost");
        allowPartialSales = config.getBoolean("allowPartialSales");

        try {
            itemListType = ItemListType.valueOf(config.getString("itemList"));
        } catch (Exception e) {
            itemListType = ItemListType.NONE;
        }

        worldBlackList = config.getStringList("worldBlacklist");
        for (String world : config.getStringList("worldBlacklist")) {
            worldBlackList.add(world);
        }

        clickTypeActionMap = new HashMap<>();
        clickTypeActionMap.put(com.theflood424k.shoprevanced.util.ShopClickType.valueOf(config.getString("actionMappings.transactWithShop")), com.theflood424k.shoprevanced.util.ShopAction.TRANSACT);
        clickTypeActionMap.put(com.theflood424k.shoprevanced.util.ShopClickType.valueOf(config.getString("actionMappings.transactWithShopFullStack")), com.theflood424k.shoprevanced.util.ShopAction.TRANSACT_FULLSTACK);
        clickTypeActionMap.put(com.theflood424k.shoprevanced.util.ShopClickType.valueOf(config.getString("actionMappings.viewShopDetails")), com.theflood424k.shoprevanced.util.ShopAction.VIEW_DETAILS);
        clickTypeActionMap.put(com.theflood424k.shoprevanced.util.ShopClickType.valueOf(config.getString("actionMappings.cycleShopDisplay")), com.theflood424k.shoprevanced.util.ShopAction.CYCLE_DISPLAY);

        // Load ShopRevanced display optimization settings
        displayProcessInterval = config.getDouble("displayProcessInterval");
        displayMovementThreshold = config.getDouble("displayMovementThreshold");
        maxShopDisplayDistance = config.getDouble("maxShopDisplayDistance");
        shopSearchRadius = config.getInt("shopSearchRadius");
        displayBatchSize = config.getInt("displayBatchSize", 10);
        displayBatchDelay = config.getInt("displayBatchDelay", 2);

        // Debug settings
        debug_allowUseOwnShop = config.getBoolean("debug.allowUseOwnShop");
        debug_transactionDebugLogs = config.getBoolean("debug.transactionDebugLogs");
        debug_shopCreateCooldown = config.getInt("debug.shopCreateCooldown");
        debug_forceResaveAll = config.getBoolean("debug.forceResaveAll");
    }

    /**
     * Runs legacy config migrations on {@code config.yml}.
     */
    private void runConfigMigrations(File configFile) throws IOException {
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);

        // v1.10.0
        // Check if offlinePurchaseNotifications.enabled is a new value
        // One time update if the Offline Purchase Notifications feature is being started up for the very first time
        // Previous default OFF, new default FILE
        if (oldConfig.get("offlinePurchaseNotifications") == null && oldConfig.getString("logging.type").equals("OFF")) {
            plugin.getLogger().info("Config default update: v1.10.0(+) is being run for the first time, setting logging type to FILE from old default OFF");
            oldConfig.set("logging.type", "FILE");
            oldConfig.save(configFile);
        }

        // v1.10.2
        // Migrate old hookWorldGuard to new worldGuard.requireAllowShopFlag structure
        if (oldConfig.get("hookWorldGuard") != null && oldConfig.get("worldGuard.requireAllowShopFlag") == null) {
            boolean oldValue = oldConfig.getBoolean("hookWorldGuard");
            plugin.getLogger().info("Config migration: moving 'hookWorldGuard' to 'worldGuard.requireAllowShopFlag'");
            oldConfig.set("worldGuard.requireAllowShopFlag", oldValue);
            oldConfig.set("hookWorldGuard", null); // remove old key
            oldConfig.save(configFile);
        }
    }

    // ========================================================================
    // WorldGuard / Arclight detection
    // ========================================================================

    /**
     * Resolves WorldGuard existence and integration state. Called during {@code onEnable}.
     * Returns true if WorldGuard is installed AND integration is enabled.
     */
    public boolean resolveWorldGuard() {
        boolean worldGuardDetected = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        worldGuardExists = worldGuardDetected && worldGuardIntegrationEnabled;

        if (worldGuardExists) {
            worldGuardConfig = new WorldGuardConfig(config);
        } else {
            worldGuardConfig = null;
        }

        return worldGuardExists;
    }

    /**
     * Resolves Arclight existence. Called during {@code onEnable}.
     */
    public boolean resolveArclight() {
        boolean arclightDetected = plugin.getServer().getPluginManager().getPlugin("Arclight") != null;
        boolean arclightIntegrationEnabled = config.getBoolean("arclight.enabled", true);

        if (arclightDetected) {
            if (arclightIntegrationEnabled) {
                arclightExists = true;
            } else {
                arclightExists = false;
            }
        } else {
            arclightExists = false;
        }

        return arclightExists != null && arclightExists;
    }

    // ========================================================================
    // Accessors (getters)
    // ========================================================================

    public YamlConfiguration getConfig() {
        return config;
    }

    public YamlConfiguration getChatConfig() {
        return chatConfig;
    }

    public YamlConfiguration getSignConfig() {
        return signConfig;
    }

    public YamlConfiguration getDisplayConfig() {
        return displayConfig;
    }

    public File getConfigFile() {
        return configFile;
    }

    public File getChatConfigFile() {
        return chatConfigFile;
    }

    public File getSignConfigFile() {
        return signConfigFile;
    }

    public File getDisplayConfigFile() {
        return displayConfigFile;
    }

    public boolean usePerms() {
        return usePerms;
    }

    public boolean checkUpdates() {
        return checkUpdates;
    }

    public boolean useGUI() {
        return enableGUI;
    }

    public WorldGuardConfig getWorldGuardConfig() {
        return worldGuardConfig;
    }

    public boolean worldGuardExists() {
        return worldGuardExists;
    }

    public boolean isWorldGuardIntegrationEnabled() {
        return worldGuardIntegrationEnabled;
    }

    public boolean isLwcIntegrationEnabled() {
        return lwcIntegrationEnabled;
    }

    public boolean isBentoBoxIntegrationEnabled() {
        return bentoBoxIntegrationEnabled;
    }

    public boolean isAdvancedRegionMarketIntegrationEnabled() {
        return advancedRegionMarketIntegrationEnabled;
    }

    public boolean isPlotSquaredIntegrationEnabled() {
        return plotSquaredIntegrationEnabled;
    }

    public boolean isBoltTrustIntegrationEnabled() {
        return boltTrustIntegrationEnabled;
    }

    public boolean isBlockProtTrustIntegrationEnabled() {
        return blockProtTrustIntegrationEnabled;
    }

    public boolean isCashierLockTrustIntegrationEnabled() {
        return cashierLockTrustIntegrationEnabled;
    }

    public boolean isGriefPreventionIntegrationEnabled() {
        return griefPreventionIntegrationEnabled;
    }

    public boolean isGeyserIntegrationEnabled() {
        return geyserIntegrationEnabled;
    }

    public boolean isEzTaxIntegrationEnabled() {
        return ezTaxIntegrationEnabled;
    }

    public boolean hookTowny() {
        return hookTowny;
    }

    public String getCommandAlias() {
        return commandAlias;
    }

    public com.theflood424k.shoprevanced.display.DisplayType getDisplayType() {
        return displayType;
    }

    public com.theflood424k.shoprevanced.display.DisplayTagOption getDisplayTagOption() {
        return displayTagOption;
    }

    public com.theflood424k.shoprevanced.display.DisplayType[] getDisplayCycle() {
        return displayCycle;
    }

    public boolean checkItemDurability() {
        return checkItemDurability;
    }

    public boolean ignoreItemRepairCost() {
        return ignoreItemRepairCost;
    }

    public boolean allowCreativeSelection() {
        return allowCreativeSelection;
    }

    public boolean forceDisplayToNoneIfBlocked() {
        return forceDisplayToNoneIfBlocked;
    }

    public int getDisplayLightLevel() {
        return displayLightLevel;
    }

    public boolean getGlowingItemFrame() {
        return setGlowingItemFrame;
    }

    public boolean getGlowingSignText() {
        return setGlowingSignText;
    }

    public NavigableMap<Double, String> getPriceSuffixes() {
        return priceSuffixes;
    }

    public Double getPriceSuffixMinimumValue() {
        return priceSuffixMinimumValue;
    }

    public boolean getDestroyShopRequiresSneak() {
        return destroyShopRequiresSneak;
    }

    public int getHoursOfflineToRemoveShops() {
        return hoursOfflineToRemoveShops;
    }

    public boolean playSounds() {
        return playSounds;
    }

    public boolean playEffects() {
        return playEffects;
    }

    public boolean getAllowCreationMethodSign() {
        return allowCreateMethodSign;
    }

    public boolean getAllowCreationMethodChest() {
        return allowCreateMethodChest;
    }

    public int getShopCreationTimeout() {
        return shopCreationTimeout;
    }

    public ItemStack getGambleDisplayItem() {
        return gambleDisplayItem;
    }

    public ItemStack getItemCurrency() {
        return itemCurrency;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCurrencyFormat() {
        return currencyFormat;
    }

    public boolean getAllowFractionalCurrency() {
        return allowFractionalCurrency;
    }

    public List<Material> getEnabledContainers() {
        return enabledContainers;
    }

    public boolean inverseComboShops() {
        return inverseComboShops;
    }

    public double getCreationCost() {
        return creationCost;
    }

    public double getDestructionCost() {
        return destructionCost;
    }

    public double getTeleportCost() {
        return teleportCost;
    }

    public double getTeleportCooldown() {
        return teleportCooldown;
    }

    public boolean returnCreationCost() {
        return returnCreationCost;
    }

    public boolean getAllowPartialSales() {
        return allowPartialSales;
    }

    public double getTaxPercent() {
        return taxPercent;
    }

    public boolean offlinePurchaseNotificationsEnabled() {
        return offlinePurchaseNotificationsEnabled;
    }

    public ItemListType getItemListType() {
        return itemListType;
    }

    public List<String> getWorldBlacklist() {
        return worldBlackList;
    }

    public Map<com.theflood424k.shoprevanced.util.ShopClickType, com.theflood424k.shoprevanced.util.ShopAction> getClickTypeActionMap() {
        return clickTypeActionMap;
    }

    public com.theflood424k.shoprevanced.util.ShopAction getShopAction(com.theflood424k.shoprevanced.util.ShopClickType shopClickType) {
        return clickTypeActionMap.get(shopClickType);
    }

    public NamespacedKey getSignLocationNameSpacedKey() {
        return signLocationNameSpacedKey;
    }

    public NamespacedKey getPlayerUUIDNameSpacedKey() {
        return playerUUIDNameSpacedKey;
    }

    public double getDisplayProcessInterval() {
        return displayProcessInterval;
    }

    public double getDisplayMovementThreshold() {
        return displayMovementThreshold;
    }

    public double getMaxShopDisplayDistance() {
        return maxShopDisplayDistance;
    }

    public int getShopSearchRadius() {
        return shopSearchRadius;
    }

    public int getDisplayBatchSize() {
        return displayBatchSize;
    }

    public int getDisplayBatchDelay() {
        return displayBatchDelay;
    }

    public boolean getDebug_allowUseOwnShop() {
        return debug_allowUseOwnShop;
    }

    public boolean getDebug_transactionDebugLogs() {
        return debug_transactionDebugLogs;
    }

    public int getDebug_shopCreateCooldown() {
        return debug_shopCreateCooldown;
    }

    public boolean getDebug_forceResaveAll() {
        return debug_forceResaveAll;
    }

    public boolean arclightExists() {
        return arclightExists != null && arclightExists;
    }

    // ========================================================================
    // Economy lifecycle
    // ========================================================================

    /**
     * Sets up the VaultUnlocked economy connection.
     */
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            return false;
        }
        plugin.getLogger().notice("VaultUnlocked is installed, creating VaultUnlocked integration for Economy support");
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Returns the economy provider, setting it up lazily if needed.
     */
    public Economy getEconomy() {
        if (econ == null) {
            setupEconomy();
        }
        return econ;
    }

    // ========================================================================
    // Mutators (setters that persist to file)
    // ========================================================================

    /**
     * Sets the item currency and persists it to {@code Data/itemCurrency.yml}.
     */
    public void setItemCurrency(ItemStack itemCurrency) {
        this.itemCurrency = itemCurrency;

        try {
            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            File itemCurrencyFile = new File(fileDirectory, "itemCurrency.yml");
            YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
            currencyConfig.set("item", this.itemCurrency);
            currencyConfig.save(itemCurrencyFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the gamble display item and persists it to {@code Data/gambleDisplayItem.yml},
     * then triggers a full plugin reload.
     */
    public void setGambleDisplayItem(ItemStack is) {
        this.gambleDisplayItem = is;

        try {
            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            File gambleDisplayFile = new File(fileDirectory, "gambleDisplayItem.yml");
            if (!gambleDisplayFile.exists()) {
                gambleDisplayFile.getParentFile().mkdirs();
                gambleDisplayFile.createNewFile();
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(gambleDisplayFile);

            config.set("GAMBLE_DISPLAY", is);
            config.save(gambleDisplayFile);

            plugin.reload();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


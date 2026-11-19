package com.theflood424k.shoprevanced;

import com.theflood424k.shoprevanced.config.ConfigManager;
import com.theflood424k.shoprevanced.display.DisplayTagOption;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.gui.ShopGUIListener;
import com.theflood424k.shoprevanced.handler.*;
import com.theflood424k.shoprevanced.search.ShopSearchService;
import com.theflood424k.shoprevanced.integration.*;
import com.theflood424k.shoprevanced.listener.BucketEmptyListener;
import com.theflood424k.shoprevanced.listener.CreativeSelectionListener;
import com.theflood424k.shoprevanced.listener.DisplayListener;
import com.theflood424k.shoprevanced.listener.MiscListener;
import com.theflood424k.shoprevanced.listener.ShopCreationListener;
import com.theflood424k.shoprevanced.listener.ShopCreationProcessManager;
import com.theflood424k.shoprevanced.listener.ShopProtectionListener;
import com.theflood424k.shoprevanced.listener.ShopRevancedListener;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.*;
import com.theflood424k.shoprevanced.integration.IntegrationManager;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.util.Metrics;
import com.theflood424k.shoprevanced.util.Metrics.*;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopRevanced extends JavaPlugin {

    private static ShopRevanced plugin;
    private ShopRevancedLogger logger = new ShopRevancedLogger(this, true);
    private FoliaLib foliaLib;
    private ConfigManager configManager;

    private static final String CONFIG_PATH_WORLD_GUARD_ENABLED = "worldGuard.enabled";

    private ShopRevancedListener ShopRevancedListener;
    private DisplayListener displayListener;
    private TransactionHandler transactionHandler;
    private MiscListener miscListener;
    private CreativeSelectionListener creativeSelectionListener;
    private ShopGUIListener guiListener;

    // Split listeners extracted from MiscListener and ShopRevancedListener
    private BucketEmptyListener bucketEmptyListener;
    private ShopCreationProcessManager shopCreationProcessManager;
    private ShopCreationListener shopCreationListener;
    private ShopProtectionListener shopProtectionListener;

    private ShopRevancedHandler ShopRevancedHandler;
    private ShopSearchService shopSearchService;
    private ShopRevancedGuiHandler guiHandler;
    private ItemNameUtil itemNameUtil;
    private ShopRevancedCreationUtil ShopRevancedCreationUtil;
    private LogHandler logHandler;
    private Metrics metrics;
    private IntegrationManager integrationManager;

    // Config fields delegated to ConfigManager
    private boolean usePerms;
    private boolean checkUpdates;
    private boolean enableGUI;
    private boolean hookTowny;
    private String commandAlias;
    private CurrencyType currencyType;
    private boolean allowFractionalCurrency;
    private boolean offlinePurchaseNotificationsEnabled;
    private ItemStack itemCurrency;
    private ItemStack gambleDisplayItem;
    private String currencyName;
    private String currencyFormat;
    private List<Material> enabledContainers;
    private boolean inverseComboShops;
    private double creationCost;
    private double destructionCost;
    private double teleportCost;
    private double teleportCooldown;
    private boolean returnCreationCost;
    private boolean allowPartialSales;
    private ItemListType itemListType;
    private List<String> worldBlackList;
    private Map<ShopClickType, ShopAction> clickTypeActionMap;
    private double displayProcessInterval;
    private double displayMovementThreshold;
    private double maxShopDisplayDistance;
    private int shopSearchRadius;
    private int displayBatchSize;
    private int displayBatchDelay;
    private boolean debug_allowUseOwnShop;
    private boolean debug_transactionDebugLogs;
    private int debug_shopCreateCooldown;
    private boolean debug_forceResaveAll;
    private boolean checkItemDurability;
    private int displayLightLevel;
    private boolean setGlowingItemFrame;
    private int hoursOfflineToRemoveShops;
    private boolean playSounds;
    private boolean playEffects;
    private boolean setGlowingSignText;
    private boolean destroyShopRequiresSneak;
    private boolean allowCreativeSelection;
    private boolean forceDisplayToNoneIfBlocked;
    private boolean allowCreateMethodSign;
    private boolean allowCreateMethodChest;
    private TreeMap<Double, String> priceSuffixes;
    private Double priceSuffixMinimumValue;

    // Integration-enabled flags
    private boolean dynmapEnabled;
    private boolean bluemapEnabled;
    private BluemapHookListener bluemapHookListener;
    private boolean lwcIntegrationEnabled;
    private boolean bentoBoxIntegrationEnabled;
    private boolean advancedRegionMarketIntegrationEnabled;
    private boolean plotSquaredIntegrationEnabled;
    private boolean boltTrustIntegrationEnabled;
    private boolean blockProtTrustIntegrationEnabled;
    private boolean cashierLockTrustIntegrationEnabled;
    private boolean griefPreventionIntegrationEnabled;
    private boolean geyserIntegrationEnabled;

    // Additional config/state fields
    private boolean arclightExists;
    private boolean ignoreItemRepairCost;
    private int shopCreationTimeout;
    private double taxPercent;

    // Economy
    private Economy econ;

    // Display
    private DisplayType displayType;
    private DisplayTagOption displayTagOption;
    private DisplayType[] displayCycle;

    // Namespaced keys
    private NamespacedKey signLocationNameSpacedKey;
    private NamespacedKey playerUUIDNameSpacedKey;

    public static ShopRevanced getPlugin() {
        return plugin;
    }

    public static boolean loggedDisplayDisabledWarning = false;

    // Return the custom ShopRevancedLogger so that we can log at higher levels.
    @Override
    public ShopRevancedLogger getLogger() { return logger; }

    public ConfigManager getConfigManager() { return configManager; }

    @Override
    public void onLoad(){
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
    }

    @Override
    public void onEnable() {
        plugin = this;

        // Initialize FoliaLib
        foliaLib = new FoliaLib(this);

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("config.yml"), configFile);
        }

        File chatConfigFile = new File(getDataFolder(), "chatConfig.yml");
        if (!chatConfigFile.exists()) {
            chatConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("chatConfig.yml"), chatConfigFile);
        }

        File signConfigFile = new File(getDataFolder(), "signConfig.yml");
        if (!signConfigFile.exists()) {
            signConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("signConfig.yml"), signConfigFile);
        }

        File displayConfigFile = new File(getDataFolder(), "displayConfig.yml");
        if (!displayConfigFile.exists()) {
            displayConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("displayConfig.yml"), displayConfigFile);
        }

        try {
            // Check if we need to update any legacy config values

            // v1.10.0
            // Check if offlinePurchaseNotifications.enabled is a new value
            YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);
            // One time update if the Offline Purchase Notifications feature is being started up for the very first time
            // Previous default OFF, new default FILE
            if (oldConfig.get("offlinePurchaseNotifications") == null && oldConfig.getString("logging.type").equals("OFF")) {
                logger.info("Config default update: v1.10.0(+) is being run for the first time, setting logging type to FILE from old default OFF");
                oldConfig.set("logging.type", "FILE");
                oldConfig.save(configFile);
            }

            // v1.10.2
            // Migrate old hookWorldGuard to new worldGuard.requireAllowShopFlag structure
            if (oldConfig.get("hookWorldGuard") != null && oldConfig.get("worldGuard.requireAllowShopFlag") == null) {
                boolean oldValue = oldConfig.getBoolean("hookWorldGuard");
                logger.info("Config migration: moving 'hookWorldGuard' to 'worldGuard.requireAllowShopFlag'");
                oldConfig.set("worldGuard.requireAllowShopFlag", oldValue);
                oldConfig.set("hookWorldGuard", null); // remove old key
                oldConfig.save(configFile);
            }

            // Next time we add a migration lets move it to a util class to keep things clean.

            ConfigUpdater.update(plugin, "config.yml", configFile, new ArrayList<>());
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

        configManager.reloadConfig();
        YamlConfiguration config = configManager.getConfig();
        // Load logger values again in case the log level was changed on a reload
        this.getLogger().setLogLevel(config.getString("logLevel"));
        this.getLogger().enableColor(config.getBoolean("enableLogColor"));
        this.getLogger().enableColor(configManager.getConfig().getBoolean("enableLogColor"));

        ShopRevancedCreationUtil = new ShopRevancedCreationUtil(this);

        ShopRevancedListener = new ShopRevancedListener(this);
        transactionHandler = new TransactionHandler(this);
        miscListener = new MiscListener(this);
        creativeSelectionListener = new CreativeSelectionListener(this);
        displayListener = new DisplayListener(this);
        guiListener = new ShopGUIListener(this);

        // New split listeners
        shopCreationProcessManager = new ShopCreationProcessManager(this);
        bucketEmptyListener = new BucketEmptyListener(this);
        shopCreationListener = new ShopCreationListener(this, shopCreationProcessManager);
        shopProtectionListener = new ShopProtectionListener(this, shopCreationProcessManager);

        // Load ShopRevancedMessage by initializing it once
        new ShopRevancedMessage(this);
        itemNameUtil = new ItemNameUtil();

        File fileDirectory = new File(this.getDataFolder(), "Data");
        if (!fileDirectory.exists()) {
            boolean success;
            success = (fileDirectory.mkdirs());
            if (!success) {
                this.getLogger().severe("[ShopRevanced] Data folder could not be created!");
            }
        }

        usePerms = configManager.usePerms();
        if (usePerms) {
            this.getLogger().info("Permissions enabled, ShopRevanced will respect player permissions");
        } else {
            this.getLogger().info("Permissions disabled, everyone will be able to create/use shops by default");
        }
        checkUpdates = configManager.checkUpdates();
        enableGUI = configManager.useGUI();
        
        hookTowny = configManager.hookTowny();
        bluemapEnabled = config.getBoolean("bluemap.enabled", false);
        dynmapEnabled = config.getBoolean("dynmap.enabled", false);
        commandAlias = configManager.getCommandAlias();

        // Initialize IntegrationManager with config-detected flags
        integrationManager = new IntegrationManager(this);
        integrationManager.setWorldGuardExists(configManager.worldGuardExists());
        integrationManager.setWorldGuardConfig(configManager.getWorldGuardConfig());
        integrationManager.setLwcIntegrationEnabled(configManager.isLwcIntegrationEnabled());
        integrationManager.setBentoBoxIntegrationEnabled(configManager.isBentoBoxIntegrationEnabled());
        integrationManager.setAdvancedRegionMarketIntegrationEnabled(configManager.isAdvancedRegionMarketIntegrationEnabled());
        integrationManager.setPlotSquaredIntegrationEnabled(configManager.isPlotSquaredIntegrationEnabled());
        integrationManager.setBoltTrustIntegrationEnabled(configManager.isBoltTrustIntegrationEnabled());
        integrationManager.setBlockProtTrustIntegrationEnabled(configManager.isBlockProtTrustIntegrationEnabled());
        integrationManager.setCashierLockTrustIntegrationEnabled(configManager.isCashierLockTrustIntegrationEnabled());
        integrationManager.setGriefPreventionIntegrationEnabled(configManager.isGriefPreventionIntegrationEnabled());
        integrationManager.setGeyserIntegrationEnabled(configManager.isGeyserIntegrationEnabled());
        integrationManager.setHookTowny(hookTowny);
        integrationManager.setBluemapEnabled(bluemapEnabled);
        integrationManager.setDynmapEnabled(dynmapEnabled);

        // Sync integration-enabled flags to local fields for test compatibility
        lwcIntegrationEnabled = configManager.isLwcIntegrationEnabled();
        bentoBoxIntegrationEnabled = configManager.isBentoBoxIntegrationEnabled();
        advancedRegionMarketIntegrationEnabled = configManager.isAdvancedRegionMarketIntegrationEnabled();
        plotSquaredIntegrationEnabled = configManager.isPlotSquaredIntegrationEnabled();
        boltTrustIntegrationEnabled = configManager.isBoltTrustIntegrationEnabled();
        blockProtTrustIntegrationEnabled = configManager.isBlockProtTrustIntegrationEnabled();
        cashierLockTrustIntegrationEnabled = configManager.isCashierLockTrustIntegrationEnabled();
        griefPreventionIntegrationEnabled = configManager.isGriefPreventionIntegrationEnabled();
        geyserIntegrationEnabled = configManager.isGeyserIntegrationEnabled();
        currencyType = configManager.getCurrencyType();
        allowFractionalCurrency = configManager.getAllowFractionalCurrency();
        offlinePurchaseNotificationsEnabled = config.getBoolean("offlinePurchaseNotifications.enabled");
        if (offlinePurchaseNotificationsEnabled && config.getString("logging.type").toUpperCase().equals("OFF")) {
            this.getLogger().warning("Offline purchase notifications are enabled in `config.yml` but DB logging is set to `OFF`. Offline purchase notifications will be disabled.");
            this.getLogger().warning("Please set `logging.type` to `FILE` or setup a database in `config.yml` to enable offline purchase notifications.");
            offlinePurchaseNotificationsEnabled = false;
        }
        itemCurrency = configManager.getItemCurrency();
        gambleDisplayItem = configManager.getGambleDisplayItem();
        currencyName = configManager.getCurrencyName();
        currencyFormat = configManager.getCurrencyFormat();
        enabledContainers = new ArrayList<>();
        for(String materialString : config.getStringList("enabledContainers")){
            try{
                enabledContainers.add(Material.valueOf(materialString));
            } catch(IllegalArgumentException e) {}
        }
        inverseComboShops = config.getBoolean("inverseComboShops");
        creationCost = configManager.getCreationCost();
        destructionCost = configManager.getDestructionCost();
        teleportCost = configManager.getTeleportCost();
        teleportCooldown = configManager.getTeleportCooldown();
        returnCreationCost = config.getBoolean("returnCreationCost");
        allowPartialSales = configManager.getAllowPartialSales();
        try {
            itemListType = ItemListType.valueOf(config.getString("itemList"));
        } catch(Exception e){
            itemListType = ItemListType.NONE;
        }
        worldBlackList = new ArrayList<>();
        for(String world : config.getStringList("worldBlacklist")){
            worldBlackList.add(world);
        }
        clickTypeActionMap = new HashMap<>();
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.transactWithShop")), ShopAction.TRANSACT);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.transactWithShopFullStack")), ShopAction.TRANSACT_FULLSTACK);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.viewShopDetails")), ShopAction.VIEW_DETAILS);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.cycleShopDisplay")), ShopAction.CYCLE_DISPLAY);
        displayProcessInterval = configManager.getDisplayProcessInterval();
        displayMovementThreshold = configManager.getDisplayMovementThreshold();
        maxShopDisplayDistance = configManager.getMaxShopDisplayDistance();
        shopSearchRadius = configManager.getShopSearchRadius();
        displayBatchSize = configManager.getDisplayBatchSize();
        displayBatchDelay = configManager.getDisplayBatchDelay();
        debug_allowUseOwnShop = config.getBoolean("debug.allowUseOwnShop");
        debug_transactionDebugLogs = config.getBoolean("debug.transactionDebugLogs");
        debug_shopCreateCooldown = config.getInt("debug.shopCreateCooldown");
        debug_forceResaveAll = config.getBoolean("debug.forceResaveAll");
        checkItemDurability = configManager.checkItemDurability();
        displayLightLevel = configManager.getDisplayLightLevel();
        setGlowingItemFrame = configManager.getGlowingItemFrame();
        hoursOfflineToRemoveShops = configManager.getHoursOfflineToRemoveShops();
        playSounds = config.getBoolean("playSounds");
        playEffects = config.getBoolean("playEffects");
        setGlowingSignText = configManager.getGlowingSignText();
        destroyShopRequiresSneak = configManager.getDestroyShopRequiresSneak();
        priceSuffixes = new TreeMap<>();
        priceSuffixMinimumValue = configManager.getPriceSuffixMinimumValue();
        for(String suffixKey : config.getConfigurationSection("priceSuffixes").getKeys(false)){
            if(suffixKey.equals("minimumValue")){
                priceSuffixMinimumValue = configManager.getPriceSuffixMinimumValue();
            }
            else {
                boolean enabled = config.getBoolean("priceSuffixes." + suffixKey + ".enabled");
                if (enabled) {
                    Double suffixValue = config.getDouble("priceSuffixes." + suffixKey + ".value");
                    priceSuffixes.put(suffixValue, suffixKey);
                }
            }
        }
        allowCreativeSelection = config.getBoolean("allowCreativeSelection");
        forceDisplayToNoneIfBlocked = config.getBoolean("forceDisplayToNoneIfBlocked");
        allowCreateMethodSign = configManager.getAllowCreationMethodSign();
        allowCreateMethodChest = configManager.getAllowCreationMethodChest();
        ignoreItemRepairCost = configManager.ignoreItemRepairCost();
        shopCreationTimeout = configManager.getShopCreationTimeout();
        taxPercent = configManager.getTaxPercent();

        // Check if we should load VAULT economy
        if (configManager.getCurrencyType() == CurrencyType.VAULT) {
            if (configManager.setupEconomy()) {
                this.getLogger().info("Shops will use the VaultUnlocked economy (" + configManager.getCurrencyName() + ") as currency on the server.");
            } else {
                this.getLogger().severe("Unable to connect to VaultUnlocked Economy! Are both VaultUnlocked AND an Economy plugin installed?");
                this.getLogger().severe("Plugin Disabled: Invalid configuration value `economy.type` config.yml. If you do not wish to use Vault with ShopRevanced, make sure to set `economy.type` in the config file to `ITEM`.");
                getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
        } else {
            if (configManager.getItemCurrency() == null) {
                this.getLogger().severe("Plugin Disabled: Invalid value for `itemCurrencyID` in `config.yml`");
                getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
            this.getLogger().info("Shops will use " + PlainTextComponentSerializer.plainText().serialize(itemNameUtil.getName(configManager.getItemCurrency())) + "(s) as the currency on the server.");
        }

        // Load CommandHandler by initializing it once
        new CommandHandler(this, null, commandAlias, "Base command for the ShopRevanced plugin", "/ShopRevanced", new ArrayList(Arrays.asList(commandAlias)));

        guiHandler = new ShopRevancedGuiHandler(plugin);
        ShopRevancedHandler = new ShopRevancedHandler(plugin);
        shopSearchService = new ShopSearchService(this);
        guiHandler.loadIconsAndTitles();
        logHandler = new LogHandler(plugin, config);

        getServer().getPluginManager().registerEvents(displayListener, this);
        getServer().getPluginManager().registerEvents(ShopRevancedListener, this);
        getServer().getPluginManager().registerEvents(miscListener, this);
        getServer().getPluginManager().registerEvents(shopCreationListener, this);
        getServer().getPluginManager().registerEvents(creativeSelectionListener, this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        // Register new split listeners
        getServer().getPluginManager().registerEvents(bucketEmptyListener, this);
        getServer().getPluginManager().registerEvents(shopProtectionListener, this);

        // Initialize all optional plugin integrations via IntegrationManager
        integrationManager.initialize();

        int bstatsPluginId = 25211;
        metrics = new Metrics(plugin, bstatsPluginId);
        metrics.addCustomChart(new SingleLineChart("transactions", () -> logHandler.getRecentTransactionCount()));
        metrics.addCustomChart(new SingleLineChart("item_volume", () -> logHandler.getRecentItemVolume()));
        metrics.addCustomChart(new SingleLineChart("shops", () -> ShopRevancedHandler.getNumberOfShops()));
        metrics.addCustomChart(new AdvancedPie("shop_types", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Buy", ShopRevancedHandler.getNumberOfShops(ShopType.BUY));
            valueMap.put("Sell", ShopRevancedHandler.getNumberOfShops(ShopType.SELL));
            valueMap.put("Barter", ShopRevancedHandler.getNumberOfShops(ShopType.BARTER));
            valueMap.put("Combo", ShopRevancedHandler.getNumberOfShops(ShopType.COMBO));
            valueMap.put("Gamble", ShopRevancedHandler.getNumberOfShops(ShopType.GAMBLE));
            return valueMap;
        }));
        metrics.addCustomChart(new AdvancedPie("shop_display_types", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Floating Item", ShopRevancedHandler.getNumberOfShopDisplayTypes(DisplayType.ITEM));
            valueMap.put("Large Item", ShopRevancedHandler.getNumberOfShopDisplayTypes(DisplayType.LARGE_ITEM));
            valueMap.put("Item Frame", ShopRevancedHandler.getNumberOfShopDisplayTypes(DisplayType.ITEM_FRAME));
            valueMap.put("Glass Case", ShopRevancedHandler.getNumberOfShopDisplayTypes(DisplayType.GLASS_CASE));
            valueMap.put("None", ShopRevancedHandler.getNumberOfShopDisplayTypes(DisplayType.NONE));
            return valueMap;
        }));
        metrics.addCustomChart(new AdvancedPie("shop_containers", () -> ShopRevancedHandler.getShopContainerCounts()));
        metrics.addCustomChart(new SimplePie("economy_type", () -> { return currencyType.toString(); }));
        metrics.addCustomChart(new SimplePie("fractional_currency", () -> { return String.valueOf(allowFractionalCurrency); }));
        // Add metrics for more configuration options
        metrics.addCustomChart(new SimplePie("use_permissions", () -> String.valueOf(usePerms)));
        metrics.addCustomChart(new SimplePie("allow_partial_sales", () -> String.valueOf(allowPartialSales)));

        // Group these into an advanced pie
        metrics.addCustomChart(new AdvancedPie("shop_creation_methods", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Sign Creation", allowCreateMethodSign ? 1 : 0);
            valueMap.put("Chest Creation", allowCreateMethodChest ? 1 : 0);
            valueMap.put("Signs Disabled", allowCreateMethodSign ? 0 : 1);
            valueMap.put("Chests Disabled", allowCreateMethodChest ? 0 : 1);
            return valueMap;
        }));

        metrics.addCustomChart(new SimplePie("offline_purchase_notifications", () -> String.valueOf(offlinePurchaseNotificationsEnabled)));
        metrics.addCustomChart(new SimplePie("shop_gui_enabled", () -> { return String.valueOf(enableGUI); }));
        metrics.addCustomChart(new SimplePie("allow_searching_items", () -> String.valueOf(allowCreativeSelection)));
        metrics.addCustomChart(new SimplePie("check_item_durability", () -> String.valueOf(checkItemDurability)));
        metrics.addCustomChart(new SimplePie("ignore_item_repair_cost", () -> String.valueOf(ignoreItemRepairCost)));
        metrics.addCustomChart(new AdvancedPie("sounds_and_effects", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Sounds Enabled", playSounds ? 1 : 0);
            valueMap.put("Effects Enabled", playEffects ? 1 : 0);
            valueMap.put("Sounds Disabled", playSounds ? 0 : 1);
            valueMap.put("Effects Disabled", playEffects ? 0 : 1);
            return valueMap;
        }));
        
        metrics.addCustomChart(new SimplePie("worldguard_enabled", () -> { return String.valueOf(worldGuardExists()); }));
        metrics.addCustomChart(new SimplePie("towny_enabled", () -> { return String.valueOf(hookTowny); }));
        metrics.addCustomChart(new SimplePie("dynmap_enabled", () -> String.valueOf(dynmapEnabled)));
        metrics.addCustomChart(new SimplePie("bluemap_enabled", () -> String.valueOf(bluemapEnabled)));
        metrics.addCustomChart(new SimplePie("database_type", () -> String.valueOf(config.getString("logging.type"))));
        
        // Track display type preferences
        metrics.addCustomChart(new SimplePie("item_hover_display_type", () -> displayType.toString()));
        metrics.addCustomChart(new SimplePie("hover_text_activation_type", () -> displayTagOption.toString()));
        
        // Track if ShopRevanced auto-deletion is enabled
        metrics.addCustomChart(new SimplePie("auto_cleanup_dead_shops", () -> String.valueOf(hoursOfflineToRemoveShops > 0)));
        // Track if destroying shops requires sneaking
        metrics.addCustomChart(new SimplePie("destroy_requires_sneak", () -> String.valueOf(destroyShopRequiresSneak)));
        // Track if combo shops are inverted
        metrics.addCustomChart(new SimplePie("inverse_combo_shops", () -> String.valueOf(inverseComboShops)));

        // Add container types tracking - group by container categories
        metrics.addCustomChart(new AdvancedPie("enabled_containers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            // Track basic chest types
            boolean hasChests = enabledContainers.contains(Material.CHEST) || 
                                enabledContainers.contains(Material.TRAPPED_CHEST);
            valueMap.put("Chests Allowed", hasChests ? 1 : 0);
            valueMap.put("Chests Disabled", hasChests ? 0 : 1);
            
            // Track barrels
            boolean hasBarrel = enabledContainers.contains(Material.BARREL);
            valueMap.put("Barrels Allowed", hasBarrel ? 1 : 0);
            valueMap.put("Barrels Disabled", hasBarrel ? 0 : 1);
            
            // Track if any shulker box is enabled
            boolean hasShulker = enabledContainers.stream()
                    .anyMatch(m -> m.name().endsWith("SHULKER_BOX"));
            valueMap.put("Shulker Boxes Allowed", hasShulker ? 1 : 0);
            valueMap.put("Shulker Boxes Disabled", hasShulker ? 0 : 1);
            
            return valueMap;
        }));
        
        // Track economic barriers (costs)
        metrics.addCustomChart(new AdvancedPie("economic_barriers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Creation Cost", creationCost > 0 ? 1 : 0);
            valueMap.put("No Creation Cost", creationCost > 0 ? 0 : 1);
            valueMap.put("Destruction Cost", destructionCost > 0 ? 1 : 0);
            valueMap.put("No Destruction Cost", destructionCost > 0 ? 0 : 1);
            valueMap.put("Teleport Cost", teleportCost > 0 ? 1 : 0);
            valueMap.put("No Teleport Cost", teleportCost > 0 ? 0 : 1);
            valueMap.put("Return Creation Cost", returnCreationCost ? 1 : 0);
            valueMap.put("Do not Return Creation Cost", returnCreationCost ? 0 : 1);
            return valueMap;
        }));
        
        // Track display enhancement features (1.17+)
        metrics.addCustomChart(new AdvancedPie("display_enhancements", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Custom Light Level", displayLightLevel > 0 ? 1 : 0);
            valueMap.put("Normal Light Level", displayLightLevel > 0 ? 0 : 1);
            valueMap.put("Glowing Item Frames", setGlowingItemFrame ? 1 : 0);
            valueMap.put("Normal Item Frames", setGlowingItemFrame ? 0 : 1);
            valueMap.put("Glowing Sign Text", setGlowingSignText ? 1 : 0);
            valueMap.put("Normal Sign Text", setGlowingSignText ? 0 : 1);
            return valueMap;
        }));
        
        // Track which click types are used for each action
        // Find which click type is assigned to TRANSACT
        metrics.addCustomChart(new SimplePie("transaction_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.TRANSACT) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
        // Find which click type is assigned to TRANSACT_FULLSTACK
        metrics.addCustomChart(new SimplePie("full_stack_transaction_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.TRANSACT_FULLSTACK) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
        // Find which click type is assigned to VIEW_DETAILS
        metrics.addCustomChart(new SimplePie("view_details_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.VIEW_DETAILS) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
        // Find which click type is assigned to CYCLE_DISPLAY
        metrics.addCustomChart(new SimplePie("cycle_display_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.CYCLE_DISPLAY) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));

        metrics.addCustomChart(new SimplePie("display_processing_interval", () -> String.valueOf(displayProcessInterval)));
        metrics.addCustomChart(new SimplePie("display_movement_threshold", () -> String.valueOf(displayMovementThreshold)));
        metrics.addCustomChart(new SimplePie("display_max_shop_distance", () -> String.valueOf(maxShopDisplayDistance)));
        metrics.addCustomChart(new SimplePie("display_shop_search_radius", () -> String.valueOf(shopSearchRadius)));
        metrics.addCustomChart(new SimplePie("display_batch_size", () -> String.valueOf(displayBatchSize)));
        metrics.addCustomChart(new SimplePie("display_batch_delay", () -> String.valueOf(displayBatchDelay)));

        debug_allowUseOwnShop = config.getBoolean("debug.allowUseOwnShop");
        debug_transactionDebugLogs = config.getBoolean("debug.transactionDebugLogs");
        debug_shopCreateCooldown = config.getInt("debug.shopCreateCooldown");
        debug_forceResaveAll = config.getBoolean("debug.forceResaveAll");

        displayListener.startRepeatingDisplayViewTask();

        this.getLogger().info("Enabled ShopRevanced " + this.getDescription().getVersion());

        if(checkUpdates){
            new UpdateChecker(this).checkForUpdate();
        }
    }

    @Override
    public void onDisable(){
        // Cancel all FoliaLib scheduled tasks
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }
        
        //save any remaining shops (usually not required but just in case)
        if (ShopRevancedHandler != null) ShopRevancedHandler.saveAllShops();

        // Save player name cache to ensure no data loss
        PlayerNameCache.saveToFile();

        // shutdown the database
        if (logHandler != null) logHandler.shutdown();
        if (metrics != null) metrics.shutdown();

        // Shutdown all integrations
        if (integrationManager != null) integrationManager.shutdown();

        this.getLogger().info("Disabled ShopRevanced " + this.getDescription().getVersion());
    }

    public void reload(){
        this.getLogger().info("Reloading ShopRevanced " + this.getDescription().getVersion());

        HandlerList.unregisterAll(displayListener);
        HandlerList.unregisterAll(ShopRevancedListener);
        HandlerList.unregisterAll(miscListener);
        HandlerList.unregisterAll(creativeSelectionListener);
        HandlerList.unregisterAll(guiListener);

        // Unregister new split listeners
        HandlerList.unregisterAll(bucketEmptyListener);
        HandlerList.unregisterAll(shopCreationListener);
        HandlerList.unregisterAll(shopProtectionListener);

        // Shutdown integrations (onDisable will handle integration listener unregistration)
        if (integrationManager != null) integrationManager.shutdown();

        plugin.getShopRevancedHandler().removeAllDisplays(null);

        onDisable();
        onEnable();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            return false;
        }
        this.getLogger().notice("VaultUnlocked is installed, creating VaultUnlocked integration for Economy support");
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public ShopRevancedListener getShopRevancedListener() {
        return ShopRevancedListener;
    }

    public DisplayListener getDisplayListener() {
        return displayListener;
    }

    public MiscListener getMiscListener(){
        return miscListener;
    }

    public CreativeSelectionListener getCreativeSelectionListener() {
        return creativeSelectionListener;
    }

    public BucketEmptyListener getBucketEmptyListener() {
        return bucketEmptyListener;
    }

    public ShopCreationProcessManager getShopCreationProcessManager() {
        return shopCreationProcessManager;
    }

    public ShopCreationListener getShopCreationListener() {
        return shopCreationListener;
    }

    public ShopProtectionListener getShopProtectionListener() {
        return shopProtectionListener;
    }

    public BluemapHookListener getBluemapHookListener() {
        return integrationManager != null ? integrationManager.getBluemapHookListener() : null;
    }

    public TransactionHandler getTransactionHelper() {
        return transactionHandler;
    }

    public ShopRevancedHandler getShopRevancedHandler() {
        return ShopRevancedHandler;
    }

    public ShopSearchService getShopSearchService() {
        return shopSearchService;
    }

    public ShopRevancedGuiHandler getGuiHandler(){
        return guiHandler;
    }

    public boolean usePerms() {
        return usePerms;
    }

    public boolean getAllowCreationMethodSign(){
        return allowCreateMethodSign;
    }

    public boolean getAllowCreationMethodChest(){
        return allowCreateMethodChest;
    }

    public int getShopCreationTimeout(){
        return shopCreationTimeout;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    // Simplified WorldGuard configuration getter
    public WorldGuardHook.WorldGuardConfig getWorldGuardConfig() {
        return integrationManager != null ? integrationManager.getWorldGuardConfig() : null;
    }

    public boolean worldGuardExists() {
        return integrationManager != null && integrationManager.worldGuardExists();
    }

    public boolean isWorldGuardIntegrationEnabled() { return worldGuardExists(); }
    public boolean isLwcIntegrationEnabled() { return lwcIntegrationEnabled; }
    public boolean isBentoBoxIntegrationEnabled() { return bentoBoxIntegrationEnabled; }
    public boolean isAdvancedRegionMarketIntegrationEnabled() { return advancedRegionMarketIntegrationEnabled; }
    public boolean isPlotSquaredIntegrationEnabled() { return plotSquaredIntegrationEnabled; }
    public boolean isBoltTrustIntegrationEnabled() { return boltTrustIntegrationEnabled; }
    public boolean isBlockProtTrustIntegrationEnabled() { return blockProtTrustIntegrationEnabled; }
    public boolean isCashierLockTrustIntegrationEnabled() { return cashierLockTrustIntegrationEnabled; }
    public boolean isGriefPreventionIntegrationEnabled() { return griefPreventionIntegrationEnabled; }
    public boolean isGeyserIntegrationEnabled() { return geyserIntegrationEnabled; }
    public boolean arclightExists() { return arclightExists; }

    public boolean hookTowny(){
        return integrationManager != null && integrationManager.hookTowny();
    }

    public DisplayType getDisplayType(){
        return displayType;
    }

    public DisplayTagOption getDisplayTagOption(){
        return displayTagOption;
    }

    public DisplayType[] getDisplayCycle(){
        return displayCycle;
    }

    public boolean checkItemDurability(){
        return checkItemDurability;
    }
    public boolean ignoreItemRepairCost(){
        return ignoreItemRepairCost;
    }

    public boolean allowCreativeSelection(){
        return allowCreativeSelection;
    }

    public boolean forceDisplayToNoneIfBlocked() {
        return forceDisplayToNoneIfBlocked;
    }

    public int getDisplayLightLevel(){
        return displayLightLevel;
    }

    public boolean getGlowingItemFrame(){
        return setGlowingItemFrame;
    }

    public int getHoursOfflineToRemoveShops(){
        return hoursOfflineToRemoveShops;
    }

    public boolean playSounds(){
        return playSounds;
    }

    public boolean playEffects(){
        return playEffects;
    }

    public boolean getGlowingSignText(){
        return setGlowingSignText;
    }

    public NavigableMap<Double, String> getPriceSuffixes(){
        return priceSuffixes;
    }

    public Double getPriceSuffixMinimumValue(){
        return priceSuffixMinimumValue;
    }

    public boolean useGUI(){
        return enableGUI;
    }

    public ItemStack getGambleDisplayItem(){
        return gambleDisplayItem;
    }

    public ItemStack getItemCurrency() {
        return itemCurrency;
    }

    public boolean offlinePurchaseNotificationsEnabled() {
        return offlinePurchaseNotificationsEnabled;
    }

    private Boolean isMockBukkit = null;
    public boolean isMockBukkit() { 
        if (this.isMockBukkit == null) {
            this.isMockBukkit = plugin.getServer().getClass().getPackage().getName().contains("mockbukkit");
        }
        return this.isMockBukkit;
    }
    public boolean getDebug_allowUseOwnShop() { return debug_allowUseOwnShop; }
    public boolean getDebug_transactionDebugLogs() { return debug_transactionDebugLogs; }
    public int getDebug_shopCreateCooldown() { return debug_shopCreateCooldown; }
    public boolean getDebug_forceResaveAll() { return debug_forceResaveAll; }

    public void setItemCurrency(ItemStack itemCurrency){
        this.itemCurrency = itemCurrency;

        try {
            File fileDirectory = new File(getDataFolder(), "Data");
            File itemCurrencyFile = new File(fileDirectory, "itemCurrency.yml");
            YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
            currencyConfig.set("item", plugin.getItemCurrency());
            currencyConfig.save(itemCurrencyFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setGambleDisplayItem(ItemStack is){
        this.gambleDisplayItem = is;

        try{
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

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCommandAlias(){
        return commandAlias;
    }

    public String getPriceString(double price, boolean pricePer){
        if(price == 0){
            return ShopRevancedMessage.getFreePriceWord();
        }

        String format = currencyFormat;

        if(format.contains("[name]")){
            format = format.replace("[name]", currencyName);
        }
        if(format.contains("[price]")){
            if(currencyType == CurrencyType.VAULT) {
                return format.replace("[price]", UtilMethods.formatLongToKString(price, true));
                //return format.replace("[price]", new DecimalFormat("0.00").format(price).toString());
            }
            else if(pricePer) {
                return format.replace("[price]", UtilMethods.formatLongToKString(price, false));
                //return format.replace("[price]", new DecimalFormat("#.##").format(price).toString());
            }
            else
                return format.replace("[price]", ""+(int)price);
        }
        return format;
    }

    public String getPriceComboString(double price, double priceSell, boolean pricePer){
        if(price == 0){
            return ShopRevancedMessage.getFreePriceWord();
        }

        String format = currencyFormat;

        if(format.contains("[name]")){
            format = format.replace("[name]", currencyName);
        }
        if(format.contains("[price]")){
            if(currencyType == CurrencyType.VAULT)
                //return format.replace("[price]", new DecimalFormat("0.00").format(price)+"/"+new DecimalFormat("0.00").format(priceSell).toString());
            return format.replace("[price]", UtilMethods.formatLongToKString(price, true)+"/"+UtilMethods.formatLongToKString(priceSell, true));
            else if(pricePer)
                //return format.replace("[price]", new DecimalFormat("#.##").format(price).toString()+"/"+new DecimalFormat("0.00").format(priceSell).toString());
                return format.replace("[price]", UtilMethods.formatLongToKString(price, false)+"/"+UtilMethods.formatLongToKString(priceSell, true));
            else
                return format.replace("[price]", ""+(int)price+"/"+(int)priceSell);
        }
        return format;
    }

    public double getTaxPercent(){
        return taxPercent;
    }

    public Economy getEconomy() {

        if (econ == null) {
            setupEconomy();
        }

        return econ;
    }

    public boolean getAllowFractionalCurrency() { 
        return allowFractionalCurrency;
    }

    public List<Material> getEnabledContainers(){
        return enabledContainers;
    }

    public boolean inverseComboShops(){
        return inverseComboShops;
    }

    public double getCreationCost(){
        return creationCost;
    }

    public double getDestructionCost(){
        return destructionCost;
    }

    public boolean getDestroyShopRequiresSneak(){
        return destroyShopRequiresSneak;
    }

    public double getTeleportCost(){
        return teleportCost;
    }

    public double getTeleportCooldown(){
        return teleportCooldown;
    }

    public boolean returnCreationCost(){
        return returnCreationCost;
    }

    public boolean getAllowPartialSales(){
        return allowPartialSales;

    }

    public ItemNameUtil getItemNameUtil(){
        return itemNameUtil;
    }

    public ShopRevancedCreationUtil getShopRevancedCreationUtil(){
        return ShopRevancedCreationUtil;
    }

    public ItemListType getItemListType(){
        return itemListType;
    }

    public List<String> getWorldBlacklist(){
        return worldBlackList;
    }

    public ShopAction getShopAction(ShopClickType shopClickType){
        return clickTypeActionMap.get(shopClickType);
    }

    public NamespacedKey getSignLocationNameSpacedKey(){
        return signLocationNameSpacedKey;
    }

    public NamespacedKey getPlayerUUIDNameSpacedKey(){
        return playerUUIDNameSpacedKey;
    }

    public LogHandler getLogHandler(){
        return logHandler;
    }

    // Getter for FoliaLib
    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

    public double getDisplayProcessInterval() {
        return displayProcessInterval;
    }
    
    public double getDisplayMovementThreshold() {
        return displayMovementThreshold;
    }

    /**
     * Gets the maximum distance at which ShopRevanced displays will be shown to players.
     * Higher values will show shops from further away but may cause client lag.
     * 
     * @return The maximum display distance in blocks
     */
    public double getMaxShopDisplayDistance() {
        return maxShopDisplayDistance;
    }

    /**
     * Gets the radius (in chunks) around a player to search for shops.
     * Each increment searches exponentially more chunks (1=3x3 area, 2=5x5 area, 3=7x7 area).
     * 
     * @return The ShopRevanced search radius in chunks
     */
    public int getShopSearchRadius() {
        return shopSearchRadius;
    }

    /**
     * Gets the number of ShopRevanced displays to process in a single batch when sending to a player.
     * This controls how many displays are sent at once to create a smoother appearance.
     * 
     * @return The batch size for ShopRevanced display processing
     */
    public int getDisplayBatchSize() {
        return displayBatchSize;
    }

    /**
     * Gets the delay between batches of ShopRevanced displays in server ticks (20 ticks = 1 second).
     * Higher values create a smoother appearance but take longer to show all displays.
     * 
     * @return The delay in ticks between display batches
     */
    public int getDisplayBatchDelay() {
        return displayBatchDelay;
    }
}







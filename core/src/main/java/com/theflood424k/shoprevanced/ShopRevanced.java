package com.theflood424k.shoprevanced;

import com.theflood424k.shoprevanced.config.ConfigManager;
import com.theflood424k.shoprevanced.config.ConfigValidator;
import com.theflood424k.shoprevanced.display.DisplayScheduler;
import com.theflood424k.shoprevanced.display.DisplayTagOption;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.gui.ShopGUIListener;
import com.theflood424k.shoprevanced.handler.PluginBootstrap;
import com.theflood424k.shoprevanced.handler.TransactionHandler;
import com.theflood424k.shoprevanced.util.ShopAction;
import com.theflood424k.shoprevanced.util.ShopClickType;
import com.theflood424k.shoprevanced.integration.BluemapHookListener;
import com.theflood424k.shoprevanced.integration.IntegrationCoordinator;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.listener.BucketEmptyListener;
import com.theflood424k.shoprevanced.listener.CreativeSelectionListener;
import com.theflood424k.shoprevanced.listener.DisplayListener;
import com.theflood424k.shoprevanced.listener.MiscListener;
import com.theflood424k.shoprevanced.listener.ShopCreationListener;
import com.theflood424k.shoprevanced.listener.ShopCreationProcessManager;
import com.theflood424k.shoprevanced.listener.ShopProtectionListener;
import com.theflood424k.shoprevanced.listener.ShopRevancedListener;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.ConfigUpdater;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.EconomyUtils;
import com.theflood424k.shoprevanced.util.InventoryUtils;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.ItemNameUtil;
import com.theflood424k.shoprevanced.handler.LogHandler;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.util.Metrics;
import com.theflood424k.shoprevanced.util.PlayerNameCache;
import com.theflood424k.shoprevanced.util.ShopRevancedCreationUtil;
import com.theflood424k.shoprevanced.util.ShopRevancedLogger;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import com.theflood424k.shoprevanced.util.UtilMethods;
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
    private IntegrationCoordinator integrationCoordinator;

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
    private ShopRevancedGuiHandler guiHandler;
    private ItemNameUtil itemNameUtil;
    private ShopRevancedCreationUtil ShopRevancedCreationUtil;
    private LogHandler logHandler;
    private Metrics metrics;

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

    // Bootstrap coordinator (delegates lifecycle wiring)
    private PluginBootstrap pluginBootstrap;

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
        pluginBootstrap = new PluginBootstrap(this);
        integrationCoordinator = new IntegrationCoordinator(this);
    }

    @Override
    public void onEnable() {
        plugin = this;

        configManager.reloadConfig();
        YamlConfiguration config = configManager.getConfig();
        // Load logger values again in case the log level was changed on a reload
        this.getLogger().setLogLevel(config.getString("logLevel"));
        this.getLogger().enableColor(config.getBoolean("enableLogColor"));
        this.getLogger().enableColor(configManager.getConfig().getBoolean("enableLogColor"));

        // Prepare config files and run migrations before loading config fields
        pluginBootstrap.copyDefaultConfigFiles();
        pluginBootstrap.runConfigMigrations(configManager);
        pluginBootstrap.createDataFolder();

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

        // Sync config fields to extracted subsystems before lifecycle wiring
        syncDisplaySchedulerFields();

        pluginBootstrap.bootstrap(configManager, config);
    }

    /** Sync ShopRevanced config fields to DisplayScheduler for metrics instrumentation. */
    private void syncDisplaySchedulerFields() {
        var ds = pluginBootstrap.getDisplayScheduler();
        ds.setDisplayType(displayType);
        ds.setDisplayTagOption(displayTagOption);
        ds.setDisplayCycle(displayCycle);
        ds.setDisplayProcessInterval(displayProcessInterval);
        ds.setDisplayMovementThreshold(displayMovementThreshold);
        ds.setMaxShopDisplayDistance(maxShopDisplayDistance);
        ds.setShopSearchRadius(shopSearchRadius);
        ds.setDisplayBatchSize(displayBatchSize);
        ds.setDisplayBatchDelay(displayBatchDelay);
        ds.setDisplayLightLevel(displayLightLevel);
        ds.setGlowingItemFrame(setGlowingItemFrame);
        ds.setGlowingSignText(setGlowingSignText);
        ds.setUsePerms(usePerms);
        ds.setAllowPartialSales(allowPartialSales);
        ds.setAllowCreateMethodSign(allowCreateMethodSign);
        ds.setAllowCreateMethodChest(allowCreateMethodChest);
        ds.setOfflinePurchaseNotificationsEnabled(offlinePurchaseNotificationsEnabled);
        ds.setEnableGUI(enableGUI);
        ds.setAllowCreativeSelection(allowCreativeSelection);
        ds.setCheckItemDurability(checkItemDurability);
        ds.setIgnoreItemRepairCost(ignoreItemRepairCost);
        ds.setPlaySounds(playSounds);
        ds.setPlayEffects(playEffects);
        ds.setHoursOfflineToRemoveShops(hoursOfflineToRemoveShops);
        ds.setDestroyShopRequiresSneak(destroyShopRequiresSneak);
        ds.setInverseComboShops(inverseComboShops);
        ds.setCurrencyType(currencyType);
        ds.setCreationCost(creationCost);
        ds.setDestructionCost(destructionCost);
        ds.setTeleportCost(teleportCost);
        ds.setReturnCreationCost(returnCreationCost);
        ds.setClickTypeActionMap(clickTypeActionMap);
        ds.setPriceSuffixes(priceSuffixes);
        ds.setBluemapEnabled(bluemapEnabled);
        ds.setDynmapEnabled(dynmapEnabled);
    }

    @Override
    public void onDisable(){
        pluginBootstrap.shutdown();
    }

    public void reload(){
        pluginBootstrap.reload(configManager, getConfigManager().getConfig());
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
        return integrationCoordinator.getBluemapHookListener();
    }

    public TransactionHandler getTransactionHelper() {
        return transactionHandler;
    }

    public ShopRevancedHandler getShopRevancedHandler() {
        return ShopRevancedHandler;
    }

    public ShopRevancedGuiHandler getGuiHandler(){
        return guiHandler;
    }

    public ShopGUIListener getGuiListener(){
        return guiListener;
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
        return integrationCoordinator.getWorldGuardConfig();
    }

    public boolean worldGuardExists() {
        return integrationCoordinator.worldGuardExists();
    }

    public boolean isWorldGuardIntegrationEnabled() { return integrationCoordinator.isWorldGuardIntegrationEnabled(); }
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
        return integrationCoordinator.hookTowny();
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

    // ---- Setters for PluginBootstrap delegation ----

    public void setFoliaLib(FoliaLib foliaLib) {
        this.foliaLib = foliaLib;
    }

    public void setShopRevancedCreationUtil(ShopRevancedCreationUtil shopRevancedCreationUtil) {
        ShopRevancedCreationUtil = shopRevancedCreationUtil;
    }

    public void setShopRevancedListener(ShopRevancedListener shopRevancedListener) {
        ShopRevancedListener = shopRevancedListener;
    }

    public void setTransactionHandler(TransactionHandler transactionHandler) {
        this.transactionHandler = transactionHandler;
    }

    public void setMiscListener(MiscListener miscListener) {
        this.miscListener = miscListener;
    }

    public void setCreativeSelectionListener(CreativeSelectionListener creativeSelectionListener) {
        this.creativeSelectionListener = creativeSelectionListener;
    }

    public void setDisplayListener(DisplayListener displayListener) {
        this.displayListener = displayListener;
    }

    public void setGuiListener(ShopGUIListener guiListener) {
        this.guiListener = guiListener;
    }

    public void setShopCreationProcessManager(ShopCreationProcessManager shopCreationProcessManager) {
        this.shopCreationProcessManager = shopCreationProcessManager;
    }

    public void setBucketEmptyListener(BucketEmptyListener bucketEmptyListener) {
        this.bucketEmptyListener = bucketEmptyListener;
    }

    public void setShopCreationListener(ShopCreationListener shopCreationListener) {
        this.shopCreationListener = shopCreationListener;
    }

    public void setShopProtectionListener(ShopProtectionListener shopProtectionListener) {
        this.shopProtectionListener = shopProtectionListener;
    }

    public void setItemNameUtil(ItemNameUtil itemNameUtil) {
        this.itemNameUtil = itemNameUtil;
    }

    public void setGuiHandler(ShopRevancedGuiHandler guiHandler) {
        this.guiHandler = guiHandler;
    }

    public void setShopRevancedHandler(ShopRevancedHandler shopRevancedHandler) {
        ShopRevancedHandler = shopRevancedHandler;
    }

    public void setLogHandler(LogHandler logHandler) {
        this.logHandler = logHandler;
    }
}







package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.config.ConfigManager;
import com.theflood424k.shoprevanced.config.ConfigValidator;
import com.theflood424k.shoprevanced.listener.BucketEmptyListener;
import com.theflood424k.shoprevanced.listener.CreativeSelectionListener;
import com.theflood424k.shoprevanced.listener.DisplayListener;
import com.theflood424k.shoprevanced.listener.MiscListener;
import com.theflood424k.shoprevanced.listener.ShopCreationListener;
import com.theflood424k.shoprevanced.listener.ShopCreationProcessManager;
import com.theflood424k.shoprevanced.listener.ShopProtectionListener;
import com.theflood424k.shoprevanced.listener.ShopRevancedListener;
import com.theflood424k.shoprevanced.gui.ShopGUIListener;
import com.theflood424k.shoprevanced.integration.IntegrationCoordinator;
import com.theflood424k.shoprevanced.display.DisplayScheduler;
import com.theflood424k.shoprevanced.util.ConfigUpdater;
import com.theflood424k.shoprevanced.handler.LogHandler;
import com.theflood424k.shoprevanced.util.PlayerNameCache;
import com.theflood424k.shoprevanced.util.ShopRevancedCreationUtil;
import com.theflood424k.shoprevanced.util.ItemNameUtil;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import com.theflood424k.shoprevanced.util.UpdateChecker;
import com.theflood424k.shoprevanced.util.UtilMethods;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Bootstraps the ShopRevanced plugin: wires listeners, handlers, economy, and
 * subsystem initialization.
 * <p>
 * Extracted from {@link ShopRevanced} to isolate plugin lifecycle wiring logic
 * into a cohesive class. ShopRevanced delegates {@code onEnable},
 * {@code onDisable}, and {@code reload} to this class. Config field loading
 * remains in ShopRevanced to preserve the public getter API.
 * </p>
 */
public class PluginBootstrap {

    private final ShopRevanced plugin;
    private final IntegrationCoordinator integrationCoordinator;
    private final DisplayScheduler displayScheduler;

    public PluginBootstrap(ShopRevanced plugin) {
        this.plugin = plugin;
        this.integrationCoordinator = new IntegrationCoordinator(plugin);
        this.displayScheduler = new DisplayScheduler(plugin);
    }

    public IntegrationCoordinator getIntegrationCoordinator() {
        return integrationCoordinator;
    }

    public DisplayScheduler getDisplayScheduler() {
        return displayScheduler;
    }

    /**
     * Perform full plugin initialization (called from {@code onEnable}).
     * Config fields are loaded by ShopRevanced before this is called.
     */
    public void bootstrap(ConfigManager configManager, YamlConfiguration config) {
        plugin.setFoliaLib(new FoliaLib(plugin));

        createListenersAndHandlers();
        setupEconomy(configManager);

        try {
            new ConfigValidator().validate(configManager, configManager.getEconomy());
        } catch (IllegalStateException e) {
            plugin.getLogger().severe("Invalid configuration: " + e.getMessage());
            plugin.getLogger().severe("Plugin Disabled: invalid configuration value(s) in config.yml.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        createCommandHandler(config);
        createGuiHandler();
        createShopHandler();
        createLogHandler(config);

        registerEvents();

        integrationCoordinator.setupIntegrations(configManager, config);

        displayScheduler.setupMetrics();
        displayScheduler.startDisplayTasks(plugin.getDisplayListener());

        plugin.getLogger().info("Enabled ShopRevanced " + plugin.getDescription().getVersion());

        if (config.getBoolean("checkUpdates", true)) {
            new UpdateChecker(plugin).checkForUpdate();
        }
    }

    /**
     * Perform plugin shutdown (called from {@code onDisable}).
     */
    public void shutdown() {
        if (plugin.getFoliaLib() != null) {
            plugin.getFoliaLib().getScheduler().cancelAllTasks();
        }

        if (plugin.getShopRevancedHandler() != null) {
            plugin.getShopRevancedHandler().saveAllShops();
        }

        PlayerNameCache.saveToFile();

        if (plugin.getLogHandler() != null) {
            plugin.getLogHandler().shutdown();
        }
        displayScheduler.shutdown();

        integrationCoordinator.shutdownIntegrations();

        plugin.getLogger().info("Disabled ShopRevanced " + plugin.getDescription().getVersion());
    }

    /**
     * Reload the plugin (called from {@code reload}).
     */
    public void reload(ConfigManager configManager, YamlConfiguration config) {
        plugin.getLogger().info("Reloading ShopRevanced " + plugin.getDescription().getVersion());

        unregisterEvents();

        integrationCoordinator.shutdownIntegrations();

        plugin.getShopRevancedHandler().removeAllDisplays(null);

        shutdown();
        bootstrap(configManager, config);
    }

    // ---- public helpers (called by ShopRevanced before bootstrap) ----
    public void copyDefaultConfigFiles() {
        copyConfigFile("config.yml");
        copyConfigFile("chatConfig.yml");
        copyConfigFile("signConfig.yml");
        copyConfigFile("displayConfig.yml");
    }

    private void copyConfigFile(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource(fileName), configFile);
        }
    }

    public void runConfigMigrations(ConfigManager configManager) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);
            if (oldConfig.get("offlinePurchaseNotifications") == null
                    && oldConfig.getString("logging.type").equals("OFF")) {
                plugin.getLogger().info("Config default update: v1.10.0(+) is being run for the first time, "
                        + "setting logging type to FILE from old default OFF");
                oldConfig.set("logging.type", "FILE");
                oldConfig.save(configFile);
            }
            if (oldConfig.get("hookWorldGuard") != null && oldConfig.get("worldGuard.requireAllowShopFlag") == null) {
                boolean oldValue = oldConfig.getBoolean("hookWorldGuard");
                plugin.getLogger().info("Config migration: moving 'hookWorldGuard' to 'worldGuard.requireAllowShopFlag'");
                oldConfig.set("worldGuard.requireAllowShopFlag", oldValue);
                oldConfig.set("hookWorldGuard", null);
                oldConfig.save(configFile);
            }
            new ConfigUpdater().update(plugin, "config.yml", configFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            new ConfigUpdater().update(plugin, "chatConfig.yml",
                    new File(plugin.getDataFolder(), "chatConfig.yml"), new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            new ConfigUpdater().update(plugin, "signConfig.yml",
                    new File(plugin.getDataFolder(), "signConfig.yml"), new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            new ConfigUpdater().update(plugin, "displayConfig.yml",
                    new File(plugin.getDataFolder(), "displayConfig.yml"), new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createDataFolder() {
        File fileDirectory = new File(plugin.getDataFolder(), "Data");
        if (!fileDirectory.exists()) {
            boolean success = fileDirectory.mkdirs();
            if (!success) {
                plugin.getLogger().severe("[ShopRevanced] Data folder could not be created!");
            }
        }
    }

    private void createListenersAndHandlers() {
        plugin.setShopRevancedCreationUtil(new ShopRevancedCreationUtil(plugin));

        plugin.setShopRevancedListener(new ShopRevancedListener(plugin));
        plugin.setTransactionHandler(new TransactionHandler(plugin));
        plugin.setMiscListener(new MiscListener(plugin));
        plugin.setCreativeSelectionListener(new CreativeSelectionListener(plugin));
        plugin.setDisplayListener(new DisplayListener(plugin));
        plugin.setGuiListener(new ShopGUIListener(plugin));

        plugin.setShopCreationProcessManager(new ShopCreationProcessManager(plugin));
        plugin.setBucketEmptyListener(new BucketEmptyListener(plugin));
        plugin.setShopCreationListener(new ShopCreationListener(plugin, plugin.getShopCreationProcessManager()));
        plugin.setShopProtectionListener(new ShopProtectionListener(plugin, plugin.getShopCreationProcessManager()));

        new ShopRevancedMessage(plugin);
        plugin.setItemNameUtil(new ItemNameUtil());
    }

    private void setupEconomy(ConfigManager configManager) {
        if (configManager.getCurrencyType() == com.theflood424k.shoprevanced.util.CurrencyType.VAULT) {
            if (configManager.setupEconomy()) {
                plugin.getLogger().info("Shops will use the VaultUnlocked economy ("
                        + configManager.getCurrencyName() + ") as currency on the server.");
            } else {
                plugin.getLogger().severe("Unable to connect to VaultUnlocked Economy! Are both VaultUnlocked "
                        + "AND an Economy plugin installed?");
                plugin.getLogger().severe("Plugin Disabled: Invalid configuration value `economy.type` config.yml. "
                        + "If you do not wish to use Vault with ShopRevanced, make sure to set `economy.type` "
                        + "in the config file to `ITEM`.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
        } else {
            if (configManager.getItemCurrency() == null) {
                plugin.getLogger().severe("Plugin Disabled: Invalid value for `itemCurrencyID` in `config.yml`");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
            plugin.getLogger().info("Shops will use "
                    + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(plugin.getItemNameUtil().getName(configManager.getItemCurrency()))
                    + "(s) as the currency on the server.");
        }
    }

    private void createCommandHandler(YamlConfiguration config) {
        new com.theflood424k.shoprevanced.handler.CommandHandler(plugin, null, plugin.getCommandAlias(),
                "Base command for the ShopRevanced plugin", "/ShopRevanced",
                new ArrayList<>(java.util.Arrays.asList(plugin.getCommandAlias())));
    }

    private void createGuiHandler() {
        plugin.setGuiHandler(new com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler(plugin));
        plugin.getGuiHandler().loadIconsAndTitles();
    }

    private void createShopHandler() {
        plugin.setShopRevancedHandler(new ShopRevancedHandler(plugin));
    }

    private void createLogHandler(YamlConfiguration config) {
        plugin.setLogHandler(new LogHandler(plugin, config));
    }

    private void registerEvents() {
        plugin.getServer().getPluginManager().registerEvents(plugin.getDisplayListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getShopRevancedListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getMiscListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getShopCreationListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getCreativeSelectionListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getGuiListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getBucketEmptyListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(plugin.getShopProtectionListener(), plugin);
    }

    private void unregisterEvents() {
        HandlerList.unregisterAll(plugin.getDisplayListener());
        HandlerList.unregisterAll(plugin.getShopRevancedListener());
        HandlerList.unregisterAll(plugin.getMiscListener());
        HandlerList.unregisterAll(plugin.getCreativeSelectionListener());
        HandlerList.unregisterAll(plugin.getGuiListener());
        HandlerList.unregisterAll(plugin.getBucketEmptyListener());
        HandlerList.unregisterAll(plugin.getShopCreationListener());
        HandlerList.unregisterAll(plugin.getShopProtectionListener());
    }
}

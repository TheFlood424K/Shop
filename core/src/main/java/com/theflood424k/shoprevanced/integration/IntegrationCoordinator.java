package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Coordinates all external plugin integrations for ShopRevanced.
 * <p>
 * Extracted from {@link ShopRevanced} to reduce the god class surface area.
 * Holds integration-enabled flags, the {@link IntegrationManager}, and provides
 * setup/shutdown lifecycle methods plus getter compatibility for existing callers.
 * </p>
 */
public class IntegrationCoordinator {

    private final ShopRevanced plugin;
    private final IntegrationManager integrationManager;

    // Integration-enabled flags (synced for test/API compatibility)
    private boolean lwcIntegrationEnabled;
    private boolean bentoBoxIntegrationEnabled;
    private boolean advancedRegionMarketIntegrationEnabled;
    private boolean plotSquaredIntegrationEnabled;
    private boolean boltTrustIntegrationEnabled;
    private boolean blockProtTrustIntegrationEnabled;
    private boolean cashierLockTrustIntegrationEnabled;
    private boolean griefPreventionIntegrationEnabled;
    private boolean geyserIntegrationEnabled;
    private boolean bluemapEnabled;
    private boolean dynmapEnabled;
    private boolean hookTowny;

    public IntegrationCoordinator(ShopRevanced plugin) {
        this.plugin = plugin;
        this.integrationManager = new IntegrationManager(plugin);
    }

    /**
     * Configure and initialize all integrations based on config values.
     */
    public void setupIntegrations(ConfigManager configManager, YamlConfiguration config) {
        // Load integration-enabled flags from config BEFORE passing them to the
        // IntegrationManager. Reading them after the setters below would pass the
        // default (false) values and silently disable the integrations.
        bluemapEnabled = config.getBoolean("bluemap.enabled", false);
        dynmapEnabled = config.getBoolean("dynmap.enabled", false);
        hookTowny = configManager.hookTowny();

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

        integrationManager.initialize();
    }

    /**
     * Shutdown all integrations.
     */
    public void shutdownIntegrations() {
        if (integrationManager != null) {
            integrationManager.shutdown();
        }
    }

    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    public boolean worldGuardExists() {
        return integrationManager != null && integrationManager.worldGuardExists();
    }

    public boolean isWorldGuardIntegrationEnabled() {
        return worldGuardExists();
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

    public boolean isBluemapEnabled() {
        return bluemapEnabled;
    }

    public boolean isDynmapEnabled() {
        return dynmapEnabled;
    }

    public boolean hookTowny() {
        return integrationManager != null && integrationManager.hookTowny();
    }

    public WorldGuardHook.WorldGuardConfig getWorldGuardConfig() {
        return integrationManager != null ? integrationManager.getWorldGuardConfig() : null;
    }

    public BluemapHookListener getBluemapHookListener() {
        return integrationManager != null ? integrationManager.getBluemapHookListener() : null;
    }
}

package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.util.CurrencyType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Manages all optional plugin integrations (WorldGuard, LWC, Dynmap, BlueMap, BentoBox,
 * AdvancedRegionMarket, PlotSquared, Bolt, BlockProt, CashierLock, GriefPrevention3D,
 * Geyser/Floodgate, Towny, Arclight, Vault economy).
 *
 * <p>Extracting integration logic from the {@link ShopRevanced} God class reduces its
 * responsibilities and makes each integration independently testable. ShopRevanced delegates
 * integration-related queries to this manager, preserving the existing public API.</p>
 */
public class IntegrationManager {

    private final ShopRevanced plugin;

    // WorldGuard
    private boolean worldGuardExists = false;
    private WorldGuardHook.WorldGuardConfig worldGuardConfig;

    // LWC
    private boolean lwcIntegrationEnabled;
    private LWCHookListener lwcHookListener;

    // Dynmap
    private boolean dynmapEnabled;
    private DynmapHookListener dynmapHookListener;

    // BlueMap
    private boolean bluemapEnabled;
    private BluemapHookListener bluemapHookListener;

    // BentoBox
    private boolean bentoBoxIntegrationEnabled;
    private BentoBoxHookListener bentoBoxHookListener;

    // AdvancedRegionMarket
    private boolean advancedRegionMarketIntegrationEnabled;
    private ARMHookListener armHookListener;

    // PlotSquared
    private boolean plotSquaredIntegrationEnabled;
    private PlotSquaredHookListener plotSquaredHookListener;

    // Bolt trust
    private boolean boltTrustIntegrationEnabled;
    private BoltTrustListener boltTrustListener;

    // BlockProt trust
    private boolean blockProtTrustIntegrationEnabled;
    private BlockProtTrustListener blockProtTrustListener;

    // CashierLock trust
    private boolean cashierLockTrustIntegrationEnabled;
    private CashierLockTrustListener cashierLockTrustListener;

    // GriefPrevention3D
    private boolean griefPreventionIntegrationEnabled;
    private GriefPreventionHookListener griefPreventionHookListener;

    // Geyser/Floodgate
    private boolean geyserIntegrationEnabled;
    private GeyserFloodgateHook geyserFloodgateHook;

    // Towny
    private boolean hookTowny;

    // Arclight
    private boolean arclightExists;

    // Economy (Vault)
    private Economy econ;

    public IntegrationManager(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes all integrations: detects installed plugins, reads enabled flags from config,
     * creates hook listeners, and registers event listeners.
     */
    public void initialize() {
        initWorldGuard();
        initTowny();
        initLWC();
        initDynmap();
        initBlueMap();
        initBentoBox();
        initAdvancedRegionMarket();
        initPlotSquared();
        initBoltTrust();
        initBlockProtTrust();
        initCashierLockTrust();
        initGriefPrevention();
        initArclight();
        initGeyserFloodgate();
        initEconomy();
    }

    /**
     * Unregisters all integration event listeners and shuts down the economy provider.
     */
    public void shutdown() {
        if (lwcHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(lwcHookListener);
        }
        if (dynmapHookListener != null) {
            dynmapHookListener.deleteMarkers();
            org.bukkit.event.HandlerList.unregisterAll(dynmapHookListener);
        }
        if (bluemapHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(bluemapHookListener);
        }
        if (bentoBoxHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(bentoBoxHookListener);
        }
        if (armHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(armHookListener);
        }
        if (plotSquaredHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(plotSquaredHookListener);
        }
        if (boltTrustListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(boltTrustListener);
        }
        if (blockProtTrustListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(blockProtTrustListener);
        }
        if (cashierLockTrustListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(cashierLockTrustListener);
        }
        if (griefPreventionHookListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(griefPreventionHookListener);
        }
        if (geyserFloodgateHook != null) {
            org.bukkit.event.HandlerList.unregisterAll(geyserFloodgateHook);
        }
    }

    /* ---------------------------------------------------------------- *
     * WorldGuard                                                       *
     * ---------------------------------------------------------------- */

    private void initWorldGuard() {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            if (worldGuardExists) {
                plugin.getLogger().notice("WorldGuard is installed, WorldGuard integration is enabled");
                if (worldGuardConfig.requireAllowShopFlag) {
                    plugin.getLogger().helpful("WorldGuard `allow-ShopRevanced` flag restriction enabled, Shops can only be created in regions with the `allow-ShopRevanced` flag set!");
                } else {
                    plugin.getLogger().helpful("WorldGuard `allow-ShopRevanced` flag restriction is disabled, if you want to only allow shops in regions with the `allow-ShopRevanced` flag, please set `worldGuard.requireAllowShopFlag` to `true` in `config.yml`");
                }
                plugin.getLogger().helpful("WorldGuard flags that will be checked for ShopRevanced creation: " + worldGuardConfig.createShopFlags.toString());
                plugin.getLogger().helpful("WorldGuard flags that will be checked for ShopRevanced use: " + worldGuardConfig.useShopFlags.toString());
            } else {
                plugin.getLogger().notice("WorldGuard detected, but ShopRevanced WorldGuard integration is disabled via `worldGuard.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Towny                                                            *
     * ---------------------------------------------------------------- */

    private void initTowny() {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") != null && hookTowny) {
            plugin.getLogger().notice("Towny is installed, ShopRevanced will respect Towny!");
        }
    }

    /* ---------------------------------------------------------------- *
     * LWC                                                              *
     * ---------------------------------------------------------------- */

    private void initLWC() {
        if (plugin.getServer().getPluginManager().getPlugin("LWC") != null) {
            if (lwcIntegrationEnabled) {
                lwcHookListener = new LWCHookListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(lwcHookListener, plugin);
                plugin.getLogger().notice("LWC is installed, enabling LWC integration");
            } else {
                plugin.getLogger().notice("LWC detected, but ShopRevanced LWC integration is disabled via `lwc.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Dynmap                                                           *
     * ---------------------------------------------------------------- */

    private void initDynmap() {
        if (plugin.getServer().getPluginManager().getPlugin("dynmap") != null && dynmapEnabled) {
            dynmapHookListener = new DynmapHookListener(plugin);
            plugin.getServer().getPluginManager().registerEvents(dynmapHookListener, plugin);
            plugin.getLogger().notice("Dynmap is installed, creating Dynmap listener");
        }
    }

    /* ---------------------------------------------------------------- *
     * BlueMap                                                          *
     * ---------------------------------------------------------------- */

    private void initBlueMap() {
        if (plugin.getServer().getPluginManager().getPlugin("BlueMap") != null && bluemapEnabled) {
            plugin.getLogger().notice("BlueMap is installed, starting BlueMap integration");
            plugin.getFoliaLib().getScheduler().runTimer(task -> {
                de.bluecolored.bluemap.api.BlueMapAPI.getInstance().ifPresent(api -> {
                    plugin.getLogger().debug("BlueMap is ready, creating BlueMap listener");
                    bluemapHookListener = new BluemapHookListener(plugin);
                    plugin.getServer().getPluginManager().registerEvents(bluemapHookListener, plugin);
                    bluemapHookListener.reloadMarkers(plugin.getShopRevancedHandler());
                    plugin.getFoliaLib().getScheduler().cancelTask(task);
                });
            }, 20, 20);
        }
    }

    /* ---------------------------------------------------------------- *
     * BentoBox                                                         *
     * ---------------------------------------------------------------- */

    private void initBentoBox() {
        if (plugin.getServer().getPluginManager().getPlugin("BentoBox") != null) {
            if (bentoBoxIntegrationEnabled) {
                bentoBoxHookListener = new BentoBoxHookListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(bentoBoxHookListener, plugin);
                plugin.getLogger().notice("BentoBox is installed, enabling BentoBox integration");
            } else {
                plugin.getLogger().notice("BentoBox detected, but ShopRevanced BentoBox integration is disabled via `bentoBox.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * AdvancedRegionMarket                                             *
     * ---------------------------------------------------------------- */

    private void initAdvancedRegionMarket() {
        if (plugin.getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null) {
            if (advancedRegionMarketIntegrationEnabled) {
                armHookListener = new ARMHookListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(armHookListener, plugin);
                plugin.getLogger().notice("AdvancedRegionMarket is installed, enabling AdvancedRegionMarket integration");
            } else {
                plugin.getLogger().notice("AdvancedRegionMarket detected, but ShopRevanced AdvancedRegionMarket integration is disabled via `advancedRegionMarket.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * PlotSquared                                                      *
     * ---------------------------------------------------------------- */

    private void initPlotSquared() {
        if (plugin.getServer().getPluginManager().getPlugin("PlotSquared") != null) {
            if (plotSquaredIntegrationEnabled) {
                plotSquaredHookListener = new PlotSquaredHookListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(plotSquaredHookListener, plugin);
                plugin.getLogger().notice("PlotSquared is installed, enabling PlotSquared integration");
            } else {
                plugin.getLogger().notice("PlotSquared detected, but ShopRevanced PlotSquared integration is disabled via `plotSquared.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Bolt trust                                                       *
     * ---------------------------------------------------------------- */

    private void initBoltTrust() {
        if (plugin.getServer().getPluginManager().getPlugin("Bolt") != null) {
            if (boltTrustIntegrationEnabled) {
                try {
                    boltTrustListener = new BoltTrustListener();
                    plugin.getServer().getPluginManager().registerEvents(boltTrustListener, plugin);
                    plugin.getLogger().notice("Bolt is installed, enabling trust integration for opening ShopRevanced containers");
                } catch (Exception e) {
                    plugin.getLogger().warning("Bolt detected but could not enable trust integration: " + e.getMessage());
                }
            } else {
                plugin.getLogger().notice("Bolt detected, but ShopRevanced Bolt trust integration is disabled via `bolt.trustIntegration.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * BlockProt trust                                                  *
     * ---------------------------------------------------------------- */

    private void initBlockProtTrust() {
        if (plugin.getServer().getPluginManager().getPlugin("BlockProt") != null) {
            if (blockProtTrustIntegrationEnabled) {
                try {
                    blockProtTrustListener = new BlockProtTrustListener();
                    plugin.getServer().getPluginManager().registerEvents(blockProtTrustListener, plugin);
                    plugin.getLogger().notice("BlockProt is installed, enabling trust integration for opening ShopRevanced containers");
                } catch (Exception e) {
                    plugin.getLogger().warning("BlockProt detected but could not enable trust integration: " + e.getMessage());
                }
            } else {
                plugin.getLogger().notice("BlockProt detected, but ShopRevanced BlockProt trust integration is disabled via `blockProt.trustIntegration.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * CashierLock trust                                                *
     * ---------------------------------------------------------------- */

    private void initCashierLockTrust() {
        if (plugin.getServer().getPluginManager().getPlugin("CashierLock") != null) {
            if (cashierLockTrustIntegrationEnabled) {
                try {
                    cashierLockTrustListener = new CashierLockTrustListener();
                    plugin.getServer().getPluginManager().registerEvents(cashierLockTrustListener, plugin);
                    plugin.getLogger().notice("CashierLock is installed, enabling trust integration for opening ShopRevanced containers");
                } catch (Exception e) {
                    plugin.getLogger().warning("CashierLock detected but could not enable trust integration: " + e.getMessage());
                }
            } else {
                plugin.getLogger().notice("CashierLock detected, but ShopRevanced CashierLock trust integration is disabled via `cashierLock.trustIntegration.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * GriefPrevention3D                                                *
     * ---------------------------------------------------------------- */

    private void initGriefPrevention() {
        if (plugin.getServer().getPluginManager().getPlugin("GriefPrevention3D") != null) {
            if (griefPreventionIntegrationEnabled) {
                griefPreventionHookListener = new GriefPreventionHookListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(griefPreventionHookListener, plugin);
                plugin.getLogger().notice("GriefPrevention3D is installed, enabling grief prevention integration");
            } else {
                plugin.getLogger().notice("GriefPrevention3D detected, but ShopRevanced GriefPrevention3D integration is disabled via `griefprevention.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Arclight                                                         *
     * ---------------------------------------------------------------- */

    private void initArclight() {
        boolean arclightDetected = plugin.getServer().getPluginManager().getPlugin("Arclight") != null;
        boolean arclightIntegrationEnabled = plugin.getConfig().getBoolean("arclight.enabled", true);
        if (arclightDetected) {
            if (arclightIntegrationEnabled) {
                plugin.getLogger().notice("Arclight detected, ShopRevanced will respect Arclight claimed areas during ShopRevanced creation!");
                this.arclightExists = true;
            } else {
                this.arclightExists = false;
                plugin.getLogger().notice("Arclight detected, but ShopRevanced Arclight integration is disabled via `arclight.enabled: false`");
            }
        } else {
            this.arclightExists = false;
        }
    }

    /* ---------------------------------------------------------------- *
     * Geyser / Floodgate                                               *
     * ---------------------------------------------------------------- */

    private void initGeyserFloodgate() {
        if (plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") != null
                || plugin.getServer().getPluginManager().getPlugin("Floodgate") != null) {
            if (geyserIntegrationEnabled) {
                try {
                    geyserFloodgateHook = new GeyserFloodgateHook(plugin);
                    plugin.getServer().getPluginManager().registerEvents(geyserFloodgateHook, plugin);
                    plugin.getLogger().notice("GeyserMC/Floodgate detected, enabling Bedrock player compatibility");
                } catch (Exception e) {
                    plugin.getLogger().warning("GeyserMC/Floodgate detected but could not enable compatibility: " + e.getMessage());
                }
            } else {
                plugin.getLogger().notice("GeyserMC/Floodgate detected, but ShopRevanced Geyser integration is disabled via `geyser.enabled: false`");
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Economy (Vault)                                                  *
     * ---------------------------------------------------------------- */

    private void initEconomy() {
        if (plugin.getConfigManager().getCurrencyType() == CurrencyType.VAULT) {
            if (setupEconomy()) {
                plugin.getLogger().info("Shops will use the VaultUnlocked economy (" + plugin.getConfigManager().getCurrencyName() + ") as currency on the server.");
            } else {
                plugin.getLogger().severe("Unable to connect to VaultUnlocked Economy! Are both VaultUnlocked AND an Economy plugin installed?");
                plugin.getLogger().severe("Plugin Disabled: Invalid configuration value `economy.type` config.yml. If you do not wish to use Vault with ShopRevanced, make sure to set `economy.type` in the config file to `ITEM`.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
        } else {
            if (plugin.getConfigManager().getItemCurrency() == null) {
                plugin.getLogger().severe("Plugin Disabled: Invalid value for `itemCurrencyID` in `config.yml`");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
            plugin.getLogger().info("Shops will use "
                    + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(plugin.getItemNameUtil().getName(plugin.getConfigManager().getItemCurrency()))
                    + "(s) as the currency on the server.");
        }
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            return false;
        }
        plugin.getLogger().notice("VaultUnlocked is installed, creating VaultUnlocked integration for Economy support");
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /* ---------------------------------------------------------------- *
     * Getters                                                          *
     * ---------------------------------------------------------------- */

    public boolean isWorldGuardIntegrationEnabled() {
        return worldGuardExists;
    }

    public boolean worldGuardExists() {
        return worldGuardExists;
    }

    public WorldGuardHook.WorldGuardConfig getWorldGuardConfig() {
        return worldGuardConfig;
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

    public boolean arclightExists() {
        return arclightExists;
    }

    public boolean hookTowny() {
        return hookTowny;
    }

    public Economy getEconomy() {
        if (econ == null) {
            setupEconomy();
        }
        return econ;
    }

    /* ---------------------------------------------------------------- *
     * Setters (called by ShopRevanced during config load)              *
     * ---------------------------------------------------------------- */

    public void setWorldGuardExists(boolean worldGuardExists) {
        this.worldGuardExists = worldGuardExists;
    }

    public void setWorldGuardConfig(WorldGuardHook.WorldGuardConfig worldGuardConfig) {
        this.worldGuardConfig = worldGuardConfig;
    }

    public void setLwcIntegrationEnabled(boolean lwcIntegrationEnabled) {
        this.lwcIntegrationEnabled = lwcIntegrationEnabled;
    }

    public void setBentoBoxIntegrationEnabled(boolean bentoBoxIntegrationEnabled) {
        this.bentoBoxIntegrationEnabled = bentoBoxIntegrationEnabled;
    }

    public void setAdvancedRegionMarketIntegrationEnabled(boolean advancedRegionMarketIntegrationEnabled) {
        this.advancedRegionMarketIntegrationEnabled = advancedRegionMarketIntegrationEnabled;
    }

    public void setPlotSquaredIntegrationEnabled(boolean plotSquaredIntegrationEnabled) {
        this.plotSquaredIntegrationEnabled = plotSquaredIntegrationEnabled;
    }

    public void setBoltTrustIntegrationEnabled(boolean boltTrustIntegrationEnabled) {
        this.boltTrustIntegrationEnabled = boltTrustIntegrationEnabled;
    }

    public void setBlockProtTrustIntegrationEnabled(boolean blockProtTrustIntegrationEnabled) {
        this.blockProtTrustIntegrationEnabled = blockProtTrustIntegrationEnabled;
    }

    public void setCashierLockTrustIntegrationEnabled(boolean cashierLockTrustIntegrationEnabled) {
        this.cashierLockTrustIntegrationEnabled = cashierLockTrustIntegrationEnabled;
    }

    public void setGriefPreventionIntegrationEnabled(boolean griefPreventionIntegrationEnabled) {
        this.griefPreventionIntegrationEnabled = griefPreventionIntegrationEnabled;
    }

    public void setGeyserIntegrationEnabled(boolean geyserIntegrationEnabled) {
        this.geyserIntegrationEnabled = geyserIntegrationEnabled;
    }

    public void setHookTowny(boolean hookTowny) {
        this.hookTowny = hookTowny;
    }

    public void setBluemapEnabled(boolean bluemapEnabled) {
        this.bluemapEnabled = bluemapEnabled;
    }

    public void setDynmapEnabled(boolean dynmapEnabled) {
        this.dynmapEnabled = dynmapEnabled;
    }

    public void setArclightExists(boolean arclightExists) {
        this.arclightExists = arclightExists;
    }

    public LWCHookListener getLwcHookListener() {
        return lwcHookListener;
    }

    public DynmapHookListener getDynmapHookListener() {
        return dynmapHookListener;
    }

    public BluemapHookListener getBluemapHookListener() {
        return bluemapHookListener;
    }

    public BentoBoxHookListener getBentoBoxHookListener() {
        return bentoBoxHookListener;
    }

    public ARMHookListener getArmHookListener() {
        return armHookListener;
    }

    public PlotSquaredHookListener getPlotSquaredHookListener() {
        return plotSquaredHookListener;
    }

    public BoltTrustListener getBoltTrustListener() {
        return boltTrustListener;
    }

    public BlockProtTrustListener getBlockProtTrustListener() {
        return blockProtTrustListener;
    }

    public CashierLockTrustListener getCashierLockTrustListener() {
        return cashierLockTrustListener;
    }

    public GriefPreventionHookListener getGriefPreventionHookListener() {
        return griefPreventionHookListener;
    }

    public GeyserFloodgateHook getGeyserFloodgateHook() {
        return geyserFloodgateHook;
    }
}

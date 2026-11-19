package com.theflood424k.shoprevanced.display;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.ShopAction;
import com.theflood424k.shoprevanced.util.ShopClickType;
import com.theflood424k.shoprevanced.listener.DisplayListener;
import com.theflood424k.shoprevanced.util.Metrics;
import com.theflood424k.shoprevanced.util.Metrics.AdvancedPie;
import com.theflood424k.shoprevanced.util.Metrics.SimplePie;
import com.theflood424k.shoprevanced.util.Metrics.SingleLineChart;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Schedules display-related background tasks and configures bStats metrics.
 * <p>
 * Extracted from {@link ShopRevanced} to isolate display scheduling and metrics
 * instrumentation into a single cohesive class.
 * </p>
 */
public class DisplayScheduler {

    private final ShopRevanced plugin;
    private final Metrics metrics;

    // Display-related config fields
    private DisplayType displayType;
    private DisplayTagOption displayTagOption;
    private DisplayType[] displayCycle;
    private double displayProcessInterval;
    private double displayMovementThreshold;
    private double maxShopDisplayDistance;
    private int shopSearchRadius;
    private int displayBatchSize;
    private int displayBatchDelay;
    private int displayLightLevel;
    private boolean setGlowingItemFrame;
    private boolean setGlowingSignText;

    // General config fields referenced by metrics lambdas
    private boolean usePerms;
    private boolean allowPartialSales;
    private boolean allowCreateMethodSign;
    private boolean allowCreateMethodChest;
    private boolean offlinePurchaseNotificationsEnabled;
    private boolean enableGUI;
    private boolean allowCreativeSelection;
    private boolean checkItemDurability;
    private boolean ignoreItemRepairCost;
    private boolean playSounds;
    private boolean playEffects;
    private int hoursOfflineToRemoveShops;
    private boolean destroyShopRequiresSneak;
    private boolean inverseComboShops;
    private CurrencyType currencyType;
    private double creationCost;
    private double destructionCost;
    private double teleportCost;
    private boolean returnCreationCost;
    private Map<ShopClickType, ShopAction> clickTypeActionMap;
    private java.util.NavigableMap<Double, String> priceSuffixes;
    private boolean bluemapEnabled;
    private boolean dynmapEnabled;

    public DisplayScheduler(ShopRevanced plugin) {
        this.plugin = plugin;
        int bstatsPluginId = 25211;
        this.metrics = new Metrics(plugin, bstatsPluginId);
    }

    /**
     * Configure all bStats metrics charts.
     * Must be called after config fields have been populated.
     */
    public void setupMetrics() {
        metrics.addCustomChart(new SingleLineChart("transactions", () -> plugin.getLogHandler().getRecentTransactionCount()));
        metrics.addCustomChart(new SingleLineChart("item_volume", () -> plugin.getLogHandler().getRecentItemVolume()));
        metrics.addCustomChart(new SingleLineChart("shops", () -> plugin.getShopRevancedHandler().getNumberOfShops()));
        metrics.addCustomChart(new AdvancedPie("shop_types", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Buy", plugin.getShopRevancedHandler().getNumberOfShops(ShopType.BUY));
            valueMap.put("Sell", plugin.getShopRevancedHandler().getNumberOfShops(ShopType.SELL));
            valueMap.put("Barter", plugin.getShopRevancedHandler().getNumberOfShops(ShopType.BARTER));
            valueMap.put("Combo", plugin.getShopRevancedHandler().getNumberOfShops(ShopType.COMBO));
            valueMap.put("Gamble", plugin.getShopRevancedHandler().getNumberOfShops(ShopType.GAMBLE));
            return valueMap;
        }));
        metrics.addCustomChart(new AdvancedPie("shop_display_types", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Floating Item", plugin.getShopRevancedHandler().getNumberOfShopDisplayTypes(DisplayType.ITEM));
            valueMap.put("Large Item", plugin.getShopRevancedHandler().getNumberOfShopDisplayTypes(DisplayType.LARGE_ITEM));
            valueMap.put("Item Frame", plugin.getShopRevancedHandler().getNumberOfShopDisplayTypes(DisplayType.ITEM_FRAME));
            valueMap.put("Glass Case", plugin.getShopRevancedHandler().getNumberOfShopDisplayTypes(DisplayType.GLASS_CASE));
            valueMap.put("None", plugin.getShopRevancedHandler().getNumberOfShopDisplayTypes(DisplayType.NONE));
            return valueMap;
        }));
        metrics.addCustomChart(new AdvancedPie("shop_containers", () -> plugin.getShopRevancedHandler().getShopContainerCounts()));
        metrics.addCustomChart(new SimplePie("economy_type", () -> currencyType.toString()));
        metrics.addCustomChart(new SimplePie("fractional_currency", () -> String.valueOf(plugin.getAllowFractionalCurrency())));
        metrics.addCustomChart(new SimplePie("use_permissions", () -> String.valueOf(usePerms)));
        metrics.addCustomChart(new SimplePie("allow_partial_sales", () -> String.valueOf(allowPartialSales)));
        metrics.addCustomChart(new AdvancedPie("shop_creation_methods", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Sign Creation", allowCreateMethodSign ? 1 : 0);
            valueMap.put("Chest Creation", allowCreateMethodChest ? 1 : 0);
            valueMap.put("Signs Disabled", allowCreateMethodSign ? 0 : 1);
            valueMap.put("Chests Disabled", allowCreateMethodChest ? 0 : 1);
            return valueMap;
        }));
        metrics.addCustomChart(new SimplePie("offline_purchase_notifications", () -> String.valueOf(offlinePurchaseNotificationsEnabled)));
        metrics.addCustomChart(new SimplePie("shop_gui_enabled", () -> String.valueOf(enableGUI)));
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
        metrics.addCustomChart(new SimplePie("worldguard_enabled", () -> String.valueOf(plugin.worldGuardExists())));
        metrics.addCustomChart(new SimplePie("towny_enabled", () -> String.valueOf(plugin.hookTowny())));
        metrics.addCustomChart(new SimplePie("dynmap_enabled", () -> String.valueOf(dynmapEnabled)));
        metrics.addCustomChart(new SimplePie("bluemap_enabled", () -> String.valueOf(bluemapEnabled)));
        metrics.addCustomChart(new SimplePie("database_type", () -> String.valueOf(plugin.getConfigManager().getConfig().getString("logging.type"))));
        metrics.addCustomChart(new SimplePie("item_hover_display_type", () -> displayType.toString()));
        metrics.addCustomChart(new SimplePie("hover_text_activation_type", () -> displayTagOption.toString()));
        metrics.addCustomChart(new SimplePie("auto_cleanup_dead_shops", () -> String.valueOf(hoursOfflineToRemoveShops > 0)));
        metrics.addCustomChart(new SimplePie("destroy_requires_sneak", () -> String.valueOf(destroyShopRequiresSneak)));
        metrics.addCustomChart(new SimplePie("inverse_combo_shops", () -> String.valueOf(inverseComboShops)));
        metrics.addCustomChart(new AdvancedPie("enabled_containers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            boolean hasChests = plugin.getEnabledContainers().contains(Material.CHEST) ||
                    plugin.getEnabledContainers().contains(Material.TRAPPED_CHEST);
            valueMap.put("Chests Allowed", hasChests ? 1 : 0);
            valueMap.put("Chests Disabled", hasChests ? 0 : 1);
            boolean hasBarrel = plugin.getEnabledContainers().contains(Material.BARREL);
            valueMap.put("Barrels Allowed", hasBarrel ? 1 : 0);
            valueMap.put("Barrels Disabled", hasBarrel ? 0 : 1);
            boolean hasShulker = plugin.getEnabledContainers().stream()
                    .anyMatch(m -> m.name().endsWith("SHULKER_BOX"));
            valueMap.put("Shulker Boxes Allowed", hasShulker ? 1 : 0);
            valueMap.put("Shulker Boxes Disabled", hasShulker ? 0 : 1);
            return valueMap;
        }));
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
        metrics.addCustomChart(new SimplePie("transaction_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.TRANSACT) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
        metrics.addCustomChart(new SimplePie("full_stack_transaction_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.TRANSACT_FULLSTACK) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
        metrics.addCustomChart(new SimplePie("view_details_action_mapping", () -> {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getValue() == ShopAction.VIEW_DETAILS) return entry.getKey().toString();
            }
            return "NOT_SET";
        }));
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
    }

    /**
     * Start the repeating display view task.
     */
    public void startDisplayTasks(DisplayListener displayListener) {
        displayListener.startRepeatingDisplayViewTask();
    }

    public void shutdown() {
        if (metrics != null) {
            metrics.shutdown();
        }
    }

    public Metrics getMetrics() {
        return metrics;
    }

    // Config field setters (called by ShopRevanced during onEnable)
    public void setDisplayType(DisplayType displayType) { this.displayType = displayType; }
    public void setDisplayTagOption(DisplayTagOption displayTagOption) { this.displayTagOption = displayTagOption; }
    public void setDisplayCycle(DisplayType[] displayCycle) { this.displayCycle = displayCycle; }
    public void setDisplayProcessInterval(double displayProcessInterval) { this.displayProcessInterval = displayProcessInterval; }
    public void setDisplayMovementThreshold(double displayMovementThreshold) { this.displayMovementThreshold = displayMovementThreshold; }
    public void setMaxShopDisplayDistance(double maxShopDisplayDistance) { this.maxShopDisplayDistance = maxShopDisplayDistance; }
    public void setShopSearchRadius(int shopSearchRadius) { this.shopSearchRadius = shopSearchRadius; }
    public void setDisplayBatchSize(int displayBatchSize) { this.displayBatchSize = displayBatchSize; }
    public void setDisplayBatchDelay(int displayBatchDelay) { this.displayBatchDelay = displayBatchDelay; }
    public void setDisplayLightLevel(int displayLightLevel) { this.displayLightLevel = displayLightLevel; }
    public void setGlowingItemFrame(boolean glowingItemFrame) { this.setGlowingItemFrame = glowingItemFrame; }
    public void setGlowingSignText(boolean glowingSignText) { this.setGlowingSignText = glowingSignText; }

    public void setUsePerms(boolean usePerms) { this.usePerms = usePerms; }
    public void setAllowPartialSales(boolean allowPartialSales) { this.allowPartialSales = allowPartialSales; }
    public void setAllowCreateMethodSign(boolean allowCreateMethodSign) { this.allowCreateMethodSign = allowCreateMethodSign; }
    public void setAllowCreateMethodChest(boolean allowCreateMethodChest) { this.allowCreateMethodChest = allowCreateMethodChest; }
    public void setOfflinePurchaseNotificationsEnabled(boolean offlinePurchaseNotificationsEnabled) { this.offlinePurchaseNotificationsEnabled = offlinePurchaseNotificationsEnabled; }
    public void setEnableGUI(boolean enableGUI) { this.enableGUI = enableGUI; }
    public void setAllowCreativeSelection(boolean allowCreativeSelection) { this.allowCreativeSelection = allowCreativeSelection; }
    public void setCheckItemDurability(boolean checkItemDurability) { this.checkItemDurability = checkItemDurability; }
    public void setIgnoreItemRepairCost(boolean ignoreItemRepairCost) { this.ignoreItemRepairCost = ignoreItemRepairCost; }
    public void setPlaySounds(boolean playSounds) { this.playSounds = playSounds; }
    public void setPlayEffects(boolean playEffects) { this.playEffects = playEffects; }
    public void setHoursOfflineToRemoveShops(int hoursOfflineToRemoveShops) { this.hoursOfflineToRemoveShops = hoursOfflineToRemoveShops; }
    public void setDestroyShopRequiresSneak(boolean destroyShopRequiresSneak) { this.destroyShopRequiresSneak = destroyShopRequiresSneak; }
    public void setInverseComboShops(boolean inverseComboShops) { this.inverseComboShops = inverseComboShops; }
    public void setCurrencyType(CurrencyType currencyType) { this.currencyType = currencyType; }
    public void setCreationCost(double creationCost) { this.creationCost = creationCost; }
    public void setDestructionCost(double destructionCost) { this.destructionCost = destructionCost; }
    public void setTeleportCost(double teleportCost) { this.teleportCost = teleportCost; }
    public void setReturnCreationCost(boolean returnCreationCost) { this.returnCreationCost = returnCreationCost; }
    public void setClickTypeActionMap(Map<ShopClickType, ShopAction> clickTypeActionMap) { this.clickTypeActionMap = clickTypeActionMap; }
    public void setPriceSuffixes(java.util.NavigableMap<Double, String> priceSuffixes) { this.priceSuffixes = priceSuffixes; }
    public void setBluemapEnabled(boolean bluemapEnabled) { this.bluemapEnabled = bluemapEnabled; }
    public void setDynmapEnabled(boolean dynmapEnabled) { this.dynmapEnabled = dynmapEnabled; }
}

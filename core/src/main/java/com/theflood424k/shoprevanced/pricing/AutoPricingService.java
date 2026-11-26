package com.theflood424k.shoprevanced.pricing;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.logging.Level;

/**
 * Automatically adjusts shop prices based on market-trend data.
 *
 * <p>The service runs on a configurable schedule via the FoliaLib scheduler. Each
 * cycle it iterates all loaded shops and, for shops that have not opted out,
 * requests a suggested market price for the shop's item. If a suggestion is
 * available and differs from the current price by no more than the configured
 * maximum adjustment percent, the shop's price is updated and flagged for saving.</p>
 *
 * <p>Graceful failure is central to the design: if market data is unavailable,
 * the provider throws, or any other error occurs, the affected shop keeps its
 * current (manual) price and a warning is logged. Checkout and listing updates
 * are never blocked by the pricing service.</p>
 */
public class AutoPricingService {

    private final ShopRevanced plugin;
    private final ShopRevancedHandler shopHandler;
    private MarketDataProvider marketDataProvider;

    private boolean enabled = false;
    private int intervalMinutes = 60;
    private double maxAdjustmentPercent = 15.0;

    private WrappedTask scheduledTask;

    public AutoPricingService(ShopRevanced plugin) {
        this.plugin = plugin;
        this.shopHandler = plugin.getShopRevancedHandler();
        this.marketDataProvider = new SimulatedMarketDataProvider();
        loadConfig();
    }

    /** Loads auto-pricing settings from the plugin config, validating values. */
    private void loadConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("autoPricing.enabled", false);

        intervalMinutes = config.getInt("autoPricing.intervalMinutes", 60);
        if (intervalMinutes <= 0 || intervalMinutes > 10_080) { // > 1 week is unreasonable
            intervalMinutes = 60;
            plugin.getLogger().warning("Invalid autoPricing.intervalMinutes; falling back to 60 minutes");
        }

        maxAdjustmentPercent = config.getDouble("autoPricing.maxAdjustmentPercent", 15.0);
        if (Double.isNaN(maxAdjustmentPercent) || Double.isInfinite(maxAdjustmentPercent)
                || maxAdjustmentPercent < 0.0 || maxAdjustmentPercent > 100.0) {
            maxAdjustmentPercent = 15.0;
            plugin.getLogger().warning("Invalid autoPricing.maxAdjustmentPercent; falling back to 15.0 percent");
        }
    }

    /** Returns whether auto-pricing is currently enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Starts the scheduled pricing cycle if auto-pricing is enabled in config.
     * Safe to call multiple times; only one cycle runs at a time.
     */
    public void enable() {
        if (!enabled) {
            return;
        }
        disable(); // avoid duplicate tasks
        long periodTicks = (long) intervalMinutes * 60L * 20L;
        scheduledTask = plugin.getFoliaLib().getScheduler().runTimer(this::runPricingCycle, periodTicks, periodTicks);
        plugin.getLogger().info("Auto-pricing enabled, running every " + intervalMinutes + " minutes");
    }

    /** Cancels the scheduled pricing cycle, if any. */
    public void disable() {
        if (scheduledTask != null) {
            plugin.getFoliaLib().getScheduler().cancelTask(scheduledTask);
            scheduledTask = null;
        }
    }

    /**
     * Runs a single pricing cycle. Called on the Folia scheduler; no block/world
     * access is performed here, only shop data reads and price writes.
     */
    public void runPricingCycle() {
        if (!enabled) {
            return;
        }
        List<AbstractShop> shops = shopHandler.getAllShops();
        int adjusted = 0;
        int skipped = 0;
        int failed = 0;

        for (AbstractShop shop : shops) {
            if (shop == null || shop.isAdmin() || !shop.isAutoPricingEnabled()) {
                skipped++;
                continue;
            }
            try {
                ItemStack item = shop.getItemStack();
                if (item == null || item.getType() == org.bukkit.Material.AIR) {
                    skipped++;
                    continue;
                }
                double currentPrice = shop.getPrice();
                if (currentPrice <= 0) {
                    skipped++;
                    continue;
                }
                Double suggested = marketDataProvider.getMarketPrice(item).orElse(null);
                if (suggested == null || suggested <= 0) {
                    failed++;
                    continue;
                }
                double maxPrice = currentPrice * (1.0 + maxAdjustmentPercent / 100.0);
                double minPrice = currentPrice * (1.0 - maxAdjustmentPercent / 100.0);
                double newPrice = Math.max(minPrice, Math.min(maxPrice, suggested));
                newPrice = Math.round(newPrice * 100.0) / 100.0;
                if (Math.abs(newPrice - currentPrice) > 0.005) {
                    shop.setPrice(newPrice);
                    shop.setNeedsSave(true);
                    adjusted++;
                    plugin.getLogger().helpful(
                            "Auto-priced shop " + shop.getId() + " (" + item.getType() + "): "
                                    + currentPrice + " -> " + newPrice);
                }
            } catch (Throwable t) {
                failed++;
                plugin.getLogger().warning("Auto-pricing failed for shop " + shop.getId()
                        + ", keeping current price: " + t.getMessage());
            }
        }

        plugin.getLogger().helpful("Auto-pricing cycle complete: "
                + adjusted + " adjusted, " + skipped + " skipped, " + failed + " failed");
    }

    /**
     * Sets the market data provider (primarily for testing). Defaults to a
     * {@link SimulatedMarketDataProvider}.
     */
    public void setMarketDataProvider(MarketDataProvider provider) {
        this.marketDataProvider = provider != null ? provider : new SimulatedMarketDataProvider();
    }
}

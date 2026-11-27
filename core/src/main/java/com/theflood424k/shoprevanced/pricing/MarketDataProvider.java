package com.theflood424k.shoprevanced.pricing;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Provides market-trend price data for a given shop item.
 *
 * <p>Implementations may query external market APIs, price-tracking plugins, or
 * simulated data sources. Providers must be safe to call from the scheduler thread
 * and must never throw unexpected exceptions — a missing or stale market price is
 * signalled by returning {@link Optional#empty()}, which the auto-pricing service
 * treats as a graceful-failure case and leaves the shop's current price unchanged.</p>
 */
public interface MarketDataProvider {

    /**
     * Returns the current suggested market price for the given item, or empty if
     * market data is unavailable, stale, or the item is not tracked.
     *
     * @param item the shop item to price
     * @return the suggested market price, or empty if unavailable
     */
    Optional<Double> getMarketPrice(ItemStack item);
}

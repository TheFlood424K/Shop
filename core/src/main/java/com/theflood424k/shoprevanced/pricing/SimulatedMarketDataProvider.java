package com.theflood424k.shoprevanced.pricing;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A deterministic, simulated market-data provider used when no real market
 * integration is available.
 *
 * <p>The suggested price is derived from the item type so that identical items
 * always receive the same suggestion. By default the provider returns a price
 * for every tracked item; call {@link #setUnavailable(boolean)} to simulate a
 * market outage and exercise the auto-pricing service's graceful-failure path.</p>
 */
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private boolean unavailable = false;

    /**
     * Base price in currency units per material. A small sample of common items;
     * unlisted materials fall back to a hashed price derived from the material name.
     */
    private static final double BASE_PRICE_MULTIPLIER = 2.5;

    @Override
    public Optional<Double> getMarketPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        if (unavailable) {
            return Optional.empty();
        }
        double base = switch (item.getType()) {
            case DIAMOND -> 120.0;
            case EMERALD -> 90.0;
            case GOLD_INGOT -> 45.0;
            case IRON_INGOT -> 15.0;
            case COAL -> 3.0;
            case STONE -> 1.0;
            case DIRT -> 0.5;
            default -> materialHashedPrice(item.getType());
        };
        // Small simulated market fluctuation so prices drift over time.
        double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2; // +/- 10%
        return Optional.of(Math.max(0.01, base * (1.0 + jitter)));
    }

    /** Simulate a market outage so the service can demonstrate graceful failure. */
    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    private double materialHashedPrice(Material material) {
        int hash = material.name().hashCode();
        return 1.0 + (Math.abs(hash) % 100) * BASE_PRICE_MULTIPLIER;
    }
}

package com.theflood424k.shoprevanced.integration.eztax;

import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates sales tax for a shop transaction based on customer location and
 * product taxability.
 *
 * <p>Tax rules are deterministic and configurable so the calculator can be
 * unit-tested without a live ezTax service:
 * <ul>
 *   <li>Location-based rates: a default rate plus optional per-world overrides.</li>
 *   <li>Tax-exempt customers: players holding the {@code eztax.exempt} permission
 *       or listed in the exempt customer set pay no tax.</li>
 *   <li>Tax-exempt products: items whose type is in the exempt set pay no tax.</li>
 * </ul>
 *
 * <p>When ezTax is unavailable the integration layer returns {@code 0.0} tax so
 * shop operations continue uninterrupted.
 */
public class EzTaxCalculator {

    /** Default sales tax rate expressed as a fraction (e.g. 0.07 = 7%). */
    private double defaultRate = 0.0;

    /** Per-world tax rate overrides, keyed by world name. */
    private final Map<String, Double> worldRates = new ConcurrentHashMap<>();

    /** Customers exempt from tax, keyed by player unique id. */
    private final Set<String> exemptCustomers = ConcurrentHashMap.newKeySet();

    /** Product materials exempt from tax. */
    private final Set<Material> exemptProducts = ConcurrentHashMap.newKeySet();

    public EzTaxCalculator() {
    }

    /** @return the default tax rate as a fraction (e.g. {@code 0.07} for 7%). */
    public double getDefaultRate() {
        return defaultRate;
    }

    public void setDefaultRate(double defaultRate) {
        this.defaultRate = Math.max(0.0, defaultRate);
    }

    /** Sets the tax rate for a specific world (location-based override). */
    public void setWorldRate(String world, double rate) {
        if (rate < 0.0) {
            this.worldRates.remove(world);
        } else {
            this.worldRates.put(world, Math.max(0.0, rate));
        }
    }

    public void addExemptCustomer(String playerUuid) {
        if (playerUuid != null) {
            this.exemptCustomers.add(playerUuid);
        }
    }

    public void removeExemptCustomer(String playerUuid) {
        if (playerUuid != null) {
            this.exemptCustomers.remove(playerUuid);
        }
    }

    public void addExemptProduct(Material material) {
        if (material != null) {
            this.exemptProducts.add(material);
        }
    }

    public void removeExemptProduct(Material material) {
        if (material != null) {
            this.exemptProducts.remove(material);
        }
    }

    /**
     * Calculate the tax owed for a purchase.
     *
     * @param player the customer (may be {@code null}; no exemptions apply then)
     * @param shop the shop being purchased from
     * @param amount quantity being purchased
     * @param basePrice unit price before tax
     * @return the tax amount (never negative; {@code 0.0} when exempt or rate is 0)
     */
    public double calculateTax(Player player, AbstractShop shop, int amount, double basePrice) {
        if (amount <= 0 || basePrice <= 0.0) {
            return 0.0;
        }

        // Tax-exempt customer short-circuit.
        if (player != null && isCustomerExempt(player)) {
            return 0.0;
        }

        // Tax-exempt product short-circuit.
        if (shop != null && isProductExempt(shop)) {
            return 0.0;
        }

        double rate = player != null ? getRateForPlayer(player) : defaultRate;
        double taxableAmount = basePrice * amount;
        return Math.max(0.0, taxableAmount * rate);
    }

    private boolean isCustomerExempt(Player player) {
        if (player.hasPermission("eztax.exempt")) {
            return true;
        }
        return exemptCustomers.contains(player.getUniqueId().toString());
    }

    private boolean isProductExempt(AbstractShop shop) {
        if (shop == null || shop.getItemStack() == null) {
            return false;
        }
        Material itemType = shop.getItemStack().getType();
        return exemptProducts.contains(itemType);
    }

    private double getRateForPlayer(Player player) {
        String world = player.getWorld().getName();
        Double worldRate = worldRates.get(world);
        return worldRate != null ? worldRate : defaultRate;
    }

}

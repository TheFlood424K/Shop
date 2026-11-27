package com.theflood424k.shoprevanced.integration.eztax;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Main entry point for the ezTax integration.
 *
 * <p>This is a <b>soft/optional integration</b>: ezTax is only active when the ezTax
 * plugin is installed on the server <i>and</i> {@code ezTax.enabled} is true in
 * {@code config.yml}. When ezTax is missing or disabled, all tax calculations
 * return {@code 0.0} and shop checkout continues uninterrupted (graceful fallback).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detect the ezTax plugin and read ezTax configuration.</li>
 *   <li>Calculate tax at checkout via {@link EzTaxCalculator}.</li>
 *   <li>Display a tax breakdown to the customer.</li>
 *   <li>Record collected tax via {@link EzTaxReport} for filing/remittance.</li>
 * </ul>
 */
public class EzTaxIntegration {

    private final ShopRevanced plugin;
    private final EzTaxCalculator calculator;
    private final EzTaxReport report;

    private boolean enabled;
    private String apiUrl;

    public EzTaxIntegration(ShopRevanced plugin) {
        this.plugin = plugin;
        this.calculator = new EzTaxCalculator();
        this.report = new EzTaxReport();
        readConfig();

        if (enabled) {
            Plugin ezTaxPlugin = plugin.getServer().getPluginManager().getPlugin("ezTax");
            if (ezTaxPlugin == null) {
                enabled = false;
                plugin.getLogger().warning(
                        "ezTax integration is enabled in config.yml but the ezTax plugin was not found. "
                                + "Tax calculation is disabled; shops will run with no tax.");
            } else {
                plugin.getLogger().notice("ezTax integration enabled (apiUrl=" + apiUrl + ").");
            }
        }
    }

    private void readConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("ezTax.enabled", false);
        apiUrl = config.getString("ezTax.apiUrl", "https://api.eztax.example.com/v1");

        // Load tax rates. config.yml uses percentages (e.g. 8.0 = 8%); the calculator
        // stores fractions (e.g. 0.08 = 8%). Divide by 100 when loading.
        double defaultRate = config.getDouble("ezTax.defaultRate", 0.0) / 100.0;
        calculator.setDefaultRate(defaultRate);

        var worldRatesSection = config.getConfigurationSection("ezTax.worldRates");
        if (worldRatesSection != null) {
            for (String world : worldRatesSection.getKeys(false)) {
                double rate = worldRatesSection.getDouble(world, 0.0) / 100.0;
                calculator.setWorldRate(world, rate);
            }
        }
    }

    /** @return true when ezTax is enabled and the ezTax plugin is present. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Calculate the tax owed for a customer purchase.
     *
     * @return the tax amount (never negative; {@code 0.0} when ezTax is unavailable
     *         or the customer/product is tax-exempt).
     */
    public double calculateTax(Player player, AbstractShop shop, int amount, double basePrice) {
        if (!enabled) {
            return 0.0;
        }
        return calculator.calculateTax(player, shop, amount, basePrice);
    }

    /**
     * Record collected tax for filing/remittance reporting. No-op when ezTax is
     * disabled or the tax amount is zero.
     */
    public void recordTax(Player player, AbstractShop shop, double basePrice, double taxAmount) {
        if (!enabled || taxAmount <= 0.0) {
            return;
        }
        double totalPrice = basePrice + taxAmount;
        report.recordTransaction(player, shop, basePrice, taxAmount, totalPrice);
    }

    /** @return the tax report, usable for generating a filing export. */
    public EzTaxReport getReport() {
        return report;
    }

    /**
     * Send the customer a tax breakdown message before/at purchase.
     *
     * <p>When ezTax is disabled or tax is zero, no message is sent so the purchase
     * flow is unchanged.
     */
    public void displayTaxBreakdown(Player player, double basePrice, double taxAmount, double totalPrice) {
        if (!enabled || taxAmount <= 0.0 || player == null) {
            return;
        }
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text("ezTax: "))
                .append(net.kyori.adventure.text.Component.text(
                        String.format("%.2f + %.2f tax = %.2f total", basePrice, taxAmount, totalPrice)))
                .build();
        player.sendMessage(message);
    }

}

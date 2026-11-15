package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import org.bukkit.entity.Player;

/**
 * Calculates shop prices from player chat input during creation.
 * Handles both Vault economy and item-based currency pricing.
 */
public class CreationPricing {

    private final ShopRevanced plugin;

    public CreationPricing(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    public double getShopPrice(Player player, String input, ShopType shopType) {
        double price = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                double multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);
                price = line3.contains(".")
                        ? Double.parseDouble(line3)
                        : Long.parseLong(line3);
                price *= multiplyValue;
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                price = Long.parseLong(line3);
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return -1;
            }
        }
        if (price < 0 || (price == 0 && shopType == ShopType.BARTER)) {
            ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
            return -1;
        }
        return price;
    }

    public double getShopPriceCombo(Player player, String input, ShopType shopType) {
        double priceCombo = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                double multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);
                priceCombo = line3.contains(".")
                        ? Double.parseDouble(line3)
                        : Long.parseLong(line3);
                priceCombo *= multiplyValue;
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                priceCombo = Long.parseLong(line3);
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return -1;
            }
        }
        if (priceCombo < 0 || (priceCombo == 0 && shopType == ShopType.BARTER)) {
            ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
            return -1;
        }
        return priceCombo;
    }

    public PricePair getShopPricePair(Player player, String input, ShopType shopType) {
        double price = 0;
        double priceCombo = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                double multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);
                price = line3.contains(".")
                        ? Double.parseDouble(line3)
                        : Long.parseLong(line3);
                price *= multiplyValue;
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return null;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                price = Long.parseLong(line3);
            } catch (NumberFormatException e) {
                ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
                return null;
            }
        }
        if (price < 0 || (price == 0 && shopType == ShopType.BARTER)) {
            ShopRevancedMessage.sendMessage("interactionIssue", "line3", player, null);
            return null;
        }
        return new PricePair(price, priceCombo);
    }
}

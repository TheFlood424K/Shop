package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationPricingTest {

    private ShopRevanced plugin;
    private Player player;
    private CreationPricing pricing;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(ShopRevanced.class);
        player = Mockito.mock(Player.class);
        pricing = new CreationPricing(plugin);
    }

    @DisplayName("getShopPrice: VAULT currency, valid integer → returns price * multiplyValue")
    @Test
    void getShopPrice_vault_validInteger_returnsPriceTimesMultiply() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("100")).thenReturn(1.5);
            um.when(() -> UtilMethods.cleanNumberText("100")).thenReturn("100");

            double result = pricing.getShopPrice(player, "100", ShopType.SELL);
            assertEquals(150.0, result);
        }
    }

    @DisplayName("getShopPrice: VAULT currency, valid decimal → returns price * multiplyValue")
    @Test
    void getShopPrice_vault_validDecimal_returnsPriceTimesMultiply() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("10.5")).thenReturn(2.0);
            um.when(() -> UtilMethods.cleanNumberText("10.5")).thenReturn("10.5");

            double result = pricing.getShopPrice(player, "10.5", ShopType.SELL);
            assertEquals(21.0, result);
        }
    }

    @DisplayName("getShopPrice: VAULT currency, invalid number → returns -1")
    @Test
    void getShopPrice_vault_invalidNumber_returnsMinusOne() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("abc")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("abc")).thenReturn("abc");

            double result = pricing.getShopPrice(player, "abc", ShopType.SELL);
            assertEquals(-1, result);
        }
    }

    @DisplayName("getShopPrice: non-VAULT currency, valid number → returns price")
    @Test
    void getShopPrice_nonVault_validNumber_returnsPrice() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.ITEM);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.cleanNumberText("50")).thenReturn("50");

            double result = pricing.getShopPrice(player, "50", ShopType.SELL);
            assertEquals(50.0, result);
        }
    }

    @DisplayName("getShopPrice: non-VAULT currency, invalid number → returns -1")
    @Test
    void getShopPrice_nonVault_invalidNumber_returnsMinusOne() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.ITEM);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.cleanNumberText("abc")).thenReturn("abc");

            double result = pricing.getShopPrice(player, "abc", ShopType.SELL);
            assertEquals(-1, result);
        }
    }

    @DisplayName("getShopPrice: negative price → returns -1")
    @Test
    void getShopPrice_negativePrice_returnsMinusOne() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("-5")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("-5")).thenReturn("-5");

            double result = pricing.getShopPrice(player, "-5", ShopType.SELL);
            assertEquals(-1, result);
        }
    }

    @DisplayName("getShopPrice: zero price with BARTER → returns -1")
    @Test
    void getShopPrice_zeroPrice_barter_returnsMinusOne() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("0")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("0")).thenReturn("0");

            double result = pricing.getShopPrice(player, "0", ShopType.BARTER);
            assertEquals(-1, result);
        }
    }

    @DisplayName("getShopPrice: zero price with SELL → returns 0")
    @Test
    void getShopPrice_zeroPrice_sell_returnsZero() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("0")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("0")).thenReturn("0");

            double result = pricing.getShopPrice(player, "0", ShopType.SELL);
            assertEquals(0, result);
        }
    }

    @DisplayName("getShopPriceCombo: same behavior as getShopPrice")
    @Test
    void getShopPriceCombo_vault_validInteger_returnsPriceTimesMultiply() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("100")).thenReturn(1.5);
            um.when(() -> UtilMethods.cleanNumberText("100")).thenReturn("100");

            double result = pricing.getShopPriceCombo(player, "100", ShopType.SELL);
            assertEquals(150.0, result);
        }
    }

    @DisplayName("getShopPricePair: VAULT currency, valid → returns PricePair with same price/combo")
    @Test
    void getShopPricePair_vault_valid_returnsPricePair() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("100")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("100")).thenReturn("100");

            PricePair result = pricing.getShopPricePair(player, "100", ShopType.SELL);
            assertNotNull(result);
            assertEquals(100.0, result.getPrice());
            assertEquals(0.0, result.getPriceCombo());
        }
    }

    @DisplayName("getShopPricePair: VAULT currency, invalid → returns null")
    @Test
    void getShopPricePair_vault_invalid_returnsNull() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.VAULT);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.getMultiplyValue("abc")).thenReturn(1.0);
            um.when(() -> UtilMethods.cleanNumberText("abc")).thenReturn("abc");

            PricePair result = pricing.getShopPricePair(player, "abc", ShopType.SELL);
            assertNull(result);
        }
    }

    @DisplayName("getShopPricePair: non-VAULT currency, valid → returns PricePair")
    @Test
    void getShopPricePair_nonVault_valid_returnsPricePair() {
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.ITEM);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.cleanNumberText("75")).thenReturn("75");

            PricePair result = pricing.getShopPricePair(player, "75", ShopType.SELL);
            assertNotNull(result);
            assertEquals(75.0, result.getPrice());
            assertEquals(0.0, result.getPriceCombo());
        }
    }
}

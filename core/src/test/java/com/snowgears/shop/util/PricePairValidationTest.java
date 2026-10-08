package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Regression coverage for explicit combo separators, grouped prices, and negative prices. */
@ExtendWith(MockBukkitExtension.class)
class PricePairValidationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private PlayerMock player;

    private Shop plugin;
    private ShopCreationUtil util;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        util = plugin.getShopCreationUtil();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Verifies that price cleaning removes grouping spaces and currency decoration while retaining decimals. */
    @Test
    @DisplayName("priceToken collapses spaces in a single price")
    void priceTokenCollapsesSpaces() {
        // The price cleaner must collapse spaces, not preserve them: a price written as
        // "10 000" is ten thousand, not two tokens. Combo prices are split by getShopPricePair
        // before each token is cleaned, so the separator never reaches this method. See issue #125.
        assertEquals("10000", UtilMethods.priceToken("10 000"));
        assertEquals("100", UtilMethods.priceToken("100"));
        assertEquals("100.5", UtilMethods.priceToken("100.5"));
        // Non-numeric noise is still stripped.
        assertEquals("100000", UtilMethods.priceToken("$100,000"),
                "Currency symbols and commas are stripped");
    }

    /** Verifies that a slash-separated combo preserves distinct primary and secondary prices. */
    @Test
    @DisplayName("A two-price combo line sets both prices correctly")
    void twoPriceComboLineSplits() {
        PricePair pair = util.getShopPricePair(player, "100 / 250", ShopType.COMBO);

        assertNotNull(pair, "The line parses and splits into two prices");
        assertEquals(100.0, pair.getPrice());
        assertEquals(250.0, pair.getPriceCombo());
    }

    @Test
    @DisplayName("A single price behaves as expected")
    void singlePriceParsesNormally() {
        PricePair pair = util.getShopPricePair(player, "100", ShopType.SELL);

        assertNotNull(pair);
        assertEquals(100.0, pair.getPrice());
        assertEquals(0.0, pair.getPriceCombo());
    }

    /** Verifies that a negative primary price is rejected for a selling shop. */
    @Test
    @DisplayName("A negative single price is rejected")
    void negativeBuyPriceRejected() {
        assertNull(util.getShopPricePair(player, "-100", ShopType.SELL),
                "The primary price guard is intact");
    }

    @Test
    @DisplayName("A zero price is refused for BARTER but allowed otherwise")
    void zeroPriceRulesUnchanged() {
        assertNull(util.getShopPricePair(player, "0", ShopType.BARTER));

        PricePair sell = util.getShopPricePair(player, "0", ShopType.SELL);
        assertNotNull(sell, "A zero price is allowed for selling");
        assertEquals(0.0, sell.getPrice());
    }

    /** Verifies that a combo can have a zero secondary price and a positive primary price. */
    @Test
    @DisplayName("A zero combo price is allowed for a combo shop")
    void zeroComboPriceAllowed() {
        PricePair pair = util.getShopPricePair(player, "100 / 0", ShopType.COMBO);
        assertNotNull(pair, "A zero combo price is not negative, so it is allowed");
        assertEquals(100.0, pair.getPrice());
        assertEquals(0.0, pair.getPriceCombo());
    }

    /**
     * Verifies that space-grouped digits form one primary price in both currency modes.
     *
     * @param currency the currency mode under test
     * @throws Exception if the fixture currency cannot be configured
     */
    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void groupedPricesAreSinglePrices(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String input : new String[]{"10 000", "10 500", "100 250", "1 234 567"}) {
            PricePair pair = util.getShopPricePair(player, input, ShopType.COMBO);
            assertNotNull(pair, input);
            assertEquals(Double.parseDouble(input.replace(" ", "")), pair.getPrice(), input);
            assertEquals(0, pair.getPriceCombo(), input);
        }
    }

    /**
     * Verifies that spaces and tabs around a slash do not alter the two combo prices.
     *
     * @param currency the currency mode under test
     * @throws Exception if the fixture currency cannot be configured
     */
    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void comboWhitespaceIsNormalized(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String input : new String[]{"100/250", "  100   /   250  ", "\t100\t/\t250\t"}) {
            PricePair pair = util.getShopPricePair(player, input, ShopType.COMBO);
            assertNotNull(pair, input);
            assertEquals(100, pair.getPrice(), input);
            assertEquals(250, pair.getPriceCombo(), input);
        }
    }

    /**
     * Verifies rejection of negative prices, missing combo sides, and extra price tokens.
     *
     * @param currency the currency mode under test
     * @throws Exception if the fixture currency cannot be configured
     */
    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void malformedAndNegativeCombosAreRejected(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String input : new String[]{"100 -50", "100 / -50", "-100 / 50",
                "100 / 250 300", "100 250 / 300", "100 / 250 / 300", "100 // 250",
                "/100", "100/", "100 / 250 /"}) {
            assertNull(util.getShopPricePair(player, input, ShopType.COMBO), input);
        }
    }

    /**
     * Verifies that a multiplier on the first Vault combo token scales both decimal prices.
     *
     * @throws Exception if the Vault fixture cannot be configured
     */
    @Test
    void vaultComboMultiplierScalesBothPrices() throws Exception {
        useVaultCurrency();
        PricePair pair = util.getShopPricePair(player, " 100.5x2 / 250.5 ", ShopType.COMBO);
        assertNotNull(pair);
        assertEquals(201, pair.getPrice());
        assertEquals(501, pair.getPriceCombo());
    }

    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void malformedNumericTokensAreRejectedInEitherComboPosition(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String token : new String[]{"", "   ", "words", ".", "1.2.3", "9223372036854775808"}) {
            assertNull(util.getShopPricePair(player, token + " / 20", ShopType.COMBO),
                    "Invalid primary token: " + token);
            assertNull(util.getShopPricePair(player, "20 / " + token, ShopType.COMBO),
                    "Invalid secondary token: " + token);
            assertNull(util.getShopPricePair(player, token, ShopType.SELL),
                    "Invalid single token: " + token);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void currencyDecorationsDoNotMergeComboPrices(CurrencyType currency) throws Exception {
        useCurrency(currency);
        PricePair pair = util.getShopPricePair(player, "$1,234 / $5,678", ShopType.COMBO);
        assertNotNull(pair);
        assertEquals(1234, pair.getPrice());
        assertEquals(5678, pair.getPriceCombo());
    }

    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void unseparatedNegativePricesCannotBeHiddenByCurrencyCleaning(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String input : new String[]{"100 $-50", "100\t-50", "100 250 -1"}) {
            assertNull(util.getShopPricePair(player, input, ShopType.COMBO), input);
        }
    }

    @Test
    void itemCurrencyRejectsFractionalPricesInEitherPosition() throws Exception {
        useCurrency(CurrencyType.ITEM);
        for (String input : new String[]{"0.5", "0.5 / 20", "20 / 0.5"}) {
            assertNull(util.getShopPricePair(player, input, ShopType.COMBO), input);
        }
    }

    @Test
    void vaultAcceptsFractionalPricesAndRejectsNegativeFractions() throws Exception {
        useCurrency(CurrencyType.VAULT);
        PricePair pair = util.getShopPricePair(player, "0.125 / 0.5", ShopType.COMBO);
        assertNotNull(pair);
        assertEquals(0.125, pair.getPrice());
        assertEquals(0.5, pair.getPriceCombo());
        assertNull(util.getShopPricePair(player, "-0.125 / 0.5", ShopType.COMBO));
        assertNull(util.getShopPricePair(player, "0.125 / -0.5", ShopType.COMBO));
    }

    @ParameterizedTest
    @EnumSource(value = CurrencyType.class, names = {"ITEM", "VAULT"})
    void chatPricesRejectGroupingSpacesInBothSteps(CurrencyType currency) throws Exception {
        useCurrency(currency);
        for (String input : new String[]{"10 000", "10   500", "1.2.3", "words"}) {
            assertEquals(-1, util.getShopPrice(player, input, ShopType.COMBO), input);
            assertEquals(-1, util.getShopPriceCombo(player, input, ShopType.COMBO), input);
        }
    }

    @Test
    void vaultChatPricesPreserveDecimalsWithoutMultiplyingByTheirOwnDigits() throws Exception {
        useCurrency(CurrencyType.VAULT);
        assertEquals(1234.5, util.getShopPrice(player, "$1,234.50", ShopType.COMBO));
        assertEquals(1234.5, util.getShopPriceCombo(player, "$1,234.50", ShopType.COMBO));
    }

    @Test
    void vaultPrimaryChatPriceAppliesExplicitMultiplier() throws Exception {
        useCurrency(CurrencyType.VAULT);
        assertEquals(200, util.getShopPrice(player, "100x2", ShopType.COMBO),
                "The multiplier digits must not become part of the price");
    }

    @Test
    void vaultSecondaryChatPriceAppliesExplicitMultiplier() throws Exception {
        useCurrency(CurrencyType.VAULT);
        assertEquals(200, util.getShopPriceCombo(player, "100x2", ShopType.COMBO),
                "The secondary prompt must apply the same multiplier as sign prices");
    }

    /**
     * Selects a currency mode on the test plugin through its private currency field.
     *
     * @param currency the mode to use for subsequent parsing
     * @throws Exception if reflective access to the fixture field fails
     */
    private void useCurrency(CurrencyType currency) throws Exception {
        java.lang.reflect.Field field = Shop.class.getDeclaredField("currencyType");
        field.setAccessible(true);
        field.set(plugin, currency);
    }

    // --- VAULT currency (the multiplier path) ---

    /**
     * Switches the test instance to VAULT currency. The default test config is ITEM, where the
     * multiplier path is dead code, so these cases only exercise the VAULT branch. This test
     * class does not extend {@code BaseMockBukkTest}, so the switch is done by reflection.
     */
    private void useVaultCurrency() throws Exception {
        java.lang.reflect.Field f = Shop.class.getDeclaredField("currencyType");
        f.setAccessible(true);
        f.set(plugin, com.snowgears.shop.util.CurrencyType.VAULT);

        java.lang.reflect.Field econ = Shop.class.getDeclaredField("econ");
        econ.setAccessible(true);
        net.milkbowl.vault.economy.Economy mocked = org.mockito.Mockito.mock(net.milkbowl.vault.economy.Economy.class);
        org.mockito.Mockito.when(mocked.getBalance(org.mockito.Mockito.any(org.bukkit.OfflinePlayer.class)))
                .thenReturn(10_000.0);
        org.mockito.Mockito.when(mocked.withdrawPlayer(org.mockito.Mockito.any(org.bukkit.OfflinePlayer.class),
                org.mockito.Mockito.anyDouble()))
                .thenAnswer(inv -> new net.milkbowl.vault.economy.EconomyResponse(
                        inv.getArgument(1), 10_000.0,
                        net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, "ok"));
        org.mockito.Mockito.when(mocked.depositPlayer(org.mockito.Mockito.any(org.bukkit.OfflinePlayer.class),
                org.mockito.Mockito.anyDouble()))
                .thenAnswer(inv -> new net.milkbowl.vault.economy.EconomyResponse(
                        inv.getArgument(1), 10_000.0,
                        net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, "ok"));
        econ.set(plugin, mocked);
    }

    /**
     * Verifies that a grouped Vault sign price is parsed as ten thousand with no secondary price.
     *
     * @throws Exception if the Vault fixture cannot be configured
     */
    @Test
    @DisplayName("A single price with an internal space is ten thousand, not ten")
    void spacedSinglePriceIsTenThousand() throws Exception {
        useVaultCurrency();

        // Issue #125: priceToken now collapses spaces, so "10 000" is one token of 10,000.
        // Previously it split into "10" and "000" and the shop was created at 10.
        PricePair pair = util.getShopPricePair(player, "10 000", ShopType.SELL);
        assertNotNull(pair, "The line parses");
        assertEquals(10_000.0, pair.getPrice(),
                "A single price with an internal space is one number");
        assertEquals(0.0, pair.getPriceCombo());
    }

    /**
     * Verifies that bare Vault combo prices are not interpreted as their own multipliers.
     *
     * @throws Exception if the Vault fixture cannot be configured
     */
    @Test
    @DisplayName("A combo price is not multiplied by its own digits")
    void comboPriceNotSelfMultiplied() throws Exception {
        useVaultCurrency();

        // Issue #125: getMultiplyValue used to strip every digit, so "100 250" multiplied each
        // price by 100250. The multiplier is now read from an explicit xN marker only.
        PricePair pair = util.getShopPricePair(player, "100 / 250", ShopType.COMBO);
        assertNotNull(pair, "The line parses");
        assertEquals(100.0, pair.getPrice());
        assertEquals(250.0, pair.getPriceCombo());
    }

    /**
     * Verifies that an explicit multiplier scales a single Vault sign price.
     *
     * @throws Exception if the Vault fixture cannot be configured
     */
    @Test
    @DisplayName("An explicit xN multiplier still scales the price")
    void explicitMultiplierStillWorks() throws Exception {
        useVaultCurrency();

        PricePair pair = util.getShopPricePair(player, "100x2", ShopType.SELL);
        assertNotNull(pair, "The line parses");
        assertEquals(200.0, pair.getPrice(),
                "An explicit x2 multiplier scales the price to 200");
    }

    /** Verifies that only explicit multiplier markers contribute a multiplier, including after a price. */
    @Test
    @DisplayName("getMultiplyValue ignores a bare price but honours an xN marker")
    void getMultiplyValueIgnoresBarePrice() {
        assertEquals(1, UtilMethods.getMultiplyValue("100"),
                "A bare price has no multiplier");
        assertEquals(4, UtilMethods.getMultiplyValue("x4"),
                "An x4 marker yields 4");
        assertEquals(2, UtilMethods.getMultiplyValue("100x2"),
                "An x2 marker following the price yields 2");
    }
}

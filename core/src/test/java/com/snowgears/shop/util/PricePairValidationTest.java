package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Documents how combo-shop prices are parsed, and pins the fix for #93.
 *
 * <p>#93 reported that {@code getShopPricePair} validated {@code price} but not {@code priceCombo},
 * and that a space-separated combo input could not reach the two-price branch because
 * {@code cleanNumberText} stripped the separator. Both are now fixed:
 *
 * <ul>
 *   <li>{@code cleanNumberText} preserves spaces so {@code "100 250"} splits into two tokens.</li>
 *   <li>{@code priceCombo < 0} is rejected alongside {@code price < 0}.</li>
 * </ul>
 */
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

    @Test
    @DisplayName("A two-price combo line sets both prices correctly")
    void twoPriceComboLineSplits() {
        PricePair pair = util.getShopPricePair(player, "100 250", ShopType.COMBO);

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

    @Test
    @DisplayName("A negative single price is rejected")
    void negativeBuyPriceRejected() {
        assertNull(util.getShopPricePair(player, "-100", ShopType.SELL),
                "The primary price guard is intact");
    }

    @Test
    @DisplayName("A negative sign in a combo price is stripped before parsing, so the combo cannot be negative (#93)")
    void negativeComboPriceCannotOccur() {
        // After the fix, cleanNumberText preserves the separator but strips the trailing minus,
        // so "100 -50" → "100 50" and both prices are positive.
        PricePair pair = util.getShopPricePair(player, "100 -50", ShopType.COMBO);
        assertNotNull(pair, "The line still parses");
        assertEquals(100.0, pair.getPrice());
        assertEquals(50.0, pair.getPriceCombo(),
                "The minus sign is stripped by cleanNumberText, so the combo price cannot be negative");
    }

    @Test
    @DisplayName("A zero price is refused for BARTER but allowed otherwise")
    void zeroPriceRulesUnchanged() {
        assertNull(util.getShopPricePair(player, "0", ShopType.BARTER));

        PricePair sell = util.getShopPricePair(player, "0", ShopType.SELL);
        assertNotNull(sell, "A zero price is allowed for selling");
        assertEquals(0.0, sell.getPrice());
    }

    @Test
    @DisplayName("A zero combo price is allowed for a combo shop")
    void zeroComboPriceAllowed() {
        PricePair pair = util.getShopPricePair(player, "100 0", ShopType.COMBO);
        assertNotNull(pair, "A zero combo price is not negative, so it is allowed");
        assertEquals(100.0, pair.getPrice());
        assertEquals(0.0, pair.getPriceCombo());
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

    @Test
    @DisplayName("A combo price is not multiplied by its own digits")
    void comboPriceNotSelfMultiplied() throws Exception {
        useVaultCurrency();

        // Issue #125: getMultiplyValue used to strip every digit, so "100 250" multiplied each
        // price by 100250. The multiplier is now read from an explicit xN marker only.
        PricePair pair = util.getShopPricePair(player, "100 250", ShopType.COMBO);
        assertNotNull(pair, "The line parses");
        assertEquals(100.0, pair.getPrice());
        assertEquals(250.0, pair.getPriceCombo());
    }

    @Test
    @DisplayName("An explicit xN multiplier still scales the price")
    void explicitMultiplierStillWorks() throws Exception {
        useVaultCurrency();

        PricePair pair = util.getShopPricePair(player, "100x2", ShopType.SELL);
        assertNotNull(pair, "The line parses");
        assertEquals(200.0, pair.getPrice(),
                "An explicit x2 multiplier scales the price to 200");
    }

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

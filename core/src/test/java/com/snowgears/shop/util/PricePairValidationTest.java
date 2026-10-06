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
    @DisplayName("cleanNumberText preserves spaces so combo prices can be split")
    void cleanNumberTextPreservesWhitespace() {
        // The fix for #93: the separator must survive so the two-price split works.
        assertEquals("100 250", UtilMethods.cleanNumberText("100 250"));
        assertEquals("100 50", UtilMethods.cleanNumberText("100  -50"),
                "Minus signs after the first position are stripped, spaces preserved");
        assertEquals("100.5 250", UtilMethods.cleanNumberText("100.5 250"));
        // Non-numeric noise is still stripped.
        assertEquals("100000 250", UtilMethods.cleanNumberText("$100,000 and 250 coins"),
                "Currency symbols and commas are stripped, spaces preserved");
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
}

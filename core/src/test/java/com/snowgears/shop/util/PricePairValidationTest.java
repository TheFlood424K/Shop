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
 * Documents how combo-shop prices are parsed, and what that means for #93.
 *
 * <p>#93 reported that {@code getShopPricePair} validates {@code price} but not {@code priceCombo},
 * so a combo shop could be created with a negative sell price. Tracing it: the parse cannot produce a
 * negative combo price at all, because {@code cleanNumberText} strips every character except digits,
 * a leading {@code -}, and {@code .} — <strong>including the whitespace between the two prices</strong>.
 *
 * <p>So {@code "100 250"} becomes {@code "100250"}: one token, not two. The
 * {@code multiplePrices.length > 1} branch is unreachable for any input a player can type, and the
 * combo price silently collapses into the buy price.
 *
 * <p>That makes #93's stated severity wrong in the safe direction — there is no currency duplication —
 * and reveals a different problem: <strong>combo shops cannot be given a separate sell price through
 * this path.</strong> These tests pin the actual behaviour so the distinction is on the record, and so
 * a future fix is measurable.
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
    @DisplayName("cleanNumberText removes the separator between two prices")
    void cleanNumberTextStripsWhitespace() {
        // This is the root of #93: the two-price split downstream can never see two tokens.
        assertEquals("100250", UtilMethods.cleanNumberText("100 250"));
        assertEquals("10050", UtilMethods.cleanNumberText("100 -50"));
        assertEquals("100.5250", UtilMethods.cleanNumberText("100.5 250"));
    }

    @Test
    @DisplayName("A two-price combo line collapses into one price rather than setting both")
    void twoPriceComboLineCollapses() {
        // Recorded as current behaviour. If combo pricing is repaired, this test is the thing that
        // should change - it is the observable difference.
        PricePair pair = util.getShopPricePair(player, "100 250", ShopType.COMBO);

        assertNotNull(pair, "The line parses; it just does not split");
        assertEquals(100250.0, pair.getPrice(),
                "Both prices are concatenated into the buy price - the separator is gone before the "
                        + "split, so the combo branch is unreachable for player input");
        assertEquals(0.0, pair.getPriceCombo(),
                "No separate combo price is ever set from this path");
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
                "The one price check that does exist is intact");
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
    @DisplayName("No input produces a negative combo price, which is why #93 is not exploitable")
    void negativeComboPriceUnreachable() {
        // The specific scenario #93 described. It does not produce a negative combo price; it
        // produces a large positive one, because the minus sign is deleted with the whitespace.
        PricePair pair = util.getShopPricePair(player, "100 -50", ShopType.COMBO);

        assertNotNull(pair);
        assertEquals(10050.0, pair.getPrice());
        assertEquals(0.0, pair.getPriceCombo(),
                "The sell side is never negative from this path, so there is no currency duplication");
    }
}
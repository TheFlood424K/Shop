package com.theflood424k.shoprevanced.shop;

import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ShopTypeTest {

    @DisplayName("toCreationWord: SELL → delegates to ShopRevancedMessage")
    @Test
    void toCreationWord_sell_delegates() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            assertEquals("sell", ShopType.SELL.toCreationWord());
        }
    }

    @DisplayName("toCreationWord: BUY → delegates to ShopRevancedMessage")
    @Test
    void toCreationWord_buy_delegates() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            assertEquals("buy", ShopType.BUY.toCreationWord());
        }
    }

    @DisplayName("toCreationWord: BARTER → delegates to ShopRevancedMessage")
    @Test
    void toCreationWord_barter_delegates() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            assertEquals("barter", ShopType.BARTER.toCreationWord());
        }
    }

    @DisplayName("toCreationWord: COMBO → delegates to ShopRevancedMessage")
    @Test
    void toCreationWord_combo_delegates() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");
            assertEquals("combo", ShopType.COMBO.toCreationWord());
        }
    }

    @DisplayName("toCreationWord: GAMBLE → delegates to ShopRevancedMessage")
    @Test
    void toCreationWord_gamble_delegates() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            assertEquals("gamble", ShopType.GAMBLE.toCreationWord());
        }
    }

    @DisplayName("toString: all enum values return lowercase word")
    @Test
    void toString_returnsLowercaseWord() {
        assertEquals("sell", ShopType.SELL.toString());
        assertEquals("buy", ShopType.BUY.toString());
        assertEquals("barter", ShopType.BARTER.toString());
        assertEquals("gamble", ShopType.GAMBLE.toString());
        assertEquals("combo", ShopType.COMBO.toString());
    }

    @DisplayName("enum values: all six types present")
    @Test
    void enumValues_hasSixTypes() {
        ShopType[] types = ShopType.values();
        assertEquals(5, types.length);
        assertTrue(java.util.Arrays.stream(types).anyMatch(t -> t == ShopType.SELL));
        assertTrue(java.util.Arrays.stream(types).anyMatch(t -> t == ShopType.BUY));
        assertTrue(java.util.Arrays.stream(types).anyMatch(t -> t == ShopType.BARTER));
        assertTrue(java.util.Arrays.stream(types).anyMatch(t -> t == ShopType.GAMBLE));
        assertTrue(java.util.Arrays.stream(types).anyMatch(t -> t == ShopType.COMBO));
    }
}

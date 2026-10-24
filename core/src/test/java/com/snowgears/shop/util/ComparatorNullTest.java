package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ComparatorNullTest {
    @Test
    public void testPriceHighNulls() {
        ComparatorShopPriceHigh comp = new ComparatorShopPriceHigh();
        assertEquals(0, comp.compare(null, null));
        assertEquals(-1, comp.compare(null, new MockShop()));
        assertEquals(1, comp.compare(new MockShop(), null));
    }

    @Test
    public void testPriceLowNulls() {
        ComparatorShopPriceLow comp = new ComparatorShopPriceLow();
        assertEquals(0, comp.compare(null, null));
        assertEquals(-1, comp.compare(null, new MockShop()));
        assertEquals(1, comp.compare(new MockShop(), null));
    }

    @Test
    public void testTypeNulls() {
        ComparatorShopType comp = new ComparatorShopType();
        assertDoesNotThrow(() -> comp.compare(null, null));
        assertDoesNotThrow(() -> comp.compare(null, new MockShop()));
        assertDoesNotThrow(() -> comp.compare(new MockShop(), null));
    }

    private static class MockShop extends AbstractShop {
        public MockShop() {
            super(null, null, null, 0, null, null);
        }
    }
}

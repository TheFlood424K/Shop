package com.snowgears.shop.util;

import com.snowgears.shop.shop.ShopType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class Issue13BugFixesTest {
    @Test
    public void testShopMessageGetMessageFromOrdersReturnsNullForMissingMessage() {
        String result = ShopMessage.getMessageFromOrders(ShopType.BUY, "missing_key_that_does_not_exist", 10.0, 1);
        assertNull(result);
    }
}

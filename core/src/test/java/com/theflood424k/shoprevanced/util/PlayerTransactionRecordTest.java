package com.snowgears.shop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTransactionRecordTest {

    @Test
    void constructorShouldSetAllFields() {
        PlayerTransactionRecord record = new PlayerTransactionRecord(
                42, 1700000000000L, "Notch", "ShopOwner",
                ShopActionType.TRANSACT, 9.99, 5, "base64item", "base64barter");

        assertEquals(42, record.getTransactionId());
        assertEquals(1700000000000L, record.getTimestamp());
        assertEquals("Notch", record.getCustomerName());
        assertEquals("ShopOwner", record.getOwnerName());
        assertEquals(ShopActionType.TRANSACT, record.getActionType());
        assertEquals(9.99, record.getPrice());
        assertEquals(5, record.getAmount());
        assertEquals("base64item", record.getItemBase64());
        assertEquals("base64barter", record.getBarterItemBase64());
    }

    @Test
    void equalsAndHashCodeShouldWork() {
        PlayerTransactionRecord r1 = new PlayerTransactionRecord(
                1, 100L, "A", "B", ShopActionType.CREATE, 1.0, 2, null, null);
        PlayerTransactionRecord r2 = new PlayerTransactionRecord(
                1, 100L, "A", "B", ShopActionType.CREATE, 1.0, 2, null, null);
        PlayerTransactionRecord r3 = new PlayerTransactionRecord(
                2, 100L, "A", "B", ShopActionType.CREATE, 1.0, 2, null, null);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
    }

    @Test
    void toStringShouldNotThrow() {
        PlayerTransactionRecord record = new PlayerTransactionRecord(
                1, 100L, "A", "B", ShopActionType.CREATE, 1.0, 2, "aaaa", null);
        assertNotNull(record.toString());
    }
}

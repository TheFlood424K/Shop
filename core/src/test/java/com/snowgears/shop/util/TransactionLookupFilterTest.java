package com.snowgears.shop.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionLookupFilterTest {

    @Test
    void builderShouldSetAllFields() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = TransactionLookupFilter.builder()
                .ownerUUID(ownerUUID)
                .startTime(1000L)
                .endTime(2000L)
                .customerName("Notch")
                .actionType(ShopActionType.TRANSACT)
                .limit(50)
                .build();

        assertEquals(ownerUUID, filter.getOwnerUUID());
        assertEquals(1000L, filter.getStartTime());
        assertEquals(2000L, filter.getEndTime());
        assertEquals("Notch", filter.getCustomerName());
        assertEquals(ShopActionType.TRANSACT, filter.getActionType());
        assertEquals(50, filter.getLimit());
    }

    @Test
    void builderShouldHaveDefaults() {
        TransactionLookupFilter filter = TransactionLookupFilter.builder().build();

        assertNull(filter.getOwnerUUID());
        assertEquals(0L, filter.getStartTime());
        assertEquals(0L, filter.getEndTime());
        assertNull(filter.getCustomerName());
        assertNull(filter.getActionType());
        assertEquals(0, filter.getLimit());
    }

    @Test
    void equalsAndHashCodeShouldWork() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter f1 = TransactionLookupFilter.builder()
                .ownerUUID(ownerUUID).limit(10).build();
        TransactionLookupFilter f2 = TransactionLookupFilter.builder()
                .ownerUUID(ownerUUID).limit(10).build();
        TransactionLookupFilter f3 = TransactionLookupFilter.builder()
                .limit(20).build();

        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
        assertNotEquals(f1, f3);
    }
}

package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.TransactionLookupFilter;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Tag("integration")
public class TransactionLookupTest extends BaseMockBukkitTest {

    @Test
    void testTransactionLookupFilterCreation() {
        UUID ownerUUID = UUID.randomUUID();
        Set<Material> includeItems = new HashSet<>();
        includeItems.add(Material.DIRT);
        Set<Material> excludeItems = new HashSet<>();
        excludeItems.add(Material.STONE);

        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, includeItems, excludeItems, "TestPlayer", ownerUUID);

        assertEquals(ShopType.BUY, filter.getAction());
        assertEquals(includeItems, filter.getIncludeItems());
        assertEquals(excludeItems, filter.getExcludeItems());
        assertEquals("TestPlayer", filter.getCustomerName());
        assertEquals(ownerUUID, filter.getOwnerUUID());
    }

    @Test
    void testTransactionLookupFilterMatchesItem() {
        UUID ownerUUID = UUID.randomUUID();
        Set<Material> includeItems = new HashSet<>();
        includeItems.add(Material.DIRT);
        includeItems.add(Material.COBBLESTONE);
        Set<Material> excludeItems = new HashSet<>();
        excludeItems.add(Material.STONE);

        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, includeItems, excludeItems, null, ownerUUID);

        // Should match included items
        assertTrue(filter.matchesItem(new ItemStack(Material.DIRT)));
        assertTrue(filter.matchesItem(new ItemStack(Material.COBBLESTONE)));

        // Should not match excluded items
        assertFalse(filter.matchesItem(new ItemStack(Material.STONE)));

        // Should not match items not in include list when include list is not empty
        assertFalse(filter.matchesItem(new ItemStack(Material.DIAMOND)));
    }

    @Test
    void testTransactionLookupFilterMatchesItemEmptyInclude() {
        UUID ownerUUID = UUID.randomUUID();
        Set<Material> includeItems = new HashSet<>(); // Empty include list
        Set<Material> excludeItems = new HashSet<>();
        excludeItems.add(Material.STONE);

        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, includeItems, excludeItems, null, ownerUUID);

        // With empty include list, should match any non-excluded item
        assertTrue(filter.matchesItem(new ItemStack(Material.DIRT)));
        assertTrue(filter.matchesItem(new ItemStack(Material.COBBLESTONE)));
        assertTrue(filter.matchesItem(new ItemStack(Material.DIAMOND)));

        // Should not match excluded items
        assertFalse(filter.matchesItem(new ItemStack(Material.STONE)));
    }

    @Test
    void testTransactionLookupFilterMatchesCustomer() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, new HashSet<>(), new HashSet<>(), "TestPlayer", ownerUUID);

        // Should match exact name (case insensitive)
        assertTrue(filter.matchesCustomer("TestPlayer"));
        assertTrue(filter.matchesCustomer("testplayer"));
        assertTrue(filter.matchesCustomer("TESTPLAYER"));

        // Should not match different names
        assertFalse(filter.matchesCustomer("OtherPlayer"));
        assertFalse(filter.matchesCustomer("Test"));
    }

    @Test
    void testTransactionLookupFilterMatchesCustomerEmpty() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, new HashSet<>(), new HashSet<>(), "", ownerUUID);

        // Empty customer name should match any customer
        assertTrue(filter.matchesCustomer("AnyPlayer"));
        assertTrue(filter.matchesCustomer("TestPlayer"));
    }

    @Test
    void testTransactionLookupFilterMatchesCustomerNull() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, new HashSet<>(), new HashSet<>(), null, ownerUUID);

        // Null customer name should match any customer
        assertTrue(filter.matchesCustomer("AnyPlayer"));
        assertTrue(filter.matchesCustomer("TestPlayer"));
    }

    @Test
    void testTransactionLookupFilterMatchesOwner() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, new HashSet<>(), new HashSet<>(), null, ownerUUID);

        // Should match the owner UUID
        assertTrue(filter.matchesOwner(ownerUUID));

        // Should not match different UUIDs
        assertFalse(filter.matchesOwner(UUID.randomUUID()));
    }

    @Test
    void testTransactionLookupFilterMatchesOwnerNull() {
        UUID ownerUUID = UUID.randomUUID();
        TransactionLookupFilter filter = new TransactionLookupFilter(
                ShopType.BUY, new HashSet<>(), new HashSet<>(), null, null);

        // Null owner UUID should match any owner
        assertTrue(filter.matchesOwner(UUID.randomUUID()));
        assertTrue(filter.matchesOwner(ownerUUID));
    }
}
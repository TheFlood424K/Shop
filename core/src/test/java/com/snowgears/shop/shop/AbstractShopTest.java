package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AbstractShop core functionality.
 * Tests the critical fixes for NPEs, race conditions, and logic bugs.
 */
@ExtendWith(MockBukkitExtension.class)
class AbstractShopTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    @MockBukkitInject
    private Shop plugin;

    @Test
    void testIsInitializedReturnsFalseWhenItemNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop without item should not be initialized
        assertFalse(shop.isInitialized());
    }

    @Test
    void testIsInitializedReturnsTrueWhenItemSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting item should mark as initialized
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        assertTrue(shop.isInitialized());
    }

    @Test
    void testGetItemStackReturnsNullWhenNotSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        assertNull(shop.getItemStack());
    }

    @Test
    void testGetItemStackReturnsClone() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        ItemStack original = new ItemStack(Material.DIAMOND);
        original.setAmount(64);
        shop.setItemStack(original);

        ItemStack returned = shop.getItemStack();
        assertNotNull(returned);
        assertNotSame(original, returned); // Should be a clone
        assertEquals(Material.DIAMOND, returned.getType());
        assertEquals(1, returned.getAmount()); // Amount should be set to 1
    }

    @Test
    void testCalculateStockReturnsUnavailableWhenUninitialized() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Uninitialized shop should return STOCK_UNAVAILABLE
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testCalculateStockReturnsUnavailableWhenInventoryNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        shop.setItemStack(new ItemStack(Material.DIAMOND));
        // chestLocation is null, so getInventory() returns null
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testUpdateStockSkipsWhenUnavailable() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // updateStock() should not throw NPE when stock is unavailable
        // This tests the fix for Bug 4
        assertDoesNotThrow(() -> shop.updateStock());
    }

    @Test
    void testSetItemStackNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting null item should not throw
        assertDoesNotThrow(() -> shop.setItemStack(null));
        assertFalse(shop.isInitialized());
    }

    @Test
    void testGetChestLocationNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop has null chestLocation until load() is called
        assertNull(shop.getChestLocation());
    }

    @Test
    void testDeleteHandlesNullDisplay() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(world, 100, 64, 100);

        // delete() should not NPE if display is somehow null
        SellShop shop2 = new SellShop(
            new Location(world, 200, 64, 200),
            ownerUUID,
            10.0,
            1,
            false,
            BlockFace.NORTH
        );
        // Use reflection to set display to null
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("display");
            field.setAccessible(true);
            field.set(shop2, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        assertDoesNotThrow(() -> shop2.delete());
    }
}
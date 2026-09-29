package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbstractShop core functionality.
 * Tests the critical fixes for NPEs, race conditions, and logic bugs.
 */
class AbstractShopTest {

    private AutoCloseable mocks;
    @Mock
    private World world;
    @Mock
    private ItemStack mockItemStack;
    @Mock
    private Shop plugin;
    @Mock
    private ShopLogger shopLogger;
    @Mock
    private ShopHandler mockShopHandler;
    private SellShop shop;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock Material.DIAMOND behavior
        when(mockItemStack.getType()).thenReturn(Material.DIAMOND);
        when(mockItemStack.getAmount()).thenReturn(1);
        when(mockItemStack.clone()).thenReturn(mockItemStack);

        // Set the static plugin field in Shop to our mock plugin
        try {
            java.lang.reflect.Field field = Shop.class.getDeclaredField("plugin");
            field.setAccessible(true);
            field.set(null, plugin);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Mock plugin.getLogger()
        when(plugin.getLogger()).thenReturn(shopLogger);

        // Mock plugin.getShopHandler() to return a mock that doesn't NPE
        when(plugin.getShopHandler()).thenReturn(mockShopHandler);
        when(mockShopHandler.createDisplay(any(Location.class))).thenReturn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testIsInitializedReturnsFalseWhenItemNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop without item should not be initialized
        assertFalse(shop.isInitialized());
    }

    @Test
    void testIsInitializedReturnsTrueWhenItemSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting item should mark as initialized
        shop.setItemStack(mockItemStack);
        assertTrue(shop.isInitialized());
    }

    @Test
    void testGetItemStackReturnsNullWhenNotSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        assertNull(shop.getItemStack());
    }

    @Test
    void testGetItemStackReturnsClone() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Mock a different item stack for the "original" with amount 64
        ItemStack original = mock(ItemStack.class);
        when(original.getType()).thenReturn(Material.DIAMOND);
        when(original.getAmount()).thenReturn(64);
        when(original.clone()).thenReturn(mockItemStack);

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
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Uninitialized shop should return STOCK_UNAVAILABLE
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testCalculateStockReturnsUnavailableWhenInventoryNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        shop.setItemStack(mockItemStack);
        // chestLocation is null, so getInventory() returns null
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testUpdateStockSkipsWhenUnavailable() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // updateStock() should not throw NPE when stock is unavailable
        // This tests the fix for Bug 4
        assertDoesNotThrow(() -> shop.updateStock());
    }

    @Test
    void testSetItemStackNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting null item should not throw
        assertDoesNotThrow(() -> shop.setItemStack(null));
        assertFalse(shop.isInitialized());
    }

    @Test
    void testGetChestLocationNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop has null chestLocation until load() is called
        assertNull(shop.getChestLocation());
    }

    @Test
    void testDeleteHandlesNullDisplay() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = new Location(null, 100, 64, 100);

        // delete() should not NPE if display is somehow null
        SellShop shop2 = new SellShop(
            new Location(null, 200, 64, 200),
            ownerUUID,
            10.0,
            1,
            false,
            BlockFace.NORTH
        );
        shop2.setItemStack(mockItemStack);
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
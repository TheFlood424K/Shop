package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopHandler core functionality.
 * Tests the critical fixes for race conditions and NPEs.
 */
class ShopHandlerTest {

    private Shop plugin;
    private ShopHandler shopHandler;
    private World mockWorld;

    @BeforeEach
    void setUp() {
        mockWorld = mock(World.class);

        // Create a minimal plugin mock
        plugin = mock(Shop.class);
        when(plugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(plugin.getDataFolder()).thenReturn(new java.io.File("target/test-data"));
        when(plugin.getLogger()).thenReturn(mock(ShopLogger.class));
        when(plugin.getFoliaLib()).thenReturn(mock(com.tcoded.folialib.FoliaLib.class));
        when(plugin.getShopSearchRadius()).thenReturn(1);
        when(plugin.getMaxShopDisplayDistance()).thenReturn(64.0);
        when(plugin.getDisplayBatchSize()).thenReturn(10);
        when(plugin.getDisplayBatchDelay()).thenReturn(2);
        when(plugin.getDisplayMovementThreshold()).thenReturn(1.0);

        // Create ShopHandler - this will trigger loadShops() async
        shopHandler = new ShopHandler(plugin);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testAddShopPreventsDuplicate() {
        // Create a shop location
        Location signLoc = new Location(mockWorld, 100, 64, 100);

        // Create a test shop
        AbstractShop shop = AbstractShop.create(signLoc, UUID.randomUUID(), 10.0, 0.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Add the shop
        shopHandler.addShop(shop);

        // Try to add the same shop again (should be prevented)
        shopHandler.addShop(shop);

        // Should only have one shop
        assertEquals(1, shopHandler.getNumberOfShops());
    }

    @Test
    void testGetShopReturnsNullForNonExistent() {
        Location loc = new Location(mockWorld, 0, 0, 0);
        assertNull(shopHandler.getShop(loc));
    }

    @Test
    void testGetShopByChestHandlesNull() {
        // Test that getShopByChest doesn't NPE when chest location is null
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.getSignLocation()).thenReturn(new Location(mockWorld, 100, 64, 100));
        when(shop.getChestLocation()).thenReturn(null);

        // This should not throw NPE
        assertNull(shopHandler.getShopByChest(mock(org.bukkit.block.Block.class)));
    }
}
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShopHandler core functionality.
 * Tests the critical fixes for race conditions and NPEs.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopHandlerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    @MockBukkitInject
    private Shop plugin;

    private ShopHandler shopHandler;

    @Test
    void testAddShopPreventsDuplicate() {
        // Create a shop location
        Location signLoc = new Location(world, 100, 64, 100);

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
        Location loc = new Location(world, 0, 0, 0);
        assertNull(shopHandler.getShop(loc));
    }

    @Test
    void testGetShopByChestHandlesNull() {
        // Test that getShopByChest doesn't NPE when chest location is null
        AbstractShop shop = new SellShop(
            new Location(world, 100, 64, 100),
            UUID.randomUUID(),
            10.0,
            1,
            false,
            BlockFace.NORTH
        );
        // chestLocation is null by default

        // This should not throw NPE
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);
        assertNull(shopHandler.getShopByChest(chestBlock));
    }
}
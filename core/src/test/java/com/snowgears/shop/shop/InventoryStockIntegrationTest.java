package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for inventory and stock management.
 */
@ExtendWith(MockBukkitExtension.class)
class InventoryStockIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        shopHandler = plugin.getShopHandler();
    }

    @AfterEach
    void tearDown() {
        // Extension handles cleanup
    }

    @Test
    void testUpdateStockNoThrow() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        // Create and link chest
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getBlockInventory();
        inv.addItem(new ItemStack(Material.DIAMOND, 10));

        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Should not throw
        assertDoesNotThrow(() -> shop.updateStock());
    }

    @Test
    void testItemStackCloning() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);

        ItemStack original = new ItemStack(Material.DIAMOND);
        original.setAmount(64);
        original.setItemMeta(original.getItemMeta()); // Ensure meta exists

        shop.setItemStack(original);

        ItemStack returned = shop.getItemStack();
        assertNotNull(returned);
        assertNotSame(original, returned); // Should be a clone
        assertEquals(Material.DIAMOND, returned.getType());
        assertEquals(1, returned.getAmount()); // Amount should be set to 1
    }

    @Test
    void testMultipleItemTypesInChest() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        // Create and link chest
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getBlockInventory();

        // Add different items
        inv.addItem(new ItemStack(Material.DIAMOND, 10));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, 20));
        inv.addItem(new ItemStack(Material.IRON_INGOT, 30));

        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Should not throw when calculating stock
        assertDoesNotThrow(() -> shop.calculateStock());
    }

    @Test
    void testSetItemStackNullSafe() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);

        // Setting null item should not throw
        assertDoesNotThrow(() -> shop.setItemStack(null));
        assertFalse(shop.isInitialized());
    }
}
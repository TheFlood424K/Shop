package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.BuyShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * Integration tests for transaction handling.
 */
@ExtendWith(MockBukkitExtension.class)
class TransactionIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private TransactionHandler transactionHandler;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        transactionHandler = plugin.getTransactionHelper();
        shopHandler = plugin.getShopHandler();
    }

    @AfterEach
    void tearDown() {
        // Extension handles cleanup
    }

    @Test
    void testSellShopTransaction() {
        // Create a sell shop
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Add shop to handler
        shopHandler.addShop(shop);

        // Create player with money
        Player player = server.addPlayer("Buyer");

        // Set up chest with items
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);

        // Link chest to shop via reflection
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Test that transaction doesn't throw
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.DIRT), chestBlock, BlockFace.UP),
                shop, false
            );
        });
    }

    @Test
    void testBuyShopTransaction() {
        // Create a buy shop
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        BuyShop shop = new BuyShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Add shop to handler
        shopHandler.addShop(shop);

        // Create player with items to sell
        Player player = server.addPlayer("Seller");

        // Set up chest
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);

        // Link chest to shop
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Test that transaction doesn't throw
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.DIRT), chestBlock, BlockFace.UP),
                shop, false
            );
        });
    }

    @Test
    void testTransactionWithInvalidBlock() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        Player player = server.addPlayer("Buyer");

        // Use stone instead of chest
        Block stoneBlock = world.getBlockAt(100, 64, 102);
        stoneBlock.setType(Material.STONE);

        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, stoneBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Should handle gracefully
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.DIRT), stoneBlock, BlockFace.UP),
                shop, false
            );
        });
    }

    @Test
    void testTransactionWithNullChestLocation() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        Player player = server.addPlayer("Buyer");
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);

        // Don't set chestLocation - should be null

        // Should not throw NPE
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.DIRT), chestBlock, BlockFace.UP),
                shop, false
            );
        });
    }
}
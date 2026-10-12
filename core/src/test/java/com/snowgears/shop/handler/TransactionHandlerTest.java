package com.snowgears.shop.handler;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.StubbedPlayers;
import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static com.snowgears.shop.testsupport.BaseMockBukkitTest.waitForNextMessage;

/**
 * Unit tests for TransactionHandler core functionality.
 * Tests the critical NPE fix for chestLocation null check.
 */
class TransactionHandlerTest extends BaseMockBukkitTest {

    private TransactionHandler transactionHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        transactionHandler = plugin.getTransactionHelper();
    }

    @AfterEach
    void tearDown() {
        // Do not manually call MockBukkit.unmock() when using @ExtendWith(MockBukkitExtension.class)
        // The extension handles the MockBukkit lifecycle automatically.
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestLocation() {
        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        World world = getServer().addSimpleWorld("world");
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        PlayerMock player = StubbedPlayers.add(getServer(), "TestPlayer");
        player.setOp(true); // Give permissions

        // Create mock event
        Block clickedBlock = getServer().getWorld("world").getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // Set chestLocation to null (simulating shop not fully loaded)
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // This should not throw NPE - the fix stores chestLocation in local variable
        // and checks for null before calling getBlock()
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(event, shop, false);
        });
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestBlock() {
        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        World world = getServer().addSimpleWorld("world");
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        PlayerMock player = StubbedPlayers.add(getServer(), "TestPlayer");
        player.setOp(true); // Give permissions

        // Create mock event with a non-chest block
        Block clickedBlock = getServer().getWorld("world").getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.STONE); // Not a chest

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // Set chestLocation but the block is not a chest
        Location chestLoc = new Location(getServer().addSimpleWorld("world"), 100, 64, 101);
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestLoc);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Should handle gracefully (isChest returns false for stone)
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(event, shop, false);
        });
    }

    /**
     * Verifies that when a player tries to buy from an empty shop (out of stock),
     * the correct error message is sent to the player.
     * This tests the fix for issue #136: transaction error messages may be silently unreachable.
     */
    @Test
    void testTransactionErrorMessageForEmptyShop() {
        // Create a test shop with no stock
        UUID ownerUUID = UUID.randomUUID();
        World world = getServer().addSimpleWorld("world");
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Ensure the shop has an empty chest at the configured chestLocation
        Block chestBlock = getServer().getWorld("world").getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);
        // Empty the chest
        InventoryHolder holder = (InventoryHolder) chestBlock.getState();
        holder.getInventory().clear();

        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Create a player with sufficient funds but shop has no stock
        PlayerMock buyer = StubbedPlayers.add(getServer(), "Buyer");
        buyer.setOp(true); // Give permissions
        // Give buyer enough emeralds for 8 items at 10 each = 80 emeralds
        buyer.getInventory().addItem(new ItemStack(Material.EMERALD, 80));

        // Create the OWNER player (shop owner) with the SAME UUID as the shop owner
        // Use StubbedPlayerMock with explicit UUID to match the shop
        PlayerMock owner = new com.snowgears.shop.testsupport.StubbedPlayerMock(getServer(), "Owner", ownerUUID);
        getServer().addPlayer(owner);
        owner.setOp(true);

        // Create mock event - player right-clicks the sign
        Block signBlock = getServer().getWorld("world").getBlockAt(100, 64, 100);
        signBlock.setType(Material.OAK_WALL_SIGN);
        PlayerInteractEvent event = new PlayerInteractEvent(
            buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.EMERALD),
            getServer().getWorld("world").getBlockAt(100, 64, 100), BlockFace.NORTH, EquipmentSlot.HAND);

        // Execute the transaction - should fail with out of stock error
        // The error message should be sent to the player
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(event, shop, false);
        });

        // Verify that the error message was sent to the player
        String playerMessage = waitForNextMessage(buyer);
        assertNotNull(playerMessage, "Player should receive an error message");
        assertTrue(playerMessage.contains("out of stock") || playerMessage.contains("out of stock"),
            "Player should receive out-of-stock error message. Got: " + playerMessage);

        // Owner should also receive a notification
        String ownerMessage = waitForNextMessage(owner);
        assertNotNull(ownerMessage, "Owner should receive out-of-stock notification");
        assertTrue(ownerMessage.contains("out of stock") || ownerMessage.contains("out of stock"),
            "Owner should receive out-of-stock notification. Got: " + ownerMessage);
    }
}
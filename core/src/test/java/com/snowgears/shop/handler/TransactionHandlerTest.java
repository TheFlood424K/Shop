package com.snowgears.shop.handler;

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
 * Unit tests for TransactionHandler core functionality.
 * Tests the critical NPE fix for chestLocation null check.
 */
@ExtendWith(MockBukkitExtension.class)
class TransactionHandlerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private TransactionHandler createTransactionHandler() {
        return MockBukkit.load(Shop.class).getTransactionHelper();
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestLocation() {
        TransactionHandler transactionHandler = createTransactionHandler();

        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        Player player = server.addPlayer("TestPlayer");
        player.setOp(true); // Give permissions

        // Create mock event
        Block clickedBlock = server.getBlockAt(world, 100, 64, 101);
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
        TransactionHandler transactionHandler = createTransactionHandler();

        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        Player player = server.addPlayer("TestPlayer");
        player.setOp(true); // Give permissions

        // Create mock event with a non-chest block
        Block clickedBlock = server.getBlockAt(world, 100, 64, 101);
        clickedBlock.setType(Material.STONE); // Not a chest

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // Set chestLocation but the block is not a chest
        Location chestLoc = new Location(world, 100, 64, 101);
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
}
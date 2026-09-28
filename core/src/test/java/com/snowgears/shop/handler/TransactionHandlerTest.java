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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionHandler core functionality.
 * Tests the critical NPE fix for chestLocation null check.
 */
class TransactionHandlerTest {

    private AutoCloseable mocks;
    @Mock
    private Shop plugin;
    @Mock
    private World world;
    @Mock
    private Block chestBlock;
    @Mock
    private Block stoneBlock;
    @Mock
    private Player player;
    @Mock
    private PlayerInteractEvent event;
    private TransactionHandler transactionHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionHandler = new TransactionHandler();
    }

    @AfterEach
    void tearDown() throws Exception {
        // No specific cleanup needed
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestLocation() {
        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOp()).thenReturn(true);

        // Create mock event
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(chestBlock);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(new ItemStack(Material.DIRT));
        when(event.getBlockFace()).thenReturn(BlockFace.UP);

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
        Location signLoc = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOp()).thenReturn(true);

        // Create mock event with a non-chest block
        when(stoneBlock.getType()).thenReturn(Material.STONE);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(stoneBlock);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(new ItemStack(Material.DIRT));
        when(event.getBlockFace()).thenReturn(BlockFace.UP);

        // Set chestLocation but the block is not a chest
        Location chestLoc = new Location(null, 100, 64, 101);
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
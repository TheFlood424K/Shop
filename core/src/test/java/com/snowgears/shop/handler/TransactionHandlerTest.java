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
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionHandler core functionality.
 * Tests the critical NPE fix for chestLocation null check.
 */
class TransactionHandlerTest {

    private World mockWorld;
    private Shop plugin;
    private TransactionHandler transactionHandler;
    private SellShop shop;
    private Player player;
    private PlayerInteractEvent event;

    @BeforeEach
    void setUp() {
        mockWorld = mock(World.class);

        plugin = mock(Shop.class);
        when(plugin.getShopHandler()).thenReturn(mock(ShopHandler.class));
        when(plugin.getTransactionHelper()).thenReturn(mock(TransactionHandler.class));
        when(plugin.usePerms()).thenReturn(false);
        when(plugin.getDebug_allowUseOwnShop()).thenReturn(false);

        transactionHandler = new TransactionHandler(plugin);

        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(mockWorld, 100, 64, 100);
        shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(ownerUUID);

        // Create mock event
        event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getClickedBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        when(event.isCancelled()).thenReturn(false);
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestLocation() {
        // Set chestLocation to null (simulating shop not fully loaded)
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        when(plugin.getShopHandler().isChest(any())).thenReturn(true);

        // This should not throw NPE - the fix stores chestLocation in local variable
        // and checks for null before calling getBlock()
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(event, shop, false);
        });
    }

    @Test
    void testExecuteTransactionFromEventHandlesNullChestBlock() {
        // Set chestLocation but make isChest return false
        Location chestLoc = new Location(mockWorld, 100, 64, 101);
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestLoc);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        when(plugin.getShopHandler().isChest(any())).thenReturn(false);

        // Should handle gracefully and delete shop
        assertDoesNotThrow(() -> {
            transactionHandler.executeTransactionFromEvent(event, shop, false);
        });
    }
}
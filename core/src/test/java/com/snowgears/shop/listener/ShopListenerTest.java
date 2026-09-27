package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopListener core functionality.
 * Tests the critical NPE fix for onShopChestClick.
 */
class ShopListenerTest {

    private World mockWorld;
    private Shop plugin;
    private ShopListener shopListener;
    private ShopHandler shopHandler;
    private SellShop shop;
    private Player player;
    private PlayerInteractEvent event;
    private Block clickedBlock;

    @BeforeEach
    void setUp() {
        mockWorld = mock(World.class);

        plugin = mock(Shop.class);
        shopHandler = mock(ShopHandler.class);
        when(plugin.getShopHandler()).thenReturn(shopHandler);

        shopListener = new ShopListener(plugin);

        // Create a test shop
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(mockWorld, 100, 64, 100);
        shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Create mock player
        player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isSneaking()).thenReturn(false);
        when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        when(player.getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.DIRT));

        // Create mock event
        event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        clickedBlock = mock(Block.class);
        when(event.getClickedBlock()).thenReturn(clickedBlock);
        when(clickedBlock.getType()).thenReturn(Material.CHEST);
    }

    @Test
    void testOnShopChestClickHandlesNullSignLocation() {
        // Set shop with null signLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("signLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        when(shopHandler.getShopByChest(any())).thenReturn(shop);
        when(shopHandler.isChest(any())).thenReturn(true);

        // This should not throw NPE - the fix stores locations in local variables
        // with null checks before dereferencing
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }

    @Test
    void testOnShopChestClickHandlesNullChestLocation() {
        // Set shop with null chestLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        when(shopHandler.getShopByChest(any())).thenReturn(shop);
        when(shopHandler.isChest(any())).thenReturn(true);

        // This should not throw NPE
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }
}
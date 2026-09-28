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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopListener core functionality.
 * Tests the critical NPE fix for onShopChestClick.
 */
class ShopListenerTest {

    private AutoCloseable mocks;
    @Mock
    private Shop plugin;
    @Mock
    private World world;
    @Mock
    private Block chestBlock;
    @Mock
    private Player player;
    @Mock
    private PlayerInteractEvent event;
    private ShopHandler shopHandler;
    private ShopListener shopListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        shopHandler = new ShopHandler();
        shopListener = new ShopListener();
    }

    @AfterEach
    void tearDown() throws Exception {
        // No specific cleanup needed
    }

    @Test
    void testOnShopChestClickHandlesNullSignLocation() {
        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        when(player.getName()).thenReturn("TestPlayer");

        // Create mock event with a chest block
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(chestBlock);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(new ItemStack(Material.DIRT));
        when(event.getBlockFace()).thenReturn(BlockFace.UP);

        // Set shop with null signLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("signLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // This should not throw NPE - the fix stores locations in local variables
        // with null checks before dereferencing
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }

    @Test
    void testOnShopChestClickHandlesNullChestLocation() {
        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(null, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        when(player.getName()).thenReturn("TestPlayer");

        // Create mock event with a chest block
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(chestBlock);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(new ItemStack(Material.DIRT));
        when(event.getBlockFace()).thenReturn(BlockFace.UP);

        // Set shop with null chestLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // This should not throw NPE
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }
}
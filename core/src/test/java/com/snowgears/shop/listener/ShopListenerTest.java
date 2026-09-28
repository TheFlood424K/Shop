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
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShopListener core functionality.
 * Tests the critical NPE fix for onShopChestClick.
 */
class ShopListenerTest {

    private ServerMock server;
    private Shop plugin;
    private World world;
    private ShopHandler shopHandler;
    private ShopListener shopListener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Shop.class);
        world = server.getWorlds().get(0);
        shopHandler = plugin.getShopHandler();
        shopListener = plugin.getShopListener();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void testOnShopChestClickHandlesNullSignLocation() {
        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        Player player = server.addPlayer("TestPlayer");

        // Create mock event with a chest block
        Block clickedBlock = world.getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

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
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        Player player = server.addPlayer("TestPlayer");

        // Create mock event with a chest block
        Block clickedBlock = world.getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

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
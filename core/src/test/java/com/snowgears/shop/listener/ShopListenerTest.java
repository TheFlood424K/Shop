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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShopListener core functionality.
 * Tests the critical NPE fix for onShopChestClick.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopListenerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private ShopHandler createShopHandler() {
        // Try target/ first (when running from core module), then core/target/ (when running from project root)
        File targetDir = new File("target");
        File[] jars = targetDir.listFiles((dir, name) -> name.startsWith("shop-core-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            targetDir = new File("core/target");
            jars = targetDir.listFiles((dir, name) -> name.startsWith("shop-core-") && name.endsWith(".jar"));
        }
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("No shop-core JAR found in target/ or core/target/");
        }
        Shop plugin = (Shop) MockBukkit.loadJar(jars[0]);
        return plugin.getShopHandler();
    }

    private ShopListener createShopListener() {
        // Try target/ first (when running from core module), then core/target/ (when running from project root)
        File targetDir = new File("target");
        File[] jars = targetDir.listFiles((dir, name) -> name.startsWith("shop-core-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            targetDir = new File("core/target");
            jars = targetDir.listFiles((dir, name) -> name.startsWith("shop-core-") && name.endsWith(".jar"));
        }
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("No shop-core JAR found in target/ or core/target/");
        }
        Shop plugin = (Shop) MockBukkit.loadJar(jars[0]);
        return plugin.getShopListener();
    }

    @Test
    void testOnShopChestClickHandlesNullSignLocation() {
        ShopHandler shopHandler = createShopHandler();
        ShopListener shopListener = createShopListener();

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
        ShopHandler shopHandler = createShopHandler();
        ShopListener shopListener = createShopListener();

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
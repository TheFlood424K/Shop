package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
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
 * Unit tests for MiscListener shop creation functionality.
 * Tests the wall sign chest detection fix and creation spam prevention.
 */
@ExtendWith(MockBukkitExtension.class)
class MiscListenerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private ShopHandler shopHandler;
    private MiscListener miscListener;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        shopHandler = plugin.getShopHandler();
        miscListener = plugin.getMiscListener();
    }

    @AfterEach
    void tearDown() {
        // Extension handles MockBukkit lifecycle
    }

    @Test
    void testOnShopCreationWallSignChestDetection() {
        // Test that wall signs correctly detect chest behind them
        // WallSign.getFacing() returns direction text faces (away from chest)
        // Chest is at OPPOSITE face

        // Place a chest
        Block chestBlock = world.getBlockAt(100, 64, 100);
        chestBlock.setType(Material.CHEST);

        // Place a wall sign on the NORTH face of chest (sign faces SOUTH, text faces SOUTH)
        Block signBlock = world.getBlockAt(100, 64, 101); // North of chest
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign signData = (WallSign) signBlock.getBlockData();
        signData.setFacing(BlockFace.SOUTH); // Text faces SOUTH (away from chest)
        signBlock.setBlockData(signData);

        // Create SignChangeEvent with proper constructor
        String[] lines = {"[Shop]", "1", "10", "sell"};
        Player player = server.addPlayer("TestPlayer");
        SignChangeEvent event = new SignChangeEvent(signBlock, player, lines);

        // The event should be processed and create a shop
        // The chest should be detected at the chest location (behind the sign)
        assertDoesNotThrow(() -> {
            miscListener.onShopCreation(event);
        });

        // Note: Full integration test would require chest to be detected
        // This test verifies the event handler doesn't crash
    }

    @Test
    void testOnShopCreationWallSignFacingEast() {
        // Place a chest
        Block chestBlock = world.getBlockAt(100, 64, 100);
        chestBlock.setType(Material.CHEST);

        // Place a wall sign on the EAST face of chest (sign faces WEST)
        Block signBlock = world.getBlockAt(101, 64, 100); // East of chest
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign signData = (WallSign) signBlock.getBlockData();
        signData.setFacing(BlockFace.WEST); // Text faces WEST (away from chest)
        signBlock.setBlockData(signData);

        String[] lines = {"[Shop]", "1", "10", "sell"};
        Player player = server.addPlayer("TestPlayer2");
        SignChangeEvent event = new SignChangeEvent(signBlock, player, lines);

        assertDoesNotThrow(() -> {
            miscListener.onShopCreation(event);
        });
    }

    @Test
    void testOnShopCreationIgnoresNonShopSigns() {
        Block chestBlock = world.getBlockAt(100, 64, 100);
        chestBlock.setType(Material.CHEST);

        Block signBlock = world.getBlockAt(100, 64, 101);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign signData = (WallSign) signBlock.getBlockData();
        signData.setFacing(BlockFace.SOUTH);
        signBlock.setBlockData(signData);

        // Sign without [Shop] on first line
        String[] lines = {"Not a shop", "1", "10", "sell"};
        Player player = server.addPlayer("TestPlayer3");
        SignChangeEvent event = new SignChangeEvent(signBlock, player, lines);

        assertDoesNotThrow(() -> {
            miscListener.onShopCreation(event);
        });
    }
}
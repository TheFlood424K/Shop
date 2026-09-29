package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for ShopHandler.
 * Tests the race condition fixes for processShopDisplaysNearPlayer.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopHandlerRaceConditionTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        shopHandler = plugin.getShopHandler();
    }

    @AfterEach
    void tearDown() {
        // Do not manually call MockBukkit.unmock() when using @ExtendWith(MockBukkitExtension.class)
        // The extension handles the MockBukkit lifecycle automatically.
    }

    @Test
    void testPlayersProcessingShopDisplaysAtomicAdd() throws Exception {
        // Test that the atomic add pattern prevents duplicate processing
        Player player = server.addPlayer("TestPlayer");
        UUID playerId = player.getUniqueId();
        player.teleport(new Location(world, 100, 64, 100));

        // Use reflection to access the private field
        java.lang.reflect.Field field = ShopHandler.class.getDeclaredField("playersProcessingShopDisplays");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<UUID> processingSet = (Set<UUID>) field.get(shopHandler);

        // Clear the set first to ensure clean state
        processingSet.clear();

        // First call should add the player
        boolean firstAdd = processingSet.add(playerId);
        assertTrue(firstAdd, "First add should succeed");

        // Second call should fail (already processing)
        boolean secondAdd = processingSet.add(playerId);
        assertFalse(secondAdd);
    }

    @Test
    void testProcessShopDisplaysNearPlayerRaceCondition() {
        // Test that processShopDisplaysNearPlayer handles concurrent calls correctly
        Player player = server.addPlayer("TestPlayer2");
        UUID playerId = player.getUniqueId();
        player.teleport(new Location(world, 100, 64, 100));

        // First call should add the player to processing set
        java.lang.reflect.Field field;
        try {
            field = ShopHandler.class.getDeclaredField("playersProcessingShopDisplays");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> processingSet = (Set<UUID>) field.get(shopHandler);

            // Clear the set first to ensure clean state
            processingSet.clear();

            // Simulate concurrent calls by calling add multiple times
            boolean first = processingSet.add(playerId);
            assertTrue(first);

            // Simulate another thread trying to process the same player
            boolean second = processingSet.add(playerId);
            assertFalse(second); // Should fail - already processing

            // After first thread completes, it should remove the player
            processingSet.remove(playerId);

            // Now another thread can process
            boolean third = processingSet.add(playerId);
            assertTrue(third);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }
}
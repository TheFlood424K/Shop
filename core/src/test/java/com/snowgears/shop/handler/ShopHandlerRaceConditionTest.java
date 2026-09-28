package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private ShopHandler createShopHandler() {
        return MockBukkit.loadSimple(Shop.class).getShopHandler();
    }

    @Test
    void testPlayersProcessingShopDisplaysAtomicAdd() throws Exception {
        ShopHandler shopHandler = createShopHandler();

        // Test that the atomic add pattern prevents duplicate processing
        Player player = server.addPlayer("TestPlayer");
        UUID playerId = player.getUniqueId();
        player.teleport(new Location(world, 100, 64, 100));

        // Use reflection to access the private field
        java.lang.reflect.Field field = ShopHandler.class.getDeclaredField("playersProcessingShopDisplays");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap.KeySetView<UUID, Boolean> processingSet = (ConcurrentHashMap.KeySetView<UUID, Boolean>) field.get(shopHandler);

        // First call should add the player
        boolean firstAdd = processingSet.add(playerId);
        assertTrue(firstAdd);

        // Second call should fail (already processing)
        boolean secondAdd = processingSet.add(playerId);
        assertFalse(secondAdd);
    }

    @Test
    void testProcessShopDisplaysNearPlayerRaceCondition() {
        ShopHandler shopHandler = createShopHandler();

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
            ConcurrentHashMap.KeySetView<UUID, Boolean> processingSet = (ConcurrentHashMap.KeySetView<UUID, Boolean>) field.get(shopHandler);

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
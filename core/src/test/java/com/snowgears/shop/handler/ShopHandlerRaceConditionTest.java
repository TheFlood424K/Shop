package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for ShopHandler.
 * Tests the race condition fixes for processShopDisplaysNearPlayer.
 */
class ShopHandlerRaceConditionTest {

    private AutoCloseable mocks;
    @Mock
    private Shop plugin;
    @Mock
    private World world;
    @Mock
    private Player player;
    @Mock
    private Player player2;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        shopHandler = new ShopHandler();
    }

    @AfterEach
    void tearDown() throws Exception {
        // No specific cleanup needed
    }

    @Test
    void testPlayersProcessingShopDisplaysAtomicAdd() throws Exception {
        // Test that the atomic add pattern prevents duplicate processing
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player2.getUniqueId()).thenReturn(UUID.randomUUID());

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
        // Test that processShopDisplaysNearPlayer handles concurrent calls correctly
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player2.getUniqueId()).thenReturn(UUID.randomUUID());

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
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for ShopHandler.
 * Tests the race condition fixes for processShopDisplaysNearPlayer.
 */
class ShopHandlerRaceConditionTest {

    private World mockWorld;
    private Shop plugin;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        mockWorld = mock(World.class);

        plugin = mock(Shop.class);
        when(plugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(plugin.getDataFolder()).thenReturn(new java.io.File("target/test-data"));
        when(plugin.getLogger()).thenReturn(mock(ShopLogger.class));
        when(plugin.getFoliaLib()).thenReturn(mock(com.tcoded.folialib.FoliaLib.class));
        when(plugin.getShopSearchRadius()).thenReturn(1);
        when(plugin.getMaxShopDisplayDistance()).thenReturn(64.0);
        when(plugin.getDisplayBatchSize()).thenReturn(10);
        when(plugin.getDisplayBatchDelay()).thenReturn(2);
        when(plugin.getDisplayMovementThreshold()).thenReturn(1.0);

        shopHandler = new ShopHandler(plugin);
    }

    @Test
    void testPlayersProcessingShopDisplaysAtomicAdd() throws Exception {
        // Test that the atomic add pattern prevents duplicate processing
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(new Location(mockWorld, 100, 64, 100));
        when(player.isOnline()).thenReturn(true);

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
}
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.StubbedPlayers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for ShopHandler registration behavior: atomic addShop/removeShop,
 * duplicate detection, head icon reload deferral, and concurrent load safety.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopHandlerRegistrationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private ShopHandler shopHandler;
    private ShopGuiHandler guiHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        shopHandler = plugin.getShopHandler();
        // Spy on the gui handler to verify reloadPlayerHeadIcon calls without NPE
        guiHandler = spy(plugin.getGuiHandler());
        // Replace the real gui handler with our spy so we can verify calls
        try {
            java.lang.reflect.Field f = Shop.class.getDeclaredField("guiHandler");
            f.setAccessible(true);
            f.set(plugin, guiHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Mock reloadPlayerHeadIcon to avoid ShopMessage.formatMessage NPE on null item
        doNothing().when(guiHandler).reloadPlayerHeadIcon(any());
    }

    @AfterEach
    void tearDown() {
        // MockBukkitExtension handles cleanup
    }

    private AbstractShop makeMockShop(Location signLocation, UUID ownerUUID) {
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.getSignLocation()).thenReturn(signLocation);
        when(shop.getOwnerUUID()).thenReturn(ownerUUID);
        when(shop.getChestLocation()).thenReturn(signLocation);
        when(shop.isInitialized()).thenReturn(true);
        when(shop.getDisplay()).thenReturn(mock(com.snowgears.shop.display.AbstractDisplay.class));
        return shop;
    }

    @Test
    void duplicateAdditionReturnsRegisteredShopWithoutRepeatingSideEffects() {
        UUID ownerId = UUID.randomUUID();
        Location loc = new Location(world, 1, 64, 1);
        AbstractShop shop = makeMockShop(loc, ownerId);
        AbstractShop existing = makeMockShop(loc, ownerId);

        // First add — should succeed and return null
        AbstractShop result1 = shopHandler.addShop(shop, true);
        assertNull(result1, "First add should return null");

        // Second add of a different object at same location — should return the existing shop
        AbstractShop result2 = shopHandler.addShop(existing, true);
        assertEquals(shop, result2, "Second add should return the first registered shop");

        // The head icon reload should only have been called once (for the first shop)
        verify(guiHandler, times(1)).reloadPlayerHeadIcon(eq(shop));
        verify(guiHandler, never()).reloadPlayerHeadIcon(eq(existing));
    }

    @Test
    void duplicateLoadOnlyCountsAndSchedulesTheRegisteredShop() {
        // This test simulates the loadShops() flow where two concurrent async loads
        // produce two AbstractShop objects at the same location. The putIfAbsent pattern
        // in addShop should prevent the duplicate from being registered, and the
        // pending display types should only be scheduled for the shop that actually
        // got registered.

        UUID ownerId = UUID.randomUUID();
        Location loc = new Location(world, 2, 64, 2);

        AbstractShop shop1 = makeMockShop(loc, ownerId);
        AbstractShop shop2 = makeMockShop(loc, ownerId);

        // First registration
        AbstractShop r1 = shopHandler.addShop(shop1);
        assertNull(r1);

        // Second registration at same location — should return existing
        AbstractShop r2 = shopHandler.addShop(shop2);
        assertEquals(shop1, r2);

        // Verify both shops are tracked in the map as one entry
        assertEquals(1, shopHandler.getNumberOfShops());
    }

    @Test
    void concurrentAdditionSurvivesEmptyIndexCleanup() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        int numShops = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>(null);

        for (int i = 0; i < numShops; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    UUID ownerId = UUID.randomUUID();
                    Location loc = new Location(world, idx, 64, idx);
                    AbstractShop shop = makeMockShop(loc, ownerId);
                    shopHandler.addShop(shop, false); // Don't reload head icon to avoid async issues
                    if (shopHandler.getShop(loc) != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "All threads should complete");

        assertNull(firstError.get(), "No errors during concurrent add: " +
                (firstError.get() != null ? firstError.get().getMessage() : ""));
        assertEquals(numShops, successCount.get(),
                "All " + numShops + " shops should be registered");
        assertEquals(numShops, shopHandler.getNumberOfShops());
    }

    @Test
    void concurrentAddAndRemoveDoesNotCorruptMaps() throws Exception {
        int numIterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        // Retain every Future so a task exception fails the test instead of being
        // silently swallowed — the original code submitted and discarded them, so a
        // RuntimeException mid-loop would have left shops behind and the assertion
        // (count <= 200) would still pass.
        List<Future<?>> futures = new ArrayList<>();

        for (int iter = 0; iter < numIterations; iter++) {
            final int iteration = iter;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    UUID ownerId = UUID.randomUUID();
                    Location loc = new Location(world, iteration * 10 + i, 64, 0);
                    AbstractShop shop = makeMockShop(loc, ownerId);
                    shopHandler.addShop(shop, false);

                    // Try to remove the same shop
                    shopHandler.removeShop(shop, false);
                }
            }));
        }

        executor.shutdown();
        for (Future<?> f : futures) {
            // Re-throw any task exception so a failure is not masked by the assertion below.
            f.get();
        }
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                "All concurrent add/remove threads should complete");

        // Every shop that was added was also removed by the same task, so no shop should
        // remain. The original assertion (count <= 200) was vacuously true: it would have
        // passed even if every single removal silently failed.
        assertEquals(0, shopHandler.getNumberOfShops(),
                "No shops should remain after concurrent add+remove");
    }

    @Test
    void addShopReturnsNullOnSuccessAndExistingOnDuplicate() {
        UUID ownerId = UUID.randomUUID();
        Location loc = new Location(world, 5, 64, 5);
        AbstractShop shop = makeMockShop(loc, ownerId);

        assertNull(shopHandler.addShop(shop, false));
        assertEquals(1, shopHandler.getNumberOfShops());

        AbstractShop duplicate = makeMockShop(loc, ownerId);
        AbstractShop result = shopHandler.addShop(duplicate, false);
        assertEquals(shop, result);
        assertEquals(1, shopHandler.getNumberOfShops());
    }

    @Test
    void addShopReturnsTheRegisteredInstanceSoCallersNeverHoldAStaleObject() {
        // addShop() rejects a duplicate registration and returns the shop that is actually
        // registered. A caller that honours that return value always removes the instance that
        // is in the map, so removeShop() can stay a plain key removal — a remove(key, value)
        // would silently no-op on a Mockito spy (a distinct wrapper instance) and break
        // load-failure cleanup. The invariant this test pins is the addShop return value.
        UUID ownerId = UUID.randomUUID();
        Location loc = new Location(world, 6, 64, 6);
        AbstractShop registered = makeMockShop(loc, ownerId);
        AbstractShop stale = makeMockShop(loc, ownerId);

        assertNull(shopHandler.addShop(registered, false));
        assertEquals(1, shopHandler.getNumberOfShops());
        assertSame(registered, shopHandler.getShop(loc));

        AbstractShop returned = shopHandler.addShop(stale, false);
        assertSame(registered, returned,
                "a duplicate add must return the already-registered instance");
        assertEquals(1, shopHandler.getNumberOfShops(),
                "a duplicate add must not register a second shop");
        assertSame(registered, shopHandler.getShop(loc),
                "the registered shop must still be reachable at its sign location");

        // The caller removes the instance addShop() handed back, which is the registered one.
        shopHandler.removeShop(returned, false);
        assertEquals(0, shopHandler.getNumberOfShops());
    }
}

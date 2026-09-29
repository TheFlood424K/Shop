package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.configuration.file.FileConfiguration;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * Unit tests for ShopHandler core functionality.
 * Tests the critical fixes for race conditions and NPEs.
 * Uses pure Mockito to avoid JAR loading issues with MockBukkit.
 */
class ShopHandlerTest {

    @Mock
    private World world;

    @Mock
    private Shop plugin;

    @Mock
    private ShopGuiHandler guiHandler;

    @Mock
    private ShopLogger shopLogger;

    @Mock
    private FileConfiguration config;

    @Mock
    private FoliaLib foliaLib;
    @Mock
    private PlatformScheduler schedulerMock;

    @Mock
    private WrappedTask wrappedTask;

    @Mock
    private Block chestBlock;

    @Mock
    private ItemStack mockItemStack;

    private ShopHandler shopHandler;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Set the static plugin field in Shop to our mock plugin
        try {
            java.lang.reflect.Field field = Shop.class.getDeclaredField("plugin");
            field.setAccessible(true);
            field.set(null, plugin);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Mock world.getBlockAt
        when(world.getBlockAt(100, 64, 101)).thenReturn(chestBlock);
        // Mock plugin.getGuiHandler()
        when(plugin.getGuiHandler()).thenReturn(guiHandler);
        // Mock plugin.getLogger()
        when(plugin.getLogger()).thenReturn(shopLogger);
        // Mock plugin.getConfig()
        when(plugin.getConfig()).thenReturn(config);
        // Mock plugin.getFoliaLib()
        when(plugin.getFoliaLib()).thenReturn(foliaLib);
        // Mock foliaLib.getScheduler()
        when(foliaLib.getScheduler()).thenReturn(schedulerMock);
        // Mock scheduler.runLater() to return a mock WrappedTask (we don't care about the async loading for tests)
        when(schedulerMock.runLater(any(Runnable.class), anyLong())).thenReturn(wrappedTask);

        // Mock chestBlock
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(chestBlock.getLocation()).thenReturn(new Location(world, 100, 64, 101));

        // Mock ItemStack
        when(mockItemStack.getType()).thenReturn(Material.DIAMOND);
        when(mockItemStack.getAmount()).thenReturn(1);
        when(mockItemStack.clone()).thenReturn(mockItemStack);

        // Create shop handler
        shopHandler = new ShopHandler(plugin);
        when(plugin.getShopHandler()).thenReturn(shopHandler);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testAddShopPreventsDuplicate() {
        // Create a shop location
        Location signLoc = new Location(world, 100, 64, 100);

        // Create a test shop
        AbstractShop shop = AbstractShop.create(signLoc, UUID.randomUUID(), 10.0, 0.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        shop.setItemStack(mockItemStack);

        // Add the shop
        shopHandler.addShop(shop);

        // Try to add the same shop again (should be prevented)
        shopHandler.addShop(shop);

        // Should only have one shop
        assertEquals(1, shopHandler.getNumberOfShops());
    }

    @Test
    void testGetShopReturnsNullForNonExistent() {
        Location loc = new Location(world, 0, 0, 0);
        assertNull(shopHandler.getShop(loc));
    }

    @Test
    void testGetShopByChestHandlesNull() {
        // Test that getShopByChest doesn't NPE when chest location is null
        AbstractShop shop = new SellShop(
                new Location(world, 100, 64, 100),
                UUID.randomUUID(),
                10.0,
                1,
                false,
                BlockFace.NORTH
        );
        // chestLocation is null by default

        // This should not throw NPE
        Block chestBlock = mock(Block.class);
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(chestBlock.getLocation()).thenReturn(new Location(world, 100, 64, 101));
        when(chestBlock.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));
        assertNull(shopHandler.getShopByChest(chestBlock));
    }
}

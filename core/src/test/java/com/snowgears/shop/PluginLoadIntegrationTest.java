package com.snowgears.shop;

import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.display.Display;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.handler.*;
import com.snowgears.shop.listener.*;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for plugin loading and initialization.
 * Verifies that all components initialize correctly when the plugin enables.
 */
@ExtendWith(MockBukkitExtension.class)
class PluginLoadIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        // Extension handles cleanup
    }

    @Test
    void testPluginLoadsSuccessfully() {
        // Basic sanity check - plugin should load without throwing
        assertNotNull(plugin);
        assertEquals("Shop", plugin.getName());
    }

    @Test
    void testPluginVersion() {
        assertNotNull(plugin.getDescription().getVersion());
        assertFalse(plugin.getDescription().getVersion().isEmpty());
    }

    @Test
    void testPluginMainClass() {
        assertEquals("com.snowgears.shop.Shop", plugin.getDescription().getMain());
    }

    @Test
    void testPluginGetters() {
        // Test that all core handlers are initialized and accessible
        assertNotNull(plugin.getShopHandler(), "ShopHandler should be initialized");
        assertNotNull(plugin.getTransactionHelper(), "TransactionHandler should be initialized");
        assertNotNull(plugin.getShopListener(), "ShopListener should be initialized");
        assertNotNull(plugin.getGuiHandler(), "ShopGuiHandler should be initialized");
        assertNotNull(plugin.getLogHandler(), "LogHandler should be initialized");
        assertNotNull(plugin.getCreativeSelectionListener(), "CreativeSelectionListener should be initialized");
        assertNotNull(plugin.getDisplayListener(), "DisplayListener should be initialized");
        assertNotNull(plugin.getMiscListener(), "MiscListener should be initialized");
        assertNotNull(plugin.getFoliaLib(), "FoliaLib should be initialized");
    }

    @Test
    void testConfigLoaded() {
        assertNotNull(plugin.getConfig(), "Config should be loaded");
        // Verify key config sections exist
        assertNotNull(plugin.getConfig().get("currency"));
        assertNotNull(plugin.getConfig().get("displayType"));
        assertNotNull(plugin.getConfig().get("currency.type"));
    }

    @Test
    void testPluginEnableDisable() {
        // Test that plugin can be disabled and re-enabled
        assertTrue(plugin.isEnabled());
        MockBukkit.unmock();
        assertFalse(plugin.isEnabled());
    }

    @Test
    void testShopHandlerFunctionality() {
        ShopHandler shopHandler = plugin.getShopHandler();

        // Test basic shop operations
        assertEquals(0, shopHandler.getNumberOfShops());

        // Create a test shop
        Location signLoc = new Location(world, 100, 64, 100);
        Player player = server.addPlayer("TestPlayer");
        UUID owner = player.getUniqueId();

        // Use AbstractShop factory to create shop
        AbstractShop shop = AbstractShop.create(signLoc, owner, 10.0, 5.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);
        assertEquals(1, shopHandler.getNumberOfShops());
        assertEquals(1, shopHandler.getShops(owner).size());

        // Test lookup
        AbstractShop found = shopHandler.getShop(signLoc);
        assertNotNull(found);
        assertEquals(shop, found);

        // Test removal
        shopHandler.removeShop(shop, false);
        assertEquals(0, shopHandler.getNumberOfShops());
    }

    @Test
    void testTransactionHandlerFunctionality() {
        TransactionHandler transactionHandler = plugin.getTransactionHelper();
        assertNotNull(transactionHandler);

        // Test basic transaction setup
        Location signLoc = new Location(world, 100, 64, 100);
        Player player = server.addPlayer("Buyer");
        UUID owner = UUID.randomUUID();

        AbstractShop shop = AbstractShop.create(signLoc, owner, 10.0, 5.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Verify transaction helper can be accessed
        assertNotNull(transactionHandler);
    }

    @Test
    void testDisplaySystemInitialization() {
        // Test display creation
        AbstractDisplay display = plugin.getShopHandler().createDisplay(new Location(world, 100, 64, 100));
        assertNotNull(display);

        // Test display type enum
        assertNotNull(DisplayType.ITEM);
        assertNotNull(DisplayType.LARGE_ITEM);
        assertNotNull(DisplayType.GLASS_CASE);
        assertNotNull(DisplayType.ITEM_FRAME);
        assertNotNull(DisplayType.NONE);
    }

    @Test
    void testHookDetection() {
        // Test that hook detection works (should not throw even if hooks not present)
        assertDoesNotThrow(() -> plugin.isGriefPreventionTrustIntegrationEnabled());
        assertDoesNotThrow(() -> plugin.isWorldGuardIntegrationEnabled());
        assertDoesNotThrow(() -> plugin.isLwcIntegrationEnabled());
        assertDoesNotThrow(() -> plugin.isBentoBoxIntegrationEnabled());
        assertDoesNotThrow(() -> plugin.isAdvancedRegionMarketIntegrationEnabled());
        assertDoesNotThrow(() -> plugin.isPlotSquaredIntegrationEnabled());
    }

    @Test
    void testLoggerInitialized() {
        assertNotNull(plugin.getLogger());
        assertDoesNotThrow(() -> plugin.getLogger().info("Test log message"));
        assertDoesNotThrow(() -> plugin.getLogger().warning("Test warning"));
        assertDoesNotThrow(() -> plugin.getLogger().severe("Test error"));
    }

    @Test
    void testFoliaLibIntegration() {
        assertNotNull(plugin.getFoliaLib());
        // Test that scheduler works
        assertDoesNotThrow(() -> plugin.getFoliaLib().getScheduler().runLater(() -> {}, 1L));
    }

    @Test
    void testCommandAlias() {
        String alias = plugin.getCommandAlias();
        assertNotNull(alias);
        assertFalse(alias.isEmpty());
        assertEquals("shop", alias);
    }

    @Test
    void testCurrencySettings() {
        assertNotNull(plugin.getCurrencyType());
        assertNotNull(plugin.getItemCurrency());
        assertTrue(plugin.getCurrencyType() == com.snowgears.shop.util.CurrencyType.ITEM ||
                    plugin.getCurrencyType() == com.snowgears.shop.util.CurrencyType.VAULT ||
                    plugin.getCurrencyType() == com.snowgears.shop.util.CurrencyType.EXPERIENCE);
    }

    @Test
    void testDisplaySettings() {
        assertNotNull(plugin.getDisplayType());
        assertNotNull(plugin.getDisplayTagOption());
        assertNotNull(plugin.getDisplayCycle());
        assertTrue(plugin.getDisplayCycle().length > 0);
    }

    @Test
    void testDatabaseLoggingConfig() {
        // Check that logging config is accessible
        assertNotNull(plugin.getConfig().get("logging"));
        assertTrue(plugin.offlinePurchaseNotificationsEnabled());
    }

    @Test
    void testWorldGuardConfig() {
        // WorldGuard config is only loaded if WorldGuard plugin is installed
        // In test environment, WorldGuard is not present, so config may be null
        // This test verifies the method exists and handles null gracefully
        assertDoesNotThrow(() -> plugin.getWorldGuardConfig());
        // When WorldGuard is not installed, config may be null
        // We just verify the method exists and doesn't throw
    }

    @Test
    void testConfigDefaultValues() {
        // Verify config has sensible defaults
        assertTrue(plugin.getAllowCreationMethodSign());
        assertTrue(plugin.getAllowCreationMethodChest());
        assertTrue(plugin.checkItemDurability()); // defaults to true in config.yml
        assertTrue(plugin.ignoreItemRepairCost());
        assertTrue(plugin.getAllowPartialSales());
        assertEquals(0, plugin.getCreationCost());
    }
}
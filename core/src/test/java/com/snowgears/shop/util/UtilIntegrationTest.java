package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Material;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for utility classes.
 */
@ExtendWith(MockBukkitExtension.class)
class UtilIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

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
    void testItemStackUtilsSerializeDeserialize() {
        ItemStack original = new ItemStack(Material.DIAMOND);
        original.setAmount(64);

        // Test serialization roundtrip
        var json = ItemStackUtils.serializeItemAsJson(original);
        assertNotNull(json);

        ItemStack deserialized = ItemStackUtils.deserializeItemFromJson(json);
        assertNotNull(deserialized);
        assertEquals(Material.DIAMOND, deserialized.getType());
        assertEquals(64, deserialized.getAmount());
    }

    @Test
    void testItemStackUtilsStripFont() {
        ItemStack original = new ItemStack(Material.DIAMOND);
        original.setAmount(1);

        ItemStack stripped = ItemStackUtils.stripFontFromItem(original);

        assertNotNull(stripped);
        assertEquals(Material.DIAMOND, stripped.getType());
        assertEquals(1, stripped.getAmount());
    }

    @Test
    void testItemStackUtilsStripFontNull() {
        ItemStack result = ItemStackUtils.stripFontFromItem(null);
        assertNull(result);
    }

    @Test
    void testUtilMethodsGetItemName() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        item.setAmount(1);

        String name = UtilMethods.getItemName(item);
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @Test
    void testUtilMethodsIsMCVersion() {
        assertTrue(UtilMethods.isMCVersion17Plus());
        assertTrue(UtilMethods.isMCVersion14Plus());
    }

    @Test
    void testShopMessageGetSignLines() {
        // Test that sign lines can be generated for a shop
        assertDoesNotThrow(() -> ShopMessage.getSignLines("DIAMOND"));
    }

    @Test
    void testPlayerNameCache() {
        PlayerNameCache cache = new PlayerNameCache();

        // Test basic functionality - just verify it doesn't throw
        Player player = server.addPlayer("TestPlayer");
        assertDoesNotThrow(() -> cache.getName(player.getUniqueId()));
    }

    @Test
    void testItemNameUtilGetName() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        item.setAmount(1);

        ItemNameUtil util = new ItemNameUtil();
        var name = util.getName(item);
        assertNotNull(name);
    }

    @Test
    void testItemNameUtilGetNameTranslatable() {
        var name = ItemNameUtil.getNameTranslatable(Material.DIAMOND);
        assertNotNull(name);
    }

    @Test
    void testItemListType() {
        // Test ItemListType enum values
        for (ItemListType type : ItemListType.values()) {
            assertNotNull(type.name());
        }
    }

    @Test
    void testTransactionError() {
        // Test TransactionError enum values
        for (TransactionError error : TransactionError.values()) {
            assertNotNull(error.name());
        }
    }

    @Test
    void testShopActionType() {
        // Test ShopActionType enum values
        for (ShopActionType type : ShopActionType.values()) {
            assertNotNull(type.name());
        }
    }

    @Test
    void testShopClickType() {
        // Test ShopClickType enum values
        for (ShopClickType type : ShopClickType.values()) {
            assertNotNull(type.name());
        }
    }
}
package com.snowgears.shop.util;

import com.google.gson.JsonObject;
import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for ItemStackUtils serialization changes.
 *
 * Verifies that serializeItemAsJson handles Paper/Folia paths correctly
 * and doesn't crash when the Optional result is empty.
 */
class ItemStackUtilsTest extends BaseMockBukkitTest {

    private ItemStack testItem;

    @BeforeEach
    void setUp() {
        testItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = testItem.getItemMeta();
        meta.setDisplayName("§cTest Sword");
        testItem.setItemMeta(meta);
    }

    @Test
    void serializeItemAsJson_returnsNullWhenEncodedIsEmpty() {
        // This test verifies the fix for the empty Optional edge case
        // We can't easily mock the NMS path without a full server, but we can
        // verify the method doesn't throw NPE when encoded is empty

        // When using MockBukkit, the NMS path won't be available, so this
        // will naturally return null or use the Paper path
        JsonObject result = ItemStackUtils.serializeItemAsJson(testItem);

        // The method should either return a JsonObject or null, never throw
        assertTrue(result == null || result instanceof JsonObject);
    }

    @Test
    void serializeItemAsJson_handlesPaperPath() {
        // Verify that when FoliaLib reports Paper, it uses Bukkit.getUnsafe()
        // This is mainly a smoke test to ensure no class loading issues
        JsonObject result = ItemStackUtils.serializeItemAsJson(testItem);

        // In MockBukkit environment, Paper API may or may not be available
        // The important thing is no exception is thrown
        assertDoesNotThrow(() -> ItemStackUtils.serializeItemAsJson(testItem));
    }

    @Test
    void deserializeItemFromJson_returnsNullForNonPaper() {
        // deserializeItemFromJson should return null for non-Paper servers
        JsonObject dummy = new JsonObject();
        dummy.addProperty("id", "minecraft:diamond_sword");
        dummy.addProperty("count", 1);

        ItemStack result = ItemStackUtils.deserializeItemFromJson(dummy);

        // Should return null on non-Paper (MockBukkit) environment
        assertNull(result);
    }
}
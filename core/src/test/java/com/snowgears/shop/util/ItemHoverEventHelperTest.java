package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for ItemHoverEventHelper changes.
 *
 * Verifies that createFrom correctly creates hover events and
 * falls back to legacy path when modern components are unavailable.
 */
class ItemHoverEventHelperTest extends BaseMockBukkitTest {

    private ItemStack testItem;

    @BeforeEach
    void setUp() {
        testItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = testItem.getItemMeta();
        meta.setDisplayName("§cTest Sword");
        testItem.setItemMeta(meta);
    }

    @Test
    void createFrom_returnsHoverEvent() {
        // Should return a valid HoverEvent, never null
        HoverEvent hover = ItemHoverEventHelper.createFrom(testItem);

        assertNotNull(hover, "HoverEvent should not be null");
        assertEquals(HoverEvent.Action.SHOW_ITEM, hover.getAction(), "Action should be SHOW_ITEM");
    }

    @Test
    void createFromLegacy_returnsHoverEvent() {
        // Legacy path should also work
        HoverEvent hover = ItemHoverEventHelper.createFromLegacy(testItem);

        assertNotNull(hover, "Legacy HoverEvent should not be null");
        assertEquals(HoverEvent.Action.SHOW_ITEM, hover.getAction(), "Action should be SHOW_ITEM");
    }

    @Test
    void createFrom_handlesNullItemMeta() {
        // Item with no meta should still work
        ItemStack noMetaItem = new ItemStack(Material.DIRT);

        HoverEvent hover = ItemHoverEventHelper.createFrom(noMetaItem);

        assertNotNull(hover, "HoverEvent should not be null even with no meta");
        assertEquals(HoverEvent.Action.SHOW_ITEM, hover.getAction());
    }

    @Test
    void createFrom_handlesAirItem() {
        // Air item should be handled gracefully
        ItemStack airItem = new ItemStack(Material.AIR);

        HoverEvent hover = ItemHoverEventHelper.createFrom(airItem);

        assertNotNull(hover, "HoverEvent should not be null for AIR");
        assertEquals(HoverEvent.Action.SHOW_ITEM, hover.getAction());
    }
}
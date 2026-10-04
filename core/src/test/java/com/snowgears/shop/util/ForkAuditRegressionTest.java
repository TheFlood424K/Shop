package com.snowgears.shop.util;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import org.mockbukkit.mockbukkit.MockBukkitExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for defects found while auditing the SnowGears/Shop forks. Each test pins a bug that
 * was live in this tree; see IMPLEMENTATION_PLAN.md for the originating fork and commit.
 */
@Tag("unit")
@ExtendWith(MockBukkitExtension.class)
public class ForkAuditRegressionTest {

    /**
     * AlexanderYW/Shop 49eb321 — every case fell through, so NORTH applied all four offsets
     * cumulatively and no two faces produced symmetric results.
     */
    @Test
    void pushLocationInDirection_appliesExactlyOneOffset() {
        Location origin = new Location(null, 0, 64, 0);
        double add = 2.0;

        Location north = UtilMethods.pushLocationInDirection(origin.clone(), BlockFace.NORTH, add);
        assertEquals(-add, north.getX(), "NORTH x offset");
        assertEquals(-add, north.getZ(), "NORTH z offset");

        Location south = UtilMethods.pushLocationInDirection(origin.clone(), BlockFace.SOUTH, add);
        assertEquals(add, south.getX(), "SOUTH x offset");
        assertEquals(add, south.getZ(), "SOUTH z offset");

        Location east = UtilMethods.pushLocationInDirection(origin.clone(), BlockFace.EAST, add);
        assertEquals(add, east.getX(), "EAST x offset");
        assertEquals(-add, east.getZ(), "EAST z offset");

        Location west = UtilMethods.pushLocationInDirection(origin.clone(), BlockFace.WEST, add);
        assertEquals(-add, west.getX(), "WEST x offset");
        assertEquals(0.0, west.getZ(), "WEST z offset");

        // North and south must mirror each other.
        assertEquals(north.getX(), -south.getX(), "NORTH/SOUTH must be mirrored on x");
        assertEquals(north.getZ(), -south.getZ(), "NORTH/SOUTH must be mirrored on z");
    }

    /**
     * Snewmy/Shop 954d3f5 — List.toString() rendered multi-line lore as "[a, b]".
     */
    @Test
    void getLoreString_joinsLinesWithNewline() {
        assertEquals("", UtilMethods.getLoreString(null), "null item");

        ItemStack plain = new ItemStack(Material.DIRT);
        assertEquals("", UtilMethods.getLoreString(plain), "item with no lore");

        ItemStack lore = new ItemStack(Material.DIRT);
        ItemMeta meta = lore.getItemMeta();
        meta.setLore(List.of("first line", "second line"));
        lore.setItemMeta(meta);

        String rendered = UtilMethods.getLoreString(lore);
        assertEquals("first line\nsecond line", rendered,
                "multi-line lore must be newline-joined, not List.toString()");
        assertTrue(!rendered.startsWith("["), "must not render as a Java List");
    }

    /**
     * AlexanderYW 9fb5611 — removeItem dereferenced itemStack two lines before the null check that
     * was meant to catch it.
     */
    @Test
    void removeItem_toleratesNullItemStack() {
        assertEquals(0, InventoryUtils.removeItem(null, null));
    }
}
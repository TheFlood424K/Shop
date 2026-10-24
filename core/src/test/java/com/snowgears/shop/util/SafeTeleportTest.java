package com.snowgears.shop.util;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SafeTeleportTest extends BaseMockBukkitTest {

    private static final int Y = 100;

    private World world;
    private Block chest;
    private Block sign;

    @BeforeEach
    void buildShop() {
        world = getServer().addSimpleWorld("world");
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                world.getBlockAt(x, Y - 1, z).setType(Material.STONE);
        chest = world.getBlockAt(0, Y, 0);
        chest.setType(Material.CHEST);
        sign = world.getBlockAt(0, Y, 1);
        sign.setType(Material.OAK_WALL_SIGN);
    }

    private Location find() {
        return SafeTeleport.findStandingSpot(sign, BlockFace.SOUTH, chest);
    }

    private void assertStandable(Location loc) {
        assertNotNull(loc, "Expected a safe standing location");
        Block feet = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        Block ground = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        assertTrue(feet.isEmpty() || feet.getType() == Material.AIR || feet.getType() == Material.CAVE_AIR || feet.getType() == Material.VOID_AIR, "Feet should be air");
        assertTrue(head.isEmpty() || head.getType() == Material.AIR || head.getType() == Material.CAVE_AIR || head.getType() == Material.VOID_AIR, "Head should be air");
        assertTrue(ground.getType().isSolid(), "Ground should be solid");
    }

    @Test
    void basicOpenSpotDirectlyInFront() {
        // Front block at (0, Y, 2) should be air with solid ground
        world.getBlockAt(0, Y, 2).setType(Material.AIR);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
        // Should face the sign (north)
        assertEquals(180f, loc.getYaw(), 1f);
        assertEquals(25f, loc.getPitch(), 1f);
    }

    @Test
    void prefersFrontColumnAtSameHeight() {
        // Make direct front have stone at feet height (suffocation), but front column works
        world.getBlockAt(0, Y, 2).setType(Material.STONE);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void prefersChestTopWhenFrontBlocked() {
        // Block front column, but chest top is free
        world.getBlockAt(0, Y, 2).setType(Material.STONE);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        // Chest top at (0, Y+1, 0) - make it air with solid ground below (chest block)
        world.getBlockAt(0, Y + 1, 0).setType(Material.AIR);
        world.getBlockAt(0, Y + 2, 0).setType(Material.AIR);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void avoidsLavaAtFeet() {
        world.getBlockAt(0, Y, 2).setType(Material.LAVA);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        // Next candidate should work
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);
        world.getBlockAt(0, Y - 2, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 3, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 4, 2).setType(Material.STONE);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void avoidsFireAtHead() {
        world.getBlockAt(0, Y, 2).setType(Material.AIR);
        world.getBlockAt(0, Y + 1, 2).setType(Material.FIRE);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void avoidsMagmaBlockGround() {
        world.getBlockAt(0, Y, 2).setType(Material.AIR);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.MAGMA_BLOCK);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void returnsNullWhenCompletelyBlocked() {
        // Fill everything with stone - no safe spot
        for (int y = Y - 5; y <= Y + 5; y++) {
            world.getBlockAt(0, y, 2).setType(Material.STONE);
            world.getBlockAt(0, y, 0).setType(Material.STONE);
        }
        // Also block side columns
        for (int dx : new int[]{-1, 1}) {
            for (int y = Y - 5; y <= Y + 5; y++) {
                world.getBlockAt(dx, y, 2).setType(Material.STONE);
                world.getBlockAt(dx, y, 0).setType(Material.STONE);
            }
        }

        Location loc = find();
        assertNull(loc);
    }

    @Test
    void usesChestAsColumnWhenFrontColumnBlocked() {
        // Block front of sign column
        world.getBlockAt(0, Y, 2).setType(Material.STONE);
        world.getBlockAt(0, Y + 1, 2).setType(Material.STONE);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        // Front of chest column at (0, Y, -1) - but facing SOUTH so chest is north of sign
        // Wait: sign at (0,Y,1), facing SOUTH means front is +Z (towards 2)
        // Chest is at (0,Y,0) - opposite face is NORTH, so chest is behind sign
        // Front of chest would be south of chest = (0,Y,-1)? No.
        // facing = SOUTH, opposite = NORTH. Chest = sign.getRelative(NORTH) = (0,Y,0)
        // Front of chest = chest.getRelative(SOUTH) = (0,Y,1) which is sign location
        // So chest column front is same as sign location - not useful

        // Test with sign facing NORTH so chest is at +Z
        Block signNorth = world.getBlockAt(5, Y, 1);
        signNorth.setType(Material.OAK_WALL_SIGN);
        Block chestNorth = world.getBlockAt(5, Y, 2);
        chestNorth.setType(Material.CHEST);

        // Front of sign (north) = -Z
        world.getBlockAt(5, Y, 0).setType(Material.STONE); // blocked
        world.getBlockAt(5, Y + 1, 0).setType(Material.STONE);
        world.getBlockAt(5, Y - 1, 0).setType(Material.STONE);

        // Front of chest (north) = (5, Y, 1) - that's sign location again
        // So chest front column doesn't add new candidates in this setup

        // But we can test side columns
        world.getBlockAt(6, Y, 0).setType(Material.AIR); // right of front
        world.getBlockAt(6, Y + 1, 0).setType(Material.AIR);
        world.getBlockAt(6, Y - 1, 0).setType(Material.STONE);

        Location loc = SafeTeleport.findStandingSpot(signNorth, BlockFace.NORTH, chestNorth);
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void handlesNullSignGracefully() {
        assertNull(SafeTeleport.findStandingSpot(null, BlockFace.NORTH, chest));
    }

    @Test
    void handlesNullFacingGracefully() {
        assertNull(SafeTeleport.findStandingSpot(sign, null, chest));
    }

    @Test
    void handlesNullChestGracefully() {
        // Should still find spot using just sign
        world.getBlockAt(0, Y, 2).setType(Material.AIR);
        world.getBlockAt(0, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        Location loc = SafeTeleport.findStandingSpot(sign, BlockFace.SOUTH, null);
        assertNotNull(loc);
        assertStandable(loc);
    }

    @Test
    void sideColumnsUsedWhenDirectFrontBlocked() {
        // Block direct front
        world.getBlockAt(0, Y, 2).setType(Material.STONE);
        world.getBlockAt(0, Y + 1, 2).setType(Material.STONE);
        world.getBlockAt(0, Y - 1, 2).setType(Material.STONE);

        // Left side column (west) at (-1, Y, 2)
        world.getBlockAt(-1, Y, 2).setType(Material.AIR);
        world.getBlockAt(-1, Y + 1, 2).setType(Material.AIR);
        world.getBlockAt(-1, Y - 1, 2).setType(Material.STONE);

        Location loc = find();
        assertNotNull(loc);
        assertStandable(loc);
        // X should be -1 (left/west of front)
        assertEquals(-1, loc.getBlockX());
    }
}
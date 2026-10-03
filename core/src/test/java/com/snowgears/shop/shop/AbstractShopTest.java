package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AbstractShop core functionality.
 * Tests the critical fixes for NPEs, race conditions, and logic bugs.
 */
@Tag("unit")
class AbstractShopTest extends BaseMockBukkitTest {

    private ShopHandler shopHandler;
    private WorldMock world;

    @Override
    @BeforeEach
    public void initServer() {
        super.initServer();
        shopHandler = getPlugin().getShopHandler();
        world = getServer().addSimpleWorld("world");
    }

    private Location loc(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    @Test
    void testIsInitializedReturnsFalseWhenItemNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop without item should not be initialized
        assertFalse(shop.isInitialized());
    }

    @Test
    void testIsInitializedReturnsTrueWhenItemSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting item should mark as initialized
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        assertTrue(shop.isInitialized());
    }

    @Test
    void testGetItemStackReturnsNullWhenNotSet() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        assertNull(shop.getItemStack());
    }

    @Test
    void testGetItemStackReturnsClone() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        ItemStack original = new ItemStack(Material.DIAMOND);
        original.setAmount(64);
        shop.setItemStack(original);

        ItemStack returned = shop.getItemStack();
        assertNotNull(returned);
        assertNotSame(original, returned); // Should be a clone
        assertEquals(Material.DIAMOND, returned.getType());
        assertEquals(1, returned.getAmount()); // Amount should be set to 1
    }

    @Test
    void testCalculateStockReturnsUnavailableWhenUninitialized() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Uninitialized shop should return STOCK_UNAVAILABLE
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testCalculateStockReturnsUnavailableWhenInventoryNull() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        shop.setItemStack(new ItemStack(Material.DIAMOND));
        // chestLocation is null, so getInventory() returns null
        assertEquals(AbstractShop.STOCK_UNAVAILABLE, shop.calculateStock());
    }

    @Test
    void testUpdateStockSkipsWhenUnavailable() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // updateStock() should not throw NPE when stock is unavailable
        // This tests the fix for Bug 4
        assertDoesNotThrow(() -> shop.updateStock());
    }

    @Test
    void testSetItemStackNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // Setting null item should not throw
        assertDoesNotThrow(() -> shop.setItemStack(null));
        assertFalse(shop.isInitialized());
    }

    @Test
    void testGetChestLocationNullSafe() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);

        // New shop has null chestLocation until load() is called
        assertNull(shop.getChestLocation());
    }

    @Test
    void testDeleteHandlesNullDisplay() {
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 100);

        // delete() should not NPE if display is somehow null
        SellShop shop2 = new SellShop(
            loc(200, 64, 200),
            ownerUUID,
            10.0,
            1,
            false,
            BlockFace.NORTH
        );
        shop2.setItemStack(new ItemStack(Material.DIAMOND));
        // Use reflection to set display to null
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("display");
            field.setAccessible(true);
            field.set(shop2, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        assertDoesNotThrow(() -> shop2.delete());
    }

    @Test
    void testLoadWallSignResolvesChestCorrectly() {
        // Test that load() correctly resolves chest location from wall sign facing
        // Sign on WEST face of chest (at x=100) -> sign at x=99 facing EAST (text faces chest)
        // Chest is at sign.getRelative(EAST.getOppositeFace()) = sign.getRelative(WEST) = x=98
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(99, 64, 100);
        Location chestLocation = loc(98, 64, 100);

        // Place wall sign at signLocation
        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.EAST); // Sign attached to west face of chest, faces EAST (text faces chest)
        signBlock.setBlockData(wallSignData);
        world.setBlockData(signLocation, wallSignData); // Persist

        // Debug: verify sign facing after persistence
        WallSign persistedSignData = (WallSign) world.getBlockAt(signLocation).getBlockData();
        System.out.println("DEBUG testLoadWallSignResolvesChestCorrectly: sign facing after persist = " + persistedSignData.getFacing());

        // Place chest at chestLocation (WEST of sign, opposite of EAST facing)
        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);
        world.setBlockData(chestLocation, chestBlock.getBlockData()); // Persist

        // Create shop with sign location and EAST facing (matching the sign's facing)
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.EAST);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Load should read the sign's facing and resolve chest correctly
        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing (opposite face)");
    }

    @Test
    void testLoadWallSignOppositeFace() {
        // Sign on EAST face of chest (at x=100) -> sign at x=101 facing WEST (text faces chest)
        // Chest is at sign.getRelative(WEST.getOppositeFace()) = sign.getRelative(EAST) = x=102
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(101, 64, 100);
        Location chestLocation = loc(102, 64, 100);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.WEST); // Sign attached to east face of chest, faces WEST (text faces chest)
        signBlock.setBlockData(wallSignData);
        world.setBlockData(signLocation, wallSignData); // Persist

        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);
        world.setBlockData(chestLocation, chestBlock.getBlockData()); // Persist

        // Create shop with WEST facing (matching the sign's facing)
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.WEST);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing (opposite face)");
    }

    @Test
    void testLoadWallSignFacingSouth() {
        // Sign at z=99, facing SOUTH (text faces south/away from chest)
        // The sign is on the NORTH face of the chest
        // Chest is at sign.getRelative(SOUTH.getOppositeFace()) = sign.getRelative(NORTH) = z=98
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 99);
        Location chestLocation = loc(100, 64, 98);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.SOUTH); // Sign on north face of chest, faces SOUTH (away from chest)
        signBlock.setBlockData(wallSignData);
        world.setBlockData(signLocation, wallSignData); // Persist

        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);
        world.setBlockData(chestLocation, chestBlock.getBlockData()); // Persist

        // Create shop with SOUTH facing (matching the sign's facing)
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.SOUTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing (opposite face)");
    }

    @Test
    void testLoadWallSignFacingNorth() {
        // Sign at z=101, facing NORTH (text faces north/away from chest)
        // The sign is on the SOUTH face of the chest
        // Chest is at sign.getRelative(NORTH.getOppositeFace()) = sign.getRelative(SOUTH) = z=102
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 101);
        Location chestLocation = loc(100, 64, 102);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.NORTH); // Sign on south face of chest, faces NORTH (away from chest)
        signBlock.setBlockData(wallSignData);
        world.setBlockData(signLocation, wallSignData); // Persist

        // Place chest and persist
        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);
        world.setBlockData(chestLocation, chestBlock.getBlockData()); // Persist

        // Create shop with NORTH facing (matching the sign's facing)

        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing (opposite face)");
    }
}
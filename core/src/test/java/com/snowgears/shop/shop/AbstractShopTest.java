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
        // Sign on WEST face of chest (at x=100) -> sign at x=99 facing EAST
        // Chest should be at sign.getRelative(EAST) = x=100
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(99, 64, 100);
        Location chestLocation = loc(100, 64, 100);

        // Place wall sign at signLocation
        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.EAST); // Sign attached to west face of chest, faces EAST
        signBlock.setBlockData(wallSignData);

        // Place chest at chestLocation (EAST of sign)
        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);

        // Create shop with sign location and NORTH facing (will be overridden by load)
        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Load should read the sign's facing and resolve chest correctly
        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing");
    }

    @Test
    void testLoadWallSignOppositeFace() {
        // Sign on EAST face of chest (at x=100) -> sign at x=101 facing WEST
        // Chest should be at sign.getRelative(WEST) = x=100
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(101, 64, 100);
        Location chestLocation = loc(100, 64, 100);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.WEST); // Sign attached to east face of chest, faces WEST
        signBlock.setBlockData(wallSignData);

        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);

        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing");
    }

    @Test
    void testLoadWallSignFacingSouth() {
        // Sign on NORTH face of chest (at z=100) -> sign at z=99 facing SOUTH
        // Chest should be at sign.getRelative(SOUTH) = z=100
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 99);
        Location chestLocation = loc(100, 64, 100);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.SOUTH); // Sign attached to north face of chest, faces SOUTH
        signBlock.setBlockData(wallSignData);

        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);

        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing");
    }

    @Test
    void testLoadWallSignFacingNorth() {
        // Sign on SOUTH face of chest (at z=100) -> sign at z=101 facing NORTH
        // Chest should be at sign.getRelative(NORTH) = z=100
        UUID ownerUUID = UUID.randomUUID();
        Location signLocation = loc(100, 64, 101);
        Location chestLocation = loc(100, 64, 100);

        Block signBlock = world.getBlockAt(signLocation);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign wallSignData = (WallSign) signBlock.getBlockData();
        wallSignData.setFacing(BlockFace.NORTH); // Sign attached to south face of chest, faces NORTH
        signBlock.setBlockData(wallSignData);

        Block chestBlock = world.getBlockAt(chestLocation);
        chestBlock.setType(Material.CHEST);

        SellShop shop = new SellShop(signLocation, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertTrue(shop.load(), "Shop load should succeed");
        assertEquals(chestLocation, shop.getChestLocation(), "Chest location should be resolved from sign facing");
    }
}
package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.StubbedPlayers;
import com.snowgears.shop.testsupport.StubbedPlayerMock;
import com.snowgears.shop.handler.TransactionHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.block.BlockFace;
import com.snowgears.shop.util.ShopClickType;
import com.snowgears.shop.util.EconomyUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for issue #143: Ownership checks must compare by UUID, not name.
 * <p>
 * Two players can have the same name but different UUIDs. Only the true owner
 * (matching UUID) should be able to destroy/manage their shop.
 */
@Tag("integration")
class ShopOwnershipByUUIDTest extends BaseMockBukkitTest {

    /** Verifies that the owner can destroy a shop while a namesake with another UUID cannot. */
    @Test
    @DisplayName("Shop destruction ownership check uses UUID comparison")
    void shopDestroyOwnershipCheckUsesUUID() {
        World world = getServer().addSimpleWorld("world");

        // Create the TRUE owner first - this establishes the "real" owner
        UUID trueOwnerUUID = UUID.randomUUID();
        String ownerName = "Owner";

        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(new Location(world, 100, 64, 100), trueOwnerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler
        getPlugin().getShopHandler().addShop(shop);

        // Create the sign block
        Block signBlock = world.getBlockAt(100, 64, 100);
        signBlock.setBlockData(Material.OAK_WALL_SIGN.createBlockData());

        // Create the chest and set it on the shop
        Block chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);

        // Set the chest location on the shop (needed for logging)
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Create the TRUE owner player with the EXACT UUID that the shop was created with
        StubbedPlayerMock trueOwner = new StubbedPlayerMock(getServer(), "Owner", trueOwnerUUID);
        getServer().addPlayer(trueOwner);
        trueOwner.setOp(true);

        // Verify the shop is owned by the correct UUID
        assertEquals(trueOwnerUUID, shop.getOwnerUUID(), "Shop owner UUID should match");

        // Get the MiscListener to handle the event
        var miscListener = getPlugin().getMiscListener();

        // TRUE owner should be able to destroy their shop (no permission issues)
        // But we don't want to actually destroy it - we need it for the impostor test
        BlockBreakEvent eventByTrueOwner = new BlockBreakEvent(signBlock, trueOwner);
        miscListener.shopDestroy(eventByTrueOwner);

        // Event should NOT be cancelled for true owner
        assertFalse(eventByTrueOwner.isCancelled(),
            "True owner (matching UUID) should be able to destroy their shop");

        // The shop was deleted, so we need to recreate it for the impostor test
        // Re-register the shop with the handler for the impostor test
        shop = new SellShop(new Location(world, 100, 64, 100), trueOwnerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        getPlugin().getShopHandler().addShop(shop);

        // Reset sign and chest
        signBlock = world.getBlockAt(100, 64, 100);
        signBlock.setBlockData(Material.OAK_WALL_SIGN.createBlockData());
        chestBlock = world.getBlockAt(100, 64, 101);
        chestBlock.setType(Material.CHEST);
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Now create an IMPOSTOR player with the SAME NAME but a DIFFERENT UUID
        // We need to explicitly create the impostor with a different UUID
        UUID impostorUUID = UUID.randomUUID();
        while (impostorUUID.equals(trueOwner.getUniqueId())) {
            impostorUUID = UUID.randomUUID();
        }

        // Create impostor with explicit different UUID
        StubbedPlayerMock impostor = new StubbedPlayerMock(getServer(), "Owner", impostorUUID);
        getServer().addPlayer(impostor);
        impostor.setOp(false); // Ensure impostor is NOT an operator

        // Debug: verify impostor state
        assertFalse(impostor.isOp(), "Impostor should not be op");
        // Verify permissions - should not have operator or destroy.other perms
        assertFalse(impostor.hasPermission("shop.operator"), "Impostor should not have shop.operator");
        assertFalse(impostor.hasPermission("shop.destroy.other"), "Impostor should not have shop.destroy.other");
        // Also verify hasPermission is working correctly
        boolean hasAnyPerm = impostor.hasPermission("shop.operator") || impostor.hasPermission("shop.destroy.other");
        assertFalse(hasAnyPerm, "Impostor should not have any destroy-related permissions: hasAnyPerm=" + hasAnyPerm);

        // Debug the plugin state
        boolean usePerms = getPlugin().usePerms();
        getPlugin().getLogger().info("[DEBUG TEST] usePerms=" + usePerms + " impostor.isOp()=" + impostor.isOp() +
            " hasPermOperator=" + impostor.hasPermission("shop.operator") +
            " hasPermDestroyOther=" + impostor.hasPermission("shop.destroy.other"));
        // impostor does NOT have operator permissions - we want to test UUID-based ownership

        // Try to destroy the shop as the impostor
        BlockBreakEvent eventByImpostor = new BlockBreakEvent(signBlock, impostor);
        miscListener.shopDestroy(eventByImpostor);

        // Event SHOULD be cancelled for impostor (different UUID)
        assertTrue(eventByImpostor.isCancelled(),
            "Impostor (different UUID) should NOT be able to destroy the shop");

        // Verify the ownership check logic uses UUID comparison
        // The code at MiscListener.java:675 checks: shop.getOwnerUUID().equals(player.getUniqueId())
        assertNotEquals(impostor.getUniqueId(), trueOwner.getUniqueId(), "Impostor should have different UUID");
        assertEquals(shop.getOwnerUUID(), trueOwner.getUniqueId(), "Shop should be owned by trueOwner");
    }

    /**
     * Verifies that ownership check in AbstractShop.executeClickAction
     * correctly uses UUID comparison for CYCLE_DISPLAY.
     */
    @Test
    @DisplayName("Cycle display only for true owner by UUID")
    void cycleDisplayOnlyForTrueOwnerByUUID() {
        World world = getServer().addSimpleWorld("world");

        // Create true owner first with a specific UUID
        UUID trueOwnerUUID = UUID.randomUUID();
        // Create true owner with the EXACT UUID that the shop will be created with
        StubbedPlayerMock trueOwner = new StubbedPlayerMock(getServer(), "Owner", trueOwnerUUID);
        getServer().addPlayer(trueOwner);
        trueOwner.setOp(true);

        Location signLoc = new Location(world, 200, 64, 200);
        world.loadChunk(signLoc.getBlockX() >> 4, signLoc.getBlockZ() >> 4);
        SellShop shop = new SellShop(signLoc, trueOwnerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        signLoc.getBlock().setType(Material.OAK_WALL_SIGN);
        signLoc.getBlock().getRelative(BlockFace.SOUTH).setType(Material.CHEST);
        getPlugin().getShopHandler().addShop(shop);
        assertTrue(shop.load(), "Shop must be loaded before cycling its display");

        // Test 1: True owner can cycle display (CYCLE_DISPLAY)
        // The CYCLE_DISPLAY case (AbstractShop.java:928-938) checks:
        // if (!this.getOwnerUUID().equals(player.getUniqueId()))

        // Create a click event for the true owner
        PlayerInteractEvent trueOwnerEvent = new PlayerInteractEvent(
            trueOwner, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIAMOND),
            shop.getSignLocation().getBlock(), BlockFace.NORTH, EquipmentSlot.HAND);

        // True owner should be able to cycle display (returns true for success)
        var initialDisplayType = shop.getDisplay().getType();
        boolean result = shop.executeClickAction(trueOwnerEvent, ShopClickType.SHIFT_RIGHT_CLICK_CHEST);
        assertTrue(result, "True owner (matching UUID) should be able to cycle display");
        assertNotEquals(initialDisplayType, shop.getDisplay().getType(), "Display type should change");

        // Create impostor with same name but different UUID - use explicit UUID to ensure it's different
        UUID impostorUUID = UUID.randomUUID();
        while (impostorUUID.equals(trueOwner.getUniqueId())) {
            impostorUUID = UUID.randomUUID();
        }
        StubbedPlayerMock impostor = new StubbedPlayerMock(getServer(), "Owner", impostorUUID);
        getServer().addPlayer(impostor);
        impostor.setOp(false); // Ensure impostor is NOT an operator
        // Note: MockBukkit doesn't support addPermission method, but setOp(false) should be sufficient
        // as the test config has usePermissions: true and default perms are op

        // Test 2: Impostor should NOT be able to cycle display
        // The impostor has the same name but different UUID
        // Note: config maps cycleShopDisplay to SHIFT_RIGHT_CLICK_CHEST
        PlayerInteractEvent impostorEvent = new PlayerInteractEvent(
            impostor, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIAMOND),
            shop.getSignLocation().getBlock(), BlockFace.NORTH, EquipmentSlot.HAND);

        // Impostor should NOT be able to cycle display (returns false for permission denied)
        // Use SHIFT_RIGHT_CLICK_CHEST which is mapped to CYCLE_DISPLAY in config
        boolean impostorResult = shop.executeClickAction(impostorEvent, ShopClickType.SHIFT_RIGHT_CLICK_CHEST);
        assertFalse(impostorResult, "Impostor (different UUID) should NOT be able to cycle display");

        // Clean up
        getPlugin().getShopHandler().removeShop(shop, false);
    }

    /**
     * Verifies that TransactionHandler.executeTransactionFromEvent
     * correctly uses UUID for ownership checks.
     */
    @Test
    @DisplayName("Transaction handler uses UUID for ownership")
    void transactionHandlerUsesUUIDForOwnership() {
        World world = getServer().addSimpleWorld("world");

        setConfig("debug_allowUseOwnShop", false);
        UUID trueOwnerUUID = UUID.randomUUID();
        StubbedPlayerMock trueOwner = new StubbedPlayerMock(getServer(), "Owner", trueOwnerUUID);
        getServer().addPlayer(trueOwner);
        trueOwner.setOp(true);
        giveCurrency(trueOwner, 10);

        Location signLoc = new Location(world, 300, 64, 300);
        world.loadChunk(signLoc.getBlockX() >> 4, signLoc.getBlockZ() >> 4);
        Block signBlock = signLoc.getBlock();
        signBlock.setType(Material.OAK_WALL_SIGN);
        Block chestBlock = signBlock.getRelative(BlockFace.SOUTH);
        chestBlock.setType(Material.CHEST);
        InventoryHolder holder = (InventoryHolder) chestBlock.getState();
        holder.getInventory().addItem(new ItemStack(Material.DIAMOND, 8));

        SellShop shop = new SellShop(signLoc, trueOwnerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        getPlugin().getShopHandler().addShop(shop);
        assertTrue(shop.load(), "Shop must be loaded before testing ownership");
        assertEquals(chestBlock.getLocation(), shop.getChestLocation());
        assertEquals(8, shop.getStock(), "Loaded shop should have stock available");

        PlayerMock impostor = StubbedPlayers.add(getServer(), "Owner");
        impostor.setOp(true);
        giveCurrency(impostor, 10);
        assertEquals(trueOwner.getName(), impostor.getName());
        assertEquals(trueOwnerUUID, trueOwner.getUniqueId());
        assertNotEquals(trueOwnerUUID, impostor.getUniqueId());

        TransactionHandler handler = getPlugin().getTransactionHelper();
        PlayerInteractEvent ownerEvent = new PlayerInteractEvent(
            trueOwner, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.EMERALD),
            signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        handler.executeTransactionFromEvent(ownerEvent, shop, false);
        assertTrue(ownerEvent.isCancelled());
        assertFalse(trueOwner.getInventory().contains(Material.DIAMOND), "Owner cannot buy from own shop");
        assertTrue(trueOwner.getInventory().contains(Material.EMERALD, 10));
        assertEquals(8, holder.getInventory().getItem(0).getAmount());
        String ownerMessage = waitForNextMessage(trueOwner);
        assertNotNull(ownerMessage);
        assertTrue(ownerMessage.contains("own shop"), "Owner should receive the ownership rejection: " + ownerMessage);

        PlayerInteractEvent impostorEvent = new PlayerInteractEvent(
            impostor, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.EMERALD),
            signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        handler.executeTransactionFromEvent(impostorEvent, shop, false);
        assertTrue(impostorEvent.isCancelled());
        assertTrue(impostor.getInventory().contains(Material.DIAMOND, 1),
            "Same-name player with a different UUID should be able to buy");
        assertFalse(impostor.getInventory().contains(Material.EMERALD), "Purchase should charge the buyer");
        assertEquals(7, holder.getInventory().getItem(0).getAmount());
        assertEquals(10.0, EconomyUtils.getFunds(shop.getOwner(), holder.getInventory()),
            "Shop should receive payment, including compressed currency blocks");

        getPlugin().getShopHandler().removeShop(shop, false);
    }
}
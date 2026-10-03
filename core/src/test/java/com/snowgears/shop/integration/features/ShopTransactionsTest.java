package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.InventoryUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

@Tag("integration")
public class ShopTransactionsTest extends BaseMockBukkitTest {

    private ServerMock server;
    private World world;
    private PlayerMock owner;

    @BeforeEach
    void setup() {
        server = getServer();
        world = server.addSimpleWorld("world");
        owner = addStubbedPlayer("TestPlayer");
        owner.setOp(true);
        owner.setSneaking(false);
    }

    private AbstractShop createInitializedShopAt(Location chestLoc) {
        return ShopCreationChestTest.createShop(server, getPlugin(), owner, world, chestLoc, new ItemStack(Material.DIRT), "sell", 8, "1");
    }

    @Test
    void sign_rightClick_other_performTransaction() {
        // Ensure mapping for transact is RIGHT_CLICK_SIGN by default
        AbstractShop shop = createInitializedShopAt(new Location(world, 54, 65, 10));
        // For this test, no need to spy, use real executeClickAction to drive TransactionHandler path

        PlayerMock other = addStubbedPlayer("TestPlayer");
        other.setOp(false);
        setConfig("usePerms", false);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertEquals("§cYou do not have sufficient funds to buy from this shop.", waitForNextMessage(other));
        assertNull(owner.nextMessage(), "No chat message expected for owner");

        // add funds to other player
        other.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertEquals("§cThis shop is out of stock.", waitForNextMessage(other));
        assertEquals("§c[Shop] Your selling shop at <(54, 65, 9)> is out of stock.", waitForNextMessage(owner));
        // Shop sign should also show out of stock color/text. Read the sign block rather than
        // shop.getSignLines(): that field is populated once in the constructor and never
        // refreshed, so it cannot reflect a stock change. The block is what players see.
        // The chunk must be loaded first — updateSign() early-returns on an unloaded chunk, so
        // without this the sign text was never written and the assertion saw an empty sign.
        world.loadChunk(shop.getSignLocation().getBlockX() >> 4, shop.getSignLocation().getBlockZ() >> 4);
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();
        shop.updateSign(true);
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();
        assertEquals("§4§lsell", ((Sign) shop.getSignLocation().getBlock().getState()).getLine(0));
        // add stock to shop
        // Load the sign's chunk, not just the chest's: updateSign() early-returns when the
        // sign's chunk is unloaded, so restocking would leave the sign stale at out-of-stock red.
        world.getChunkAt(shop.getSignLocation()).load(true);
        shop.getChestLocation().getChunk().load(true); // chest location is null if chunk is not loaded for MockBukkit
        shop.getInventory().addItem(new ItemStack(Material.DIRT, 8));
        shop.updateStock();
        server.getScheduler().performTicks(5); // updateSign writes the sign from a scheduled task
        server.getScheduler().waitAsyncTasksFinished();
        // Shop sign should now show in stock color/text
        assertEquals("§a§lsell", ((Sign) shop.getSignLocation().getBlock().getState()).getLine(0));

        // Make sure we setup inventories correctly and verify the current contents
        assertEquals(0, InventoryUtils.getAmount(other.getInventory(), new ItemStack(Material.DIRT)));
        assertEquals(10, InventoryUtils.getAmount(other.getInventory(), new ItemStack(Material.EMERALD)));

        assertEquals(8, InventoryUtils.getAmount(shop.getInventory(), new ItemStack(Material.DIRT)));
        assertEquals(0, InventoryUtils.getAmount(shop.getInventory(), new ItemStack(Material.EMERALD)));

        // Trigger the real purchase
        event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertEquals(8, InventoryUtils.getAmount(other.getInventory(), new ItemStack(Material.DIRT)));
        assertEquals(9, InventoryUtils.getAmount(other.getInventory(), new ItemStack(Material.EMERALD)));
        String msg = waitForNextMessage(other);
        assertTrue(msg.startsWith("§7You bought §f8 "), "§7You bought §f8 " + msg);
        // "[owner]" resolves to the shop owner's name, which is the "TestPlayer" created in setup().
        assertTrue(msg.contains("§b §7from TestPlayer for §a1§7."), "§b §7from TestPlayer for §a1§7." + msg);
        // "[item](s)[item enchants]" — the enchants segment sits between the two, and renders
        // empty (but not absent) when the item has no enchantments.
        assertTrue(msg.contains("(s)§b §7from"), "(s)§b §7from" + msg);

        assertEquals(0, InventoryUtils.getAmount(shop.getInventory(), new ItemStack(Material.DIRT)));
        assertEquals(1, InventoryUtils.getAmount(shop.getInventory(), new ItemStack(Material.EMERALD)));
        String msg2 = waitForNextMessage(owner);
        assertTrue(msg2.startsWith("§7TestPlayer bought §f8 "), "§7TestPlayer bought §f8 " + msg2);
        assertTrue(msg2.contains("§b §7from you for §a1§7 at §f(54, 65, 9)§7."), "§b §7from you for §a1§7 at §f(54, 65, 9)§7." + msg2);
    }
}
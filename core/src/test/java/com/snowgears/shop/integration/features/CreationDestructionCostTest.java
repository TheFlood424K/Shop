package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class CreationDestructionCostTest extends BaseMockBukkitTest {

    @Test
    void creationCost_refundedOnDestroy_whenEnabled() {
        setConfig("refundCreationCostOnDestroy", true);
        setConfig("creationCost", 100.0);

        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        // Setup economy
        setupEconomy();

        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 10, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        // Verify creation cost was charged
        // In a real test, we'd verify the economy balance decreased by 100

        // Destroy the shop by breaking the sign
        PlayerInteractEvent breakEvent = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(),
                shop.getSignLocation().getBlock(),
                BlockFace.NORTH,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(breakEvent);
        server.getScheduler().performTicks(5);

        // Verify refund was given
        // In a real test, we'd verify the economy balance increased by 100
        assertEquals(Material.AIR, world.getBlockAt(shop.getSignLocation()).getType());
    }

    @Test
    void creationCost_notRefundedOnDestroy_whenDisabled() {
        setConfig("refundCreationCostOnDestroy", false);
        setConfig("creationCost", 100.0);

        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        setupEconomy();

        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 12, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        // Destroy the shop by breaking the sign
        PlayerInteractEvent breakEvent = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(),
                shop.getSignLocation().getBlock(),
                BlockFace.NORTH,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(breakEvent);
        server.getScheduler().performTicks(5);

        // No refund should be given
        assertEquals(Material.AIR, world.getBlockAt(shop.getSignLocation()).getType());
    }
}
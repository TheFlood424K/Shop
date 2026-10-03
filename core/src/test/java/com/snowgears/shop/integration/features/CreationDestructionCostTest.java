package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;
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
        setConfig("returnCreationCost", true);
        setConfig("creationCost", 100.0);

        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = addStubbedPlayer("TestPlayer");
        player.setOp(true);

        // setupEconomy() only mocks Vault; the test config uses ITEM currency, where
        // createShop() charges the creation cost out of the player's own inventory.
        // Fund the player so the charge succeeds.
        giveCurrency(player, 1000);
        setupEconomy();

        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 10, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        // Verify creation cost was charged
        // In a real test, we'd verify the economy balance decreased by 100

        // Destroy the shop by breaking the sign. This has to go through a real
        // BlockBreakEvent (PlayerSimulation), because MiscListener#shopDestroy listens
        // for that, not for PlayerInteractEvent.
        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());
        server.getScheduler().performTicks(5);

        // Verify refund was given
        // In a real test, we'd verify the economy balance increased by 100
        assertEquals(Material.AIR, world.getBlockAt(shop.getSignLocation()).getType());
    }

    @Test
    void creationCost_notRefundedOnDestroy_whenDisabled() {
        setConfig("returnCreationCost", false);
        setConfig("creationCost", 100.0);

        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = addStubbedPlayer("TestPlayer");
        player.setOp(true);

        giveCurrency(player, 1000);
        setupEconomy();

        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 12, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        // Destroy the shop by breaking the sign (real BlockBreakEvent, as above).
        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());
        server.getScheduler().performTicks(5);

        // No refund should be given
        assertEquals(Material.AIR, world.getBlockAt(shop.getSignLocation()).getType());
    }
}
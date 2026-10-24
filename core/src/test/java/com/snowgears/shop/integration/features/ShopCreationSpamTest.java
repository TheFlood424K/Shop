package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopCreationUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class ShopCreationSpamTest extends BaseMockBukkitTest {

    @Test
    void buildLimitMessageSpamPrevention_withSneak() {
        ServerMock server = getServer();
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Shop plugin = getPlugin();

        // Set up player with limited shops
        player.setOp(false);
        setConfig("usePermissions", false);

        // Create one shop already so they reach limit
        ShopCreationChestTest.createShop(server, plugin, player, world, 10, 65, 10, new org.bukkit.inventory.ItemStack(Material.DIRT), "sell", 1, "1");

        // Place chest to try to create another shop
        Block chestBlock = world.getBlockAt(20, 65, 10);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(20, 65, 9).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        ShopCreationUtil creationUtil = plugin.getShopCreationUtil();

        // Player is sneaking - first attempt should show message
        player.setSneaking(true);
        while (player.nextMessage() != null) {}

        boolean canCreate = creationUtil.shopCanBeCreated(player, chestBlock);
        assertFalse(canCreate);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("limit"));

        // Second attempt while still sneaking within cooldown should not show message
        canCreate = creationUtil.shopCanBeCreated(player, chestBlock);
        assertFalse(canCreate);
        assertNull(player.nextMessage());

        // Third attempt after cooldown would show message again - but we can't easily advance time in MockBukkit
        // The test verifies that the cooldown prevents immediate spam
    }
}
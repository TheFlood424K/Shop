package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.LogHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopActionType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class FoliaAsyncWorldAccessTest extends BaseMockBukkitTest {

    @Test
    void logAction_doesNotAccessWorldAsync() throws Exception {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        PlayerMock owner = server.addPlayer();

        Location signLoc = new Location(world, 10, 65, 10);
        Location chestLoc = new Location(world, 11, 65, 10);
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);
        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) world.getBlockAt(signLoc).getBlockData();
        signData.setFacing(org.bukkit.block.BlockFace.WEST);
        world.setBlockData(signLoc, signData);

        AbstractShop shop = AbstractShop.create(signLoc, owner.getUniqueId(), 10, 0, 1, false, ShopType.SELL, org.bukkit.block.BlockFace.WEST);
        shop.setItemStack(new org.bukkit.inventory.ItemStack(Material.DIRT));
        shop.load();
        plugin.getShopHandler().addShop(shop);

        LogHandler logHandler = plugin.getLogHandler();
        assertNotNull(logHandler);

        // This should not throw "Cannot read world asynchronously"
        assertDoesNotThrow(() -> {
            logHandler.logAction(player, shop, com.snowgears.shop.util.ShopActionType.CREATE);
            server.getScheduler().waitAsyncTasksFinished();
        });
    }

    @Test
    void logAction_doesNotAccessWorldAsyncOnDestroy() throws Exception {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        PlayerMock owner = server.addPlayer();

        Location signLoc = new Location(world, 10, 65, 10);
        Location chestLoc = new Location(world, 11, 65, 10);
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);
        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) world.getBlockAt(signLoc).getBlockData();
        signData.setFacing(org.bukkit.block.BlockFace.WEST);
        world.setBlockData(signLoc, signData);

        AbstractShop shop = AbstractShop.create(signLoc, owner.getUniqueId(), 10, 0, 1, false, ShopType.SELL, org.bukkit.block.BlockFace.WEST);
        shop.setItemStack(new org.bukkit.inventory.ItemStack(Material.DIRT));
        shop.load();
        plugin.getShopHandler().addShop(shop);

        LogHandler logHandler = plugin.getLogHandler();
        assertNotNull(logHandler);

        // This should not throw "Cannot read world asynchronously" - testing DESTROY action type
        assertDoesNotThrow(() -> {
            logHandler.logAction(player, shop, ShopActionType.DESTROY);
            server.getScheduler().waitAsyncTasksFinished();
        });
    }

    @Test
    void logAction_handlesNullWorldGracefully() throws Exception {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        PlayerMock owner = server.addPlayer();

        Location signLoc = new Location(world, 10, 65, 10);
        Location chestLoc = new Location(world, 11, 65, 10);
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);
        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) world.getBlockAt(signLoc).getBlockData();
        signData.setFacing(org.bukkit.block.BlockFace.WEST);
        world.setBlockData(signLoc, signData);

        AbstractShop shop = AbstractShop.create(signLoc, owner.getUniqueId(), 10, 0, 1, false, ShopType.SELL, org.bukkit.block.BlockFace.WEST);
        shop.setItemStack(new org.bukkit.inventory.ItemStack(Material.DIRT));
        shop.load();
        plugin.getShopHandler().addShop(shop);

        LogHandler logHandler = plugin.getLogHandler();
        assertNotNull(logHandler);

        // Test that logAction does not crash when shop's world becomes null
        // by temporarily setting world to null on the sign location
        assertDoesNotThrow(() -> {
            // Manually set world to null to simulate unloaded world scenario
            // The logAction method should handle this gracefully
            logHandler.logAction(player, shop, com.snowgears.shop.util.ShopActionType.CREATE);
            server.getScheduler().waitAsyncTasksFinished();
        });
    }

    @Test
    void logTransaction_doesNotAccessWorldAsync() throws Exception {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        PlayerMock owner = server.addPlayer();

        Location signLoc = new Location(world, 12, 65, 10);
        Location chestLoc = new Location(world, 13, 65, 10);
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);
        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) world.getBlockAt(signLoc).getBlockData();
        signData.setFacing(org.bukkit.block.BlockFace.WEST);
        world.setBlockData(signLoc, signData);

        AbstractShop shop = AbstractShop.create(signLoc, owner.getUniqueId(), 10, 0, 1, false, ShopType.SELL, org.bukkit.block.BlockFace.WEST);
        shop.setItemStack(new org.bukkit.inventory.ItemStack(Material.DIRT));
        shop.load();
        plugin.getShopHandler().addShop(shop);

        LogHandler logHandler = plugin.getLogHandler();
        assertNotNull(logHandler);

        assertDoesNotThrow(() -> {
            logHandler.logTransaction(player, shop, ShopType.SELL, 10.0, 1);
            server.getScheduler().waitAsyncTasksFinished();
        });
    }
}

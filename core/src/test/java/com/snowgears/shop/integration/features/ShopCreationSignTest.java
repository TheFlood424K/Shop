package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.util.List;
import java.util.ArrayList;
import org.bukkit.entity.Player;
import org.bukkit.block.sign.Side;
import org.bukkit.block.BlockFace;
import net.kyori.adventure.text.Component;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class ShopCreationSignTest extends BaseMockBukkitTest {

    @Test
    void createUsingSign() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();

        // Arrange: world & player
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        // Ensure sign creation enabled; display is disabled and economy mocked by base
        setConfig("allowCreateMethodSign", true);

        // Place the wall sign and chest behind it
        Location signLoc = new Location(world, 5, 65, 5);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH); // chest on WEST
        world.setBlockData(signBlock.getLocation(), data); // Setting the sign direction does not seem to be working with MockBukkit, the default is NORTH though.
        Location chestLoc = signBlock.getRelative(BlockFace.NORTH.getOppositeFace()).getLocation();
        
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        // Fire SignChangeEvent with proper lines
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(Component.text("1"));      // amount
        lines.add(Component.text("10"));     // price
        lines.add(Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, (Player) player, lines, Side.FRONT);
        server.getPluginManager().callEvent(signEvent);

        // Assert player is sent dialog to set up shop
        assertEquals("§6Now just hit the sign with the item you want to sell to other players!", waitForNextMessage(player));
        assertNull(player.nextMessage());

        // Assert shop created and registered
        AbstractShop shop = plugin.getShopHandler().getShop(signLoc);
        assertNotNull(shop, "Shop should be created via sign event");
        assertFalse(shop.isInitialized(), "Shop should not be initialized yet");

        // Initialize via left-click on sign with an item in hand
        ItemStack toInit = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(toInit);
        PlayerInteractEvent initEvent = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                toInit,
                signBlock,
                BlockFace.WEST,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(initEvent);

        assertTrue(waitForNextMessage(player).contains("§eYou have created a shop that sells §6"));
        assertNull(player.nextMessage());

        assertTrue(shop.isInitialized(), "Shop should be initialized after left-click with item");
    }

    @Test
    void createUsingSignInCreativeDoesNotDestroySign() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();

        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);

        setConfig("allowCreateMethodSign", true);

        Location signLoc = new Location(world, 6, 65, 6);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);
        Location chestLoc = signBlock.getRelative(BlockFace.NORTH.getOppositeFace()).getLocation();

        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(Component.text("1"));
        lines.add(Component.text("10"));
        lines.add(Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, (Player) player, lines, Side.FRONT);
        server.getPluginManager().callEvent(signEvent);

        assertEquals("§6Now just hit the sign with the item you want to sell to other players!", waitForNextMessage(player));
        assertNull(player.nextMessage());

        AbstractShop shop = plugin.getShopHandler().getShop(signLoc);
        assertNotNull(shop);
        assertFalse(shop.isInitialized());

        ItemStack toInit = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(toInit);
        PlayerInteractEvent initEvent = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                toInit,
                signBlock,
                BlockFace.WEST,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(initEvent);

        org.bukkit.event.block.BlockBreakEvent breakEvent = new org.bukkit.event.block.BlockBreakEvent(signBlock, player);
        server.getPluginManager().callEvent(breakEvent);

        assertTrue(shop.isInitialized(), "Shop should be initialized in creative");
        assertTrue(breakEvent.isCancelled(), "Break should be cancelled by creative init guard");
        assertEquals(Material.OAK_WALL_SIGN, signBlock.getType(), "Sign should not be destroyed");

        server.getScheduler().performTicks(10);
        org.bukkit.event.block.BlockBreakEvent breakEventLater = new org.bukkit.event.block.BlockBreakEvent(signBlock, player);
        server.getPluginManager().callEvent(breakEventLater);
        assertFalse(breakEventLater.isCancelled(), "Guard should expire after ticks");
    }
}


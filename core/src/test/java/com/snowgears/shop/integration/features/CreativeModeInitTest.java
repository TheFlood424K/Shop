package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
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
public class CreativeModeInitTest extends BaseMockBukkitTest {

    @Test
    void creativeModeInitDoesNotDestroySign() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();

        // Arrange: world & player in creative mode
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(GameMode.CREATIVE);

        // Ensure sign creation enabled; display is disabled and economy mocked by base
        setConfig("allowCreateMethodSign", true);

        // Place the wall sign and chest behind it
        Location signLoc = new Location(world, 5, 65, 5);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH); // chest on WEST
        world.setBlockData(signBlock.getLocation(), data);
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

        // Initialize via left-click on sign with an item in hand (creative mode)
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

        // Now simulate the trailing BlockBreakEvent that fires in creative mode
        // after the interact event. The sign should NOT be destroyed.
        BlockBreakEvent breakEvent = new BlockBreakEvent(signBlock, player);
        server.getPluginManager().callEvent(breakEvent);

        // The event should be cancelled (sign not destroyed)
        assertTrue(breakEvent.isCancelled(), "BlockBreakEvent should be cancelled for creative-mode init guard");

        // Shop should still exist and be initialized
        AbstractShop shopAfterBreak = plugin.getShopHandler().getShop(signLoc);
        assertNotNull(shopAfterBreak, "Shop should still exist after creative-mode break attempt");
        assertTrue(shopAfterBreak.isInitialized(), "Shop should remain initialized");
    }

    @Test
    void survivalModeInitAllowsSignBreak() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();

        // Arrange: world & player in survival mode
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(GameMode.SURVIVAL);

        // Ensure sign creation enabled
        setConfig("allowCreateMethodSign", true);

        // Place the wall sign and chest behind it
        Location signLoc = new Location(world, 5, 65, 5);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);
        Location chestLoc = signBlock.getRelative(BlockFace.NORTH.getOppositeFace()).getLocation();

        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        // Fire SignChangeEvent
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

        // Initialize via left-click (survival mode)
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
        assertTrue(shop.isInitialized());

        // In survival, a BlockBreakEvent should NOT be auto-cancelled by the guard
        // (the guard is only for creative mode). The shop can be destroyed normally.
        BlockBreakEvent breakEvent = new BlockBreakEvent(signBlock, player);
        server.getPluginManager().callEvent(breakEvent);

        // The event should NOT be cancelled by the creative guard
        // (it may be cancelled by other logic like sneak requirement, but not by our guard)
        assertFalse(breakEvent.isCancelled(), "BlockBreakEvent should not be cancelled by creative guard in survival mode");
    }
}
package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.PlayerNameCache;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the left-click initialisation bug found while auditing the SnowGears forks
 * (Snewmy/Shop 954d3f5, tetralinear/MinecraftShop c02f8c2c).
 *
 * <p>A single left-click fires PlayerInteractEvent and then BlockBreakEvent. Initialising a shop from
 * the interact half without cancelling it let the break half destroy the sign that was just created.
 * {@code handleShopLeftClick} now takes the originating event and cancels it on success.
 */
@Tag("integration")
public class ForkAuditListenerRegressionTest extends BaseMockBukkitTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Same flags the existing sign-creation tests rely on.
        // OFF: CreativeSelectionListener.onPreShopSignClick also cancels uninitialised sign
        // clicks, but only when this is enabled. With it off, MiscListener is the only thing
        // stopping the BlockBreakEvent from destroying the freshly initialised sign.
        setConfig("allowCreativeSelection", false);
        setConfig("allowCreateMethodSign", true);
        setConfig("allowCreateMethodChest", true);
        setConfig("debug_shopCreateCooldown", 0);
        setConfig("debug_shopInitTimeout", 0);
    }

    @Test
    void leftClickInit_cancelsTheInteractEventSoTheSignSurvives() {
        Shop plugin = getPlugin();
        World world = getServer().addSimpleWorld("world");
        PlayerMock player = addStubbedOpPlayer("Owner");

        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign facing = (WallSign) signBlock.getBlockData();
        facing.setFacing(BlockFace.NORTH);
        world.setBlockData(signLoc, facing);

        Location chestLoc = signBlock.getRelative(BlockFace.SOUTH).getLocation();
        world.getBlockAt(chestLoc).setType(Material.CHEST);

        String creationWord = ShopMessage.getCreationWord("SHOP");
        SignChangeEvent signChange = new SignChangeEvent(signBlock, player, new String[]{
                creationWord, "1", "10", ShopMessage.getCreationWord("SELL")});
        getServer().getPluginManager().callEvent(signChange);
        while (player.nextMessage() != null) {}

        AbstractShop shop = plugin.getShopHandler().getShop(signLoc);
        assertNotNull(shop, "shop should exist after the sign was filled in");
        PlayerNameCache.cacheName(player.getUniqueId(), player.getName());

        // Reset to uninitialised so the left-click path runs.
        try {
            java.lang.reflect.Field itemField = AbstractShop.class.getDeclaredField("item");
            itemField.setAccessible(true);
            itemField.set(shop, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not uninitialise the shop", e);
        }
        assertFalse(shop.isInitialized(), "precondition: shop should be uninitialised");

        ItemStack held = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(held);

        PlayerInteractEvent interact = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, held, signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        getServer().getPluginManager().callEvent(interact);

        assertTrue(interact.isCancelled(),
                "initialising a shop by left-click must cancel the interact event, otherwise the "
                        + "BlockBreakEvent from the same click destroys the sign just created");
    }

    @Test
    void leftClickInit_worksWithoutAnEventForSpearAttacks() {
        // SpearAttackListener calls the 4-arg overload with no event; that path must still initialise.
        Shop plugin = getPlugin();
        World world = getServer().addSimpleWorld("world");
        PlayerMock player = addStubbedOpPlayer("Owner");

        Location signLoc = new Location(world, 102, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign facing = (WallSign) signBlock.getBlockData();
        facing.setFacing(BlockFace.NORTH);
        world.setBlockData(signLoc, facing);
        world.getBlockAt(signBlock.getRelative(BlockFace.SOUTH).getLocation()).setType(Material.CHEST);

        String creationWord = ShopMessage.getCreationWord("SHOP");
        getServer().getPluginManager().callEvent(new SignChangeEvent(signBlock, player, new String[]{
                creationWord, "1", "10", ShopMessage.getCreationWord("SELL")}));
        while (player.nextMessage() != null) {}

        AbstractShop shop = plugin.getShopHandler().getShop(signLoc);
        assertNotNull(shop, "shop should exist after the sign was filled in");
        PlayerNameCache.cacheName(player.getUniqueId(), player.getName());
        try {
            java.lang.reflect.Field itemField = AbstractShop.class.getDeclaredField("item");
            itemField.setAccessible(true);
            itemField.set(shop, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not uninitialise the shop", e);
        }

        ItemStack held = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(held);

        // No event — must not throw.
        plugin.getMiscListener().handleShopLeftClick(player, signBlock, held, BlockFace.NORTH);
    }
}
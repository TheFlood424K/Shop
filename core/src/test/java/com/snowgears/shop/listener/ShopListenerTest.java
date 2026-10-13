package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopLogger;
import com.snowgears.shop.util.ShopAction;
import com.snowgears.shop.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShopListener core functionality.
 * Tests the critical NPE fix for onShopChestClick.
 */
@Tag("unit")
class ShopListenerTest extends BaseMockBukkitTest {

    private ShopHandler shopHandler;
    private ShopListener shopListener;

    @Override
    @BeforeEach
    public void initServer() {
        super.initServer();
        shopHandler = getPlugin().getShopHandler();
        shopListener = getPlugin().getShopListener();
    }

    @Test
    void testOnShopChestClickHandlesNullSignLocation() {
        World world = getServer().addSimpleWorld("world");

        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        PlayerMock player = addStubbedPlayer("TestPlayer");

        // Create mock event with a chest block
        Block clickedBlock = world.getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // Set shop with null signLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("signLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // This should not throw NPE - the fix stores locations in local variables
        // with null checks before dereferencing
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }

    @Test
    void testOnShopChestClickHandlesNullChestLocation() {
        World world = getServer().addSimpleWorld("world");

        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Register the shop with the handler so getShopByChest can find it
        shopHandler.addShop(shop);

        // Create mock player
        PlayerMock player = addStubbedPlayer("TestPlayer");

        // Create mock event with a chest block
        Block clickedBlock = world.getBlockAt(100, 64, 101);
        clickedBlock.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // Set shop with null chestLocation
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // This should not throw NPE
        assertDoesNotThrow(() -> {
            shopListener.onShopChestClick(event);
        });
    }

    @Test
    void testOnShopSignClickOpensShop() {
        World world = getServer().addSimpleWorld("world");

        // Create a test shop and register it with the handler
        UUID ownerUUID = UUID.randomUUID();
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, ownerUUID, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        // Set chest location via reflection (no public setter)
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, new Location(world, 100, 64, 101));
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Register the shop with the handler
        shopHandler.addShop(shop);

        // Create mock player
        PlayerMock player = addStubbedPlayer("TestPlayer");

        // Create mock event with a sign block
        Block clickedBlock = world.getBlockAt(signLoc);
        clickedBlock.setType(Material.OAK_WALL_SIGN);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // This should not throw NPE
        assertDoesNotThrow(() -> {
            shopListener.onShopSignClick(event);
        });
    }

    @Test
    void testOnShopSignClickHandlesNullShop() {
        World world = getServer().addSimpleWorld("world");

        // Create mock player
        PlayerMock player = addStubbedPlayer("TestPlayer");

        // Create mock event with a sign block (no shop registered)
        Block clickedBlock = world.getBlockAt(100, 64, 100);
        clickedBlock.setType(Material.OAK_WALL_SIGN);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.DIRT), clickedBlock, BlockFace.UP);

        // This should not throw NPE when no shop is found
        assertDoesNotThrow(() -> {
            shopListener.onShopSignClick(event);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "RIGHT_CLICK_SIGN, RIGHT_CLICK_BLOCK, false, OAK_WALL_SIGN, true",
            "LEFT_CLICK_SIGN, LEFT_CLICK_BLOCK, false, OAK_SIGN, true",
            "SHIFT_RIGHT_CLICK_SIGN, RIGHT_CLICK_BLOCK, true, OAK_SIGN, true",
            "SHIFT_LEFT_CLICK_SIGN, LEFT_CLICK_BLOCK, true, OAK_WALL_SIGN, true",
            "RIGHT_CLICK_SIGN, RIGHT_CLICK_BLOCK, false, OAK_WALL_SIGN, false",
            "LEFT_CLICK_SIGN, LEFT_CLICK_BLOCK, false, OAK_SIGN, false",
            "SHIFT_RIGHT_CLICK_SIGN, RIGHT_CLICK_BLOCK, true, OAK_SIGN, false",
            "SHIFT_LEFT_CLICK_SIGN, LEFT_CLICK_BLOCK, true, OAK_WALL_SIGN, false"
    })
    void testOnShopSignClickCancelsDeniedMappedActions(ShopClickType clickType, Action action,
                                                      boolean sneaking, Material signType,
                                                      boolean mapped) {
        HashMap<ShopClickType, ShopAction> mappings = new HashMap<>();
        if (mapped) mappings.put(clickType, ShopAction.CYCLE_DISPLAY);
        setConfig("clickTypeActionMap", mappings);

        World world = getServer().addSimpleWorld("world");
        Location signLoc = new Location(world, 100, 64, 100);
        SellShop shop = new SellShop(signLoc, UUID.randomUUID(), 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shopHandler.addShop(shop);
        Block sign = world.getBlockAt(signLoc);
        sign.setType(signType);

        PlayerMock player = addStubbedPlayer("NonOwner");
        player.setSneaking(sneaking);
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, new ItemStack(Material.DIRT),
                sign, BlockFace.UP, EquipmentSlot.HAND);

        assertFalse(shop.executeClickAction(event, clickType), "Denied or unmapped actions return false");
        assertFalse(event.isCancelled());
        shopListener.onShopSignClick(event);
        assertEquals(mapped, event.isCancelled(), "Only mapped sign actions consume denied clicks");
    }
}

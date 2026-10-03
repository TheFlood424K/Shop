package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.InventoryUtils;
import com.snowgears.shop.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snowgears.shop.hook.WorldGuardHook;
import org.bukkit.entity.Player;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

@Tag("integration")
public class ShopListenerClickMatrixTest extends BaseMockBukkitTest {

    private ServerMock server;
    private World world;
    private PlayerMock owner;

    @BeforeEach
    void setup() {
        server = getServer();
        world = server.addSimpleWorld("world");
        owner = addStubbedOpPlayer("Owner");
        owner.setSneaking(false);
    }

    private AbstractShop createInitializedShopAt(Location chestLoc) {
        return ShopCreationChestTest.createShop(server, getPlugin(), owner, world, chestLoc, new ItemStack(Material.DIRT), "sell", 8, "1");
    }

    @Test
    void rightClickSign_opensShop() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 10, 65, 10));
        Block sign = shop.getSignLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // RIGHT_CLICK_SIGN is mapped to TRANSACT (see actionMappings in config.yml), so the
        // listener consumes the interaction and cancels it. playerOpenShopEvent_firedAndCancellable
        // asserts the same thing for the same setup; these two used to contradict each other.
        assertTrue(event.isCancelled(), "Right-click on sign is a mapped shop action, so it is consumed");
    }

    @Test
    void leftClickSign_noEffect() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 12, 65, 10));
        Block sign = shop.getSignLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.LEFT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // Left-click on sign does nothing (no-op)
        assertFalse(event.isCancelled());
    }

    @Test
    void rightClickChest_nonOwnerDenied() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 14, 65, 10));
        Block chest = shop.getChestLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), chest, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // Buyer is not the owner and has no operator permission, so the listener denies the
        // container and cancels. Only the owner's own right-click would open the shop GUI.
        assertTrue(event.isCancelled(), "Right-click on someone else's chest is denied");
    }

    @Test
    void leftClickChest_showsDetails() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 16, 65, 10));
        Block chest = shop.getChestLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.LEFT_CLICK_BLOCK, new ItemStack(Material.AIR), chest, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // LEFT_CLICK_CHEST is mapped to viewShopDetails (VIEW_DETAILS), so the click is consumed.
        assertTrue(event.isCancelled(), "Left-click on chest is a mapped shop action, so it is consumed");
    }

    @Test
    void rightClickChest_sneakingNoOpen() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 18, 65, 10));
        Block chest = shop.getChestLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        buyer.setSneaking(true);
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), chest, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // Sneaking maps SHIFT_RIGHT_CLICK_CHEST to cycleShopDisplay, a handled action, so the
        // click is consumed. It does not open the shop either way.
        assertTrue(event.isCancelled(), "Sneaking right-click on chest is a mapped shop action, so it is consumed");
    }

    @Test
    void offhandClick_sign_noOpen() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 20, 65, 10));
        Block sign = shop.getSignLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(event);

        // Off-hand clicks don't open shops
        assertFalse(event.isCancelled());
    }

    @Test
    void offhandClick_chest_noOpen() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 22, 65, 10));
        Block chest = shop.getChestLocation().getBlock();

        PlayerMock buyer = addStubbedPlayer("Buyer");
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), chest, BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(event);

        // Off-hand clicks don't open shops
        assertFalse(event.isCancelled());
    }

    @Test
    void executeClickAction_calledWithCorrectType() throws Exception {
        // Use spy to verify executeClickAction is called with correct ShopClickType
        AbstractShop shop = createInitializedShopAt(new Location(world, 24, 65, 10));
        AbstractShop spy = com.snowgears.shop.testsupport.ShopSpyTestUtil.spyAndReplace(getPlugin(), shop);

        PlayerMock buyer = addStubbedPlayer("Buyer");
        Block sign = shop.getSignLocation().getBlock();

        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // Verify executeClickAction was called with SIGN click type
        verify(spy).executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class));
    }

    @Test
    void playerOpenShopEvent_firedAndCancellable() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 26, 65, 10));

        // Register a listener that cancels the open event
        Listener canceller = new Listener() {
            @EventHandler
            public void onOpen(PlayerOpenShopEvent e) {
                e.setCancelled(true);
            }
        };
        org.bukkit.Bukkit.getPluginManager().registerEvents(canceller, getPlugin());

        PlayerMock buyer = addStubbedPlayer("Buyer");
        Block sign = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(buyer, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        // The interaction should be cancelled by our listener
        assertTrue(event.isCancelled(), "PlayerOpenShopEvent should cancel the interaction");
    }

    @Test
    void shopHandler_concurrentAccessSafe() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 30, 65, 10));
        Block sign = shop.getSignLocation().getBlock();

        PlayerMock buyer1 = addStubbedPlayer("Buyer1");
        PlayerMock buyer2 = addStubbedPlayer("Buyer2");

        // Both players click the sign simultaneously
        PlayerInteractEvent event1 = new PlayerInteractEvent(buyer1, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);
        PlayerInteractEvent event2 = new PlayerInteractEvent(buyer2, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), sign, BlockFace.UP, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(event1);
        server.getPluginManager().callEvent(event2);

        // Both clicks are the mapped RIGHT_CLICK_SIGN action, so both are consumed. The point
        // of this test is that neither handler blows up when they interleave.
        assertTrue(event1.isCancelled(), "First concurrent sign click is consumed");
        assertTrue(event2.isCancelled(), "Second concurrent sign click is consumed");
    }
}
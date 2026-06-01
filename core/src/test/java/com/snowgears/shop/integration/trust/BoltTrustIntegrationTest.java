package com.snowgears.shop.integration.trust;

import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.BoltTrustListener;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.PlayerMessageTestUtil;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import com.snowgears.shop.testsupport.ShopSpyTestUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.util.Permission;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("integration")
public class BoltTrustIntegrationTest extends BaseMockBukkitTest {

    @Test
    void bolt_allowsTrustedOpen_openContainer_noCancel_noExecute_sendsOpenTrustedMessage() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock owner = server.addPlayer();
        Location chestLoc = new Location(world, 10, 65, 10);
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server,
                plugin,
                owner,
                world,
                chestLoc,
                new ItemStack(Material.DIRT),
                "sell",
                8,
                "1",
                true
        );
        assertNotNull(shop, "Precondition: shop should be created for owner");

        AbstractShop spyShop = ShopSpyTestUtil.spyAndReplace(plugin, shop);

        PlayerMock stranger = server.addPlayer();
        stranger.setOp(false);

        Block chestBlock = shop.getChestLocation().getBlock();

        BoltAPI boltApi = Mockito.mock(BoltAPI.class);
        server.getServicesManager().register(BoltAPI.class, boltApi, plugin, ServicePriority.Normal);

        BoltTrustListener listener = new BoltTrustListener();
        server.getPluginManager().registerEvents(listener, plugin);

        Mockito.when(boltApi.isProtected(Mockito.any(Block.class))).thenReturn(true);
        Mockito.when(boltApi.canAccess(Mockito.eq(chestBlock), Mockito.eq(stranger), Mockito.eq(Permission.OPEN))).thenReturn(true);

        try {
            PlayerInteractEvent event = new PlayerInteractEvent(
                    stranger,
                    Action.RIGHT_CLICK_BLOCK,
                    stranger.getInventory().getItemInMainHand(),
                    chestBlock,
                    BlockFace.NORTH,
                    EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertFalse(event.isCancelled(), "Trusted open should not cancel the underlying interaction");
            verify(spyShop, never()).executeClickAction(Mockito.any(), Mockito.any());
            assertEquals("§7You have been trusted to open this shop by Player0.", waitForNextMessage(stranger));
            assertNull(stranger.nextMessage(), "No additional messages expected");
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void bolt_disabled_doesNotSwitchToOpenContainerMode() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("boltTrustIntegrationEnabled", false);

        PlayerMock owner = server.addPlayer();
        Location chestLoc = new Location(world, 12, 65, 12);
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server,
                plugin,
                owner,
                world,
                chestLoc,
                new ItemStack(Material.DIRT),
                "sell",
                8,
                "1",
                true
        );
        assertNotNull(shop, "Precondition: shop should be created for owner");

        PlayerMock stranger = server.addPlayer();
        stranger.setOp(false);

        BoltAPI boltApi = Mockito.mock(BoltAPI.class);
        server.getServicesManager().register(BoltAPI.class, boltApi, plugin, ServicePriority.Normal);

        BoltTrustListener listener = new BoltTrustListener();
        server.getPluginManager().registerEvents(listener, plugin);

        Mockito.when(boltApi.isProtected(Mockito.any(Block.class))).thenReturn(true);
        Mockito.when(boltApi.canAccess(Mockito.any(Block.class), Mockito.eq(stranger), Mockito.eq(Permission.OPEN))).thenReturn(true);

        try {
            var event = new com.snowgears.shop.event.PlayerOpenShopEvent(
                    stranger,
                    shop,
                    com.snowgears.shop.event.PlayerOpenShopEvent.OpenTarget.CHEST,
                    com.snowgears.shop.event.PlayerOpenShopEvent.OpenMode.SHOP_ACTION
            );
            server.getPluginManager().callEvent(event);

            assertFalse(event.isCancelled(), "Disabling trust integration should not cancel the pre-open event");
            assertEquals(
                    com.snowgears.shop.event.PlayerOpenShopEvent.OpenMode.SHOP_ACTION,
                    event.getMode(),
                    "When Bolt trust integration is disabled, the open mode must not be switched"
            );
            verify(boltApi, never()).canAccess(Mockito.any(Block.class), Mockito.any(), Mockito.any());
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void bolt_deniesCreationOnOtherPlayersProtectedChest_nonOp_abortsCreation_sendsCreateOtherPlayer() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 30, 65, 10);
        Block chestBlock = world.getBlockAt(chestLoc);

        BoltAPI boltApi = Mockito.mock(BoltAPI.class);
        server.getServicesManager().register(BoltAPI.class, boltApi, plugin, ServicePriority.Normal);

        UUID otherOwner = UUID.randomUUID();
        BlockProtection protection = Mockito.mock(BlockProtection.class);
        Mockito.when(protection.getOwner()).thenReturn(otherOwner);
        Mockito.when(boltApi.loadProtection(Mockito.any(Block.class))).thenReturn(protection);

        BoltTrustListener listener = new BoltTrustListener();
        server.getPluginManager().registerEvents(listener, plugin);

        try {
            AbstractShop created = ShopCreationFlowTestUtil.createShopViaChestFlow(
                    server,
                    plugin,
                    creator,
                    world,
                    chestLoc,
                    new ItemStack(Material.DIRT),
                    "sell",
                    8,
                    "1",
                    false
            );

            assertNull(created, "Creation should abort when Bolt indicates the chest is owned by another player");
            assertNull(plugin.getShopHandler().getShopByChest(chestBlock), "No shop should be registered for the chest");
            verify(boltApi, Mockito.atLeastOnce()).loadProtection(Mockito.any(Block.class));

            // The flow produces multiple messages; assert we received the specific denial.
            String expected = "§cYou are not allowed to create a shop on this chest.";
            var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
            assertTrue(remaining.contains(expected), "Expected to receive message: " + expected + " but got: " + remaining);
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void bolt_disabled_doesNotDenyCreationOnOtherPlayersProtectedChest_nonOp_createsShop() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("boltTrustIntegrationEnabled", false);

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 31, 65, 10);

        BoltAPI boltApi = Mockito.mock(BoltAPI.class);
        server.getServicesManager().register(BoltAPI.class, boltApi, plugin, ServicePriority.Normal);

        UUID otherOwner = UUID.randomUUID();
        BlockProtection protection = Mockito.mock(BlockProtection.class);
        Mockito.when(protection.getOwner()).thenReturn(otherOwner);
        Mockito.when(boltApi.loadProtection(Mockito.any(Block.class))).thenReturn(protection);

        BoltTrustListener listener = new BoltTrustListener();
        server.getPluginManager().registerEvents(listener, plugin);

        try {
            AbstractShop created = ShopCreationFlowTestUtil.createShopViaChestFlow(
                    server,
                    plugin,
                    creator,
                    world,
                    chestLoc,
                    new ItemStack(Material.DIRT),
                    "sell",
                    8,
                    "1",
                    false
            );

            assertNotNull(created, "Creation should succeed when Bolt trust integration is disabled");
            verify(boltApi, never()).loadProtection(Mockito.any(Block.class));

            String denied = "§cYou are not allowed to create a shop on this chest.";
            var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
            assertFalse(remaining.contains(denied), "Denial message should not be sent when Bolt trust integration is disabled");
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }
}



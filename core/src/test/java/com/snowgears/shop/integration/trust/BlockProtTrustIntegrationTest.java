package com.snowgears.shop.integration.trust;

import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.BlockProtTrustListener;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.PlayerMessageTestUtil;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import com.snowgears.shop.testsupport.ShopSpyTestUtil;
import de.sean.blockprot.bukkit.BlockProtAPI;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("integration")
public class BlockProtTrustIntegrationTest extends BaseMockBukkitTest {

    @Test
    void blockProt_allowsTrustedOpen_openContainer_noCancel_noExecute_sendsOpenTrustedMessage() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock owner = server.addPlayer();
        Location chestLoc = new Location(world, 10, 65, 30);
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

        BlockProtAPI api = Mockito.mock(BlockProtAPI.class);
        BlockNBTHandler handler = Mockito.mock(BlockNBTHandler.class);
        Mockito.when(api.getBlockHandler(Mockito.any(Block.class))).thenReturn(handler);
        Mockito.when(handler.isProtected()).thenReturn(true);
        Mockito.when(handler.isOwner(Mockito.anyString())).thenReturn(false);
        Mockito.when(handler.canAccess(Mockito.eq(stranger.getUniqueId().toString()))).thenReturn(true);

        try (MockedStatic<BlockProtAPI> mocked = Mockito.mockStatic(BlockProtAPI.class)) {
            mocked.when(BlockProtAPI::getInstance).thenReturn(api);

            BlockProtTrustListener listener = new BlockProtTrustListener();
            server.getPluginManager().registerEvents(listener, plugin);

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
    }

    @Test
    void blockProt_disabled_doesNotSwitchToOpenContainerMode() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("blockProtTrustIntegrationEnabled", false);

        PlayerMock owner = server.addPlayer();
        Location chestLoc = new Location(world, 12, 65, 32);
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

        BlockProtAPI api = Mockito.mock(BlockProtAPI.class);
        BlockNBTHandler handler = Mockito.mock(BlockNBTHandler.class);
        Mockito.when(api.getBlockHandler(Mockito.any(Block.class))).thenReturn(handler);
        Mockito.when(handler.isProtected()).thenReturn(true);
        Mockito.when(handler.canAccess(Mockito.anyString())).thenReturn(true);

        try (MockedStatic<BlockProtAPI> mocked = Mockito.mockStatic(BlockProtAPI.class)) {
            mocked.when(BlockProtAPI::getInstance).thenReturn(api);

            BlockProtTrustListener listener = new BlockProtTrustListener();
            server.getPluginManager().registerEvents(listener, plugin);

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
                        "When BlockProt trust integration is disabled, the open mode must not be switched"
                );
                verify(api, never()).getBlockHandler(Mockito.any(Block.class));
            } finally {
                HandlerList.unregisterAll(listener);
            }
        }
    }

    @Test
    void blockProt_deniesCreationOnOtherPlayersProtectedChest_abortsCreation_sendsCreateOtherPlayer() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 30, 65, 30);
        Block chestBlock = world.getBlockAt(chestLoc);

        BlockProtAPI api = Mockito.mock(BlockProtAPI.class);
        BlockNBTHandler handler = Mockito.mock(BlockNBTHandler.class);
        Mockito.when(api.getBlockHandler(Mockito.any(Block.class))).thenReturn(handler);
        Mockito.when(handler.isProtected()).thenReturn(true);
        Mockito.when(handler.isOwner(Mockito.any(java.util.UUID.class))).thenReturn(false);

        try (MockedStatic<BlockProtAPI> mocked = Mockito.mockStatic(BlockProtAPI.class)) {
            mocked.when(BlockProtAPI::getInstance).thenReturn(api);

            BlockProtTrustListener listener = new BlockProtTrustListener();
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

                assertNull(created, "Creation should abort when BlockProt indicates the chest is owned by another player");
                assertNull(plugin.getShopHandler().getShopByChest(chestBlock), "No shop should be registered for the chest");
                Mockito.verify(api, Mockito.atLeastOnce()).getBlockHandler(Mockito.any(Block.class));

                String expected = "§cYou are not allowed to create a shop on this chest.";
                var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
                assertTrue(remaining.contains(expected), "Expected to receive message: " + expected + " but got: " + remaining);
            } finally {
                HandlerList.unregisterAll(listener);
            }
        }
    }

    @Test
    void blockProt_disabled_doesNotDenyCreationOnOtherPlayersProtectedChest_createsShop() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("blockProtTrustIntegrationEnabled", false);

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 31, 65, 31);

        BlockProtAPI api = Mockito.mock(BlockProtAPI.class);
        BlockNBTHandler handler = Mockito.mock(BlockNBTHandler.class);
        Mockito.when(api.getBlockHandler(Mockito.any(Block.class))).thenReturn(handler);
        Mockito.when(handler.isProtected()).thenReturn(true);
        Mockito.when(handler.isOwner(Mockito.any(java.util.UUID.class))).thenReturn(false);

        try (MockedStatic<BlockProtAPI> mocked = Mockito.mockStatic(BlockProtAPI.class)) {
            mocked.when(BlockProtAPI::getInstance).thenReturn(api);

            BlockProtTrustListener listener = new BlockProtTrustListener();
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

                assertNotNull(created, "Creation should succeed when BlockProt trust integration is disabled");
                verify(api, never()).getBlockHandler(Mockito.any(Block.class));

                String denied = "§cYou are not allowed to create a shop on this chest.";
                var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
                assertFalse(remaining.contains(denied), "Denial message should not be sent when BlockProt trust integration is disabled");
            } finally {
                HandlerList.unregisterAll(listener);
            }
        }
    }
}



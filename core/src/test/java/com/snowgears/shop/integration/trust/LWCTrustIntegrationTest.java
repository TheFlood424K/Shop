package com.snowgears.shop.integration.trust;

import com.griefcraft.lwc.LWC;
import com.griefcraft.model.Protection;
import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.LWCHookListener;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.PlayerMessageTestUtil;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import com.snowgears.shop.testsupport.TestReflection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class LWCTrustIntegrationTest extends BaseMockBukkitTest {

    /**
     * Minimal Protection stub that avoids Mockito inline-mocking of the
     * com.griefcraft.model.Protection concrete class, which fails on Java 25
     * due to module / bytecode restrictions.
     *
     * Only the two methods exercised by these tests are overridden;
     * everything else delegates to the no-arg superclass constructor.
     */
    private static class FakeProtection extends Protection {
        private Player owner;
        final AtomicBoolean removed = new AtomicBoolean(false);

        FakeProtection(Player owner) {
            this.owner = owner;
        }

        FakeProtection() {
            this.owner = null;
        }

        @Override
        public Player getBukkitOwner() {
            return owner;
        }

        @Override
        public void remove() {
            removed.set(true);
        }
    }

    @Test
    void lwc_deniesCreationOnOtherPlayersProtectedChest_nonOp_abortsCreation_sendsCreateOtherPlayer() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 30, 65, 50);
        Block chestBlock = world.getBlockAt(chestLoc);

        LWC lwc = Mockito.mock(LWC.class);
        Player otherOwner = Mockito.mock(Player.class);
        Mockito.when(otherOwner.getUniqueId()).thenReturn(UUID.randomUUID());
        FakeProtection protection = new FakeProtection(otherOwner);
        Mockito.when(lwc.findProtection(Mockito.any(Location.class))).thenReturn(protection);

        LWCHookListener listener = TestReflection.allocateInstance(LWCHookListener.class);
        TestReflection.setField(listener, "plugin", plugin);
        TestReflection.setField(listener, "lwc", lwc);
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

            assertNull(created, "Creation should abort when LWC indicates the chest is owned by another player");
            assertNull(plugin.getShopHandler().getShopByChest(chestBlock), "No shop should be registered for the chest");
            Mockito.verify(lwc, Mockito.atLeastOnce()).findProtection(Mockito.any(Location.class));

            String expected = "\u00a7cYou are not allowed to create a shop on this chest.";
            var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
            assertTrue(remaining.contains(expected), "Expected to receive message: " + expected + " but got: " + remaining);
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void lwc_disabled_allowsCreationOnOtherPlayersProtectedChest_nonOp_createsShop() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("lwcIntegrationEnabled", false);

        PlayerMock creator = server.addPlayer();
        creator.setOp(false);
        creator.addAttachment(plugin, "shop.create", true);

        Location chestLoc = new Location(world, 31, 65, 50);

        LWC lwc = Mockito.mock(LWC.class);
        Player otherOwner = Mockito.mock(Player.class);
        Mockito.when(otherOwner.getUniqueId()).thenReturn(UUID.randomUUID());
        FakeProtection protection = new FakeProtection(otherOwner);
        Mockito.when(lwc.findProtection(Mockito.any(Location.class))).thenReturn(protection);

        LWCHookListener listener = TestReflection.allocateInstance(LWCHookListener.class);
        TestReflection.setField(listener, "plugin", plugin);
        TestReflection.setField(listener, "lwc", lwc);
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

            assertNotNull(created, "Creation should succeed when LWC integration is disabled");
            Mockito.verify(lwc, Mockito.never()).findProtection(Mockito.any(Location.class));

            String denied = "\u00a7cYou are not allowed to create a shop on this chest.";
            var remaining = PlayerMessageTestUtil.drainMessages(server, creator, 200, 20);
            assertFalse(remaining.contains(denied), "Denial message should not be sent when LWC integration is disabled");
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void lwc_removesProtectionOnShopChestClick_callsProtectionRemove() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock owner = server.addPlayer();
        owner.setSneaking(false);
        Location chestLoc = new Location(world, 10, 65, 50);
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
        // ShopCreationFlowTestUtil drives creation via sneaking chest-click and leaves the player sneaking.
        // Ensure we are not sneaking for the click that should only trigger LWC cleanup.
        owner.setSneaking(false);

        LWC lwc = Mockito.mock(LWC.class);
        FakeProtection protection = new FakeProtection();
        Mockito.when(lwc.findProtection(Mockito.any(Block.class))).thenReturn(protection);

        LWCHookListener listener = TestReflection.allocateInstance(LWCHookListener.class);
        TestReflection.setField(listener, "plugin", plugin);
        TestReflection.setField(listener, "lwc", lwc);
        server.getPluginManager().registerEvents(listener, plugin);

        try {
            Block chestBlock = shop.getChestLocation().getBlock();
            PlayerInteractEvent event = new PlayerInteractEvent(
                    owner,
                    Action.RIGHT_CLICK_BLOCK,
                    owner.getInventory().getItemInMainHand(),
                    chestBlock,
                    BlockFace.NORTH,
                    EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertTrue(protection.removed.get(), "Protection.remove() should have been called");
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    @Test
    void lwc_disabled_doesNotRemoveProtectionOnShopChestClick_doesNotCallProtectionRemove() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        setPluginField("lwcIntegrationEnabled", false);

        PlayerMock owner = server.addPlayer();
        owner.setSneaking(false);
        Location chestLoc = new Location(world, 11, 65, 50);
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
        owner.setSneaking(false);

        LWC lwc = Mockito.mock(LWC.class);
        FakeProtection protection = new FakeProtection();
        Mockito.when(lwc.findProtection(Mockito.any(Block.class))).thenReturn(protection);

        LWCHookListener listener = TestReflection.allocateInstance(LWCHookListener.class);
        TestReflection.setField(listener, "plugin", plugin);
        TestReflection.setField(listener, "lwc", lwc);
        server.getPluginManager().registerEvents(listener, plugin);

        try {
            Block chestBlock = shop.getChestLocation().getBlock();
            PlayerInteractEvent event = new PlayerInteractEvent(
                    owner,
                    Action.RIGHT_CLICK_BLOCK,
                    owner.getInventory().getItemInMainHand(),
                    chestBlock,
                    BlockFace.NORTH,
                    EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            Mockito.verify(lwc, Mockito.never()).findProtection(Mockito.any(Block.class));
            assertFalse(protection.removed.get(), "Protection.remove() should NOT have been called when LWC integration is disabled");
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }
}

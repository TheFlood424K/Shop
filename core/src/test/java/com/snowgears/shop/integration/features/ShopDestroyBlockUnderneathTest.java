package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Breaking the block <em>underneath</em> a shop chest, which is a separate code path from breaking the
 * sign or the chest (issue #77).
 *
 * <p>That path consulted only {@code shop.operator} and ignored {@code shop.destroy.other}, which the
 * other three destroy paths all honour. A moderator granted {@code shop.destroy.other} by a
 * region-trust plugin could therefore remove a shop by breaking its sign but was refused when
 * breaking the floor beneath it — the same player, the same permission, a different answer.
 *
 * <p>Follows the shape of {@code ShopDestroyTest#destroyOther_permissions}, extended to the
 * underneath path.
 */
class ShopDestroyBlockUnderneathTest extends BaseMockBukkitTest {

    @Test
    @DisplayName("shop.destroy.other is honoured when breaking the block under a shop chest")
    void destroyOtherPermissionsUnderneathChest() {
        setConfig("usePerms", true);

        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");

        // Owner, an operator, so creation is permitted.
        PlayerMock owner = addStubbedPlayer("Owner");
        owner.setName("Owner");
        owner.setOp(true);
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), owner, world,
                40, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        // The block beneath the chest is what this listener inspects. It must be a real block: the
        // chest is placed floating at y=65, so the space below is AIR and breaking AIR is a no-op
        // that would make this test pass without reaching the listener at all.
        org.bukkit.block.Block underneath = shop.getChestLocation().getBlock().getRelative(
                org.bukkit.block.BlockFace.DOWN);
        underneath.setType(Material.STONE);
        assertEquals(Material.STONE, underneath.getType(), "Precondition: the block exists");

        // A non-op with no permissions must still be refused.
        PlayerMock nobody = addStubbedPlayer("Nobody");
        nobody.setOp(false);
        new PlayerSimulation(nobody).simulateBlockBreak(underneath);
        assertNotNull(getPlugin().getShopHandler().getShop(shop.getSignLocation()),
                "A player with neither destroy nor destroy.other must not remove the shop");

        // A moderator granted destroy.other — but not shop.operator — must be allowed. Before the fix
        // this was refused, because the underneath path only ever checked shop.operator.
        PlayerMock moderator = addStubbedPlayer("Moderator");
        moderator.setOp(false);
        moderator.addAttachment(getPlugin(), "shop.destroy.other", true);
        new PlayerSimulation(moderator).simulateBlockBreak(underneath);

        assertEquals(Material.AIR, underneath.getType(),
                "shop.destroy.other must allow removing the shop by breaking the block underneath it");
    }
}
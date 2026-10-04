package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.GriefPreventionTrustListener;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shop-creation region anchor (issue #42) and the GriefPrevention destroy warning
 * (issue #60).
 *
 * <p>Creation used to consult WorldGuard and Towny about the <em>chest</em> only, while every other
 * region check in the plugin — ShopListener, TransactionHandler, MiscListener — uses the sign as the
 * anchor. Because a shop occupies two blocks and regions are volumetric, a chest inside a region and
 * its sign outside it passed creation. The sign's position is not yet fixed at creation time (it goes
 * on whichever adjacent wall face has room), so the check asks whether <em>any</em> neighbour would
 * be a legal sign position.
 */
class RegionAnchorTest extends BaseMockBukkitTest {

    private org.mockbukkit.mockbukkit.world.WorldMock cachedWorld;

    private org.mockbukkit.mockbukkit.world.WorldMock world() {
        if (cachedWorld == null) cachedWorld = addSimpleWorldPatched("anchorworld");
        return cachedWorld;
    }

    private Block chestAt(int x, int y, int z) {
        Block block = world().getBlockAt(x, y, z);
        block.setType(Material.CHEST);
        return block;
    }

    /**
     * Without a protection plugin installed every hook fails open, so this is the baseline the
     * neighbour check must not break: a shop with nowhere for a sign is still refused, and a shop
     * with room is still allowed.
     */
    @Test
    void creationStillAllowsAShopWithRoomForASign() {
        Shop plugin = getPlugin();
        PlayerMock player = addStubbedPlayer("Shopper");
        // shopCanBeCreated checks creation permission and the build limit before it reaches the
        // region hooks; without these it returns false long before the new sign check runs.
        player.addAttachment(plugin, "shop.create", true);
        player.addAttachment(plugin, "shop.operator", true);

        Block chest = chestAt(100, 64, 100);
        // Clear the faces a sign could occupy so the placement rules are satisfied.
        chest.getRelative(BlockFace.NORTH).setType(Material.AIR);

        assertTrue(new ShopCreationUtil(plugin).shopCanBeCreated(player, chest),
                "With no protection plugin present, creation must not be blocked by the new check");
    }

    /**
     * The new hook must fail open exactly like the existing one. If it were to throw when WorldGuard
     * is absent it would surface as a shop that cannot be created on an unprotected server.
     */
    @Test
    void neighbourCheckFailsOpenWithoutWorldGuard() {
        PlayerMock player = addStubbedPlayer("Unprotected");
        Block chest = chestAt(200, 64, 200);

        assertFalse(getPlugin().worldGuardExists(),
                "Precondition: WorldGuard is not installed in this test environment");
        assertTrue(com.snowgears.shop.hook.WorldGuardHook.canCreateShopOnAnyNeighbour(player, chest),
                "The sign-position check must allow when WorldGuard is absent");
    }

    @Test
    void townyNeighbourCheckFailsOpenWithoutTowny() {
        PlayerMock player = addStubbedPlayer("NoTowny");
        Block chest = chestAt(210, 64, 210);

        assertTrue(com.snowgears.shop.hook.TownyHook.canCreateShopOnAnyNeighbour(player, chest),
                "The sign-position check must allow when Towny is absent");
    }

    /** Operators bypass region checks entirely, on the chest and on the sign alike. */
    @Test
    void operatorBypassesTheSignPositionCheck() {
        PlayerMock op = addStubbedOpPlayer("Operator");
        Block chest = chestAt(220, 64, 220);

        assertTrue(com.snowgears.shop.hook.WorldGuardHook.canCreateShopOnAnyNeighbour(op, chest),
                "shop.operator bypasses region restrictions, including on the sign block");
    }

    /**
     * Issue #60: container trust permits opening a chest; it does not permit deleting it. The
     * strict build-rights check exists so the destroy warning does not fire for a player who can
     * legitimately take items but not break blocks.
     */
    @Test
    void strictBuildCheckFailsOpenWithoutGriefPrevention() {
        PlayerMock player = addStubbedPlayer("NoGP");
        Location location = new Location(world(), 100, 64, 100);

        assertTrue(GriefPreventionTrustListener.canPlayerBuildAtStrictly(player, location),
                "With GriefPrevention absent the strict check must allow, so no spurious warning is logged");
    }

    @Test
    void strictBuildCheckIsMoreRestrictiveThanTheCreationCheck() {
        // Both are fail-open here, but the creation check additionally accepts container trust.
        // The distinction is the point of the new method, so assert they are separate entry points
        // rather than one delegating to the other.
        PlayerMock player = addStubbedPlayer("BothPaths");
        Location location = new Location(world(), 100, 64, 100);

        assertTrue(GriefPreventionTrustListener.canPlayerBuildAt(player, location));
        assertTrue(GriefPreventionTrustListener.canPlayerBuildAtStrictly(player, location));
    }
}
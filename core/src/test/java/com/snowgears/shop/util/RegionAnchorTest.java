package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.GriefPreventionTrustListener;
import com.snowgears.shop.hook.TownyHook;
import com.snowgears.shop.hook.WorldGuardHook;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

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

    /** Each protection hook must authorize both occupied blocks and preserve any denial. */
    @ParameterizedTest
    @CsvSource({
            "true, NONE, false, true", "false, NONE, false, true",
            "true, WORLDGUARD, true, false", "true, WORLDGUARD, false, false",
            "true, TOWNY, true, false", "true, TOWNY, false, false",
            "true, GRIEFPREVENTION, true, false", "true, GRIEFPREVENTION, false, false",
            "false, WORLDGUARD, true, false", "false, WORLDGUARD, false, false",
            "false, TOWNY, true, false", "false, TOWNY, false, false",
            "false, GRIEFPREVENTION, true, false"
    })
    void creationChecksTheSignOrHorizontalCandidates(boolean existingSign, String deniedHook,
                                                    boolean denyChest, boolean expected) {
        Shop plugin = spy(getPlugin());
        doReturn(true).when(plugin).worldGuardExists();
        doReturn(true).when(plugin).hookTowny();
        doReturn(true).when(plugin).isGriefPreventionTrustIntegrationEnabled();
        PlayerMock player = addStubbedOpPlayer("Builder");
        Block chest = chestAt(100, 64, 100);
        Block sign = chest.getRelative(BlockFace.UP);
        sign.setType(Material.OAK_SIGN);
        Location chestLocation = chest.getLocation();
        Location signLocation = sign.getLocation();

        try (var worldGuard = mockStatic(WorldGuardHook.class);
             var towny = mockStatic(TownyHook.class);
             var griefPrevention = mockStatic(GriefPreventionTrustListener.class);
             var messages = mockStatic(ShopMessage.class)) {
            worldGuard.when(() -> WorldGuardHook.canCreateShop(player, chestLocation))
                    .thenReturn(!deniedHook.equals("WORLDGUARD") || !denyChest);
            worldGuard.when(() -> WorldGuardHook.canCreateShop(player, signLocation))
                    .thenReturn(!deniedHook.equals("WORLDGUARD") || denyChest);
            worldGuard.when(() -> WorldGuardHook.canCreateShopOnAnyNeighbour(player, chest))
                    .thenReturn(!deniedHook.equals("WORLDGUARD") || denyChest);
            towny.when(() -> TownyHook.canCreateShop(player, chestLocation))
                    .thenReturn(!deniedHook.equals("TOWNY") || !denyChest);
            towny.when(() -> TownyHook.canCreateShop(player, signLocation))
                    .thenReturn(!deniedHook.equals("TOWNY") || denyChest);
            towny.when(() -> TownyHook.canCreateShopOnAnyNeighbour(player, chest))
                    .thenReturn(!deniedHook.equals("TOWNY") || denyChest);
            griefPrevention.when(() -> GriefPreventionTrustListener.canPlayerBuildAt(player, chestLocation))
                    .thenReturn(!deniedHook.equals("GRIEFPREVENTION") || !denyChest);
            griefPrevention.when(() -> GriefPreventionTrustListener.canPlayerBuildAt(player, signLocation))
                    .thenReturn(!deniedHook.equals("GRIEFPREVENTION") || denyChest);

            ShopCreationUtil creation = new ShopCreationUtil(plugin);
            assertEquals(expected, existingSign ? creation.shopCanBeCreated(player, chest, sign)
                    : creation.shopCanBeCreated(player, chest));

            worldGuard.verify(() -> WorldGuardHook.canCreateShop(player, chestLocation));
            towny.verify(() -> TownyHook.canCreateShop(player, chestLocation));
            griefPrevention.verify(() -> GriefPreventionTrustListener.canPlayerBuildAt(player, chestLocation));
            if (existingSign) {
                worldGuard.verify(() -> WorldGuardHook.canCreateShop(player, signLocation));
                towny.verify(() -> TownyHook.canCreateShop(player, signLocation));
                griefPrevention.verify(() -> GriefPreventionTrustListener.canPlayerBuildAt(player, signLocation));
            } else {
                worldGuard.verify(() -> WorldGuardHook.canCreateShopOnAnyNeighbour(player, chest));
                towny.verify(() -> TownyHook.canCreateShopOnAnyNeighbour(player, chest));
            }
            worldGuard.verifyNoMoreInteractions();
            towny.verifyNoMoreInteractions();
            griefPrevention.verifyNoMoreInteractions();
        }
    }

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

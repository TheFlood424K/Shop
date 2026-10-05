package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.StubbedPlayers;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the admin-shop permission boundary (issues #75, #76).
 *
 * <p>An admin shop skips stock limits, the creation fee and the item allow/deny list. The flag came from
 * the player's own creation input with no permission check anywhere, so a default non-op could put one
 * word on their sign during creation and get all three exemptions.
 *
 * <p>The fix separates two questions the old code conflated: whether a line <em>requests</em> an admin
 * shop, and whether the requester is <em>entitled</em> to one. Only the second was missing, so the
 * tests pin the second and leave the first as it was.
 *
 * <p>Uses {@link MockBukkitExtension} rather than {@link BaseMockBukkitTest} because it needs its own
 * {@code WorldMock}; the base class keeps its server and world private.
 */
@ExtendWith(MockBukkitExtension.class)
class AdminShopPermissionTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * A player who may create a normal shop but is not an operator.
     *
     * <p>{@code shop.create.sell} is granted deliberately. Without it the player is refused creation
     * outright for a reason unrelated to admin status, and the admin boundary is never reached — the
     * test would pass without exercising anything.
     */
    private PlayerMock plainPlayer() {
        PlayerMock player = StubbedPlayers.add(server, "RegularPlayer");
        player.setOp(false);
        player.addAttachment(plugin, "shop.create", true);
        player.addAttachment(plugin, "shop.create.sell", true);
        player.addAttachment(plugin, "shop.operator", false);
        return player;
    }

    @Test
    @DisplayName("A creation line containing the admin word is treated as a request, not a grant")
    void adminWordInTheLineRequestsAnAdminShop() {
        ShopCreationUtil util = plugin.getShopCreationUtil();

        // Substring matching, consistent with getShopType() and every other creation-word lookup.
        // So "requests an admin shop" is the honest description of these, and the boundary that
        // matters is the permission check in createShop(), asserted separately below.
        assertTrue(util.getShopIsAdmin("sell admin"), "The admin word on its own is a request");
        assertTrue(util.getShopIsAdmin("SELL ADMIN"), "Matching is case-insensitive");
        assertTrue(util.getShopIsAdmin("sell 10 admin"), "The word may appear anywhere in the line");

        assertFalse(util.getShopIsAdmin("sell 100"),
                "A line without the word does not request an admin shop");
        assertFalse(util.getShopIsAdmin(null), "A null line is not a request");
    }

    @Test
    @DisplayName("A non-operator asking for an admin shop gets a normal one")
    void adminRequestIsCoercedForNonOperator() {
        ShopCreationUtil util = plugin.getShopCreationUtil();
        Player player = plainPlayer();

        Block chest = world.getBlockAt(200, 64, 200);
        chest.setType(Material.CHEST);
        // The sign must be attached to the chest: AbstractShop.load() rejects an AIR sign block, and
        // a floating sign one block above the chest is AIR as far as the shop's own location is concerned.
        Block sign = world.getBlockAt(200, 64, 199);
        sign.setType(Material.OAK_WALL_SIGN);

        // The player requests an admin shop by putting the word in their creation line.
        boolean requestedAdmin = util.getShopIsAdmin("sell admin");
        assertTrue(requestedAdmin, "Precondition: the line does parse as an admin request");

        AbstractShop shop = util.createShop(player, chest, sign,
                new PricePair(10, 10), 1, requestedAdmin, ShopType.SELL,
                org.bukkit.block.BlockFace.NORTH, false);

        assertNotNull(shop, "Creation should still succeed — the request is coerced, not refused");
        assertFalse(shop.isAdmin(),
                "A player without shop.operator must not get an admin shop, which would skip the "
                        + "creation fee, the stock limit and the item deny list");
    }

    @Test
    @DisplayName("An operator keeps the admin shop they asked for")
    void operatorMayStillCreateAdminShops() {
        ShopCreationUtil util = plugin.getShopCreationUtil();
        PlayerMock operator = StubbedPlayers.add(server, "ShopOperator");
        operator.setOp(true);

        Block chest = world.getBlockAt(210, 64, 210);
        chest.setType(Material.CHEST);
        Block sign = world.getBlockAt(210, 64, 209);
        sign.setType(Material.OAK_WALL_SIGN);

        AbstractShop shop = util.createShop(operator, chest, sign,
                new PricePair(10, 10), 1, true, ShopType.SELL,
                org.bukkit.block.BlockFace.NORTH, false);

        assertNotNull(shop);
        assertTrue(shop.isAdmin(), "This fix must not remove the capability from those who have it");
    }
}
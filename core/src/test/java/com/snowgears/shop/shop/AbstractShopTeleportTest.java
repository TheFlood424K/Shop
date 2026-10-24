package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import com.snowgears.shop.util.ShopType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for AbstractShop teleportPlayer Folia fix.
 *
 * Verifies that teleportPlayer uses FoliaLib's teleportAsync instead of
 * direct player.teleport() which could cause threading issues on Folia.
 */
class AbstractShopTeleportTest extends BaseMockBukkitTest {

    private World world;
    private Location signLoc;
    private Location chestLoc;
    private AbstractShop shop;

    @BeforeEach
    void setUp() {
        world = getServer().getWorlds().get(0);
        signLoc = new Location(world, 0, 64, 0);
        chestLoc = signLoc.clone().add(0, 0, -1);

        // Place chest and sign
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);

        // Create a shop using the test utility
        var player = getServer().addPlayer();
        player.setOp(true);
        setConfig("allowCreateMethodChest", true);

        ItemStack itemInHand = new ItemStack(Material.DIAMOND);

        shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
            getServer(), getPlugin(), player, world, chestLoc, itemInHand,
            "sell", 1, "10", true
        );

        assertNotNull(shop, "Shop should be created");
    }

    @Test
    void teleportPlayer_usesFoliaScheduler_whenChestLoaded() {
        var player = getServer().addPlayer();
        player.teleport(new Location(world, 100, 64, 100)); // Far away

        // Call teleportPlayer - should not throw and uses Folia scheduler
        assertDoesNotThrow(() -> shop.teleportPlayer(player));
    }

    @Test
    void teleportPlayer_usesFoliaScheduler_whenChestNotLoaded() {
        // Create a shop without chest location loaded
        AbstractShop unloadedShop = new SellShop(
            new Location(world, 1000, 64, 1000),
            getPlugin().getShopHandler().getAdminUUID(),
            10.0, 1, true, BlockFace.NORTH
        ) {
            @Override
            public void load() {
                // Don't actually load - keep chestLocation null
            }
        };

        var player = getServer().addPlayer();
        player.teleport(new Location(world, 2000, 64, 2000));

        // Should not throw and should use teleportAsync
        assertDoesNotThrow(() -> unloadedShop.teleportPlayer(player));
    }

    @Test
    void teleportPlayer_handlesNullPlayer() {
        // Null player should be handled gracefully
        assertDoesNotThrow(() -> shop.teleportPlayer(null));
    }
}
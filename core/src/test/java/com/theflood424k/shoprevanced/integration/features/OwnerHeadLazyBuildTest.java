package com.theflood424k.shoprevanced.integration.features;

import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner heads must not be built while shops are added: on Paper each build of an untextured head sends a
 * Mojang profile lookup, and building one per ShopRevanced floods the session server at startup.
 */
@Tag("integration")
public class OwnerHeadLazyBuildTest extends BaseMockBukkitTest {

    @Test
    void adding_shops_defers_owner_head_until_requested() throws Exception {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();

        ShopCreationChestTest.createShop(server, getPlugin(), player, world, 40, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");
        ShopCreationChestTest.createShop(server, getPlugin(), player, world, 42, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        assertTrue(playerHeads().isEmpty(), "No owner head should be built while shops are added");

        ItemStack head = getPlugin().getGuiHandler().getPlayerHeadIcon(player.getUniqueId());
        assertEquals(Material.PLAYER_HEAD, head.getType());
        assertSame(head, getPlugin().getGuiHandler().getPlayerHeadIcon(player.getUniqueId()), "Head should be built once, then cached");
        assertEquals(1, getPlugin().getGuiHandler().getShopOwnerHeads().size());
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> playerHeads() throws Exception {
        Field field = getPlugin().getGuiHandler().getClass().getDeclaredField("playerHeads");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(getPlugin().getGuiHandler());
    }
}



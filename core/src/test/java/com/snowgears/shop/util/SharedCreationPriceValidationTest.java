package com.snowgears.shop.util;

import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SharedCreationPriceValidationTest extends BaseMockBukkitTest {

    @ParameterizedTest
    @CsvSource({"COMBO, 0, false", "COMBO, -1, false", "COMBO, 5, true",
            "SELL, -1, false", "SELL, 0, true", ", -1, false", ", 0, true"})
    void sharedCreationValidatesSecondaryPrice(ShopType type, double secondary, boolean valid) {
        var world = getServer().addSimpleWorld("world");
        var player = addStubbedOpPlayer("Owner");
        var chest = world.getBlockAt(10, 65, 10);
        chest.setType(Material.CHEST);
        var sign = chest.getRelative(BlockFace.NORTH);
        sign.setType(Material.OAK_WALL_SIGN);

        var shop = getPlugin().getShopCreationUtil().createShop(player, chest, sign,
                new PricePair(10, secondary), 1, false, type, BlockFace.NORTH, false);

        if (valid) {
            assertNotNull(shop);
        } else {
            assertNull(shop);
            assertNull(getPlugin().getShopHandler().getShop(sign.getLocation()));
            assertEquals(Material.CHEST, chest.getType());
            assertEquals(Material.OAK_WALL_SIGN, sign.getType());
        }
    }
}

package com.snowgears.shop.shop;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.EconomyUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class ZeroPriceStockTest extends BaseMockBukkitTest {

    @ParameterizedTest
    @CsvSource({"false, false, 0", "false, false, 100", "false, true, 0", "false, true, 100",
            "true, false, 0", "true, false, 100", "true, true, 0", "true, true, 100"})
    void storedZeroPriceHasNoStock(boolean combo, boolean partialSales, double funds) {
        setConfig("allowPartialSales", partialSales);
        var owner = addStubbedPlayer("Owner");
        Location sign = new Location(getServer().addSimpleWorld("world"), 10, 65, 10);
        AbstractShop shop = combo
                ? new ComboShop(sign, owner.getUniqueId(), 0, 5, 4, false, BlockFace.NORTH)
                : new BuyShop(sign, owner.getUniqueId(), 0, 4, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shop.setStock(5);
        try (MockedStatic<EconomyUtils> economy = mockStatic(EconomyUtils.class)) {
            economy.when(() -> EconomyUtils.getFunds(shop.getOwner(), shop.getInventory())).thenReturn(funds);
            assertEquals(0, shop.calculateStock());
            assertEquals(0, shop.getStock());
        }
    }
}

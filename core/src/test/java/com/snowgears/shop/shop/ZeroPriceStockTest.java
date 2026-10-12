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

    /**
     * Verifies that a stored zero buy price cannot create buying capacity.
     *
     * @param combo whether to exercise a combo shop instead of a buy shop
     * @param partialSales whether partial sales are enabled
     * @param funds the owner's available funds
     */
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

    @ParameterizedTest
    @CsvSource({"false, 8, 2", "true, 8, 2", "false, 1, 0", "true, 1, 1", "true, 0, 0"})
    void zeroBuyPriceStillCountsSellInventory(boolean partialSales, int items, int expectedStock) {
        setConfig("allowPartialSales", partialSales);
        var owner = addStubbedPlayer("Owner");
        Location sign = new Location(getServer().addSimpleWorld("world"), 10, 65, 10);
        sign.getWorld().loadChunk(sign.getBlockX() >> 4, sign.getBlockZ() >> 4);
        var chest = sign.getBlock().getRelative(BlockFace.SOUTH);
        chest.setType(Material.CHEST);
        ComboShop shop = new ComboShop(sign, owner.getUniqueId(), 0, 5, 4, false, BlockFace.NORTH);
        shop.chestLocation = chest.getLocation();
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        if (items > 0) {
            shop.getInventory().addItem(new ItemStack(Material.DIAMOND, items));
        }
        shop.setStock(5);
        try (MockedStatic<EconomyUtils> economy = mockStatic(EconomyUtils.class)) {
            assertEquals(expectedStock, shop.calculateStock());
            assertEquals(expectedStock, shop.getStock());
            economy.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void adminZeroPriceKeepsUnlimitedStock(boolean combo, boolean partialSales) {
        setConfig("allowPartialSales", partialSales);
        var owner = addStubbedPlayer("Owner");
        Location sign = new Location(getServer().addSimpleWorld("world"), 10, 65, 10);
        AbstractShop shop = combo
                ? new ComboShop(sign, owner.getUniqueId(), 0, 0, 4, true, BlockFace.NORTH)
                : new BuyShop(sign, owner.getUniqueId(), 0, 4, true, BlockFace.NORTH);
        shop.setStock(0);

        try (MockedStatic<EconomyUtils> economy = mockStatic(EconomyUtils.class)) {
            assertEquals(Integer.MAX_VALUE, shop.calculateStock());
            assertEquals(Integer.MAX_VALUE, shop.getStock());
            economy.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, 3, true, 2, 0", "0, 3, true, 3, 1",
            "0, 3, false, 3, 0", "0, 3, false, 8, 1",
            "0, 3, true, 16, 2", "0, 0, true, 1, 0",
            "0, -3, true, 1, 0", "0, 0, true, 8, 1",
            "100, 3, true, 2, 0", "100, 3, true, 3, 1"
    })
    void partialSellingStockUsesSellPriceAndRoundsUp(double buyPrice, double sellPrice,
                                                    boolean partialSales, int items, int expectedStock) {
        setConfig("allowPartialSales", partialSales);
        var owner = addStubbedPlayer("Owner");
        var world = getServer().addSimpleWorld("world");
        Location sign = new Location(world, 10, 65, 10);
        world.loadChunk(0, 0);
        var chest = sign.getBlock().getRelative(BlockFace.SOUTH);
        chest.setType(Material.CHEST);
        ComboShop shop = new ComboShop(sign, owner.getUniqueId(), buyPrice, sellPrice, 8, false, BlockFace.NORTH);
        shop.chestLocation = chest.getLocation();
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shop.getInventory().addItem(new ItemStack(Material.DIAMOND, items));
        // Unrelated items must not satisfy the partial-selling threshold.
        shop.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        shop.setStock(99);

        // Default zero funds forces the sell-stock fallback even with a positive buy price.
        try (MockedStatic<EconomyUtils> economy = mockStatic(EconomyUtils.class)) {
            assertEquals(expectedStock, shop.calculateStock());
            assertEquals(expectedStock, shop.getStock());
            if (buyPrice == 0) economy.verifyNoInteractions();
        }
    }

}

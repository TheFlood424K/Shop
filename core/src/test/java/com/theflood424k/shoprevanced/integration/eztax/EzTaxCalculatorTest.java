package com.theflood424k.shoprevanced.integration.eztax;

import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EzTaxCalculator}.
 * Covers location-based rates, tax-exempt customers and products, and the
 * zero-tax guard cases.
 */
class EzTaxCalculatorTest extends BaseMockBukkitTest {

    private AbstractShop shop(Material material) {
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.getItemStack()).thenReturn(new ItemStack(material));
        return shop;
    }

    private Player player(String uuid, String worldName) {
        WorldMock world = mock(WorldMock.class);
        when(world.getName()).thenReturn(worldName);
        PlayerMock player = mock(PlayerMock.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.fromString(uuid));
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    @Test
    void calculateTax_defaultRateApplied() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08); // 8%

        double tax = calc.calculateTax(player("11111111-1111-1111-1111-111111111111", "world"),
                shop(Material.DIRT), 1, 100.0);

        assertEquals(8.0, tax);
    }

    @Test
    void calculateTax_worldRateOverrideTakesPrecedence() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);
        calc.setWorldRate("nether", 0.05); // 5% in the nether

        double tax = calc.calculateTax(player("22222222-2222-2222-2222-222222222222", "nether"),
                shop(Material.DIRT), 2, 100.0);

        // 100 * 2 * 0.05 = 10.0
        assertEquals(10.0, tax);
    }

    @Test
    void calculateTax_exemptCustomerPermission_paysNoTax() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);

        Player player = player("33333333-3333-3333-3333-333333333333", "world");
        when(player.hasPermission("eztax.exempt")).thenReturn(true);

        double tax = calc.calculateTax(player, shop(Material.DIRT), 1, 100.0);

        assertEquals(0.0, tax);
    }

    @Test
    void calculateTax_exemptCustomerUuidSet_paysNoTax() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);
        calc.addExemptCustomer("44444444-4444-4444-4444-444444444444");

        double tax = calc.calculateTax(player("44444444-4444-4444-4444-444444444444", "world"),
                shop(Material.DIRT), 1, 100.0);

        assertEquals(0.0, tax);
    }

    @Test
    void calculateTax_exemptProduct_paysNoTax() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);
        calc.addExemptProduct(Material.DIAMOND);

        double tax = calc.calculateTax(player("55555555-5555-5555-5555-555555555555", "world"),
                shop(Material.DIAMOND), 1, 100.0);

        assertEquals(0.0, tax);
    }

    @Test
    void calculateTax_nonExemptProduct_stillTaxed() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.10);
        calc.addExemptProduct(Material.DIAMOND);

        double tax = calc.calculateTax(player("66666666-6666-6666-6666-666666666666", "world"),
                shop(Material.DIRT), 1, 100.0);

        assertEquals(10.0, tax);
    }

    @Test
    void calculateTax_zeroAmountOrPrice_returnsZero() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);

        assertEquals(0.0, calc.calculateTax(player("77777777-7777-7777-7777-777777777777", "world"),
                shop(Material.DIRT), 0, 100.0));
        assertEquals(0.0, calc.calculateTax(player("77777777-7777-7777-7777-777777777777", "world"),
                shop(Material.DIRT), 1, 0.0));
        assertEquals(0.0, calc.calculateTax(player("77777777-7777-7777-7777-777777777777", "world"),
                shop(Material.DIRT), 1, -5.0));
    }

    @Test
    void calculateTax_nullPlayer_usesDefaultRate() {
        EzTaxCalculator calc = new EzTaxCalculator();
        calc.setDefaultRate(0.08);

        // No customer exemptions apply when player is null; default rate used.
        double tax = calc.calculateTax(null, shop(Material.DIRT), 1, 100.0);
        assertEquals(8.0, tax);
    }
}

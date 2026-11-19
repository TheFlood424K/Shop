package com.theflood424k.shoprevanced.integration.eztax;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EzTaxIntegration}.
 * Covers graceful fallback when ezTax is unavailable/disabled, tax recording,
 * report generation, and tax-breakdown messaging.
 * <p>
 * ezTax is a soft integration: {@link EzTaxIntegration} is only enabled when the
 * ezTax plugin is installed <i>and</i> {@code ezTax.enabled} is true in config.
 * Because MockBukkit's {@code PluginManagerMock} has no {@code addPlugin}, the
 * enabled state is flipped directly via reflection for the enabled-path tests.
 */
class EzTaxIntegrationTest extends BaseMockBukkitTest {

    private AbstractShop shop() {
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.getOwnerUUID()).thenReturn(java.util.UUID.randomUUID());
        when(shop.getSignLocation()).thenReturn(
                new org.bukkit.Location(getServer().getWorld("world"), 0, 0, 0));
        when(shop.getItemStack()).thenReturn(new ItemStack(org.bukkit.Material.DIRT));
        return shop;
    }

    /**
     * Flips the private {@code enabled} flag to simulate the ezTax plugin being
     * present (or absent) without needing to register a plugin in MockBukkit.
     */
    private void setEnabled(EzTaxIntegration integration, boolean value) {
        try {
            Field field = EzTaxIntegration.class.getDeclaredField("enabled");
            field.setAccessible(true);
            field.set(integration, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set 'enabled' field on EzTaxIntegration", e);
        }
    }

    @Test
    void gracefulFallback_ezTaxDisabled_returnsZeroTax() {
        // ezTax.enabled defaults to false; no ezTax plugin needed.
        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());

        assertFalse(integration.isEnabled());
        assertEquals(0.0, integration.calculateTax(
                getServer().addPlayer("buyer"), shop(), 1, 100.0));
    }

    @Test
    void gracefulFallback_ezTaxPluginMissing_returnsZeroTax() {
        // Enabled in config but ezTax plugin not installed -> disabled, zero tax.
        getPlugin().getConfig().set("ezTax.enabled", true);

        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());

        assertFalse(integration.isEnabled());
        assertEquals(0.0, integration.calculateTax(
                getServer().addPlayer("buyer"), shop(), 1, 100.0));
    }

    @Test
    void recordTaxAndGenerateReport_whenEnabled_recordsTransaction() {
        getPlugin().getConfig().set("ezTax.enabled", true);

        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());
        setEnabled(integration, true); // simulate ezTax plugin present
        assertTrue(integration.isEnabled());

        PlayerMock buyer = getServer().addPlayer("buyer");
        integration.recordTax(buyer, shop(), 100.0, 8.0);

        EzTaxReport report = integration.getReport();
        assertEquals(1, report.getTransactionCount());
        assertEquals(8.0, report.getTotalTaxCollected(), 0.001);

        String exported = report.generateReport();
        assertTrue(exported.contains("ezTax Sales Tax Report"));
        assertTrue(exported.contains("buyer"));
        assertTrue(exported.contains("108.00"));
    }

    @Test
    void recordTax_whenDisabled_isNoOp() {
        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());
        assertFalse(integration.isEnabled());

        integration.recordTax(getServer().addPlayer("buyer"), shop(), 100.0, 8.0);

        EzTaxReport report = integration.getReport();
        assertEquals(0, report.getTransactionCount());
    }

    @Test
    void reportClear_removesAllRecords() {
        getPlugin().getConfig().set("ezTax.enabled", true);

        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());
        setEnabled(integration, true);
        integration.recordTax(getServer().addPlayer("buyer"), shop(), 100.0, 8.0);

        integration.getReport().clear();
        assertEquals(0, integration.getReport().getTransactionCount());
        assertEquals(0.0, integration.getReport().getTotalTaxCollected());
    }

    @Test
    void displayTaxBreakdown_zeroTax_sendsNoMessage() {
        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());
        PlayerMock buyer = getServer().addPlayer("buyer");

        integration.displayTaxBreakdown(buyer, 100.0, 0.0, 100.0);

        assertNull(buyer.nextComponentMessage());
    }

    @Test
    void displayTaxBreakdown_whenEnabled_sendsBreakdownMessage() {
        getPlugin().getConfig().set("ezTax.enabled", true);

        EzTaxIntegration integration = new EzTaxIntegration(getPlugin());
        setEnabled(integration, true);

        PlayerMock buyer = getServer().addPlayer("buyer");
        integration.displayTaxBreakdown(buyer, 100.0, 8.0, 108.0);

        net.kyori.adventure.text.Component message = buyer.nextComponentMessage();
        assertNotNull(message);
        String serialized = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacy('§')
                .serialize(message);
        assertTrue(serialized.contains("ezTax"));
        assertTrue(serialized.contains("108.00"));
    }
}

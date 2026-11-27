package com.theflood424k.shoprevanced.pricing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import com.theflood424k.shoprevanced.testsupport.TestReflection;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class AutoPricingServiceTest extends BaseMockBukkitTest {

    private AutoPricingService service;
    private ShopRevancedHandler mockHandler;
    private MarketDataProvider mockProvider;

    @BeforeEach
    void setUp() {
        service = new AutoPricingService(getPlugin());
        mockHandler = mock(ShopRevancedHandler.class);
        mockProvider = mock(MarketDataProvider.class);
        TestReflection.setField(service, "shopHandler", mockHandler);
        service.setMarketDataProvider(mockProvider);
    }

    private AbstractShop mockShop(double price, boolean admin, boolean autoPricingEnabled, ItemStack item) {
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.getPrice()).thenReturn(price);
        when(shop.isAdmin()).thenReturn(admin);
        when(shop.isAutoPricingEnabled()).thenReturn(autoPricingEnabled);
        when(shop.getItemStack()).thenReturn(item);
        when(shop.getId()).thenReturn(UUID.randomUUID());
        return shop;
    }

    @Test
    void disabledByDefault() {
        assertFalse(service.isEnabled());
    }

    @Test
    void runPricingCycleWhenDisabledDoesNothing() {
        TestReflection.setField(service, "enabled", false);
        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(10.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
        verify(shop, never()).setPrice(anyDouble());
    }

    @Test
    void priceAdjustedWhenWithinBounds() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(110.0));

        service.runPricingCycle();

        verify(shop).setPrice(110.0);
        verify(shop).setNeedsSave(true);
    }

    @Test
    void priceClampedToMaxAdjustment() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(200.0));

        service.runPricingCycle();

        verify(shop).setPrice(115.0);
        verify(shop).setNeedsSave(true);
    }

    @Test
    void priceClampedToMinAdjustment() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(50.0));

        service.runPricingCycle();

        verify(shop).setPrice(85.0);
        verify(shop).setNeedsSave(true);
    }

    @Test
    void noChangeWhenMarketPriceEqualsCurrentPrice() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(100.0));

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void noChangeWhenMarketPriceChangeIsNegligible() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(100.003));

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void gracefulFailureWhenMarketUnavailable() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.empty());

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void gracefulFailureWhenProviderThrows() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenThrow(new RuntimeException("market down"));

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void adminShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, true, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(50.0));

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void optOutShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, false, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(mockProvider.getMarketPrice(item)).thenReturn(Optional.of(50.0));

        service.runPricingCycle();

        verify(shop, never()).setPrice(anyDouble());
        verify(shop, never()).setNeedsSave(anyBoolean());
    }

    @Test
    void nullItemShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        AbstractShop shop = mockShop(100.0, false, true, null);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
        verify(shop, never()).setPrice(anyDouble());
    }

    @Test
    void airItemShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        AbstractShop shop = mockShop(100.0, false, true, new ItemStack(Material.AIR));
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
        verify(shop, never()).setPrice(anyDouble());
    }

    @Test
    void zeroPriceShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(0.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
        verify(shop, never()).setPrice(anyDouble());
    }

    @Test
    void negativePriceShopIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(-5.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
        verify(shop, never()).setPrice(anyDouble());
    }

    @Test
    void emptyShopListDoesNotCrash() {
        TestReflection.setField(service, "enabled", true);
        when(mockHandler.getAllShops()).thenReturn(List.of());

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
    }

    @Test
    void nullShopInListIsSkipped() {
        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        when(mockHandler.getAllShops()).thenReturn(Arrays.asList((AbstractShop) null));

        service.runPricingCycle();

        verify(mockProvider, never()).getMarketPrice(any());
    }

    @Test
    void setMarketDataProviderReplacesProvider() {
        MarketDataProvider custom = mock(MarketDataProvider.class);
        service.setMarketDataProvider(custom);

        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);

        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));
        when(custom.getMarketPrice(item)).thenReturn(Optional.of(110.0));

        service.runPricingCycle();

        verify(shop).setPrice(110.0);
        verify(mockProvider, never()).getMarketPrice(any());
    }

    @Test
    void setMarketDataProviderNullFallsBackToSimulated() {
        service.setMarketDataProvider(null);

        TestReflection.setField(service, "enabled", true);
        TestReflection.setField(service, "maxAdjustmentPercent", 15.0);
        ItemStack item = new ItemStack(Material.DIAMOND);
        AbstractShop shop = mockShop(100.0, false, true, item);
        when(mockHandler.getAllShops()).thenReturn(List.of(shop));

        service.runPricingCycle();

        // Simulated provider returns a price for DIAMOND; verify adjustment happened
        verify(shop).setPrice(anyDouble());
        verify(shop).setNeedsSave(true);
    }
}

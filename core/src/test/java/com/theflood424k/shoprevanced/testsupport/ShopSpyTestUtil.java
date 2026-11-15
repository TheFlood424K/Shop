package com.theflood424k.shoprevanced.testsupport;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerInteractEvent;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Test helper for replacing a registered ShopRevanced instance with a Mockito spy.
 * <p>
 * This is used to verify whether {@link AbstractShop#executeClickAction(PlayerInteractEvent, ShopClickType)}
 * was called by {@link com.theflood424k.shoprevanced.listener.ShopRevancedListener}.
 */
public final class ShopSpyTestUtil {

    private ShopSpyTestUtil() {}

    public static AbstractShop spyAndReplace(ShopRevanced plugin, AbstractShop realShop) {
        try {
            var handler = plugin.getShopRevancedHandler();
            // Reflectively access storage field, then allShops map within storage
            var storageField = handler.getClass().getDeclaredField("storage");
            storageField.setAccessible(true);
            var storage = storageField.get(handler);
            var allShopsField = storage.getClass().getDeclaredField("allShops");
            allShopsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Location, AbstractShop> map = (ConcurrentHashMap<Location, AbstractShop>) allShopsField.get(storage);

            AbstractShop spy = spy(realShop);
            // Keep behavior deterministic if called accidentally.
            when(spy.executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class))).thenReturn(true);
            map.put(realShop.getSignLocation(), spy);
            return spy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace ShopRevanced with spy", e);
        }
    }
}








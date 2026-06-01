package com.snowgears.shop.testsupport;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerInteractEvent;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Test helper for replacing a registered shop instance with a Mockito spy.
 * <p>
 * This is used to verify whether {@link AbstractShop#executeClickAction(PlayerInteractEvent, ShopClickType)}
 * was called by {@link com.snowgears.shop.listener.ShopListener}.
 */
public final class ShopSpyTestUtil {

    private ShopSpyTestUtil() {}

    public static AbstractShop spyAndReplace(Shop plugin, AbstractShop realShop) {
        try {
            var handler = plugin.getShopHandler();
            Field f = handler.getClass().getDeclaredField("allShops");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Location, AbstractShop> map = (ConcurrentHashMap<Location, AbstractShop>) f.get(handler);

            AbstractShop spy = spy(realShop);
            // Keep behavior deterministic if called accidentally.
            when(spy.executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class))).thenReturn(true);
            map.put(realShop.getSignLocation(), spy);
            return spy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace shop with spy", e);
        }
    }
}



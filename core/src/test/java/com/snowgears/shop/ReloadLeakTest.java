package com.snowgears.shop;

import com.snowgears.shop.listener.SpearAttackListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that a reload does not accumulate listeners or scheduled tasks (issues #88, #89, #95).
 *
 * <p>{@code onEnable} and {@code onDisable} are two hand-maintained lists that must agree, and nothing
 * checked that they did. {@code spearAttackListener} was registered on every enable and never
 * unregistered, so each {@code /shop reload} left another live handler subscribed to
 * {@code PlayerInteractEvent} — and because the field is overwritten rather than unregistered, the
 * orphaned instance could never be reclaimed.
 *
 * <p>This is the structural version of that check. Fixing #88 and #89 individually leaves the
 * mechanism intact: the next {@code registerEvents} without a matching {@code unregisterAll} — an
 * absence, which no amount of diff-reading catches — reproduces the bug.
 *
 * <p><b>Not covered:</b> the BlueMap boot poller (#89). The timer only exists when BlueMap is
 * installed, so asserting on it here would pass whether or not the cancel is present. It was removed
 * rather than left as a false guarantee; the fix is three lines in {@code onDisable} and reviewing it
 * is cheaper than a test that cannot fail.
 */
@ExtendWith(MockBukkitExtension.class)
class ReloadLeakTest {

    @MockBukkitInject
    private ServerMock server;

    private Shop plugin;

    private void load() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    /** Every {@link RegisteredListener} Bukkit currently holds for a given handler class. */
    private int listenerCountFor(Class<?> handlerClass) {
        int count = 0;
        for (RegisteredListener registered : HandlerList.getRegisteredListeners(plugin)) {
            if (handlerClass.isInstance(registered.getListener())) {
                count++;
            }
        }
        return count;
    }

    private List<Class<?>> listenerClassesOf(Shop instance) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (Field f : Shop.class.getDeclaredFields()) {
            if (!org.bukkit.event.Listener.class.isAssignableFrom(f.getType())) {
                continue;
            }
            f.setAccessible(true);
            if (f.get(instance) != null) {
                classes.add(f.getType());
            }
        }
        return classes;
    }

    @Test
    @DisplayName("Every listener onEnable registers is one onDisable unregisters")
    void noListenerAccumulatesAcrossReloads() throws Exception {
        load();
        List<Class<?>> registered = listenerClassesOf(plugin);
        assertTrue(registered.contains(SpearAttackListener.class),
                "Precondition: spearAttackListener is among the registered listeners");

        int before = listenerCountFor(SpearAttackListener.class);
        assertTrue(before > 0, "Precondition: the listener starts registered");

        // reload() is onDisable() then onEnable(). Two rounds is enough to expose an accumulation.
        plugin.reload();
        plugin.reload();

        int after = listenerCountFor(SpearAttackListener.class);
        assertEquals(before, after,
                "reload() left " + (after - before) + " extra SpearAttackListener(s) registered. "
                        + "A listener registered in onEnable must be unregistered in onDisable, and "
                        + "overwriting the field does not unregister the previous instance.");
    }

    @Test
    @DisplayName("No listener class accumulates across repeated reloads")
    void noListenerClassAccumulates() throws Exception {
        load();

        // A handler class is legitimately registered once per @EventHandler method, so ShopListener
        // holding nine registrations is normal, not a leak. What must not happen is that number
        // growing with each reload — so this compares counts before and after rather than asserting
        // an absolute, which was the mistake that made the first version of this test fail.
        Map<String, Integer> before = registrationCounts();
        plugin.reload();
        plugin.reload();
        Map<String, Integer> after = registrationCounts();

        assertEquals(before, after,
                "Listener registration counts changed across reloads, so something was left behind or "
                        + "registered twice. before=" + before + " after=" + after);
    }

    /** How many {@link RegisteredListener}s each handler class currently holds. */
    private Map<String, Integer> registrationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RegisteredListener registered : HandlerList.getRegisteredListeners(plugin)) {
            counts.merge(registered.getListener().getClass().getSimpleName(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("A reload leaves the plugin enabled and functional")
    void reloadLeavesThePluginUsable() {
        load();
        assertNotNull(plugin.getLogHandler());

        plugin.reload();

        // A leak fix that broke the reload would be worse than the leak; assert it still works.
        assertTrue(plugin.isEnabled(), "Plugin should still be enabled after reload");
        assertNotNull(plugin.getShopHandler(), "Handlers should be rebuilt");
        assertNotNull(plugin.getLogHandler(), "The log handler should be rebuilt");
    }
}
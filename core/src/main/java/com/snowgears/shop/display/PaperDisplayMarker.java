package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks entities created by the Paper display implementation.
 *
 * <p>The marker intentionally uses a different key and data type from the
 * legacy {@code display: 1} cleanup marker. Cleanup code can use
 * {@link #isActiveDisplay(Entity)} to avoid deleting active Paper displays.</p>
 */
public final class PaperDisplayMarker {

    private static final String ACTIVE_DISPLAY_KEY = "paper_display_active";

    private PaperDisplayMarker() {
    }

    /** Marks an entity as an active Paper shop display entity. */
    public static void mark(Entity entity) {
        if (entity == null || Shop.getPlugin() == null) {
            return;
        }

        entity.getPersistentDataContainer().set(activeKey(), PersistentDataType.BYTE, (byte) 1);
    }

    /** Returns whether an entity is an active Paper shop display entity. */
    public static boolean isActiveDisplay(Entity entity) {
        if (entity == null || Shop.getPlugin() == null) {
            return false;
        }

        Byte marker = entity.getPersistentDataContainer().get(activeKey(), PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    /** Alias describing the representation used by the display package. */
    public static boolean isPaperDisplay(Entity entity) {
        return isActiveDisplay(entity);
    }

    private static NamespacedKey activeKey() {
        return new NamespacedKey(Shop.getPlugin(), ACTIVE_DISPLAY_KEY);
    }
}

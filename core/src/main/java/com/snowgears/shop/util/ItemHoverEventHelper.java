package com.snowgears.shop.util;

import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Creates Paper/Adventure SHOW_ITEM hover events from a Bukkit ItemStack.
 *
 * Behavior:
 * - Uses Adventure's HoverEvent.showItem(ItemStack), which leverages Paper's
 *   item serialization to emit {id,count,components} for modern clients.
 *
 * Requirements:
 * - Paper API providing item serialization.
 *
 * Example:
 *   HoverEvent hover = ItemHoverEventHelper.createFrom(itemStack);
 *   component.hoverEvent(hover);
 */
public final class ItemHoverEventHelper {

    private ItemHoverEventHelper() {}

    /**
     * Build a SHOW_ITEM hover event for the provided Bukkit ItemStack.
     *
     * @param bukkitItem Bukkit ItemStack
     * @return HoverEvent with SHOW_ITEM action suitable for modern clients
     */
    public static HoverEvent createFrom(final ItemStack bukkitItem) {
        if (bukkitItem == null) {
            return null;
        }
        // Bukkit ItemStack implements HoverEventSource<ShowItem>; Paper serializes it internally
        return bukkitItem.asHoverEvent(op -> op);
    }

    // Legacy fallback kept for API compatibility; delegates to createFrom.
    public static HoverEvent createFromLegacy(final ItemStack bukkitItem) {
        return createFrom(bukkitItem);
    }
}



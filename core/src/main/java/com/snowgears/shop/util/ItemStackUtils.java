package com.snowgears.shop.util;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStackUtils {

    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
    }

    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        return Bukkit.getUnsafe().deserializeItemFromJson(data);
    }

    /**
     * Returns a defensive clone of {@code item} with any custom font cleared from the
     * display-name component.  This is used before item-list comparisons so that items
     * whose display names were set with a custom {@link net.kyori.adventure.text.format.Style}
     * font (e.g. via resource-pack font packs) are not accidentally blocked when the list
     * entry carries the same name/type but no custom font.
     *
     * <p>The original stack is never mutated.</p>
     *
     * @param item the item to normalise — may be {@code null}
     * @return a clone with the display-name font stripped, or {@code null} if {@code item} is {@code null}
     */
    public static ItemStack stripFontFromItem(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        if (meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                // Rebuild the component without a font so equality checks ignore font keys.
                meta.displayName(name.font(null));
            }
        }
        copy.setItemMeta(meta);
        return copy;
    }
}

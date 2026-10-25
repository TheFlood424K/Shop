package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ItemStackUtils {

    // Serialize an ItemStack to a JsonObject
    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        if (Shop.getPlugin().getFoliaLib().isPaper() || Shop.getPlugin().getFoliaLib().isFolia()) {
            return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
        }
        // Non-paper servers not supported in this version
        return null;
    }

    // Deserialize a JsonObject to an ItemStack
    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        if (Shop.getPlugin().getFoliaLib().isPaper() || Shop.getPlugin().getFoliaLib().isFolia()) {
            return Bukkit.getUnsafe().deserializeItemFromJson(data);
        }
        // Non-paper servers not supported in this version
        return null;
    }
}
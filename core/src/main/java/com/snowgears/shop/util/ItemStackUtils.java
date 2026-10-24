package com.snowgears.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ItemStackUtils {

    // Serialize an ItemStack to a JsonObject
    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
    }

    // Deserialize a JsonObject to an ItemStack
    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        return Bukkit.getUnsafe().deserializeItemFromJson(data);
    }
}

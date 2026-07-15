package com.snowgears.shop.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class ItemStackUtils {
    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
    }

    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        return Bukkit.getUnsafe().deserializeItemFromJson(data);
    }
}

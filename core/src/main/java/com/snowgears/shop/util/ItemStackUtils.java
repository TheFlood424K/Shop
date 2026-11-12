package com.snowgears.shop.util;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Item serialization helpers backed by Paper's public API.
 */
public final class ItemStackUtils {

    private ItemStackUtils() {
    }

    /**
     * Serializes an item stack to Minecraft's component-based JSON format.
     *
     * @param itemStack item stack to serialize
     * @return serialized item data
     */
    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
    }

    /**
     * Deserializes an item stack from Minecraft's component-based JSON format.
     *
     * @param data serialized item data
     * @return deserialized item stack
     * @throws IllegalArgumentException when the data is invalid
     */
    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        return Bukkit.getUnsafe().deserializeItemFromJson(data);
    }
}

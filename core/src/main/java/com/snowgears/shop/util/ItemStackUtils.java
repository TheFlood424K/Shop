package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

import com.mojang.serialization.JsonOps;
import java.util.Optional;

public class ItemStackUtils {
    private static RegistryAccess.Frozen registryAccess;
    private static RegistryOps<JsonElement> itemSerializationCodec;

    // Initialize the registryAccess and itemSerializationCodec once
    public static void init() {
        if (registryAccess == null) {
            registryAccess = MinecraftServer.getServer().registryAccess();
        }
        if (itemSerializationCodec == null) {
            itemSerializationCodec = registryAccess.createSerializationContext(JsonOps.INSTANCE);
        }
    }

    // Serialize an ItemStack to a JsonObject
    public static JsonObject serializeItemAsJson(ItemStack itemStack) {
        if (Shop.getPlugin().getFoliaLib().isPaper()) {
            return Bukkit.getUnsafe().serializeItemAsJson(itemStack);
        }
        init();
        // non-paper servers, hit nms code directly
        JsonObject item;
        try {
            net.minecraft.world.item.ItemStack mcItem = Shop.getPlugin().getNmsBullshitHandler().getMCItemStack(itemStack);
            final Optional<JsonElement> encoded = net.minecraft.world.item.ItemStack.CODEC.encodeStart(itemSerializationCodec, mcItem).result();
            item = encoded.orElseThrow(() -> new RuntimeException("Failed to encode ItemStack: codec returned empty")).getAsJsonObject();
            item.addProperty("DataVersion", Bukkit.getUnsafe().getDataVersion());
            return item;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Deserialize a JsonObject to an ItemStack
    public static ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        if (Shop.getPlugin().getFoliaLib().isPaper()) {
            return Bukkit.getUnsafe().deserializeItemFromJson(data);
        }
        throw new UnsupportedOperationException("Item deserialization from JSON is only supported on Paper servers");
    }
}

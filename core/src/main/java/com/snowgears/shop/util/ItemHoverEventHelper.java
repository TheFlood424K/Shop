package com.snowgears.shop.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.snowgears.shop.Shop;

import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Content;
import org.bukkit.inventory.ItemStack;

/**
 * Creates Bungee SHOW_ITEM hover events from a Bukkit ItemStack.
 *
 * Behavior:
 * - Uses Paper's public serialization API, which emits {id,count,components} for modern clients.
 * - MockBukkit uses the legacy Bungee item content because it does not expose Paper's unsafe serializer.
 *
 * Requirements:
 * - Paper API providing Bukkit.getUnsafe().serializeItemAsJson(ItemStack)
 * - Bungee chat serializer 1.21.5+ will inline show_item payloads automatically.
 *
 * Notes:
 * - Components path preserves enchantments, names, lore, and custom data in hover.
 *
 * Example:
 *   HoverEvent hover = ItemHoverEventHelper.createFrom(itemStack);
 *   component.setHoverEvent(hover);
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
        if (Shop.getPlugin().isMockBukkit()) {
            return new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                    new net.md_5.bungee.api.chat.hover.content.Item(
                            bukkitItem.getType().getKey().toString(), bukkitItem.getAmount(), null));
        }

        try {
            JsonObject obj = ItemStackUtils.serializeItemAsJson(bukkitItem);
            if (obj != null) {
                return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverItem(obj));
            }
        } catch (Exception ignored) {
            // Fall back to legacy content if the server cannot serialize this item.
        }
        return createFromLegacy(bukkitItem);
    }

    // Old `nbt` Tag system if we are below 1.20.5
    public static HoverEvent createFromLegacy(final ItemStack bukkitItem) {
        final String id = bukkitItem.getType().getKey().toString();
        final int count = bukkitItem.getAmount();
        String nbt = null;
        try {
            nbt = bukkitItem.getItemMeta().getAsString();
            final ItemTag tag = !nbt.isEmpty() ? ItemTag.ofNbt(nbt) : null;
            return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item(id, count, tag));
        } catch (Exception ignored) {
            // Some servers may not expose ItemMeta#getAsString; continue with null nbt
            // If this is an issue for you, take a look at using Item NBT API to get the nbt string
        }
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item(id, count, null));
    }

    /** Minimal show_item content carrying components for modern clients. */
    @SuppressWarnings("unused")
    public static final class HoverItem extends Content {
        private final String id;
        private final int count;
        private final JsonElement components;

        public HoverItem(final JsonObject itemJson) {
            this.id = itemJson.get("id").getAsString();
            this.count = itemJson.get("count").getAsInt();
            this.components = itemJson.get("components");
        }

        @Override
        public HoverEvent.Action requiredAction() {
            return HoverEvent.Action.SHOW_ITEM;
        }
    }
}



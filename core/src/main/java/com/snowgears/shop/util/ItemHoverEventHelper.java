package com.snowgears.shop.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.snowgears.shop.Shop;

import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Content;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Creates Bungee SHOW_ITEM hover events from a Bukkit ItemStack.
 *
 * Behavior:
 * - Paper 1.20.5+: Uses Paper’s serializeItemAsJson(..) which emits {id,count,components} on modern clients.
 * - Older/Non-Paper: Falls back to legacy NBT using ItemMeta#getAsString() → ItemTag (may miss components).
 *
 * Requirements:
 * - Paper API providing Bukkit.getUnsafe().serializeItemAsJson(ItemStack)
 * - Bungee chat serializer 1.21.5+ will inline show_item payloads automatically.
 *
 * Notes:
 * - This helper avoids NMS/CraftBukkit compile deps; relies only on Paper/Bukkit API.
 * - Components path preserves enchantments, names, lore, custom_data in hover.
 * - Fallback is best-effort for pre-1.20.5 servers or when components are unavailable.
 *
 * Example:
 *   HoverEvent hover = ItemHoverEventHelper.createFrom(itemStack);
 *   component.setHoverEvent(hover);
 */
public final class ItemHoverEventHelper {

    private ItemHoverEventHelper() {}

    /**
     * Build a SHOW_ITEM hover event for the provided Bukkit ItemStack.
     * Prefers modern components; falls back to legacy NBT tag if unavailable.
     *
     * @param bukkitItem Bukkit ItemStack
     * @return HoverEvent with SHOW_ITEM action suitable for modern clients
     */
    public static HoverEvent createFrom(final ItemStack bukkitItem) {
        if (Shop.getPlugin().isMockBukkit()) { return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new net.md_5.bungee.api.chat.hover.content.Item(bukkitItem.getType().getKey().toString(), bukkitItem.getAmount(), null)); }

        // If we are 1.20.5+, we have to use the new Item Components Data system
        // If we are below 1.20.5, we have to use the old NBT tag system
        if (MCVersion.atLeast("1.20.5")) {
            try {
                // Note: `serializeItemAsJson` is a Paper API method
                JsonObject obj = null;
                // If we are paper, we can use the getUnsafe method to get the hover event, which is better than the NMS method
                if (Shop.getPlugin().getFoliaLib().isPaper()) { obj = Bukkit.getUnsafe().serializeItemAsJson(bukkitItem); } 
                else { obj = ItemStackUtils.serializeItemAsJson(bukkitItem); } // nms code for same paper api
                if (obj != null) {
                    return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverItem(obj));
                }
            } catch (Exception ignored) {
                // Paper-specific API not available, and/or NMS hook not working, or old version; fall back to legacy tag path below
            }
        }
        // There was either no json object returned (<1.20.5) or there was an error, regardless attempt the legacy method
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



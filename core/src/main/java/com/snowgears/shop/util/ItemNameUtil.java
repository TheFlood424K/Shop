package com.snowgears.shop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class ItemNameUtil {

    public ItemNameUtil() { }

    public String translate(String key){
        return UtilMethods.translate(key);
    }

    /**
     * Returns an Adventure Component representing the display name of the given ItemStack.
     * If the item has a custom display name it is used directly; otherwise a translatable
     * component (or a capitalised fallback) is returned.
     */
    public Component getName(ItemStack item){
        if (item == null)
            return Component.empty();

        // Custom display name (legacy-formatted string → Adventure component)
        if (item.getItemMeta() != null
                && item.getItemMeta().getDisplayName() != null
                && !item.getItemMeta().getDisplayName().isEmpty()) {
            return ShopMessage.componentFromLegacy(item.getItemMeta().getDisplayName());
        }

        // Player skull → owner name
        if (item.getItemMeta() instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta.getOwningPlayer() != null && skullMeta.getOwnerProfile() != null
                    && skullMeta.getOwnerProfile().getName() != null) {
                return Component.text(skullMeta.getOwnerProfile().getName() + "'s Head");
            }
        }

        // Smithing templates
        if (item.getItemMeta() != null) {
            String itemType = item.getType().name();
            if (itemType.endsWith("_SMITHING_TEMPLATE")) {
                String templateType = itemType.replace("_SMITHING_TEMPLATE", "");
                if (templateType.endsWith("_ARMOR_TRIM")) {
                    NamedTextColor trimColor = NamedTextColor.YELLOW;
                    if (templateType.contains("VEX") || templateType.contains("SPIRE")
                            || templateType.contains("EYE") || templateType.contains("WARD")) {
                        trimColor = NamedTextColor.AQUA;
                    } else if (templateType.contains("SILENCE")) {
                        trimColor = NamedTextColor.LIGHT_PURPLE;
                    }
                    String formattedName = UtilMethods.capitalize(
                            templateType.toLowerCase().replace("_", " "));
                    return Component.text(formattedName, trimColor);
                } else if (templateType.equals("NETHERITE_UPGRADE")) {
                    return Component.text("Netherite Upgrade Template", NamedTextColor.YELLOW);
                } else {
                    String formattedName = UtilMethods.capitalize(
                            templateType.toLowerCase().replace("_", " "));
                    return Component.text(formattedName, NamedTextColor.YELLOW);
                }
            }
        }

        // Potions
        if (item.getItemMeta() instanceof PotionMeta) {
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta.getBasePotionType() != null) {
                String formattedName = UtilMethods.capitalize(
                        item.getType().name().replace("_", " ").toLowerCase());
                formattedName += " of ";
                formattedName += UtilMethods.capitalize(
                        potionMeta.getBasePotionType().toString().replace("_", " ").toLowerCase());
                return Component.text(formattedName);
            }
        }

        // Ominous bottle (1.21+)
        try {
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
                return getNameTranslatable(item.getType()).color(NamedTextColor.YELLOW);
            }
        } catch (Exception | Error ignored) {}

        return getNameTranslatable(item.getType());
    }

    /**
     * Returns a translatable Adventure Component for the given Material.
     * Returns Component (not TextComponent) because Component.translatable() returns
     * a TranslatableComponent, which is not a subtype of TextComponent.
     * Uses material.getItemTranslationKey() — the non-deprecated Paper 1.21+ API.
     */
    public static Component getNameTranslatable(Material material) {
        if (!MCVersion.isTranslationSupported()) {
            return Component.text(
                    UtilMethods.capitalize(material.name().toLowerCase().replace("_", " ")));
        }
        // Use getItemTranslationKey() — replaces the removed getTranslationKey()
        try {
            // Paper 1.21+ exposes getItemTranslationKey() directly on Material
            String key = material.getItemTranslationKey();
            if (key != null) {
                return Component.translatable(key);
            }
        } catch (Exception | Error ignored) {}
        // Fallback: derive from namespace key (e.g. "minecraft:stone" → "block.minecraft.stone")
        String ns = material.getKey().getNamespace();
        String value = material.getKey().getKey();
        String fallbackKey = (material.isBlock() ? "block." : "item.") + ns + "." + value;
        return Component.translatable(fallbackKey);
    }

    /**
     * Returns a translatable Adventure Component for the given Enchantment.
     * Uses enchantment.getKey() to build the translation key string directly,
     * avoiding the deprecated translationKey() method.
     */
    public static Component getEnchantmentTranslatable(Enchantment enchantment) {
        if (!MCVersion.atLeast("1.20.4")) {
            return Component.text(getEnchantmentName(enchantment));
        }
        // Build the standard Minecraft enchantment translation key:
        // "enchantment.<namespace>.<key>" e.g. "enchantment.minecraft.sharpness"
        String ns = enchantment.getKey().getNamespace();
        String key = enchantment.getKey().getKey();
        return Component.translatable("enchantment." + ns + "." + key);
    }

    /**
     * Legacy enchantment name lookup used on servers older than 1.20.4.
     */
    public static String getEnchantmentName(Enchantment enchantment) {
        switch (enchantment.getKey().getKey()) {
            case "power":                  return "Power";
            case "flame":                  return "Flame";
            case "infinity":               return "Infinity";
            case "punch":                  return "Punch";
            case "binding_curse":          return "Curse of Binding";
            case "channeling":             return "Channeling";
            case "sharpness":              return "Sharpness";
            case "bane_of_arthropods":     return "Bane of Arthropods";
            case "smite":                  return "Smite";
            case "depth_strider":          return "Depth Strider";
            case "efficiency":             return "Efficiency";
            case "unbreaking":             return "Unbreaking";
            case "fire_aspect":            return "Fire Aspect";
            case "frost_walker":           return "Frost Walker";
            case "impaling":               return "Impaling";
            case "knockback":              return "Knockback";
            case "fortune":                return "Fortune";
            case "looting":                return "Looting";
            case "loyalty":                return "Loyalty";
            case "luck_of_the_sea":        return "Luck of the Sea";
            case "lure":                   return "Lure";
            case "mending":                return "Mending";
            case "multishot":              return "Multishot";
            case "respiration":            return "Respiration";
            case "piercing":               return "Piercing";
            case "protection":             return "Protection";
            case "blast_protection":       return "Blast Protection";
            case "feather_falling":        return "Feather Falling";
            case "fire_protection":        return "Fire Protection";
            case "projectile_protection":  return "Projectile Protection";
            case "quick_charge":           return "Quick Charge";
            case "riptide":                return "Riptide";
            case "silk_touch":             return "Silk Touch";
            case "soul_speed":             return "Soul Speed";
            case "sweeping_edge":
            case "sweeping":               return "Sweeping Edge";
            case "swift_sneak":            return "Swift Sneak";
            case "thorns":                 return "Thorns";
            case "vanishing_curse":        return "Curse of Vanishing";
            case "aqua_affinity":          return "Aqua Affinity";
            default:
                return UtilMethods.capitalize(
                        enchantment.getKey().getKey().toLowerCase().replace("_", " "));
        }
    }
}

package com.snowgears.shop.util;

import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.ChatColor;

public class ItemNameUtil {

    public ItemNameUtil() { }

    public String translate(String key){
        return UtilMethods.translate(key);
    }

    public TextComponent getName(ItemStack item){
        if(item == null)
            return new TextComponent("");

        if(item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null && !item.getItemMeta().getDisplayName().isEmpty()){
            return (TextComponent) ShopMessage.componentFromLegacy(item.getItemMeta().getDisplayName());
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof SkullMeta){
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                return new TextComponent(skullMeta.getOwnerProfile().getName() + "'s Head");
            }
        }

        if(item.getItemMeta() != null) {
            String itemType = item.getType().name();
            if(itemType.endsWith("_SMITHING_TEMPLATE")) {
                String templateType = itemType.replace("_SMITHING_TEMPLATE", "");
                if(templateType.endsWith("_ARMOR_TRIM")) {
                    ChatColor trimNameColor = ChatColor.YELLOW;
                    if (templateType.contains("VEX") || templateType.contains("SPIRE") || templateType.contains("EYE") || templateType.contains("WARD")) {
                        trimNameColor = ChatColor.AQUA;
                    } else if (templateType.contains("SILENCE")) { trimNameColor = ChatColor.LIGHT_PURPLE; }
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return new TextComponent(trimNameColor.toString() + formattedName);
                } else if(templateType.equals("NETHERITE_UPGRADE")) {
                    return new TextComponent(ChatColor.YELLOW.toString() + "Netherite Upgrade Template");
                } else {
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return new TextComponent(ChatColor.YELLOW.toString() + formattedName);
                }
            }
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof PotionMeta){
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta.getBasePotionType() != null) {
                String formattedName = UtilMethods.capitalize(item.getType().name().replace("_", " ").toLowerCase());
                formattedName += " of ";
                formattedName += UtilMethods.capitalize(potionMeta.getBasePotionType().toString().replace("_", " ").toLowerCase());
                return new TextComponent(formattedName);
            }
        }

        try {
            if (item.getItemMeta() != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
                TextComponent name = getNameTranslatable(item.getType());
                name.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                return name;
            }
        } catch (Exception e) {} catch (Error e) {}

        return getNameTranslatable(item.getType());
    }

    public static TextComponent getNameTranslatable(Material material){
        if (!MCVersion.isTranslationSupported()) {
            return new TextComponent(UtilMethods.capitalize(material.name().toLowerCase().replace("_", " ")));
        }
        // Use translationKey() — the non-deprecated replacement for getTranslationKey()
        return new TextComponent(new TranslatableComponent(material.translationKey()));
    }

    public static TextComponent getEnchantmentTranslatable(Enchantment enchantment){
        if (!MCVersion.atLeast("1.20.4")) {
            return new TextComponent(getEnchantmentName(enchantment));
        }
        // Use translationKey() — the non-deprecated replacement for getTranslationKey()
        return new TextComponent(new TranslatableComponent(enchantment.translationKey()));
    }

    /**
     * Legacy enchantment name lookup used on servers older than 1.20.4.
     */
    public static String getEnchantmentName(Enchantment enchantment){
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
                return UtilMethods.capitalize(enchantment.getKey().getKey().toLowerCase().replace("_", " "));
        }
    }
}

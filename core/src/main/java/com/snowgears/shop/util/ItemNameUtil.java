package com.snowgears.shop.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;

public class ItemNameUtil {

    public ItemNameUtil() { }

    public TextComponent getName(ItemStack item){
        if(item == null)
            return new TextComponent("");


        // Check if there is a name embedded in the item, aka named by an anvil or command
        if(item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null && !item.getItemMeta().getDisplayName().isEmpty()){
            return (TextComponent) ShopMessage.componentFromLegacy(item.getItemMeta().getDisplayName());
        }

        // Add custom formatting for player heads
        if(item.getItemMeta() != null && item.getItemMeta() instanceof SkullMeta){
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                return new TextComponent(skullMeta.getOwnerProfile().getName() + "'s Head");
            }
        }

        // Add support for displaying smithing template types
        if(item.getItemMeta() != null) {
            String itemType = item.getType().name();
            if(itemType.endsWith("_SMITHING_TEMPLATE")) {
                String templateType = itemType.replace("_SMITHING_TEMPLATE", "");
                // Extract the template pattern name (e.g., "EYE" from "EYE_ARMOR_TRIM_SMITHING_TEMPLATE")
                if(templateType.endsWith("_ARMOR_TRIM")) {
                    ChatColor trimNameColor = ChatColor.YELLOW;
                    // Aqua: "Vex", "Spire", "Eye" and "Ward"
                    if (templateType.contains("VEX") || templateType.contains("SPIRE") || templateType.contains("EYE") || templateType.contains("WARD")) {
                        trimNameColor = ChatColor.AQUA;
                    } else if (templateType.contains("SILENCE")) {  trimNameColor = ChatColor.LIGHT_PURPLE; }
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return new TextComponent(trimNameColor.toString() + formattedName);
                } else if(templateType.equals("NETHERITE_UPGRADE")) {
                    return new TextComponent(ChatColor.YELLOW.toString() + "Netherite Upgrade Template");
                } else {
                    // For any other potential smithing templates
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return new TextComponent(ChatColor.YELLOW.toString() + formattedName);
                }
            }
        }

        // Add custom potion formatting
        if(item.getItemMeta() != null && item.getItemMeta() instanceof PotionMeta){
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta.getBasePotionType() != null) {
                String formattedName = UtilMethods.capitalize(item.getType().name().replace("_", " ").toLowerCase());
                formattedName += " of ";
                formattedName += UtilMethods.capitalize(potionMeta.getBasePotionType().toString().replace("_", " ").toLowerCase());
                return new TextComponent(formattedName);
            }
        }

        // Ominous Bottle's are Yellow *shrug*
        if (item.getItemMeta() != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
            TextComponent name = getNameTranslatable(item.getType());
            name.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
            return name;
        }

        // Fallback to the material name
        return getNameTranslatable(item.getType());
    }

    public static TextComponent getNameTranslatable(Material material){
        TranslatableComponent component = new TranslatableComponent(material.translationKey());
        component.setFallback(readableName(material.name()));
        return new TextComponent(component);
    }

    public static TextComponent getEnchantmentTranslatable(Enchantment enchantment){
        Component description = enchantment.description();
        if (description instanceof net.kyori.adventure.text.TranslatableComponent translatable) {
            TranslatableComponent component = new TranslatableComponent(translatable.key());
            String fallback = translatable.fallback();
            if (isBlank(fallback)) {
                fallback = readableName(getRegistryKeyPath(RegistryKey.ENCHANTMENT, enchantment));
            }
            if (isBlank(fallback)) {
                fallback = readableName(translationKeyPath(translatable.key()));
            }
            component.setFallback(isBlank(fallback) ? "Enchantment" : fallback);
            return new TextComponent(component);
        }

        return new TextComponent(PlainTextComponentSerializer.plainText().serialize(description));
    }

    static <T extends Keyed> String getRegistryKeyPath(RegistryKey<T> registryKey, T keyed) {
        NamespacedKey namespacedKey = RegistryAccess.registryAccess().getRegistry(registryKey).getKey(keyed);
        return namespacedKey == null ? null : namespacedKey.getKey();
    }

    static String readableName(String identifier) {
        if (isBlank(identifier)) {
            return "";
        }

        String[] words = identifier.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder readableName = new StringBuilder();
        for (String word : words) {
            if (readableName.length() > 0) {
                readableName.append(' ');
            }
            readableName.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return readableName.toString();
    }

    static String translationKeyPath(String translationKey) {
        if (isBlank(translationKey)) {
            return null;
        }
        int separator = Math.max(translationKey.lastIndexOf('.'), translationKey.lastIndexOf(':'));
        return separator >= 0 ? translationKey.substring(separator + 1) : translationKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

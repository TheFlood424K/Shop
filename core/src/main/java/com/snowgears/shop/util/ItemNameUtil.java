package com.snowgears.shop.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    public Component getName(ItemStack item){
        if(item == null)
            return Component.empty();


        // Check if there is a name embedded in the item, aka named by an anvil or command
        if(item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null && !item.getItemMeta().getDisplayName().isEmpty()){
            return ShopMessage.componentFromLegacy(item.getItemMeta().getDisplayName());
        }

        // Add custom formatting for player heads
        if(item.getItemMeta() != null && item.getItemMeta() instanceof SkullMeta){
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                return Component.text(skullMeta.getOwnerProfile().getName() + "'s Head");
            }
        }

        // Add support for displaying smithing template types
        if(item.getItemMeta() != null) {
            String itemType = item.getType().name();
            if(itemType.endsWith("_SMITHING_TEMPLATE")) {
                String templateType = itemType.replace("_SMITHING_TEMPLATE", "");
                // Extract the template pattern name (e.g., "EYE" from "EYE_ARMOR_TRIM_SMITHING_TEMPLATE")
                if(templateType.endsWith("_ARMOR_TRIM")) {
                    net.kyori.adventure.text.format.TextColor trimNameColor = net.kyori.adventure.text.format.TextColor.color(0xFFFF00); // YELLOW
                    // Aqua: "Vex", "Spire", "Eye" and "Ward"
                    if (templateType.contains("VEX") || templateType.contains("SPIRE") || templateType.contains("EYE") || templateType.contains("WARD")) {
                        trimNameColor = net.kyori.adventure.text.format.TextColor.color(0x00FFFF); // AQUA
                    } else if (templateType.contains("SILENCE")) {  trimNameColor = net.kyori.adventure.text.format.TextColor.color(0xD080FF); // LIGHT_PURPLE
                    }
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return Component.text(trimNameColor + formattedName);
                } else if(templateType.equals("NETHERITE_UPGRADE")) {
                    return Component.text(net.kyori.adventure.text.format.TextColor.color(0xFFFF00) + "Netherite Upgrade Template");
                } else {
                    // For any other potential smithing templates
                    String formattedName = UtilMethods.capitalize(templateType.toLowerCase().replace("_", " "));
                    return Component.text(net.kyori.adventure.text.format.TextColor.color(0xFFFF00) + formattedName);
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
                return Component.text(formattedName);
            }
        }

        // Ominous Bottle's are Yellow *shrug*
        if (item.getItemMeta() != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
            Component name = getNameTranslatable(item.getType());
            name = name.color(net.kyori.adventure.text.format.TextColor.color(0xFFFF00));
            return name;
        }

        // Fallback to the material name
        return getNameTranslatable(item.getType());
    }

    public static Component getNameTranslatable(Material material){
        TranslatableComponent component = Component.translatable(material.translationKey());
        component = component.fallback(Component.text(PlainTextComponentSerializer.plainText().serialize(readableName(material.name()))));
        return component;
    }

    public static Component getEnchantmentTranslatable(Enchantment enchantment){
        Component description = enchantment.description();
        if (description instanceof net.kyori.adventure.text.TranslatableComponent translatable) {
            TranslatableComponent component = Component.translatable(translatable.key());
            String fallback = translatable.fallback();
            if (isBlank(fallback)) {
                fallback = readableName(getRegistryKeyPath(RegistryKey.ENCHANTMENT, enchantment));
            }
            if (isBlank(fallback)) {
                fallback = readableName(translationKeyPath(translatable.key()));
            }
            component = component.fallback(Component.text(isBlank(fallback) ? "Enchantment" : fallback));
            return component;
        }

        return PlainTextComponentSerializer.plainText().serialize(description) != null 
            ? Component.text(PlainTextComponentSerializer.plainText().serialize(description)) 
            : Component.empty();
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

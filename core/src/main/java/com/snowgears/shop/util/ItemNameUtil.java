package com.snowgears.shop.util;

import com.snowgears.shop.util.ShopMessage;

import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public class ItemNameUtil {

    private Map<String, String> names = new HashMap<String, String>();

    public ItemNameUtil() { }

    public String translate(String key){
        return new TranslatableComponent(key).toPlainText();
    }

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

        try {
            // Ominous Bottle's are Yellow *shrug*
            if (item.getItemMeta() != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
                TextComponent name = getNameTranslatable(item.getType());
                name.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                return name;
            }
        } catch (Exception e) {} catch (Error e) {} // Backwards compatibility

        // Fallback to the material name
        return getNameTranslatable(item.getType());
    }

    public TextComponent getNameTranslatable(Material material){
        return new TextComponent(new TranslatableComponent(material.getTranslationKey()));
    }
}

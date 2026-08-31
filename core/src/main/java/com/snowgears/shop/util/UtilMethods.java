package com.snowgears.shop.util;

import net.md_5.bungee.api.ChatColor;
import com.snowgears.shop.Shop;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.block.Sign;
import net.md_5.bungee.api.chat.TranslatableComponent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.*;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.util.io.BukkitObjectInputStream;

public class UtilMethods {

    private static ArrayList<Material> nonIntrusiveMaterials = new ArrayList<>();

    public static String trimForSign(String text) {
        final int MAX_SIGN_WIDTH = 80;
        int currentWidth = 0;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ("0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(nextChar) != -1) {
                    result.append(c).append(nextChar);
                    i++;
                    continue;
                }
            }
            int charWidth = getMinecraftCharWidth(c);
            if (currentWidth + charWidth >= MAX_SIGN_WIDTH) { break; }
            result.append(c);
            currentWidth += charWidth;
        }
        return result.toString();
    }

    private static int getMinecraftCharWidth(char c) {
        switch (c) {
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|': case '¡': return 3;
            case '\'': case 'l': case 'ì': case 'í': return 3;
            case ' ': case 'I': case '[': case ']': case 'ï': case '×': return 4;
            case '"': case '(': case ')': case '<': case '>': case 'f': case 'k': case '{': case '}': return 5;
            case '@': case '~': case '®': return 7;
            default: return 6;
        }
    }

    public static String formatLongToKString(double value, boolean formatZeros) {
        if (value == Double.MIN_VALUE) return formatLongToKString(Double.MIN_VALUE + 1, formatZeros);
        if (value < 0) return "-" + formatLongToKString(-value, formatZeros);
        Map.Entry<Double, String> e = Shop.getPlugin().getPriceSuffixes().floorEntry(value);
        Double minimumValue = Shop.getPlugin().getPriceSuffixMinimumValue();;
        if (value < 1000 || e == null || value < minimumValue){
            if(isDecimal(value)) return new DecimalFormat("0.00").format(value);
            else return new DecimalFormat("#.##").format(value);
        }
        Double divideBy = e.getKey();
        String suffix = e.getValue();
        double truncated = value / (divideBy / 10);
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);
        double fPrice = hasDecimal ? (truncated / 10d) : (truncated / 10);
        return new DecimalFormat("#.##").format(fPrice) + suffix;
    }

    public static boolean isDecimal(double d){ return (d % 1 != 0); }

    public static boolean isNumber(String s) {
        try { Double.parseDouble(s); } catch (NumberFormatException e) { return false; }
        return true;
    }

    public static boolean isInteger(String s) {
        try { Integer.parseInt(s); } catch (NumberFormatException e) { return false; }
        return true;
    }

    public static boolean isDouble(String s) {
        try { Double.parseDouble(s); } catch (NumberFormatException e) { return false; }
        return true;
    }

    public static BlockFace yawToFace(float yaw) {
        final BlockFace[] axis = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
        return axis[Math.round(yaw / 90f) & 0x3];
    }

    public static float faceToYaw(BlockFace bf) {
        switch(bf){
            case NORTH: return 180;
            case NORTH_EAST: return 225;
            case EAST: return 270;
            case SOUTH_EAST: return 315;
            case SOUTH: return 0;
            case SOUTH_WEST: return 45;
            case WEST: return 90;
            case NORTH_WEST: return 135;
        }
        return 180;
    }

    public static String capitalize(String line) {
        String[] spaces = line.split("\\s+");
        String capped = "";
        for (String s : spaces) {
            if (s.length() > 1) capped = capped + Character.toUpperCase(s.charAt(0)) + s.substring(1) + " ";
            else capped = capped + s.toUpperCase() + " ";
        }
        return capped.substring(0, capped.length()-1);
    }

    public static String getCleanLocation(Location loc, boolean includeWorld){
        String text = "";
        if (loc == null) { return text; }
        if(includeWorld && loc.getWorld() != null) text = loc.getWorld().getName() + " - ";
        text = text + "("+ loc.getBlockX() + ", "+loc.getBlockY() + ", "+loc.getBlockZ() + ")";
        return text;
    }

    public static Location getLocation(String cleanLocation){
        World world = null;
        if(cleanLocation.contains(" - ")) {
            int dashIndex = cleanLocation.indexOf(" - ");
            world = Bukkit.getWorld(cleanLocation.substring(0, dashIndex));
            cleanLocation = cleanLocation.substring(dashIndex+1, cleanLocation.length());
        } else {
            world = Bukkit.getWorld("world");
        }
        cleanLocation = cleanLocation.replaceAll("[^\\d-]", " ");
        String[] sp = cleanLocation.split("\\s+");
        try {
            return new Location(world, Integer.parseInt(sp[1]), Integer.parseInt(sp[2]), Integer.parseInt(sp[3]));
        } catch (Exception e){ return null; }
    }

    /**
     * Checks if a chunk is loaded without forcing a chunk load.
     */
    public static boolean isChunkLoaded(Location location) {
        if (location == null || location.getWorld() == null) { return false; }
        return location.getWorld().isChunkLoaded(UtilMethods.floor(location.getBlockX()) >> 4, UtilMethods.floor(location.getBlockZ()) >> 4);
    }
    public static int getChunkX(Location location){ return UtilMethods.floor(location.getBlockX()) >> 4; }
    public static int getChunkZ(Location location){ return UtilMethods.floor(location.getBlockZ()) >> 4; }
    public static boolean isInChunk(Location location, Chunk chunk){
        if (location == null || location.getWorld() == null || chunk == null) { return false; }
        if (!chunk.getWorld().toString().equals(location.getWorld().toString())) { return false; }
        return chunk.getX() == getChunkX(location) && chunk.getZ() == getChunkZ(location);
    }
    public static String getChunkKey(Location location){
        int chunkX = getChunkX(location);
        int chunkZ = getChunkZ(location);
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown_world";
        return createChunkKey(worldName, chunkX, chunkZ);
    }
    public static String getChunkKey(Chunk chunk){
        return createChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
    public static String createChunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + "_" + chunkX + "_" + chunkZ;
    }

    public static int floor(double num) {
        int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

    public static String getEulerAngleString(EulerAngle angle){
        return "EulerAngle("+angle.getX() + ", " + angle.getY() + ", " + angle.getZ() + ")";
    }

    public static int calculateSideFromClickedSign(Player player, Block signBlock){
        if(!(signBlock.getBlockData() instanceof WallSign)) return 0;
        WallSign s = (WallSign)signBlock.getBlockData();
        BlockFace attachedFace = s.getFacing().getOppositeFace();
        Location chest = signBlock.getRelative(attachedFace).getLocation().add(0.5,0.5,0.5);
        Location head = player.getLocation().add(0, player.getEyeHeight(), 0);
        Vector direction = head.subtract(chest).toVector().normalize();
        Vector look = player.getLocation().getDirection().normalize();
        Vector cp = direction.crossProduct(look);
        double d = 0;
        switch(attachedFace){
            case NORTH: d = cp.getZ(); break;
            case SOUTH: d = cp.getZ() * -1; break;
            case EAST:  d = cp.getX() * -1; break;
            case WEST:  d = cp.getX(); break;
            default: break;
        }
        if(player.getLocation().getPitch() < 0) d = -d;
        if(d > 0) return 1;
        else if(d < 0) return -1;
        else return 0;
    }

    public static String convertDurationToString(int duration) {
        duration = duration / 20;
        if (duration < 10) return "0:0" + duration;
        else if (duration < 60) return "0:" + duration;
        double mins = duration / 60;
        double secs = (mins - (int) mins);
        secs = (double) Math.round(secs * 100000) / 100000;
        if (secs == 0) return (int) mins + ":00";
        else if (secs < 10) return (int) mins + ":0" + (int) secs;
        else return (int) mins + ":" + (int) secs;
    }

    public static Location pushLocationInDirection(Location location, BlockFace direction, double add){
        switch (direction){
            case NORTH: location = location.add(-add, 0, -add);
            case EAST:  location = location.add(add, 0, -add);
            case SOUTH: location = location.add(add, 0, add);
            case WEST:  location = location.add(-add, 0, 0);
        }
        return location;
    }

    public static int getDurabilityPercent(ItemStack item) {
        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability > 0) {
            ItemMeta meta = item.getItemMeta();
            int damage = (meta instanceof Damageable) ? ((Damageable) meta).getDamage() : 0;
            double dur = (double)(maxDurability - damage) / (double) maxDurability;
            return (int)(dur * 100);
        }
        return 100;
    }

    public static String getItemName(ItemStack is){
        ItemMeta itemMeta = is.getItemMeta();
        if (itemMeta.getDisplayName() == null || itemMeta.getDisplayName().isEmpty())
            return capitalize(is.getType().name().replace("_", " ").toLowerCase());
        else
            return itemMeta.getDisplayName();
    }

    public static boolean stringStartsWithUUID(String name){
        if (name != null && name.length() > 35){
            try {
                if (UUID.fromString(name.substring(0, 36)) != null) return true;
            } catch (Exception ex) { return false; }
        }
        return false;
    }

    public static boolean containsLocation(String s){
        if(s == null) return false;
        if(s.startsWith("***{")){
            if((s.indexOf(',') != s.lastIndexOf(',')) && s.indexOf('}') != -1) return true;
        }
        return false;
    }

    public static boolean basicLocationMatch(Location loc1, Location loc2){
        return (loc1.getBlockX() == loc2.getBlockX() && loc1.getBlockY() == loc2.getBlockY() && loc1.getBlockZ() == loc2.getBlockZ());
    }

    public static boolean materialIsNonIntrusive(Material material){
        if(nonIntrusiveMaterials.isEmpty()){ initializeNonIntrusiveMaterials(); }
        return (nonIntrusiveMaterials.contains(material));
    }

    public static String getLoreString(ItemStack is){
        if(is.getItemMeta() == null || is.getItemMeta().getLore() == null || is.getItemMeta().getLore().isEmpty()) return "";
        return is.getItemMeta().getLore().toString();
    }

    public static String translate(String key){
        try {
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.Component.translatable(key);
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        } catch (NoClassDefFoundError e) {
            return new TranslatableComponent(key).toPlainText();
        }
    }

    public static String formatTickTime(int ticks){
        int totalSeconds = ticks / 20;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) return " " + String.format("%d:%02d:%02d", hours, minutes, seconds);
        else return " " + String.format("%d:%02d", minutes, seconds);
    }

    public static String formatRomanNumerals(int number){
        if (number < 2) return "";
        if(number > 5) return " " + String.valueOf(number);
        String[] romanNumerals = {"I", "II", "III", "IV", "V"};
        return " " + romanNumerals[number - 1];
    }

    public static String removeColorsIfOnlyWhite(String message){
        String COLOR_CODE_REGEX_NO_WHITE = "([&§][0-9A-EK-ORXa-ek-orx])";
        boolean hasOtherColors = Pattern.compile(COLOR_CODE_REGEX_NO_WHITE).matcher(message).find();
        String msgStr = message;
        if (!hasOtherColors) { msgStr = ChatColor.stripColor(msgStr); }
        return msgStr;
    }

    public static TextComponent getEnchantmentsComponent(ItemStack item){
        TextComponent formattedMessage = new TextComponent("");

        if(item.getItemMeta() instanceof EnchantmentStorageMeta || item.getEnchantments().size() > 0){
            Map<Enchantment, Integer> enchantsMap;
            if(item.getItemMeta() instanceof EnchantmentStorageMeta){
                enchantsMap = ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants();
            } else { enchantsMap = item.getEnchantments(); }

            if(enchantsMap == null || enchantsMap.isEmpty()) return formattedMessage;

            formattedMessage.addExtra(" [");
            int i=0;
            for(Map.Entry<Enchantment, Integer> entry : enchantsMap.entrySet()){
                formattedMessage.addExtra((BaseComponent) ItemNameUtil.getEnchantmentTranslatable(entry.getKey()));
                formattedMessage.addExtra(formatRomanNumerals(entry.getValue()));
                i++;
                if(i != enchantsMap.size()) formattedMessage.addExtra(", ");
                else formattedMessage.addExtra("]");
            }
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof ArmorMeta){
            ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();
            if (armorMeta.getTrim() != null) {
                String material = translate(armorMeta.getTrim().getMaterial().translationKey());
                String pattern = translate(armorMeta.getTrim().getPattern().translationKey());
                formattedMessage.addExtra(" [" + pattern.replace(" Armor Trim", ""));
                formattedMessage.addExtra(" (" + material.replace(" Material", "") + ")]");
            }
        }

        if(item.getItemMeta() != null) {
            String itemType = item.getType().name();

            if(itemType.startsWith("MUSIC_DISC_")) {
                String trackName = itemType.replace("MUSIC_DISC_", "");
                String formattedName = capitalize(trackName.toLowerCase().replace("_", " "));
                formattedMessage.addExtra(" [Song: " + formattedName + "]");
            } else if(itemType.equals("MUSIC_DISC")) { formattedMessage.addExtra(" [Song: Unknown]"); }
            else if(itemType.equals("PIGSTEP"))  { formattedMessage.addExtra(" [Song: Pigstep]"); }
            else if(itemType.equals("OTHERSIDE")) { formattedMessage.addExtra(" [Song: Otherside]"); }
            else if(itemType.equals("FIVE"))     { formattedMessage.addExtra(" [Song: 5]"); }
            else if(itemType.equals("RELIC"))    { formattedMessage.addExtra(" [Song: Relic]"); }
            else if(itemType.equals("GOAT_HORN")) {
                try {
                    org.bukkit.inventory.meta.MusicInstrumentMeta instrumentMeta = (org.bukkit.inventory.meta.MusicInstrumentMeta) item.getItemMeta();
                    if (instrumentMeta != null && instrumentMeta.getInstrument() != null) {
                        // Use key() (Adventure API) instead of the deprecated getKey()
                        String instrumentKey = instrumentMeta.getInstrument().key().value();
                        String soundType = instrumentKey.replace("_goat_horn", "");
                        formattedMessage.addExtra(" [Sound: " + capitalize(soundType) + "]");
                    } else {
                        formattedMessage.addExtra(" [Sound: Unknown]");
                    }
                } catch (Exception e) {
                    formattedMessage.addExtra(" [Sound: Unknown]");
                }
            } else if(itemType.equals("BEE_NEST") || itemType.equals("BEEHIVE")) {
                try {
                    if(item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta) {
                        org.bukkit.inventory.meta.BlockStateMeta blockStateMeta = (org.bukkit.inventory.meta.BlockStateMeta) item.getItemMeta();
                        if(blockStateMeta.hasBlockState() && blockStateMeta.getBlockState() instanceof org.bukkit.block.Beehive) {
                            org.bukkit.block.Beehive beehive = (org.bukkit.block.Beehive) blockStateMeta.getBlockState();
                            int honeyLevel = 0;
                            int beeCount = 0;
                            try {
                                org.bukkit.block.data.type.Beehive beehiveData = (org.bukkit.block.data.type.Beehive) beehive.getBlockData();
                                honeyLevel = beehiveData.getHoneyLevel();
                            } catch (Exception e) { }
                            try { beeCount = beehive.getEntityCount(); } catch (Exception e) {}
                            if(honeyLevel > 0 || beeCount > 0) {
                                StringBuilder beeInfo = new StringBuilder(" [");
                                if(honeyLevel > 0) {
                                    beeInfo.append("Honey: ").append(honeyLevel).append("/5");
                                    if(beeCount > 0) { beeInfo.append(", "); }
                                }
                                if(beeCount > 0) { beeInfo.append("Bees: ").append(beeCount); }
                                beeInfo.append("]");
                                formattedMessage.addExtra(beeInfo.toString());
                            }
                        }
                    }
                } catch (Exception e) { }
            }
        }

        try {
            if(item.getItemMeta() != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.OminousBottleMeta) {
                org.bukkit.inventory.meta.OminousBottleMeta ominousMeta = (org.bukkit.inventory.meta.OminousBottleMeta) item.getItemMeta();
                int level = ominousMeta.hasAmplifier() ? ominousMeta.getAmplifier() + 1 : 1;
                formattedMessage.addExtra(" [Bad Omen" + formatRomanNumerals(level) + "]");
            }
        } catch (Error e) {} catch (Exception e) {}

        if(item.getItemMeta() != null && item.getItemMeta() instanceof PotionMeta){
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta.getBasePotionType() != null) {
                formattedMessage.addExtra(getPotionEffects(potionMeta.getBasePotionType().getPotionEffects()));
            }
            List<PotionEffect> customEffects = potionMeta.getCustomEffects();
            if(!customEffects.isEmpty()) { formattedMessage.addExtra(getPotionEffects(customEffects)); }
        }

        if(item.getItemMeta() != null) {
            if(item.getItemMeta() instanceof org.bukkit.inventory.meta.FireworkEffectMeta) {
                org.bukkit.inventory.meta.FireworkEffectMeta fireworkMeta = (org.bukkit.inventory.meta.FireworkEffectMeta) item.getItemMeta();
                if(fireworkMeta.hasEffect()) { formattedMessage.addExtra(getFormattedFireworkEffect(fireworkMeta.getEffect(), true)); }
            } else if(item.getItemMeta() instanceof FireworkMeta) {
                FireworkMeta fireworkMeta = (FireworkMeta) item.getItemMeta();
                int power = fireworkMeta.getPower();
                if (power == 0) power = 1;
                formattedMessage.addExtra(" [Duration " + power + "]");
                List<org.bukkit.FireworkEffect> effects = fireworkMeta.getEffects();
                if(effects != null && !effects.isEmpty()) {
                    int effectCount = effects.size();
                    if(effectCount <= 2) {
                        for (org.bukkit.FireworkEffect effect : effects) { formattedMessage.addExtra(getFormattedFireworkEffect(effect, false)); }
                    } else {
                        formattedMessage.addExtra(" [" + effectCount + " Effects]");
                    }
                }
            }
        }

        return new TextComponent(ChatColor.stripColor(formattedMessage.toLegacyText()));
    }

    private static TextComponent getPotionEffects(List<PotionEffect> effects){
        TextComponent formattedEffects = new TextComponent("");
        int numEffects = effects.size();
        if (numEffects == 0) return formattedEffects;
        formattedEffects.addExtra(" (");
        for (int i = 0; i < numEffects; i++) {
            PotionEffect effect = effects.get(i);
            // Use translationKey() instead of the deprecated getTranslationKey()
            formattedEffects.addExtra(new TranslatableComponent(effect.getType().translationKey()));
            if(effect.getAmplifier() > 0) { formattedEffects.addExtra(formatRomanNumerals(effect.getAmplifier() + 1)); }
            boolean isInstantEffect = effect.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_HEALTH) ||
                                     effect.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE);
            if(effect.getDuration() > 0 && !isInstantEffect) { formattedEffects.addExtra(formatTickTime(effect.getDuration())); }
            if(i < numEffects - 1) formattedEffects.addExtra(", ");
        }
        formattedEffects.addExtra(")");
        return formattedEffects;
    }

    private static TextComponent getFormattedFireworkEffect(org.bukkit.FireworkEffect effect, boolean isFireworkStar) {
        TextComponent formattedEffect = new TextComponent("");
        if(effect == null) return formattedEffect;
        StringBuilder sb = new StringBuilder();
        sb.append(" [");
        sb.append(formatFireworkShape(effect.getType()));
        List<String> specialEffects = new ArrayList<>();
        if(effect.hasTrail()) specialEffects.add("Trail");
        if(effect.hasFlicker()) specialEffects.add("Twinkle");
        if(!specialEffects.isEmpty()) { sb.append(" (").append(String.join(", ", specialEffects)).append(")"); }
        List<org.bukkit.Color> colors = effect.getColors();
        if(colors != null && !colors.isEmpty()) {
            if(colors.size() == 1) { sb.append(" ").append(formatFireworkColor(colors.get(0))); }
            else if(colors.size() <= 3) {
                sb.append(" ");
                for(int i = 0; i < colors.size(); i++) {
                    sb.append(formatFireworkColor(colors.get(i)));
                    if(i < colors.size() - 1) sb.append(", ");
                }
            } else { sb.append(" ").append(colors.size()).append(" Colors"); }
        }
        List<org.bukkit.Color> fadeColors = effect.getFadeColors();
        if(fadeColors != null && !fadeColors.isEmpty()) {
            if(fadeColors.size() == 1) { sb.append("→").append(formatFireworkColor(fadeColors.get(0))); }
            else if(fadeColors.size() <= 2) {
                sb.append("→");
                for(int i = 0; i < fadeColors.size(); i++) {
                    sb.append(formatFireworkColor(fadeColors.get(i)));
                    if(i < fadeColors.size() - 1) sb.append(", ");
                }
            } else { sb.append(" → ").append(fadeColors.size()).append(" Fade Colors"); }
        }
        sb.append("]");
        formattedEffect.addExtra(sb.toString());
        return formattedEffect;
    }

    private static String formatFireworkShape(org.bukkit.FireworkEffect.Type type) {
        switch(type) {
            case BALL:       return "Small";
            case BALL_LARGE: return "Large";
            case STAR:       return "Star";
            case BURST:      return "Burst";
            case CREEPER:    return "Creeper";
            default: return capitalize(type.toString().toLowerCase().replace("_", " "));
        }
    }

    private static String formatFireworkColor(org.bukkit.Color color) {
        Shop.getPlugin().getLogger().debug("[formatFireworkColor]     color: " + color.toString());
        if(color.equals(org.bukkit.Color.WHITE))   return "White";
        if(color.equals(org.bukkit.Color.SILVER))  return "Silver";
        if(color.equals(org.bukkit.Color.GRAY))    return "Gray";
        if(color.equals(org.bukkit.Color.BLACK))   return "Black";
        if(color.equals(org.bukkit.Color.RED))     return "Red";
        if(color.equals(org.bukkit.Color.MAROON))  return "Maroon";
        if(color.equals(org.bukkit.Color.YELLOW))  return "Yellow";
        if(color.equals(org.bukkit.Color.OLIVE))   return "Olive";
        if(color.equals(org.bukkit.Color.LIME))    return "Lime";
        if(color.equals(org.bukkit.Color.GREEN))   return "Green";
        if(color.equals(org.bukkit.Color.AQUA))    return "Aqua";
        if(color.equals(org.bukkit.Color.TEAL))    return "Teal";
        if(color.equals(org.bukkit.Color.BLUE))    return "Blue";
        if(color.equals(org.bukkit.Color.NAVY))    return "Navy";
        if(color.equals(org.bukkit.Color.FUCHSIA)) return "Fuchsia";
        if(color.equals(org.bukkit.Color.PURPLE))  return "Purple";
        if(color.equals(org.bukkit.Color.ORANGE))  return "Orange";
        try {
            for(org.bukkit.DyeColor dyeColor : org.bukkit.DyeColor.values()) {
                if(dyeColor.getColor().equals(color)) return capitalize(dyeColor.toString().toLowerCase().replace("_", " "));
                if(dyeColor.getFireworkColor().equals(color)) return capitalize(dyeColor.toString().toLowerCase().replace("_", " "));
            }
        } catch(NoSuchMethodError e) { }
        return "Custom";
    }

    private static void initializeNonIntrusiveMaterials(){
        for(Material m : Material.values()){
            if(!m.isSolid()) nonIntrusiveMaterials.add(m);
        }
        try{
            nonIntrusiveMaterials.add(Material.WARPED_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.ACACIA_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.BIRCH_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.CRIMSON_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.DARK_OAK_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.JUNGLE_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.OAK_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.SPRUCE_WALL_SIGN);
        } catch(NoSuchFieldError e){
            nonIntrusiveMaterials.add(Material.LEGACY_WALL_SIGN);
        }
        nonIntrusiveMaterials.remove(Material.WATER);
        nonIntrusiveMaterials.remove(Material.LAVA);
        nonIntrusiveMaterials.remove(Material.FIRE);
        nonIntrusiveMaterials.remove(Material.END_PORTAL);
        nonIntrusiveMaterials.remove(Material.NETHER_PORTAL);
        nonIntrusiveMaterials.remove(Material.SKELETON_SKULL);
        nonIntrusiveMaterials.remove(Material.WITHER_SKELETON_SKULL);
        nonIntrusiveMaterials.remove(Material.PLAYER_HEAD);
        nonIntrusiveMaterials.remove(Material.CREEPER_HEAD);
        try{ nonIntrusiveMaterials.add(Material.LIGHT); } catch(NoSuchFieldError e){}
    }

    public static BlockFace getDirectionOfChest(Block block){
        if(block.getBlockData() instanceof Directional){ return ((Directional)block.getBlockData()).getFacing(); }
        return null;
    }

    public static boolean isMCVersion17Plus(){
        try { if(Material.LIGHT != null) return true; } catch (NoSuchFieldError e) { return false; }
        return false;
    }

    public static boolean isMCVersion14Plus(){
        try { if(Material.BARREL != null) return true; } catch (NoSuchFieldError e) { return false; }
        return false;
    }

    public static double getMultiplyValue(String text){
        String priceString = ChatColor.stripColor(text).replaceAll("\\s", "").toLowerCase();
        String priceSuffix = priceString.replaceAll("[0-9.]", "");
        NavigableMap<Double, String> configPriceSuffixes = Shop.getPlugin().getPriceSuffixes();
        for (Map.Entry<Double, String> entry : configPriceSuffixes.entrySet()) {
            if (priceSuffix.equals(entry.getValue().toLowerCase())) return entry.getKey();
        }
        return 1;
    }

    public static String cleanNumberText(String text){
        String cleaned = "";
        String toClean = ChatColor.stripColor(text).trim();
        for(int i=0; i<toClean.length(); i++) {
            if(Character.isDigit(toClean.charAt(i))) cleaned += toClean.charAt(i);
            else if(toClean.charAt(i) == '.') cleaned += toClean.charAt(i);
            else if(toClean.charAt(i) == ' ') cleaned += toClean.charAt(i);
        }
        return cleaned;
    }

    public static ChatColor getChatColorByCode(String colorCode) {
        switch (colorCode) {
            case "&b": return ChatColor.AQUA;
            case "&0": return ChatColor.BLACK;
            case "&9": return ChatColor.BLUE;
            case "&l": return ChatColor.BOLD;
            case "&3": return ChatColor.DARK_AQUA;
            case "&1": return ChatColor.DARK_BLUE;
            case "&8": return ChatColor.DARK_GRAY;
            case "&2": return ChatColor.DARK_GREEN;
            case "&5": return ChatColor.DARK_PURPLE;
            case "&4": return ChatColor.DARK_RED;
            case "&6": return ChatColor.GOLD;
            case "&7": return ChatColor.GRAY;
            case "&a": return ChatColor.GREEN;
            case "&o": return ChatColor.ITALIC;
            case "&d": return ChatColor.LIGHT_PURPLE;
            case "&k": return ChatColor.MAGIC;
            case "&c": return ChatColor.RED;
            case "&r": return ChatColor.RESET;
            case "&m": return ChatColor.STRIKETHROUGH;
            case "&n": return ChatColor.UNDERLINE;
            case "&f": return ChatColor.WHITE;
            case "&e": return ChatColor.YELLOW;
            default: return ChatColor.RESET;
        }
    }

    public static ChatColor getChatColor(String message) {
        if(message.startsWith("&") && message.length() > 1){
            ChatColor cc = getChatColorByCode(message.substring(0,2));
            if(cc != ChatColor.RESET) return cc;
        }
        return null;
    }

    public static boolean deleteDirectory(File directory) {
        if(directory.exists()){
            File[] files = directory.listFiles();
            if(null!=files){
                for(int i=0; i<files.length; i++) {
                    if(files[i].isDirectory()) deleteDirectory(files[i]);
                    else files[i].delete();
                }
            }
        }
        return(directory.delete());
    }

    public static void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) { out.write(buf, 0, len); }
            out.close();
            in.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String itemStackToBase64(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        if (item.getAmount() > 64) { item.setAmount(64); }
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }

    public static List<String> splitStringIntoLines(String text, int maxLineLength) {
        final String HEX_COLOR_CODE_REGEX = "(§x§.§.§.§.§.§.)";
        final String COLOR_CODE_REGEX = "([&§][0-9A-FK-ORa-fk-or])";

        Matcher matcher = Pattern
            .compile(HEX_COLOR_CODE_REGEX + "|" + COLOR_CODE_REGEX + "| |[^&§\\s]+")
            .matcher(ChatColor.translateAlternateColorCodes('&', text));
        List<String> words = new ArrayList<>();
        while (matcher.find()) { words.add(matcher.group()); }

        StringBuilder currentLine = new StringBuilder();
        List<String> linesByColor = new ArrayList<>();
        String latestColors = "";
        String latestHexColor = "";
        ChatColor latestColor = ChatColor.WHITE;
        boolean isBold = false;
        boolean isItalic = false;
        boolean isStrikethrough = false;
        boolean isUnderlined = false;
        boolean isObfuscated = false;

        for (String word : words) {
            if (Shop.getPlugin() != null) Shop.getPlugin().getLogger().hyper("[ShopMessage.format]     word: " + word);
            boolean isStandardColor = word.matches(COLOR_CODE_REGEX);
            boolean isHexColor = word.matches(HEX_COLOR_CODE_REGEX);
            if (isStandardColor || isHexColor) {
                if (isStandardColor) {
                    ChatColor newColor = ChatColor.getByChar(word.charAt(1));
                    if (newColor == ChatColor.BOLD) isBold = true;
                    else if (newColor == ChatColor.ITALIC) isItalic = true;
                    else if (newColor == ChatColor.STRIKETHROUGH) isStrikethrough = true;
                    else if (newColor == ChatColor.UNDERLINE) isUnderlined = true;
                    else if (newColor == ChatColor.MAGIC) isObfuscated = true;
                    else if (newColor == ChatColor.RESET) {
                        if (Shop.getPlugin() != null) Shop.getPlugin().getLogger().hyper("[ShopMessage.format]     matched RESET color code: " + word);
                        latestColor = ChatColor.WHITE;
                        latestHexColor = "";
                        isBold = false; isItalic = false; isStrikethrough = false; isUnderlined = false; isObfuscated = false;
                    } else {
                        latestColor = newColor;
                        latestHexColor = "";
                    }
                } else if (isHexColor) {
                    latestColor = null;
                    latestHexColor = word;
                }
                String newColors = "";
                if (latestColor != null) newColors += latestColor.toString();
                else if (!latestHexColor.isEmpty()) newColors += latestHexColor;
                if (isBold) newColors += ChatColor.BOLD;
                if (isItalic) newColors += ChatColor.ITALIC;
                if (isStrikethrough) newColors += ChatColor.STRIKETHROUGH;
                if (isUnderlined) newColors += ChatColor.UNDERLINE;
                if (isObfuscated) newColors += ChatColor.MAGIC;
                latestColors = newColors;
                currentLine.append(word);
            } else {
                int wordLength = word.equals(" ") ? 1 : word.replaceAll(COLOR_CODE_REGEX, "").replaceAll(HEX_COLOR_CODE_REGEX, "").length();
                if (currentLine.length() - latestColors.length() + wordLength > maxLineLength && !currentLine.toString().equals(latestColors)) {
                    linesByColor.add(currentLine.toString());
                    currentLine = new StringBuilder(latestColors);
                }
                currentLine.append(word);
            }
        }
        if (!currentLine.toString().equals("") && !currentLine.toString().equals(latestColors)) {
            linesByColor.add(currentLine.toString());
        }
        return linesByColor;
    }
}

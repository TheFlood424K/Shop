package com.snowgears.shop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import com.snowgears.shop.Shop;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.block.Sign;
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

    private static ArrayList<Material> nonIntrusiveMaterials = new ArrayList<Material>();

    /**
     * Returns true if the server is running Minecraft 1.17 or newer.
     */
    public static boolean isMCVersion17Plus() {
        try {
            String version = Bukkit.getBukkitVersion();
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1].split("-")[0]);
            return (major > 1) || (major == 1 && minor >= 17);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Returns true if the server is running Minecraft 1.14 or newer.
     * Kept as a compat method for callers that guard 1.14-specific features.
     */
    public static boolean isMCVersion14Plus() {
        try {
            String version = Bukkit.getBukkitVersion();
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1].split("-")[0]);
            return (major > 1) || (major == 1 && minor >= 14);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Copies an InputStream to a File.
     */
    public static void copy(InputStream in, File file) {
        if (in == null) return;
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            Shop.getPlugin().getLogger().warning("Error copying resource file to " + file.getPath() + ": " + e.getMessage());
        }
    }

    public static String trimForSign(String text) {
        final int MAX_SIGN_WIDTH = 80;
        int currentWidth = 0;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c == '\u00a7' || c == '&') && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ("0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(nextChar) != -1) {
                    result.append(c).append(nextChar);
                    i++;
                    continue;
                }
            }

            int charWidth = getMinecraftCharWidth(c);

            if (currentWidth + charWidth >= MAX_SIGN_WIDTH) {
                break;
            }

            result.append(c);
            currentWidth += charWidth;
        }

        return result.toString();
    }

    private static int getMinecraftCharWidth(char c) {
        switch (c) {
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|': case '\u00a1':
                return 3;
            case '\'': case 'l': case '\u00ec': case '\u00ed':
                return 3;
            case ' ': case 'I': case '[': case ']': case '\u00ef': case '\u00d7':
                return 4;
            case '"': case '(': case ')': case '<': case '>': case 'f': case 'k': case '{': case '}':
                return 5;
            case '@': case '~': case '\u00ae':
                return 7;
            default:
                return 6;
        }
    }

    public static String formatLongToKString(double value, boolean formatZeros) {
        if (value == Double.MIN_VALUE) return formatLongToKString(Double.MIN_VALUE + 1, formatZeros);
        if (value < 0) return "-" + formatLongToKString(-value, formatZeros);

        Map.Entry<Double, String> e = Shop.getPlugin().getPriceSuffixes().floorEntry(value);
        Double minimumValue = Shop.getPlugin().getPriceSuffixMinimumValue();;

        if (value < 1000 || e == null || value < minimumValue){
            if(isDecimal(value))
                return new DecimalFormat("0.00").format(value);
            else
                return new DecimalFormat("#.##").format(value);
        }

        Double divideBy = e.getKey();
        String suffix = e.getValue();

        double truncated = value / (divideBy / 10);
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);

        String builtString = "";
        double fPrice;
        if(hasDecimal){
            fPrice = (truncated / 10d);
        }
        else{
            fPrice = (truncated / 10);
        }

        builtString = new DecimalFormat("#.##").format(fPrice);
        builtString += suffix;
        return builtString;
    }

    public static boolean isDecimal(double d){
        return (d % 1 != 0);
    }

    public static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * Strips non-numeric characters from a price/number string and returns it clean.
     * E.g. "$1,234.56" → "1234.56"
     */
    public static String cleanNumberText(String text) {
        if (text == null) return "0";
        return text.replaceAll("[^0-9.]", "");
    }

    /**
     * Splits a string into lines of at most maxLength characters each.
     * Used for wrapping display text.
     */
    public static List<String> splitStringIntoLines(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLength) {
                current.append(" ").append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /**
     * Returns the multiplier value for an item amount string.
     * E.g. "x4" → 4, "4" → 4, null/invalid → 1.
     */
    public static int getMultiplyValue(String text) {
        if (text == null) return 1;
        String cleaned = text.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return 1;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Serialises an ItemStack to a Base64 string.
     * Returns null on failure.
     */
    public static String itemStackToBase64(ItemStack item) {
        if (item == null) return null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            Shop.getPlugin().getLogger().warning("Error serialising ItemStack to Base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserialises an ItemStack from a Base64 string.
     * Returns null on failure.
     */
    public static ItemStack itemStackFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            Shop.getPlugin().getLogger().warning("Error deserialising ItemStack from Base64: " + e.getMessage());
            return null;
        }
    }

    public static BlockFace yawToFace(float yaw) {
        final BlockFace[] axis = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
        return axis[Math.round(yaw / 90f) & 0x3];
    }

    public static float faceToYaw(BlockFace bf) {
        switch(bf){
            case NORTH:
                return 180;
            case NORTH_EAST:
                return 225;
            case EAST:
                return 270;
            case SOUTH_EAST:
                return 315;
            case SOUTH:
                return 0;
            case SOUTH_WEST:
                return 45;
            case WEST:
                return 90;
            case NORTH_WEST:
                return 135;
        }
        return 180;
    }

    public static String capitalize(String line) {
        String[] spaces = line.split("\\s+");
        String capped = "";
        for (String s : spaces) {
            if (s.length() > 1)
                capped = capped + Character.toUpperCase(s.charAt(0)) + s.substring(1) + " ";
            else {
                capped = capped + s.toUpperCase() + " ";
            }
        }
        return capped.substring(0, capped.length()-1);
    }

    public static String getCleanLocation(Location loc, boolean includeWorld){
        String text = "";
        if (loc == null) { return text; }
        if(includeWorld && loc.getWorld() != null)
            text = loc.getWorld().getName() + " - ";
        text = text + "("+ loc.getBlockX() + ", "+loc.getBlockY() + ", "+loc.getBlockZ() + ")";
        return text;
    }

    public static Location getLocation(String cleanLocation){
        World world = null;

        if(cleanLocation.contains(" - ")) {
            int dashIndex = cleanLocation.indexOf(" - ");
            world = Bukkit.getWorld(cleanLocation.substring(0, dashIndex));
            cleanLocation = cleanLocation.substring(dashIndex+1, cleanLocation.length());
        }
        else {
            world = Bukkit.getWorld("world");
        }
        cleanLocation = cleanLocation.replaceAll("[^\\d-]", " ");

        String[] sp = cleanLocation.split("\\s+");

        try {
            return new Location(world, Integer.valueOf(sp[1]), Integer.valueOf(sp[2]), Integer.valueOf(sp[3]));
        } catch (Exception e){
            return null;
        }
    }

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
        if(!(signBlock.getBlockData() instanceof WallSign))
            return 0;
        WallSign s = (WallSign)signBlock.getBlockData();
        BlockFace attachedFace = s.getFacing().getOppositeFace();
        Location chest = signBlock.getRelative(attachedFace).getLocation().add(0.5,0.5,0.5);
        Location head = player.getLocation().add(0, player.getEyeHeight(), 0);

        Vector direction = head.subtract(chest).toVector().normalize();
        Vector look = player.getLocation().getDirection().normalize();

        Vector cp = direction.crossProduct(look);

        double d = 0;
        switch(attachedFace){
            case NORTH:
                d = cp.getZ();
                break;
            case SOUTH:
                d = cp.getZ() * -1;
                break;
            case EAST:
                d = cp.getX() * -1;
                break;
            case WEST:
                d = cp.getX();
                break;
            default:
                break;
        }

        if(player.getLocation().getPitch() < 0)
            d = -d;

        if(d > 0)
            return 1;
        else if(d < 0)
            return -1;
        else
            return 0;
    }

    public static String convertDurationToString(int duration) {
        duration = duration / 20;
        if (duration < 10)
            return "0:0" + duration;
        else if (duration < 60)
            return "0:" + duration;
        double mins = duration / 60;
        double secs = (mins - (int) mins);
        secs = (double) Math.round(secs * 100000) / 100000;
        if (secs == 0)
            return (int) mins + ":00";
        else if (secs < 10)
            return (int) mins + ":0" + (int) secs;
        else
            return (int) mins + ":" + (int) secs;
    }

    public static Location pushLocationInDirection(Location location, BlockFace direction, double add){
        switch (direction){
            case NORTH:
                location = location.add(-add, 0, -add);
            case EAST:
                location = location.add(add, 0, -add);
            case SOUTH:
                location = location.add(add, 0, add);
            case WEST:
                location = location.add(-add, 0, 0);
        }
        return location;
    }

    public static int getDurabilityPercent(ItemStack item) {
        if (item.getType().getMaxDurability() > 0) {
            double dur = ((double)(item.getType().getMaxDurability() - item.getDurability()) / (double)item.getType().getMaxDurability());
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
                if (UUID.fromString(name.substring(0, 36)) != null)
                    return true;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    public static boolean containsLocation(String s){
        if(s == null)
            return false;
        if(s.startsWith("***{")){
            if((s.indexOf(',') != s.lastIndexOf(',')) && s.indexOf('}') != -1)
                return true;
        }
        return false;
    }

    public static boolean basicLocationMatch(Location loc1, Location loc2){
        return (loc1.getBlockX() == loc2.getBlockX() && loc1.getBlockY() == loc2.getBlockY() && loc1.getBlockZ() == loc2.getBlockZ());
    }

    public static boolean materialIsNonIntrusive(Material material){
        if(nonIntrusiveMaterials.isEmpty()){
            initializeNonIntrusiveMaterials();
        }

        return (nonIntrusiveMaterials.contains(material));
    }

    public static String getLoreString(ItemStack is){
        if(is.getItemMeta() == null || is.getItemMeta().getLore() == null || is.getItemMeta().getLore().isEmpty())
            return "";
        return is.getItemMeta().getLore().toString();
    }

    /**
     * Translates a translation key to plain text.
     */
    public static String translate(String key){
        return key;
    }

    public static String formatTickTime(int ticks){
        int totalSeconds = ticks / 20;

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return " " + String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return " " + String.format("%d:%02d", minutes, seconds);
        }
    }

    public static String formatRomanNumerals(int number){
        if (number < 2) return "";
        if(number > 5)
            return " " + String.valueOf(number);
        String[] romanNumerals = {"I", "II", "III", "IV", "V"};
        return " " + romanNumerals[number - 1];
    }

    public static String removeColorsIfOnlyWhite(String message){
        String COLOR_CODE_REGEX_NO_WHITE = "([&\u00a7][0-9A-EK-ORXa-ek-orx])";
        boolean hasOtherColors = Pattern.compile(COLOR_CODE_REGEX_NO_WHITE).matcher(message).find();
        String msgStr = message;
        if (!hasOtherColors) { msgStr = ChatColor.stripColor(msgStr); }
        return msgStr;
    }

    /**
     * Builds an Adventure TextComponent describing enchantments/trims/music discs
     * on the given ItemStack.
     */
    public static Component getEnchantmentsComponent(ItemStack item){
        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();

        if(item.getItemMeta() instanceof EnchantmentStorageMeta || item.getEnchantments().size() > 0){
            Map<Enchantment, Integer> enchantsMap;
            if(item.getItemMeta() instanceof EnchantmentStorageMeta){
                enchantsMap = ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants();
            }
            else { enchantsMap = item.getEnchantments(); }

            if(enchantsMap != null && !enchantsMap.isEmpty()) {
                builder.append(Component.text(" ["));
                int i = 0;
                for(Map.Entry<Enchantment, Integer> entry : enchantsMap.entrySet()){
                    builder.append(ItemNameUtil.getEnchantmentTranslatable(entry.getKey()));
                    builder.append(Component.text(formatRomanNumerals(entry.getValue())));
                    i++;
                    if(i != enchantsMap.size()) builder.append(Component.text(", "));
                    else builder.append(Component.text("]"));
                }
            }
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof ArmorMeta){
            ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();
            if (armorMeta.getTrim() != null) {
                // Use getKey().getKey() instead of deprecated translationKey()
                String material = armorMeta.getTrim().getMaterial().getKey().getKey().replace("_", " ");
                String pattern = armorMeta.getTrim().getPattern().getKey().getKey().replace("_", " ");
                builder.append(Component.text(" [" + capitalize(pattern)));
                builder.append(Component.text(" (" + capitalize(material) + ")]"));
            }
        }

        if(item.getItemMeta() != null) {
            String itemType = item.getType().name();

            if(itemType.startsWith("MUSIC_DISC_")) {
                String trackName = itemType.replace("MUSIC_DISC_", "");
                String formattedName = capitalize(trackName.toLowerCase().replace("_", " "));
                builder.append(Component.text(" [Song: " + formattedName + "]"));
            }
            else if(itemType.equals("MUSIC_DISC")) { builder.append(Component.text(" [Song: Unknown]")); }
            else if(itemType.equals("PIGSTEP")) { builder.append(Component.text(" [Song: Pigstep]")); }
            else if(itemType.equals("OTHERSIDE")) { builder.append(Component.text(" [Song: Otherside]")); }
            else if(itemType.equals("FIVE")) { builder.append(Component.text(" [Song: 5]")); }
            else if(itemType.equals("RELIC")) { builder.append(Component.text(" [Song: Relic]")); }

            else if(itemType.equals("GOAT_HORN")) {
                try {
                    org.bukkit.inventory.meta.MusicInstrumentMeta instrumentMeta = (org.bukkit.inventory.meta.MusicInstrumentMeta) item.getItemMeta();
                    if (instrumentMeta.getInstrument() != null) {
                        String instrumentName = capitalize(instrumentMeta.getInstrument().getKey().getKey().replace("_", " "));
                        builder.append(Component.text(" [" + instrumentName + "]"));
                    }
                } catch (Exception e) {}
            }
        }

        return builder.build();
    }

    private static void initializeNonIntrusiveMaterials(){
        nonIntrusiveMaterials.add(Material.AIR);
        nonIntrusiveMaterials.add(Material.CAVE_AIR);
        nonIntrusiveMaterials.add(Material.VOID_AIR);
        nonIntrusiveMaterials.add(Material.WATER);
        nonIntrusiveMaterials.add(Material.LAVA);
        nonIntrusiveMaterials.add(Material.TALL_GRASS);
        nonIntrusiveMaterials.add(Material.SHORT_GRASS);
        nonIntrusiveMaterials.add(Material.FERN);
        nonIntrusiveMaterials.add(Material.LARGE_FERN);
        nonIntrusiveMaterials.add(Material.DEAD_BUSH);
        nonIntrusiveMaterials.add(Material.SEAGRASS);
        nonIntrusiveMaterials.add(Material.TALL_SEAGRASS);
        nonIntrusiveMaterials.add(Material.SNOW);
        nonIntrusiveMaterials.add(Material.VINE);
        nonIntrusiveMaterials.add(Material.LILY_PAD);
    }
}

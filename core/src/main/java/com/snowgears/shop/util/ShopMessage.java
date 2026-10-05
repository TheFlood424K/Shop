package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopMessage {

    private final static Shop plugin = Shop.getPlugin();

    private static boolean disableItemHover = false;

    private static final Map<String, Function<PlaceholderContext, Component>> placeholders = new HashMap<>();
    private static final String COLOR_CODE_REGEX = "([&\u00a7][0-9A-FK-ORXa-fk-orx])";
    private static final String HEX_CODE_REGEX = "(#[0-9a-fA-F]{6})";
    private static final String PLACEHOLDER_REGEX = "(\\[([^&\u00a7#\\[\\]]+)\\])";

    private static final String TEXT_SEGMENT_REGEX = "([^&\u00a7\\[#]+)";
    private static final String OPEN_BRACKET_REGEX = "(\\[)";
    private static final String CLOSE_BRACKET_REGEX = "(\\])";
    private static final String MESSAGE_PARTS_REGEX =
            COLOR_CODE_REGEX + "|" +
            HEX_CODE_REGEX + "|" +
            PLACEHOLDER_REGEX + "|" +
            OPEN_BRACKET_REGEX + "|" +
            CLOSE_BRACKET_REGEX + "|" +
            TEXT_SEGMENT_REGEX + "|" +
            "(.{1})";

    private static HashMap<String, String> messageMap = new HashMap<>();

    /**
     * List-valued config entries, which {@link #messageMap} cannot hold since it is a
     * {@code Map<String, String>}.
     *
     * <p>Kept separate rather than widening messageMap so every existing caller is unaffected.
     */
    private static HashMap<String, List<String>> listMessageMap = new HashMap<>();
    // chatConfig.yml sections that nest a per-shop-type block (SELL:, BUY:, ...). A lookup that
    // arrives keyed only by the shop type has to search each of these, in this order.
    private static final String[] SHOP_TYPE_SECTIONS = {
        "transaction_issue", "transaction", "interaction", "interaction_issue", "description"
    };
    private static HashMap<String, String[]> shopSignTextMap = new HashMap<>();
    private static HashMap<String, List<String>> displayTextMap = new HashMap<>();
    private static String freePriceWord;
    private static String adminStockWord;
    private static String serverDisplayName;
    private static String stockColorInStock;
    private static String stockColorOutOfStock;
    private static HashMap<String, String> creationWords = new HashMap<>();
    private static YamlConfiguration chatConfig;
    private static YamlConfiguration signConfig;
    private static YamlConfiguration displayConfig;
    private static int targetMaxLength;

    public ShopMessage(Shop plugin) {
        File chatConfigFile = new File(plugin.getDataFolder(), "chatConfig.yml");
        chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);
        File signConfigFile = new File(plugin.getDataFolder(), "signConfig.yml");
        signConfig = YamlConfiguration.loadConfiguration(signConfigFile);
        File displayConfigFile = new File(plugin.getDataFolder(), "displayConfig.yml");
        displayConfig = YamlConfiguration.loadConfiguration(displayConfigFile);

        loadMessagesFromConfig();
        loadSignTextFromConfig();
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap keys: " + shopSignTextMap.keySet());
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap SELL: " + java.util.Arrays.toString(shopSignTextMap.get("SELL")));
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap BUY: " + java.util.Arrays.toString(shopSignTextMap.get("BUY")));
        loadDisplayTextFromConfig();
        loadCreationWords();

        freePriceWord = signConfig.getString("sign_text.zeroPrice");
        adminStockWord = signConfig.getString("sign_text.adminStock");
        serverDisplayName = signConfig.getString("sign_text.serverDisplayName");
        targetMaxLength = displayConfig.getInt("targetMaxLength", 40);

        // Load stock color config
        stockColorInStock = signConfig.getString("stock_color.in_stock", "&a");
        stockColorOutOfStock = signConfig.getString("stock_color.out_of_stock", "&4");

        loadPlaceholders();
    }

    /**
     * Reloads message configs from the plugin's data folder.
     * Useful for tests where configs are copied after plugin initialization.
     */
    public static void reloadConfigs(Shop plugin) {
        plugin.getLogger().info("[DEBUG ShopMessage.reloadConfigs] Loading configs from " + plugin.getDataFolder().getAbsolutePath());
        File chatConfigFile = new File(plugin.getDataFolder(), "chatConfig.yml");
        plugin.getLogger().info("[DEBUG ShopMessage.reloadConfigs] chatConfigFile exists: " + chatConfigFile.exists() + " path: " + chatConfigFile.getAbsolutePath());
        chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);
        File signConfigFile = new File(plugin.getDataFolder(), "signConfig.yml");
        signConfig = YamlConfiguration.loadConfiguration(signConfigFile);
        File displayConfigFile = new File(plugin.getDataFolder(), "displayConfig.yml");
        displayConfig = YamlConfiguration.loadConfiguration(displayConfigFile);

        loadMessagesFromConfig();
        loadSignTextFromConfig();
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap keys: " + shopSignTextMap.keySet());
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap SELL: " + java.util.Arrays.toString(shopSignTextMap.get("SELL")));
        plugin.getLogger().info("[DEBUG loadSignTextFromConfig] shopSignTextMap BUY: " + java.util.Arrays.toString(shopSignTextMap.get("BUY")));
        loadDisplayTextFromConfig();
        loadCreationWords();

        plugin.getLogger().info("[DEBUG ShopMessage.reloadConfigs] messageMap size: " + messageMap.size());
        plugin.getLogger().info("[DEBUG ShopMessage.reloadConfigs] interaction.initialCreateInstruction: " + messageMap.get("interaction.initialCreateInstruction"));

        freePriceWord = signConfig.getString("sign_text.zeroPrice");
        adminStockWord = signConfig.getString("sign_text.adminStock");
        serverDisplayName = signConfig.getString("sign_text.serverDisplayName");
        targetMaxLength = displayConfig.getInt("targetMaxLength", 40);

        // Load stock color config
        stockColorInStock = signConfig.getString("stock_color.in_stock", "&a");
        stockColorOutOfStock = signConfig.getString("stock_color.out_of_stock", "&4");
    }

    // -----------------------------------------------------------------------
    // Adventure helpers
    // -----------------------------------------------------------------------

    public static Component componentFromLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', legacy));
    }

    public static String toLegacy(Component component) {
        if (component == null) return "";
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static String toPlain(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // -----------------------------------------------------------------------
    // Colour helpers
    // -----------------------------------------------------------------------

    public static TextColor getTextColor(String code) {
        if (code == null) return null;
        if (code.matches(HEX_CODE_REGEX)) {
            return TextColor.fromHexString(code);
        }
        if (code.matches(COLOR_CODE_REGEX)) {
            char c = Character.toLowerCase(code.charAt(1));
            switch (c) {
                case '0': return NamedTextColor.BLACK;
                case '1': return NamedTextColor.DARK_BLUE;
                case '2': return NamedTextColor.DARK_GREEN;
                case '3': return NamedTextColor.DARK_AQUA;
                case '4': return NamedTextColor.DARK_RED;
                case '5': return NamedTextColor.DARK_PURPLE;
                case '6': return NamedTextColor.GOLD;
                case '7': return NamedTextColor.GRAY;
                case '8': return NamedTextColor.DARK_GRAY;
                case '9': return NamedTextColor.BLUE;
                case 'a': return NamedTextColor.GREEN;
                case 'b': return NamedTextColor.AQUA;
                case 'c': return NamedTextColor.RED;
                case 'd': return NamedTextColor.LIGHT_PURPLE;
                case 'e': return NamedTextColor.YELLOW;
                case 'f': return NamedTextColor.WHITE;
                default:  return null;
            }
        }
        return null;
    }

    private static boolean isFormattingCode(String code) {
        if (code == null || code.length() < 2) return false;
        char c = Character.toLowerCase(code.charAt(1));
        return c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r';
    }

    private static TextDecoration getDecoration(char c) {
        switch (Character.toLowerCase(c)) {
            case 'k': return TextDecoration.OBFUSCATED;
            case 'l': return TextDecoration.BOLD;
            case 'm': return TextDecoration.STRIKETHROUGH;
            case 'n': return TextDecoration.UNDERLINED;
            case 'o': return TextDecoration.ITALIC;
            default:  return null;
        }
    }

    // -----------------------------------------------------------------------
    // Placeholder registry
    // -----------------------------------------------------------------------

    public static void registerPlaceholder(String placeholder, Function<PlaceholderContext, Component> valueFunction) {
        placeholders.put(placeholder.toLowerCase(), valueFunction);
    }

    /**
     * True when a placeholder's component is nothing but a colour/formatting code, i.e. a
     * directive that tints the text after it rather than content in its own right.
     */
    private static boolean isColorDirective(Component component) {
        if (!(component instanceof TextComponent text)) return false;
        String content = text.content();
        return content != null && content.matches(COLOR_CODE_REGEX) && text.children().isEmpty();
    }

    public static Component replacePlaceholder(String placeholder, PlaceholderContext context) {
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] Attempting to replace placeholder: " + placeholder + " " + context);
        Function<PlaceholderContext, Component> valueFunction = placeholders.get(placeholder.toLowerCase());
        if (valueFunction != null) {
            try {
                plugin.getLogger().spam("[ShopMessage.replacePlaceholder]     Running placeholder function... " + placeholder);
                Object result = valueFunction.apply(context);
                if (result != null) {
                    Component message;
                    if (result instanceof Component) {
                        message = (Component) result;
                    } else if (result instanceof String) {
                        message = componentFromLegacy((String) result);
                    } else {
                        message = Component.text(result.toString());
                    }
                    plugin.getLogger().trace("[ShopMessage.replacePlaceholder]  *** placeholder " + placeholder + "  value: " + toPlain(message));
                    return message;
                }
            } catch (Error | Exception e) {
                Bukkit.getLogger().warning("Error replacing placeholder " + placeholder + ": " + e.getMessage());
            }
        }
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] *** returning empty, unable to replace: " + placeholder);
        return Component.empty();
    }

    // -----------------------------------------------------------------------
    // format() — builds an Adventure Component from a legacy-style string
    // -----------------------------------------------------------------------

    public static Component format(String message, PlaceholderContext context) {
        if (message == null) return Component.empty();
        plugin.getLogger().spam("[ShopMessage] pre-format: " + ChatColor.translateAlternateColorCodes('&', message), true);

        Matcher matcher = Pattern.compile(MESSAGE_PARTS_REGEX).matcher(message);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group());
        }

        TextColor latestColor = null;
        boolean isBold = false;
        boolean isItalic = false;
        boolean isStrikethrough = false;
        boolean isUnderlined = false;
        boolean isObfuscated = false;

        TextComponent.Builder builder = Component.text();
        boolean addedText = false;

        for (String part : parts) {
            plugin.getLogger().trace("[ShopMessage.format] part: " + part);

            if (part.matches(COLOR_CODE_REGEX) || part.matches(HEX_CODE_REGEX)) {
                try {
                    char c = Character.toLowerCase(part.charAt(1));
                    if (c == 'r') {
                        latestColor = NamedTextColor.WHITE;
                        isBold = isItalic = isStrikethrough = isUnderlined = isObfuscated = false;
                        builder.append(Component.text(""));
                    } else if (isFormattingCode(part)) {
                        TextDecoration dec = getDecoration(c);
                        if (dec == TextDecoration.BOLD)          isBold = true;
                        else if (dec == TextDecoration.ITALIC)   isItalic = true;
                        else if (dec == TextDecoration.STRIKETHROUGH) isStrikethrough = true;
                        else if (dec == TextDecoration.UNDERLINED) isUnderlined = true;
                        else if (dec == TextDecoration.OBFUSCATED) isObfuscated = true;
                    } else {
                        TextColor color = getTextColor(part);
                        if (color != null) latestColor = color;
                    }
                    continue;
                } catch (Exception e) {
                    // fall through
                }
            }

            Component partComponent;
            if (part.matches(PLACEHOLDER_REGEX) && placeholders.containsKey(part.toLowerCase())) {
                plugin.getLogger().hyper("[ShopMessage.format]     matched PLACEHOLDER_REGEX: " + part);
                Object placeholderResult = placeholders.get(part.toLowerCase()).apply(context);
                // Handle null placeholder result
                if (placeholderResult == null) {
                    partComponent = Component.empty();
                } else if (placeholderResult instanceof Component && isColorDirective((Component) placeholderResult)) {
                    // "[stock color]" resolves to a component whose entire content is a bare
                    // colour code. Treat it as a colour directive so it tints the text that
                    // follows; appending it as content dropped the colour entirely, leaving
                    // out-of-stock shops with no colour on the sign.
                    String code = ((TextComponent) placeholderResult).content();
                    char c = Character.toLowerCase(code.charAt(1));
                    if (c == 'r') {
                        latestColor = NamedTextColor.WHITE;
                        isBold = isItalic = isStrikethrough = isUnderlined = isObfuscated = false;
                    } else if (isFormattingCode(code)) {
                        TextDecoration dec = getDecoration(c);
                        if (dec == TextDecoration.BOLD)          isBold = true;
                        else if (dec == TextDecoration.ITALIC)   isItalic = true;
                        else if (dec == TextDecoration.STRIKETHROUGH) isStrikethrough = true;
                        else if (dec == TextDecoration.UNDERLINED) isUnderlined = true;
                        else if (dec == TextDecoration.OBFUSCATED) isObfuscated = true;
                    } else {
                        TextColor color = getTextColor(code);
                        if (color != null) latestColor = color;
                    }
                    partComponent = Component.empty();
                } else if (placeholderResult instanceof String && ((String) placeholderResult).matches(COLOR_CODE_REGEX)) {
                    char c = Character.toLowerCase(((String) placeholderResult).charAt(1));
                    if (c == 'r') {
                        latestColor = NamedTextColor.WHITE;
                        isBold = isItalic = isStrikethrough = isUnderlined = isObfuscated = false;
                    } else if (isFormattingCode((String) placeholderResult)) {
                        TextDecoration dec = getDecoration(c);
                        if (dec == TextDecoration.BOLD)          isBold = true;
                        else if (dec == TextDecoration.ITALIC)   isItalic = true;
                        else if (dec == TextDecoration.STRIKETHROUGH) isStrikethrough = true;
                        else if (dec == TextDecoration.UNDERLINED) isUnderlined = true;
                        else if (dec == TextDecoration.OBFUSCATED) isObfuscated = true;
                    } else {
                        TextColor color = getTextColor((String) placeholderResult);
                        if (color != null) latestColor = color;
                    }
                    partComponent = Component.text(""); // empty component, color applied via latestColor
                } else {
                    if (placeholderResult instanceof Component) {
                        partComponent = (Component) placeholderResult;
                    } else if (placeholderResult instanceof String) {
                        partComponent = componentFromLegacy((String) placeholderResult);
                    } else {
                        partComponent = Component.text(placeholderResult.toString());
                    }
                }
            } else {
                partComponent = Component.text(part);
            }

            TextComponent.Builder partBuilder = Component.text().append(partComponent);
            if (latestColor != null) partBuilder.color(latestColor);
            if (isBold)          partBuilder.decoration(TextDecoration.BOLD, true);
            if (isItalic)        partBuilder.decoration(TextDecoration.ITALIC, true);
            if (isStrikethrough) partBuilder.decoration(TextDecoration.STRIKETHROUGH, true);
            if (isUnderlined)    partBuilder.decoration(TextDecoration.UNDERLINED, true);
            if (isObfuscated)    partBuilder.decoration(TextDecoration.OBFUSCATED, true);

            builder.append(partBuilder.build());
            addedText = true;
        }

        Component result = builder.build();
        plugin.getLogger().spam("[ShopMessage] postFormat: " + toLegacy(result), true);
        return result;
    }

    /**
     * Compat wrapper: formats a message string with an AbstractShop context, no player.
     * Used by ShopGuiHandler.reloadPlayerHeadIcon and similar 2-arg call sites.
     */
    public static String formatMessage(String message, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        return toLegacy(format(message, context));
    }

    /**
     * Compat wrapper: formats a message string with an AbstractShop context and optional player.
     */
    public static String formatMessage(String message, AbstractShop shop, Player player, boolean unused) {
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        if (player != null) context.setPlayer(player);
        return toLegacy(format(message, context));
    }

    /**
     * Compat wrapper: formats a message string with a PlaceholderContext.
     */
    public static Component formatMessage(String message, PlaceholderContext context) {
        return format(message, context);
    }

    // -----------------------------------------------------------------------
    // sendMessage overloads
    // -----------------------------------------------------------------------

    public static void sendMessage(String message, Player player, PlaceholderContext context) {
        Component fancyMessage = format(message, context);
        plugin.getLogger().debug("Sent msg to player " + player.getName() + ": " + toLegacy(fancyMessage), true);
        try {
            player.sendMessage(fancyMessage);
            return;
        } catch (Exception | Error e) {
            plugin.getLogger().warning("Error sending message to player: " + e.getMessage());
            plugin.getLogger().debug("Error details: ", e);
        }
        try {
            player.sendMessage(toLegacy(fancyMessage));
            plugin.getLogger().warning("Sent legacy text message to player as backup");
        } catch (Error | Exception e) {
            plugin.getLogger().debug("Error sending message to player", e);
        }
    }

    public static void sendMessage(String message, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        sendMessage(message, player, context);
    }

    public static void sendMessage(String message, Player player, ItemStack item) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setItem(item);
        sendMessage(message, player, context);
    }

    public static void sendMessage(String key, String subkey, Player player, AbstractShop shop) {
        String message = getUnformattedMessage(key, subkey);
        plugin.getLogger().info("[DEBUG sendMessage] key=" + key + " subkey=" + subkey + " fullKey=" + (key + "." + subkey) + " message=" + message + " player=" + player.getName());
        if (message != null && !message.isEmpty())
            sendMessage(message, player, shop);
    }

    public static void sendMessage(String key, String subkey, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        String message = getUnformattedMessage(key, subkey);
        plugin.getLogger().info("[DEBUG sendMessage] key=" + key + " subkey=" + subkey + " message=" + message);
        if (message != null && !message.isEmpty())
            sendMessage(message, player, context);
    }

    public static void sendMessage(String message, Player player, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setShop(shop);
        sendMessage(message, player, context);
    }

    public static void sendMessage(String message, Player player, Player user, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(user);
        context.setShop(shop);
        sendMessage(message, player, context);
    }

    public static void sendMessage(String message, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        sendMessage(message, player, context);
    }

    public static void sendMessage(String message, Player player, OfflineTransactions offlineTxs) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setOfflineTransactions(offlineTxs);
        sendMessage(message, player, context);
    }

    // -----------------------------------------------------------------------
    // embedItem
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Component embedItem(Component display, ItemStack item) {
        if (item == null || display == null) return display != null ? display : Component.empty();
        if (disableItemHover) return display;
        try {
            // Adventure 5.x: BinaryTagHolder was removed from net.kyori.adventure.nbt.
            // Use the 2-arg showItem(key, amount) overload which omits NBT data.
            return display.hoverEvent(HoverEvent.showItem(
                    item.getType().getKey(),
                    item.getAmount()));
        } catch (Exception | Error e) {
            return display;
        }
    }

    // -----------------------------------------------------------------------
    // Placeholder loaders
    // -----------------------------------------------------------------------

    public static void loadPlaceholders() {
        registerPlaceholder("[plugin]", context -> Component.text(plugin.getCommandAlias()));
        registerPlaceholder("[server name]", context -> Component.text(ShopMessage.getServerDisplayName()));
        registerPlaceholder("[player]", context -> {
            Player player = context.getPlayer();
            return Component.text((player != null) ? player.getName() : "");
        });
        registerPlaceholder("[user]", context -> {
            if (context.getPlayer() != null) return Component.text(context.getPlayer().getName());
            if (context.getOfflinePlayer() != null) return Component.text(context.getOfflinePlayer().getName());
            return Component.text("Unknown Player");
        });
        registerPlaceholder("[shop type]", context -> {
            if (context.getProcess() != null && context.getProcess().getShopType() != null)
                return Component.text(context.getProcess().getShopType().toString());
            if (context.getShop() != null)
                return Component.text(ShopMessage.getCreationWord(context.getShop().getType().name().toUpperCase()));
            return null;
        });
        registerPlaceholder("[shop types]", ShopMessage::getShopTypesPlaceholder);
        // "[shop]" and the per-type "[<type> shop]" tags appear on line 1 of every sign_text
        // block in signConfig.yml, but were never registered, so signs rendered them literally.
        registerPlaceholder("[shop]", ShopMessage::getShopNameWord);
        for (ShopType shopType : ShopType.values()) {
            registerPlaceholder("[" + shopType.name().toLowerCase() + " shop]", context -> shopNameWord(context, shopType));
        }
        registerPlaceholder("[buy / sell]", context -> {
            if (context.getShop() == null) return null;
            return context.getShop().getType() == ShopType.COMBO
                    ? Component.text(getCreationWord("BUY") + " / " + getCreationWord("SELL"))
                    : getShopNameWord(context);
        });
        registerPlaceholder("[total shops]", context -> Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops())));

        registerPlaceholder("[owner]", context -> {
            if (context.getProcess() != null)
                return Component.text(String.valueOf(Bukkit.getOfflinePlayer(context.getProcess().getPlayerUUID())));
            if (context.getShop() != null)
                return Component.text(context.getShop().isAdmin() ? ShopMessage.getServerDisplayName() : context.getShop().getOwnerName());
            return null;
        });
        registerPlaceholder("[user amount]", context -> {
            if (context.getPlayer() != null)
                return Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getPlayer())));
            if (context.getShop().getOwner() != null)
                return Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getShop().getOwner().getUniqueId())));
            return Component.text("0");
        });
        registerPlaceholder("[build limit]", context -> Component.text(String.valueOf(plugin.getShopListener().getBuildLimit(context.getPlayer()))));
        registerPlaceholder("[tp time remaining]", context -> Component.text(String.valueOf(plugin.getShopListener().getTeleportCooldownRemaining(context.getPlayer()))));

        registerPlaceholder("[world]", context -> {
            if (context.getProcess() != null && context.getProcess().getClickedChest() != null)
                return Component.text(context.getProcess().getClickedChest().getWorld().getName());
            if (context.getShop() != null)
                return Component.text(context.getShop().getSignLocation().getWorld().getName());
            return null;
        });
        registerPlaceholder("[location]", context -> {
            Location loc = null;
            if (context.getLocation() != null) loc = context.getLocation();
            else if (context.getProcess() != null && context.getProcess().getClickedChest() != null)
                loc = context.getProcess().getClickedChest().getLocation();
            else if (context.getShop() != null) loc = context.getShop().getSignLocation();
            if (loc == null) return null;
            Component text = Component.text(UtilMethods.getCleanLocation(loc, false));
            if (context.getProcess() == null && context.getShop() == null) return text;
            return text.hoverEvent(getShopInfoHoverEvent(context));
        });

        registerPlaceholder("[currency name]", context -> Component.text(plugin.getCurrencyName()));
        registerPlaceholder("[currency item]", context -> embedItem(plugin.getItemNameUtil().getName(plugin.getItemCurrency()), plugin.getItemCurrency()));

        registerPlaceholder("[item]", ShopMessage::getItemPlaceholder);
        registerPlaceholder("[item amount]", context -> {
            if (context.getItem() != null) return Component.text(String.valueOf(context.getItem().getAmount()));
            if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getItemAmount()));
            if (context.getShop() != null && context.getShop().getItemStack() != null)
                return Component.text(String.valueOf(context.getShop().getItemStack().getAmount()));
            return null;
        });
        registerPlaceholder("[item enchants]", context -> {
            if (context.getShop() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getShop().getItemStack()), context.getShop().getItemStack());
            if (context.getProcess() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getProcess().getItemStack()), context.getProcess().getItemStack());
            if (context.getItem() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getItem()), context.getItem());
            return null;
        });
        registerPlaceholder("[item lore]", context -> {
            if (context.getShop() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getShop().getItemStack())), context.getShop().getItemStack());
            if (context.getProcess() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getProcess().getItemStack())), context.getProcess().getItemStack());
            if (context.getItem() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getItem())), context.getItem());
            return null;
        });
        registerPlaceholder("[item durability]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getItemDurabilityPercent()));
            return null;
        });
        registerPlaceholder("[item type]", context -> {
            if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE)
                return Component.text("???");
            return ItemNameUtil.getNameTranslatable(context.getShop().getItemStack().getType());
        });
        registerPlaceholder("[gamble item amount]", context -> {
            if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE)
                return Component.text(String.valueOf(context.getShop().getAmount()));
            return null;
        });
        registerPlaceholder("[gamble item]", context -> {
            if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE)
                return embedItem(plugin.getItemNameUtil().getName(plugin.getGambleDisplayItem()), plugin.getGambleDisplayItem());
            return null;
        });

        registerPlaceholder("[barter item amount]", context -> {
            if (context.getBarterItem() != null) return Component.text(String.valueOf(context.getBarterItem().getAmount()));
            if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null)
                return Component.text(String.valueOf(context.getShop().getSecondaryItemStack().getAmount()));
            if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getBarterItemAmount()));
            if (context.getItem() != null) return Component.text(String.valueOf(context.getItem().getAmount()));
            return null;
        });
        registerPlaceholder("[barter item]", ShopMessage::getBarterItemPlaceholder);
        registerPlaceholder("[barter item durability]", context -> {
            if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null)
                return Component.text(String.valueOf(context.getShop().getSecondaryItemDurabilityPercent()));
            return null;
        });
        registerPlaceholder("[barter item type]", context -> {
            if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null)
                return ItemNameUtil.getNameTranslatable(context.getShop().getSecondaryItemStack().getType());
            return null;
        });
        registerPlaceholder("[barter item enchants]", context -> {
            if (context.getBarterItem() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getBarterItem()), context.getBarterItem());
            if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null)
                return embedItem(UtilMethods.getEnchantmentsComponent(context.getShop().getSecondaryItemStack()), context.getShop().getSecondaryItemStack());
            if (context.getProcess() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getProcess().getBarterItemStack()), context.getProcess().getBarterItemStack());
            if (context.getItem() != null) return embedItem(UtilMethods.getEnchantmentsComponent(context.getItem()), context.getItem());
            return null;
        });
        registerPlaceholder("[barter item lore]", context -> {
            if (context.getBarterItem() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getBarterItem())), context.getBarterItem());
            if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null)
                return embedItem(Component.text(UtilMethods.getLoreString(context.getShop().getSecondaryItemStack())), context.getShop().getSecondaryItemStack());
            if (context.getProcess() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getProcess().getBarterItemStack())), context.getProcess().getBarterItemStack());
            if (context.getItem() != null) return embedItem(Component.text(UtilMethods.getLoreString(context.getItem())), context.getItem());
            return null;
        });

        // Fix: replaced isInfinitePrice() → isAdmin() (admin shops have infinite price)
        registerPlaceholder("[price]", context -> {
            if (context.getProcess() != null && context.getProcess().getPrice() > -1)
                return Component.text(UtilMethods.formatLongToKString(context.getProcess().getPrice(), false));
            if (context.getShop() != null)
                return Component.text(context.getShop().isAdmin() ? getAdminStockWord()
                        : UtilMethods.formatLongToKString(context.getShop().getPrice(), false));
            return null;
        });
        registerPlaceholder("[balance]", context -> {
            if (context.getPlayer() != null)
                return Component.text(UtilMethods.formatLongToKString(plugin.getEconomy().getBalance(context.getPlayer()), true));
            return null;
        });
        registerPlaceholder("[cost]", context -> {
            if (context.getShop() != null)
                return Component.text(UtilMethods.formatLongToKString(context.getShop().getPrice() * context.getShop().getItemStack().getAmount(), false));
            return null;
        });

        registerPlaceholder("[stock]", context -> {
            if (context.getShop() != null) {
                return context.getShop().isAdmin()
                        ? Component.text(getAdminStockWord())
                        : Component.text(String.valueOf(context.getShop().getStock()));
            }
            return null;
        });
        registerPlaceholder("[stock color]", context -> {
            // Keep the raw "&4"/"&a" as the component's content so format() recognises it as
            // a colour directive (see isColorDirective) and applies it to the following text.
            // Returning componentFromLegacy() instead produced an empty component and the
            // colour was silently dropped.
            if (context.getShop() != null) {
                int stock = context.getShop().isAdmin() ? Integer.MAX_VALUE : context.getShop().getStock();
                return Component.text(stock > 0 ? stockColorInStock : stockColorOutOfStock);
            }
            return null;
        });
        registerPlaceholder("[amount]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getAmount()));
            if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getItemAmount()));
            return null;
        });
        // getMaxStock() exists on AbstractShop — no change needed here
        registerPlaceholder("[max stock]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getMaxStock()));
            return null;
        });
        registerPlaceholder("[display type]", context -> {
            if (context.getShop() != null) return Component.text(context.getShop().getDisplay().getType().toString());
            return null;
        });
        registerPlaceholder("[display types]", context -> {
            StringBuilder sb = new StringBuilder();
            DisplayType[] types = DisplayType.values();
            for (int i = 0; i < types.length; i++) {
                sb.append(types[i].toString());
                if (i < types.length - 1) sb.append(", ");
            }
            return Component.text(sb.toString());
        });
        // Fix: display.getAmount() doesn't exist — use shop.getAmount() instead
        registerPlaceholder("[display amount]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getAmount()));
            return null;
        });

        registerPlaceholder("[seller]", context -> {
            if (context.getShop() != null) {
                if (context.getShop().isAdmin()) return Component.text(getServerDisplayName());
                return Component.text(context.getShop().getOwnerName());
            }
            return null;
        });
        registerPlaceholder("[buyer]", context -> {
            if (context.getShop() != null) {
                if (context.getShop().isAdmin()) return Component.text(getServerDisplayName());
                return Component.text(context.getShop().getOwnerName());
            }
            return null;
        });
        // Fix: ComboShop no longer has getBuyShop()/getSellShop() sub-shop accessors.
        // Use getOwnerName() (shared owner) and getPriceBuy()/getPriceSell() directly.
        registerPlaceholder("[combo buy shop owner]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                return Component.text(cs.getOwnerName());
            }
            return null;
        });
        registerPlaceholder("[combo sell shop owner]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                return Component.text(cs.getOwnerName());
            }
            return null;
        });
        registerPlaceholder("[combo buy price]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                return Component.text(UtilMethods.formatLongToKString(cs.getPriceBuy(), false));
            }
            return null;
        });
        registerPlaceholder("[combo sell price]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                return Component.text(UtilMethods.formatLongToKString(cs.getPriceSell(), false));
            }
            return null;
        });

        // Fix: getTransactionCount() → getNumTransactions()
        registerPlaceholder("[offline tx count]", context -> {
            if (context.getOfflineTransactions() != null)
                return Component.text(String.valueOf(context.getOfflineTransactions().getNumTransactions()));
            return null;
        });
        // Fix: getTotalAmount() removed — use getTotalProfit() + getTotalSpent() combined
        registerPlaceholder("[offline tx total]", context -> {
            if (context.getOfflineTransactions() != null) {
                double total = context.getOfflineTransactions().getTotalProfit()
                        + context.getOfflineTransactions().getTotalSpent();
                return Component.text(UtilMethods.formatLongToKString(total, true));
            }
            return null;
        });
        // Fix: getItemStack() removed — use first key from itemsSold, falling back to itemsBought
        registerPlaceholder("[offline tx item]", context -> {
            if (context.getOfflineTransactions() != null) {
                ItemStack item = null;
                Map<ItemStack, Integer> sold = context.getOfflineTransactions().getItemsSold();
                if (sold != null && !sold.isEmpty()) item = sold.keySet().iterator().next();
                if (item == null) {
                    Map<ItemStack, Integer> bought = context.getOfflineTransactions().getItemsBought();
                    if (bought != null && !bought.isEmpty()) item = bought.keySet().iterator().next();
                }
                if (item != null)
                    return embedItem(plugin.getItemNameUtil().getName(item), item);
            }
            return null;
        });
        // [offline tx type] removed — getType() no longer exists on OfflineTransactions
    }

    // -----------------------------------------------------------------------
    // Hover event builders
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static HoverEvent<?> getShopInfoHoverEvent(PlaceholderContext context) {
        TextComponent.Builder hoverText = Component.text();
        AbstractShop shop = context.getShop();
        ShopCreationProcess process = context.getProcess();

        if (shop != null) {
            hoverText.append(Component.text("Owner: " + (shop.isAdmin() ? getServerDisplayName() : shop.getOwnerName())));
            hoverText.append(Component.newline());
            hoverText.append(Component.text("Type: " + shop.getType().name()));
            hoverText.append(Component.newline());
            hoverText.append(Component.text("Item: "));
            hoverText.append(plugin.getItemNameUtil().getName(shop.getItemStack()));
            hoverText.append(Component.newline());
            // Fix: isInfinitePrice() → isAdmin()
            String priceStr = shop.isAdmin() ? getAdminStockWord() : UtilMethods.formatLongToKString(shop.getPrice(), false);
            hoverText.append(Component.text("Price: " + priceStr));
        } else if (process != null) {
            hoverText.append(Component.text("New shop at: " + UtilMethods.getCleanLocation(
                    process.getClickedChest() != null ? process.getClickedChest().getLocation() : null, true)));
        }
        return HoverEvent.showText(hoverText.build());
    }

    private static Component getItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getItem() != null) item = context.getItem();
        else if (context.getShop() != null) item = context.getShop().getItemStack();
        else if (context.getProcess() != null) item = context.getProcess().getItemStack();
        if (item == null) return null;
        return embedItem(plugin.getItemNameUtil().getName(item), item);
    }

    private static Component getBarterItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getBarterItem() != null) item = context.getBarterItem();
        else if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null)
            item = context.getShop().getSecondaryItemStack();
        else if (context.getProcess() != null) item = context.getProcess().getBarterItemStack();
        if (item == null) return null;
        return embedItem(plugin.getItemNameUtil().getName(item), item);
    }

    /**
     * The shop-type word used by the "[shop]" and "[&lt;type&gt; shop]" sign tags.
     * <p>
     * Reads the {@code sign_creation} table in signConfig.yml, NOT {@link #getCreationWord}.
     * The two deliberately differ: {@code creation_words} in chatConfig.yml is written for
     * chat, where {@code SHOP} is the brand tag "[Shop]", while {@code sign_creation} holds the
     * word to print on a sign ("sell", "buy", ...). Using the chat table here would rewrite the
     * literal "[Shop]" in shipped owner-notification messages into the word "sell".
     */
    private static Component getShopNameWord(PlaceholderContext context) {
        // Only meaningful on a sign. In chat text "[Shop]" is the plugin's brand tag — it comes
        // from creation_words.SHOP and must render literally, so resolving it to a shop-type
        // word there would rewrite the brand tag in the shipped owner notifications.
        if (!context.isForSign()) return Component.text("[Shop]");
        if (context.getShop() == null || context.getShop().getType() == null) return null;
        return shopNameWord(context, context.getShop().getType());
    }

    private static Component shopNameWord(PlaceholderContext context, ShopType type) {
        // A per-type tag only reads correctly on a shop of that type; render nothing otherwise
        // rather than telling a BARTER shop it is a "[sell shop]".
        if (context.getShop() != null && context.getShop().getType() != null
                && context.getShop().getType() != type) {
            return null;
        }
        return Component.text(getSignCreationWord(type.name()));
    }

    /**
     * The word for a shop type as it should appear on a sign, from signConfig.yml's
     * {@code sign_creation} table, falling back to the lowercase type name.
     */
    private static String getSignCreationWord(String key) {
        if (signConfig != null) {
            String word = signConfig.getString("sign_creation." + key.toUpperCase());
            if (word != null && !word.isEmpty()) return word;
        }
        return key.toLowerCase();
    }

    private static Component getShopTypesPlaceholder(PlaceholderContext context) {
        TextComponent.Builder builder = Component.text();
        ShopType[] types = ShopType.values();
        for (int i = 0; i < types.length; i++) {
            builder.append(Component.text(getCreationWord(types[i].toString().toUpperCase())));
            if (i < types.length - 1) builder.append(Component.text(", "));
        }
        return builder.build();
    }

    // -----------------------------------------------------------------------
    // Config loading
    // -----------------------------------------------------------------------

    private static void loadMessagesFromConfig() {
        messageMap.clear();
        listMessageMap.clear();
        if (chatConfig == null) return;
        for (String key : chatConfig.getKeys(false)) {
            loadSectionMessages(key, chatConfig.getConfigurationSection(key));
        }
    }

    private static void loadSectionMessages(String path, org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return;
        for (String subKey : section.getKeys(false)) {
            String fullPath = path + "." + subKey;
            Object value = section.get(subKey);
            if (value instanceof String) {
                messageMap.put(fullPath, (String) value);
            } else if (value instanceof java.util.List) {
                // YAML lists were dropped here with no diagnostic, so every list-backed message
                // resolved to null and rendered as an empty chat line — the same silent failure as a
                // misspelled key. Several shipped config sections are lists (offline.summary,
                // creativeSelection.enter and .prompt).
                List<String> lines = new ArrayList<>();
                for (Object entry : (java.util.List<?>) value) {
                    if (entry != null) {
                        lines.add(String.valueOf(entry));
                    }
                }
                listMessageMap.put(fullPath, lines);
            } else if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                // Recursively load nested sections
                loadSectionMessages(fullPath, (org.bukkit.configuration.ConfigurationSection) value);
            } else {
                // A type nothing here understands is a config error worth naming, rather than
                // disappearing the way lists used to.
                Shop.getPlugin().getLogger().warning("Message config key '" + fullPath
                        + "' has an unsupported value type (" + value.getClass().getSimpleName()
                        + "); it will not be loaded.");
            }
        }
    }

    private static void loadSignTextFromConfig() {
        shopSignTextMap.clear();
        if (signConfig == null) return;
        org.bukkit.configuration.ConfigurationSection signTextSection = signConfig.getConfigurationSection("sign_text");
        if (signTextSection == null) return;
        for (String key : signTextSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection shopSection = signTextSection.getConfigurationSection(key);
            if (shopSection == null) continue;

            // Check if the section has sub-sections (normal, admin, etc.) or direct line keys
            if (shopSection.getKeys(false).stream().anyMatch(k -> shopSection.getConfigurationSection(k) != null)) {
                // Has nested variants (normal, admin, etc.) - prefer "normal" variant
                org.bukkit.configuration.ConfigurationSection normalSection = shopSection.getConfigurationSection("normal");
                if (normalSection != null) {
                    List<String> lines = new ArrayList<>();
                    // Config uses numeric keys (1, 2, 3, 4) instead of line1, line2, etc.
                    for (int i = 1; i <= 4; i++) {
                        lines.add(normalSection.getString(String.valueOf(i), ""));
                    }
                    shopSignTextMap.put(key, lines.toArray(new String[0]));
                    System.out.println("[DEBUG loadSignTextFromConfig] Loaded " + key + " normal: " + java.util.Arrays.toString(lines.toArray()));
                }
            } else {
                // Flat structure with line1, line2, etc. or numeric keys
                List<String> lines = new ArrayList<>();
                for (int i = 1; i <= 4; i++) {
                    String val = shopSection.getString("line" + i);
                    if (val == null || val.isEmpty()) {
                        val = shopSection.getString(String.valueOf(i), "");
                    }
                    lines.add(val);
                }
                shopSignTextMap.put(key, lines.toArray(new String[0]));
            }
        }
    }

    private static void loadDisplayTextFromConfig() {
        displayTextMap.clear();
        if (displayConfig == null) return;
        org.bukkit.configuration.ConfigurationSection displaySection = displayConfig.getConfigurationSection("display_text");
        if (displaySection == null) return;
        for (String key : displaySection.getKeys(false)) {
            List<String> lines = displaySection.getStringList(key);
            displayTextMap.put(key, lines);
        }
    }

    private static void loadCreationWords() {
        creationWords.clear();
        if (chatConfig == null) return;
        org.bukkit.configuration.ConfigurationSection section = chatConfig.getConfigurationSection("creation_words");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            creationWords.put(key.toUpperCase(), section.getString(key, key));
        }
    }

    // -----------------------------------------------------------------------
    // Static getters
    // -----------------------------------------------------------------------

    public static String getUnformattedMessage(String key, String subkey) {
        String fullPath = key + "." + subkey;
        String message = messageMap.get(fullPath);
        if (message != null) {
            return message;
        }

        if (key == null || subkey == null) {
            return null;
        }

        String upperSubkey = uppercaseLeadingTypeSegment(subkey);
        if (!upperSubkey.equals(subkey)) {
            message = messageMap.get(key + "." + upperSubkey);
            if (message != null) {
                return message;
            }
        }

        // Shop-type-specific prompts reach this method in three shapes, while chatConfig.yml
        // always nests the shop-type blocks ("SELL:", "BUY:", ...) under a named section in
        // uppercase. A miss on the literal path is retried in the equivalent shape rather than
        // silently returning null:
        //   ("interaction", "sell.createHitChestAmount") -> "interaction.SELL.createHitChestAmount"
        //   ("SELL", "create")                            -> "interaction.SELL.create"
        //   ("sell", "playerNoStock")                     -> "transaction_issue.SELL.playerNoStock"
        // The shop type reaches call sites via ShopType#toString(), which is lowercase, and
        // TransactionHandler passes it as the whole key — so a bare ("sell", "playerNoStock")
        // has to be resolved against every section that scopes messages per shop type. Failing
        // to resolve it returns null, and the caller then sends the player nothing at all.
        if (isShopTypeName(key)) {
            String upperKey = key.toUpperCase();
            for (String section : SHOP_TYPE_SECTIONS) {
                message = messageMap.get(section + "." + upperKey + "." + subkey);
                if (message == null) {
                    message = messageMap.get(section + "." + key + "." + subkey);
                }
                if (message == null) {
                    message = messageMap.get(section + "." + upperKey + "." + upperSubkey);
                }
                if (message != null) {
                    return message;
                }
            }
        }
        return null;
    }

    /**
     * Uppercases a leading lowercase shop-type segment, e.g. "sell.createHitChestAmount"
     * becomes "SELL.createHitChestAmount". Other subkeys are returned unchanged.
     */
    private static String uppercaseLeadingTypeSegment(String subkey) {
        int dot = subkey.indexOf('.');
        if (dot <= 0) {
            return subkey;
        }
        String first = subkey.substring(0, dot);
        if (isShopTypeName(first)) {
            return first.toUpperCase() + subkey.substring(dot);
        }
        return subkey;
    }

    /**
     * True when the segment names a shop type (BUY, SELL, BARTER, COMBO, GAMBLE).
     */
    private static boolean isShopTypeName(String key) {
        try {
            return com.snowgears.shop.shop.ShopType.valueOf(key.toUpperCase()) != null;
        } catch (IllegalArgumentException | NullPointerException notAShopType) {
            return false;
        }
    }

    /**
     * Compat: returns a list of all unformatted messages for a given top-level key.
     */
    public static List<String> getUnformattedMessageList(String key) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : messageMap.entrySet()) {
            if (entry.getKey().startsWith(key + ".") || entry.getKey().equals(key)) {
                result.add(entry.getValue());
            }
        }
        // Include list-valued entries under the same prefix; they are stored separately and would
        // otherwise be invisible to this prefix scan.
        for (Map.Entry<String, List<String>> entry : listMessageMap.entrySet()) {
            if (entry.getKey().startsWith(key + ".") || entry.getKey().equals(key)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Compat overload: returns a list of unformatted messages for a key+subkey pair.
     */
    public static List<String> getUnformattedMessageList(String key, String subkey) {
        // List-valued entries live in their own map; messageMap is Map<String, String> and cannot
        // hold them. Check here first so a multi-line message is not flattened to a single lookup
        // that misses and returns nothing.
        List<String> listed = listMessageMap.get(key + "." + subkey);
        if (listed != null && !listed.isEmpty()) {
            return new ArrayList<>(listed);
        }
        List<String> result = new ArrayList<>();
        String value = getUnformattedMessage(key, subkey);
        if (value != null && !value.isEmpty()) result.add(value);
        return result;
    }

    /**
     * Compat: returns sign lines for a given shop type key string.
     */
    public static String[] getSignLines(String shopType) {
        return getShopSignText(shopType);
    }

    /**
     * Compat overload: returns sign lines for a shop, resolving the type key from the shop.
     */
    public static String[] getSignLines(AbstractShop shop, ShopType type) {
        String key = (type != null ? type.name() : (shop != null && shop.getType() != null ? shop.getType().name() : "sell"));
        String[] rawLines = getShopSignText(key);
        if (shop == null) return rawLines;
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        context.setForSign(true);
        String[] formatted = new String[rawLines.length];
        for (int i = 0; i < rawLines.length; i++) {
            formatted[i] = toLegacy(format(rawLines[i], context));
        }
        return formatted;
    }

    /**
     * Compat overload: returns sign lines for a named key, with a shop for placeholder context.
     */
    public static String[] getSignLines(String key, AbstractShop shop) {
        String[] rawLines = getShopSignText(key);
        if (shop == null) return rawLines;
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        String[] formatted = new String[rawLines.length];
        for (int i = 0; i < rawLines.length; i++) {
            formatted[i] = toLegacy(format(rawLines[i], context));
        }
        return formatted;
    }

    /**
     * Compat: returns display tag lines for a given shop type key.
     */
    public static List<String> getDisplayTags(String shopType) {
        return getDisplayText(shopType);
    }

    /**
     * Compat overload: returns display tag lines resolved from a shop and type.
     */
    public static List<String> getDisplayTags(AbstractShop shop, ShopType type) {
        String key = (type != null ? type.name() : (shop != null && shop.getType() != null ? shop.getType().name() : "sell"));
        return getDisplayText(key);
    }

    /**
     * Compat: returns a formatted message string for a given key path.
     */
    public static String getMessageFromOrders(String key, String subkey) {
        return getUnformattedMessage(key, subkey);
    }

    /**
     * Compat overload: ignores the extra numeric args that old callers passed.
     */
    public static String getMessageFromOrders(String key, String subkey, double amount, int count) {
        return getUnformattedMessage(key, subkey);
    }

    public static String[] getShopSignText(String shopType) {
        // Shop types are stored uppercase (SELL, BUY, ...), but the shared sign-text keys
        // are lowercase in signConfig.yml (deleted, timeout, ...). Trying the given key
        // verbatim first means both resolve, instead of every non-shop-type key falling
        // through to the placeholder default and rendering literal "[item]" text on signs.
        String[] exact = shopSignTextMap.get(shopType);
        if (exact != null) {
            return exact;
        }
        return shopSignTextMap.getOrDefault(shopType.toUpperCase(), new String[]{"Buy", "[item]", "[price]", "[stock]"});
    }

    public static List<String> getDisplayText(String shopType) {
        return displayTextMap.getOrDefault(shopType.toUpperCase(), Collections.emptyList());
    }

    public static String getFreePriceWord() { return freePriceWord != null ? freePriceWord : "Free"; }
    public static String getAdminStockWord() { return adminStockWord != null ? adminStockWord : "\u221e"; }
    public static String getServerDisplayName() { return serverDisplayName != null ? serverDisplayName : "Server"; }
    public static String getStockColorInStock() { return stockColorInStock != null ? stockColorInStock : "&a"; }
    public static String getStockColorOutOfStock() { return stockColorOutOfStock != null ? stockColorOutOfStock : "&4"; }
    public static HashMap<String, String> getCreationWords() { return creationWords; }
    public static String getCreationWord(String key) {
        return creationWords.getOrDefault(key.toUpperCase(), UtilMethods.capitalize(key.toLowerCase()));
    }
    public static int getTargetMaxLength() { return targetMaxLength; }
    public static YamlConfiguration getChatConfig() { return chatConfig; }
    public static YamlConfiguration getSignConfig() { return signConfig; }
    public static YamlConfiguration getDisplayConfig() { return displayConfig; }
}

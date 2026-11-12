package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
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
import com.google.gson.JsonParseException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles message formatting, placeholder replacement, and sending of chat messages for shops.
 * Manages configuration loading for chat, sign, and display messages, as well as placeholder registration.
 */
public class ShopMessage {

    private final static Shop plugin = Shop.getPlugin();

    private static boolean disableItemHover = false;

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final Map<String, Function<PlaceholderContext, Component>> placeholders = new HashMap<>();
    // Regex pattern to identify placeholders within square brackets, e.g., [owner]
    private static final String COLOR_CODE_REGEX = "([&][0-9A-FK-ORXa-fk-orx])";
    private static final String HEX_CODE_REGEX = "(#[0-9a-fA-F]{6})";
    // Modified placeholder regex to only match real placeholders
    private static final String PLACEHOLDER_REGEX = "(\\[([^&#\\[\\]]+)\\])";
    private static final String TEXT_SEGMENT_REGEX = "([^&\\[#]+)";
    // Add dedicated patterns for brackets to ensure they're matched individually
    private static final String OPEN_BRACKET_REGEX = "(\\[)";
    private static final String CLOSE_BRACKET_REGEX = "(\\])";
    private static final String MESSAGE_PARTS_REGEX =
        COLOR_CODE_REGEX + "|" +
        HEX_CODE_REGEX + "|" +
        PLACEHOLDER_REGEX + "|" +
        OPEN_BRACKET_REGEX + "|" +  // Match opening bracket
        CLOSE_BRACKET_REGEX + "|" +  // Match closing bracket
        TEXT_SEGMENT_REGEX + "|" +  // Match text segments
        "(.{1})";          // Match any single character as fallback

    private static HashMap<String, String> messageMap = new HashMap<String, String>();
    private static HashMap<String, String[]> shopSignTextMap = new HashMap<String, String[]>();
    private static HashMap<String, List<String>> displayTextMap = new HashMap<String, List<String>>();
    private static String freePriceWord;
    private static String adminStockWord;
    private static String serverDisplayName;
    private static HashMap<String, String> creationWords = new HashMap<String, String>();
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
        loadDisplayTextFromConfig();
        loadCreationWords();

        freePriceWord = signConfig.getString("sign_text.zeroPrice");
        adminStockWord = signConfig.getString("sign_text.adminStock");
        serverDisplayName = signConfig.getString("sign_text.serverDisplayName");
        targetMaxLength = displayConfig.getInt("targetMaxLength", 40);

        // Load in our placeholders
        loadPlaceholders();
    }

    /**
     * Registers a placeholder with its corresponding value retrieval function.
     *
     * @param placeholder The placeholder string without brackets, e.g., "owner"
     * @param valueFunction A function that takes a PlaceholderContext instance and returns the replacement string
     */
    public static void registerPlaceholder(String placeholder, Function<PlaceholderContext, Component> valueFunction) {
        placeholders.put(placeholder.toLowerCase(), valueFunction);
    }

    /**
     * Attempts to replace a single placeholder within a message.
     *
     * @param placeholder The placeholder string without brackets, e.g., "owner"
     * @param context     The PlaceholderContext instance containing Shop and Player
     * @return The replacement component or an empty component if replacement fails
     */
    public static Component replacePlaceholder(String placeholder, PlaceholderContext context) {
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] Attempting to replace placeholder: " + placeholder + " " + context);
        Function<PlaceholderContext, Component> valueFunction = placeholders.get(placeholder.toLowerCase());
        if (valueFunction != null) {
            try {
                plugin.getLogger().spam("[ShopMessage.replacePlaceholder]     Running placeholder function... " + placeholder);
                Component message = valueFunction.apply(context);
                if (message != null) {
                    plugin.getLogger().trace("[ShopMessage.replacePlaceholder]  *** placeholder " + placeholder + "  value: " + message);
                    return message;
                }
            } catch (Error | Exception e) {
                // Log at severe level so failures are visible, and include placeholder context
                Bukkit.getLogger().severe("Error replacing placeholder '" + placeholder + "': " + e.getMessage());
            }
        }
        // If placeholder not found, remove the placeholder and just return an empty string
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] *** returning empty string, unable to get function to replace placeholder: " + placeholder);
        return Component.empty();
    }

    private static String replacePlaceholdersForMiniMessage(String message, PlaceholderContext context) {
        if (message == null || context == null) {
            return message;
        }
        Pattern placeholderPattern = Pattern.compile("\\[([^\\[\\]]+)\\]");
        Matcher matcher = placeholderPattern.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            String placeholderWithBrackets = matcher.group();
            Function<PlaceholderContext, Component> valueFunction = placeholders.get(placeholderKey.toLowerCase());
            if (valueFunction != null) {
                try {
                    Component replacement = valueFunction.apply(context);
                    if (replacement != null) {
                        String replacementText = PlainTextComponentSerializer.plainText().serialize(replacement);
                        // For location placeholder in messages, ensure it's properly formatted
                        if (placeholderKey.equalsIgnoreCase("location")) {
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacementText));
                        } else {
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacementText));
                        }
                        continue;
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().severe("Error replacing placeholder '" + placeholderKey + "': " + e.getMessage());
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(placeholderWithBrackets));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Gets the Adventure TextColor from a string, supporting both legacy color codes and hex colors.
     *
     * @param color The color string to parse (e.g., "&a", "&a", or "#FF0000")
     * @return The corresponding TextColor, or null if no color is found
     */
    public static TextColor getColor(String color) {
        if (color.matches(HEX_CODE_REGEX)) {
            return TextColor.color(Integer.parseInt(color.substring(1), 16));
        } else if (color.matches(COLOR_CODE_REGEX)) {
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
            ChatColor bukkitColor = ChatColor.getByChar(color.charAt(1));
            if (bukkitColor != null) {
                return toAdventureColor(bukkitColor);
            }
        }
        return null;
    }

    /**
     * Formats a message by replacing all placeholders with their respective values.
     *
     * @param message The message containing placeholders
     * @param context The PlaceholderContext instance containing Shop and Player
     * @return The formatted message with all placeholders replaced
     */
    public static Component format(String message, PlaceholderContext context) {
        Component formattedMessage = null;
        if (message == null) { return Component.empty(); }
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
        plugin.getLogger().spam("[ShopMessage] pre-format: " + ChatColor.translateAlternateColorCodes('&', message), true);

        // Check if the message contains MiniMessage syntax (contains actual MiniMessage tags, not just < > around placeholders)
        boolean hasMiniMessageSyntax = false;
        if (message != null && message.contains("<")) {
            // Check for actual MiniMessage tags like <red>, <bold>, <#hex>, </tag>, etc.
            // Exclude angle brackets around placeholders like <[location]>
            java.util.regex.Pattern miniTagPattern = java.util.regex.Pattern.compile("<(/?[a-zA-Z][a-zA-Z0-9_-]*(?::[^>]*)?|#[0-9a-fA-F]{6}|[a-zA-Z][a-zA-Z0-9_-]*)>");
            java.util.regex.Matcher miniTagMatcher = miniTagPattern.matcher(message);
            while (miniTagMatcher.find()) {
                String tag = miniTagMatcher.group(1);
                if (tag != null && tag.startsWith("[") && tag.endsWith("]")) {
                    continue;
                }
                hasMiniMessageSyntax = true;
                break;
            }
        }

        TextColor latestColor = null;
        boolean addedText = false;

        boolean isBold = false;
        boolean isItalic = false;
        boolean isStrikethrough = false;
        boolean isUnderlined = false;
        boolean isObfuscated = false;

        if (hasMiniMessageSyntax) {
            // Escape invalid MiniMessage tags before parsing
            String escapedMessage = escapeInvalidMiniMessageTags(message);
            // Replace placeholders before MiniMessage parsing
            String processedMessage = replacePlaceholdersForMiniMessage(escapedMessage, context);
            try {
                // Use MiniMessage for formatting
                Component miniMessageComponent = miniMessage.deserialize(processedMessage);
                formattedMessage = miniMessageComponent;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse MiniMessage, falling back to legacy parsing: " + e.getMessage());
                // Fallback to legacy parsing
                message = processedMessage;
                hasMiniMessageSyntax = false;
            }
        }
        if (!hasMiniMessageSyntax || formattedMessage == null) {
            // Define the regex pattern
            Matcher matcher = Pattern.compile(MESSAGE_PARTS_REGEX).matcher(message);
            List<String> parts = new ArrayList<>();
            while (matcher.find()) {
                parts.add(matcher.group());
            }

            latestColor = null;
            addedText = false;

            isBold = false;
            isItalic = false;
            isStrikethrough = false;
            isUnderlined = false;
            isObfuscated = false;

            for (String part : parts) {
                plugin.getLogger().spam("\n\n");
                plugin.getLogger().trace("[ShopMessage.format] part: " + part);
                Component partComponent = Component.text(part);

            // Check if we are a formatting code (bold, italic, etc.) — handle before color parsing
            if (part.length() == 2 && part.charAt(0) == '&') {
                char fmtCode = part.charAt(1);
                if (fmtCode == 'l' || fmtCode == 'L') { isBold = true; continue; }
                if (fmtCode == 'm' || fmtCode == 'M') { isStrikethrough = true; continue; }
                if (fmtCode == 'n' || fmtCode == 'N') { isUnderlined = true; continue; }
                if (fmtCode == 'o' || fmtCode == 'O') { isItalic = true; continue; }
                if (fmtCode == 'k' || fmtCode == 'K') { isObfuscated = true; continue; }
                if (fmtCode == 'r' || fmtCode == 'R') {
                    latestColor = TextColor.color(0xFFFFFF);
                    isBold = false; isItalic = false; isStrikethrough = false;
                    isUnderlined = false; isObfuscated = false;
                    if (formattedMessage == null) { formattedMessage = Component.empty(); }
                    else { formattedMessage = formattedMessage.append(Component.empty()); }
                    addedText = true;
                    continue;
                }
            }

            // Check if we are a color code
            if (part.matches(COLOR_CODE_REGEX) || part.matches(HEX_CODE_REGEX)) {
                try {
                    TextColor newColor = getColor(part);
                    if (newColor != null) {
                        latestColor = newColor;
                    }
                    plugin.getLogger().hyper("[ShopMessage.format]     matched COLOR_CODE_REGEX: " + part);
                    plugin.getLogger().hyper("[ShopMessage.format]     newColor: " + newColor);
                    plugin.getLogger().hyper("[ShopMessage.format]     isBold: " + isBold + " isItalic: " + isItalic + " isStrikethrough: " + isStrikethrough + " isUnderlined: " + isUnderlined + " isObfuscated: " + isObfuscated);
                    continue; // Don't add this text to the message, just go to the next part
                } catch (Exception e) {
                    plugin.getLogger().hyper("[ShopMessage.format] XXX unknown color code! Going to add this as a normal string! " + part);
                }
            }

            // If we match to a placeholder, then we want to use it's Component instead of the "normal" one
            if (part.matches(PLACEHOLDER_REGEX)) {
                plugin.getLogger().hyper("[ShopMessage.format]     matched PLACEHOLDER_REGEX: " + part);
                plugin.getLogger().hyper("[ShopMessage.format]     is part placeholder? " + (placeholders.get(part) != null));
                if (placeholders.get(part) != null) {
                    plugin.getLogger().hyper("[ShopMessage.format]     replacing placeholder... " + part);
                    partComponent = replacePlaceholder(part, context);
                    // Check if we set a color inside our part (for example [stock color])
                    net.kyori.adventure.text.format.TextColor partColor = partComponent.color();
                    if (partColor != null && latestColor != null) {
                        if (!partColor.equals(latestColor) && partColor != TextColor.color(0xFFFFFF)) {
                            plugin.getLogger().hyper("[ShopMessage.format]     getting latestColor from partComponent: " + partColor);
                            latestColor = partColor;
                        }
                    }
                }
            }

            // Set the color
            if (latestColor != null) {
                plugin.getLogger().hyper("[ShopMessage.format]     setting part color to: " + latestColor);
                partComponent = partComponent.color(latestColor);
            }
            if (isBold) partComponent = partComponent.decoration(TextDecoration.BOLD, true);
            if (isItalic) partComponent = partComponent.decoration(TextDecoration.ITALIC, true);
            if (isStrikethrough) partComponent = partComponent.decoration(TextDecoration.STRIKETHROUGH, true);
            if (isUnderlined) partComponent = partComponent.decoration(TextDecoration.UNDERLINED, true);
            if (isObfuscated) partComponent = partComponent.decoration(TextDecoration.OBFUSCATED, true);
            if (formattedMessage == null) {
                formattedMessage = partComponent;
            } else {
                // Add the part of the string to the
                formattedMessage = formattedMessage.append(partComponent);
            }
                addedText = true;
                plugin.getLogger().hyper("[ShopMessage.format] *** add part Component to main message: " + partComponent);
            } // <-- Closes the for (String part : parts) loop
        } // <-- Closes if (!hasMiniMessageSyntax || formattedMessage == null)

        // Handle if we are just a color code with an empty string
        if (formattedMessage == null) formattedMessage = Component.empty();
        if (!addedText && latestColor != null) formattedMessage = formattedMessage.color(latestColor);
        plugin.getLogger().spam("[ShopMessage] postFormat: " + (formattedMessage != null ? LegacyComponentSerializer.legacy('&').serialize(formattedMessage) : ""), true);
        return formattedMessage;
    }

    /**
     * Converts a Bukkit ChatColor to an Adventure TextColor.
     *
     * @param chatColor The ChatColor to convert
     * @return The equivalent Adventure TextColor, or null if chatColor is null
     */
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
    private static net.kyori.adventure.text.format.TextColor toAdventureColor(ChatColor chatColor) {
        if (chatColor == null) { return null; }
        // ChatColor.getColor() is not available in Paper 26.2; map manually to hex
        return switch (chatColor) {
            case WHITE -> TextColor.color(0xFFFFFF);
            case AQUA -> TextColor.color(0x55FFFF);
            case YELLOW -> TextColor.color(0xFFFF55);
            case GREEN -> TextColor.color(0x55AA55);
            case DARK_GREEN -> TextColor.color(0x005500);
            case DARK_AQUA -> TextColor.color(0x55AAAA);
            case DARK_RED -> TextColor.color(0xAA0000);
            case DARK_PURPLE -> TextColor.color(0xAA00AA);
            case GOLD -> TextColor.color(0xFFAA00);
            case BLUE -> TextColor.color(0x5555FF);
            case DARK_BLUE -> TextColor.color(0x0000AA);
            case LIGHT_PURPLE -> TextColor.color(0xFFAAFF);
            case GRAY -> TextColor.color(0xAAAAAA);
            case DARK_GRAY -> TextColor.color(0x555555);
            case BLACK -> TextColor.color(0x000000);
            case RED -> TextColor.color(0xFF5555);
            default -> TextColor.color(0xFFFFFF);
        };
    }

    /**
     * Formats a message using MiniMessage syntax, supporting advanced formatting
     * like colors, styles, click events, and hover events beyond legacy {@code &} / {@code &} codes.
     *
     * @param message The message in MiniMessage format (e.g., "<red>Hello</red>" or "<#FF0000>Hello</#FF0000>")
     * @param context The PlaceholderContext instance containing Shop and Player
     * @return The formatted message as a Component
     */
    public static Component formatMiniMessage(String message, PlaceholderContext context) {
        Component formattedMessage = null;
        if (message == null) { return Component.empty(); }
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
        plugin.getLogger().spam("[ShopMessage] pre-format-minimessage: " + ChatColor.translateAlternateColorCodes('&', message), true);

        try {
            // Escape invalid MiniMessage tags before parsing
            String escapedMessage = escapeInvalidMiniMessageTags(message);
            // Replace placeholders before MiniMessage parsing
            String processedMessage = replacePlaceholdersForMiniMessage(escapedMessage, context);
            Component miniMessageComponent = miniMessage.deserialize(processedMessage);
            formattedMessage = miniMessageComponent;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse MiniMessage, falling back to legacy parsing: " + e.getMessage());
            formattedMessage = format(message, context);
        }

        plugin.getLogger().spam("[ShopMessage] post-format-minimessage: " + (formattedMessage != null ? LegacyComponentSerializer.legacy('&').serialize(formattedMessage) : ""), true);
        return formattedMessage != null ? formattedMessage : Component.empty();
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, PlaceholderContext context) {
        Component fancyMessage = format(message, context);
        plugin.getLogger().debug("Sent msg to player " + player.getName() + ": " + LegacyComponentSerializer.legacy('&').serialize(fancyMessage), true);
        try {
            player.sendMessage(fancyMessage);
            return;
        } catch (JsonParseException e) {
            plugin.getLogger().warning("Possible NBT error while sending message to player, Item Hover events will now be disabled! Details: " + e.getMessage());
            plugin.getLogger().debug("Error details: ", e);
            // Notify the user that hover events were disabled
            try {
                player.sendMessage(Component.text("&cHover events disabled due to message error"));
            } catch (Exception ex) {
                plugin.getLogger().debug("Failed to send hover disable notification to player", ex);
            }
            // disableItemHover = true;
        } catch (Exception | Error e) {
            plugin.getLogger().warning("Error sending message to player: " + e.getMessage());
            plugin.getLogger().debug("Error details: ", e);
        }

        // If we get here, we have an error, we should at least try to send it as legacy text
        try {
            player.sendMessage(LegacyComponentSerializer.legacy('§').serialize(fancyMessage));
            plugin.getLogger().warning("Sent legacy text message to player as backup, removed hover/click events");
        } catch (Error | Exception e) {
            plugin.getLogger().debug("Error sending message to player", e);
        }
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, ItemStack item) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setItem(item);
        sendMessage(message, player, context);
    }

    /**
     * Loads message, swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String key, String subkey, Player player, AbstractShop shop) {
        String message = getUnformattedMessage(key, subkey);
        if(message != null && !message.isEmpty())
            sendMessage(message, player, shop);
    }

    /**
     * Loads message, swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String key, String subkey, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        String message = getUnformattedMessage(key, subkey);
        if(message != null && !message.isEmpty())
            sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setShop(shop);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, Player user, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(user);
        context.setShop(shop);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, OfflineTransactions offlineTxs) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setOfflineTransactions(offlineTxs);
        sendMessage(message, player, context);
    }

    /**
     * Formats a message using MiniMessage syntax, supporting advanced formatting
     * like colors, styles, click events, and hover events beyond legacy {@code &} / {@code &} codes.
     *
     * @param message The message in MiniMessage format
     * @param player The player to send the message to
     * @param context The PlaceholderContext instance
     */
    public static void sendMiniMessage(String message, Player player, PlaceholderContext context) {
        Component fancyMessage = formatMiniMessage(message, context);
        plugin.getLogger().debug("Sent minimessage to player " + player.getName() + ": " + LegacyComponentSerializer.legacy('&').serialize(fancyMessage), true);
        try {
            player.sendMessage(fancyMessage);
            return;
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending minimessage to player: " + e.getMessage());
            // Fall back to legacy text
            try {
                player.sendMessage(LegacyComponentSerializer.legacy('§').serialize(fancyMessage));
                plugin.getLogger().warning("Sent legacy text message to player as backup, removed hover/click events");
            } catch (Error | Exception e2) {
                plugin.getLogger().debug("Error sending legacy message to player", e2);
            }
        }
    }

    /**
     * Formats a message using MiniMessage syntax (without placeholders).
     *
     * @param message The message in MiniMessage format
     * @param player The player to send the message to
     */
    public static void sendMiniMessage(String message, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        sendMiniMessage(message, player, context);
    }

    /**
     * Loads all available placeholders into the map.
     * This method should be called during the plugin's initialization phase.
     */
    public static void loadPlaceholders() {
        registerPlaceholder("[plugin]", context -> Component.text(plugin.getCommandAlias()));
        registerPlaceholder("[server name]", context -> Component.text(ShopMessage.getServerDisplayName()));
        registerPlaceholder("[player]", context -> { Player player = context.getPlayer(); return Component.text((player != null) ? player.getName() : ""); });
        registerPlaceholder("[user]", context -> {
            if (context.getPlayer() != null) return Component.text(context.getPlayer().getName());
            if (context.getOfflinePlayer() != null) return Component.text(context.getOfflinePlayer().getName());
            return Component.text("Unknown Player");
        });
        registerPlaceholder("[shop type]", context -> {
            if (context.getProcess() != null && context.getProcess().getShopType() != null) return Component.text(context.getProcess().getShopType().toString());
            if (context.getShop() != null) return Component.text(ShopMessage.getCreationWord(context.getShop().getType().toString().toUpperCase()));
            return null;
        });
        registerPlaceholder("[shop types]", ShopMessage::getShopTypesPlaceholder);
        registerPlaceholder("[total shops]", context -> Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops())));

        // Player Info Placeholders
        registerPlaceholder("[owner]", context -> {
            if (context.getProcess() != null) return Component.text(String.valueOf(Bukkit.getOfflinePlayer(context.getProcess().getPlayerUUID())));
            else if (context.getShop() != null) return Component.text(context.getShop().isAdmin() ? ShopMessage.getServerDisplayName() : context.getShop().getOwnerName());
            return null;
        });
        registerPlaceholder("[user amount]", context -> {
            if (context.getPlayer() != null) return Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getPlayer())));
            else if (context.getShop().getOwner() != null) return Component.text(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getShop().getOwner().getUniqueId())));
            return Component.text("0"); // If they don't have any shops, it should be 0
        });
        registerPlaceholder("[build limit]", context -> Component.text(String.valueOf(plugin.getShopListener().getBuildLimit(context.getPlayer()))));
        registerPlaceholder("[tp time remaining]", context -> Component.text(String.valueOf(plugin.getShopListener().getTeleportCooldownRemaining(context.getPlayer()))));

        // Location Placeholders
        registerPlaceholder("[world]", context -> {
            if (context.getProcess() != null && context.getProcess().getClickedChest() != null) return Component.text(context.getProcess().getClickedChest().getWorld().getName());
            else if (context.getShop() != null) return Component.text(context.getShop().getSignLocation().getWorld().getName());
            return null;
        });
        registerPlaceholder("[location]", context -> {
            Location loc = null;
            if (context.getLocation() != null) loc = context.getLocation();
            else if (context.getProcess() != null && context.getProcess().getClickedChest() != null) loc = context.getProcess().getClickedChest().getLocation();
            else if (context.getShop() != null) loc = context.getShop().getSignLocation();
            if (loc == null) return null;
            Component text = Component.text(UtilMethods.getCleanLocation(loc, false));
            if (context.getProcess() == null && context.getShop() == null) return text;
            try {
                text = text.hoverEvent(getShopInfoHoverEvent(context));
            } catch (Exception e) {
                // ignore
            }
            return text;
        });

        // Currency Placeholders
        registerPlaceholder("[currency name]", context -> Component.text(plugin.getCurrencyName()));
        registerPlaceholder("[currency item]", context -> embedItem(plugin.getItemNameUtil().getName(plugin.getItemCurrency()), plugin.getItemCurrency()));

        // Shop Item placeholders
        registerPlaceholder("[item]", ShopMessage::getItemPlaceholder);
        registerPlaceholder("[item amount]", context -> {
            if (context.getItem() != null) return Component.text(String.valueOf(context.getItem().getAmount()));
            else if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getItemAmount()));
            else if (context.getShop() != null && context.getShop().getItemStack() != null) return Component.text(String.valueOf(context.getShop().getItemStack().getAmount()));
            return null;
        });
        registerPlaceholder("[item enchants]", context -> { 
            if (context.getShop() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getShop().getItemStack()), context.getShop().getItemStack()); } 
            if (context.getProcess() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getProcess().getItemStack()), context.getProcess().getItemStack()); } 
            if (context.getItem() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getItem()), context.getItem()); }
            return null; });
        registerPlaceholder("[item lore]", context -> { 
            if (context.getShop() != null) { return embedItem(UtilMethods.getLoreString(context.getShop().getItemStack()), context.getShop().getItemStack()); } 
            if (context.getProcess() != null) { return embedItem(UtilMethods.getLoreString(context.getProcess().getItemStack()), context.getProcess().getItemStack()); } 
            if (context.getItem() != null) { return embedItem(UtilMethods.getLoreString(context.getItem()), context.getItem()); }
            return null; 
        });
        registerPlaceholder("[item durability]", context -> { if (context.getShop() != null) { return Component.text(String.valueOf(context.getShop().getItemDurabilityPercent())); } return null; });
        registerPlaceholder("[item type]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE) { return Component.text("???"); } else { return Shop.getPlugin().getItemNameUtil().getNameTranslatable(context.getShop().getItemStack().getType()); } });
        registerPlaceholder("[gamble item amount]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE) { return Component.text(String.valueOf(context.getShop().getAmount())); } return null; });
        registerPlaceholder("[gamble item]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.GAMBLE) { return embedItem(plugin.getItemNameUtil().getName(plugin.getGambleDisplayItem()), plugin.getGambleDisplayItem()); } return null; });

        // Shop Barter Item Placeholders
        registerPlaceholder("[barter item amount]", context -> {
            if (context.getBarterItem() != null) return Component.text(String.valueOf(context.getBarterItem().getAmount()));
            if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null) return Component.text(String.valueOf(context.getShop().getSecondaryItemStack().getAmount()));
            if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getBarterItemAmount()));
            if (context.getItem() != null) return Component.text(String.valueOf(context.getItem().getAmount()));
            return null;
        });
        registerPlaceholder("[barter item]", ShopMessage::getBarterItemPlaceholder);
        registerPlaceholder("[barter item durability]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return Component.text(String.valueOf(context.getShop().getSecondaryItemDurabilityPercent())); } return null; });
        registerPlaceholder("[barter item type]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return Shop.getPlugin().getItemNameUtil().getNameTranslatable(context.getShop().getSecondaryItemStack().getType()); } return null; });
        registerPlaceholder("[barter item enchants]", context -> { 
            if (context.getBarterItem() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getBarterItem()), context.getBarterItem()); }
            if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getShop().getSecondaryItemStack()), context.getShop().getSecondaryItemStack()); } 
            if (context.getProcess() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getProcess().getBarterItemStack()), context.getProcess().getBarterItemStack()); } 
            if (context.getItem() != null) { return embedItem(UtilMethods.getEnchantmentsComponent(context.getItem()), context.getItem()); }
            return null; 
        });
        registerPlaceholder("[barter item lore]", context -> { 
            if (context.getBarterItem() != null) { return embedItem(UtilMethods.getLoreString(context.getBarterItem()), context.getBarterItem()); }
            if (context.getShop() != null && context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return embedItem(UtilMethods.getLoreString(context.getShop().getSecondaryItemStack()), context.getShop().getSecondaryItemStack()); } 
            if (context.getProcess() != null) { return embedItem(UtilMethods.getLoreString(context.getProcess().getBarterItemStack()), context.getProcess().getBarterItemStack()); } 
            if (context.getItem() != null) { return embedItem(UtilMethods.getLoreString(context.getItem()), context.getItem()); }
            return null; 
        });

        // Shop Pricing Placeholders
        registerPlaceholder("[amount]", context -> { if (context.getShop() != null) { return Component.text(String.valueOf(context.getShop().getAmount())); } return null; });
        registerPlaceholder("[price sell]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.COMBO) { return Component.text(((ComboShop) context.getShop()).getPriceSellString()); } return null; });
        registerPlaceholder("[price sell per item]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.COMBO) { return Component.text(((ComboShop) context.getShop()).getPriceSellPerItemString()); } return null; });
        registerPlaceholder("[price combo]", context -> { if (context.getShop() != null && context.getShop().getType() == ShopType.COMBO) { return Component.text(((ComboShop) context.getShop()).getPriceComboString()); } return null; });
        registerPlaceholder("[price per item]", context -> { if (context.getShop() != null) { return Component.text(context.getShop().getPricePerItemString()); } return null; });
        registerPlaceholder("[price]", context -> { if (context.getShop() != null) { return Component.text(context.getShop().getPriceString()); } return null; });
        registerPlaceholder("[stock]", context -> {
            if (context.getShop() == null) { return null; }
            else if (context.getShop().isAdmin()) {
                return Component.text(String.valueOf(ShopMessage.getAdminStockWord()));
            } else {
                return Component.text(String.valueOf(context.getShop().getStock()));
            }
        });
        registerPlaceholder("[stock color]", context -> {
            if (context.getShop() == null) { return null; }
            if (context.getShop().getStock() < 1) {
                return format(getUnformattedMessage("signtext", "outofstockcolor"), context);
            }
            return format(getUnformattedMessage("signtext", "instockcolor"), context);
        });

        // Notify Placeholders
        registerPlaceholder("[notify user]", context -> {
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_SALE_USER);
            // Returns text_on if notifications are set to ON, otherwise text_off
            return Component.text((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON) ? text_on : text_off);
        });

        registerPlaceholder("[notify owner]", context -> {
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            return Component.text((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON) ? text_on : text_off);
        });

        registerPlaceholder("[notify stock]", context -> {
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_STOCK);
            return Component.text((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) ? text_on : text_off);
        });

        // Offline Transaction Updates
        registerPlaceholder("[offline transactions]", context -> {
            Component numOfTransactions = Component.text(String.valueOf(context.getOfflineTransactions().getNumTransactions()));
            numOfTransactions = numOfTransactions.hoverEvent(getTransactionsHoverEvent(context));
            return numOfTransactions;
        });
        registerPlaceholder("[offline profit]", context -> {
            String boughtString = Shop.getPlugin().getPriceString(context.getOfflineTransactions().getTotalProfit(), false);
            if (boughtString.equals(freePriceWord)) { boughtString = "0"; }
            return Component.text(boughtString);
        });
        registerPlaceholder("[offline spent]", context -> {
            String spentString = Shop.getPlugin().getPriceString(context.getOfflineTransactions().getTotalSpent(), false);
            if (spentString.equals(freePriceWord)) { spentString = "0"; }
            return Component.text(spentString);
        });
        registerPlaceholder("[offline items sold]", context -> ShopMessage.getOfflineItemsPlaceholder(context, context.getOfflineTransactions().getItemsSold()));
        registerPlaceholder("[offline items bought]", context -> ShopMessage.getOfflineItemsPlaceholder(context, context.getOfflineTransactions().getItemsBought()));
        registerPlaceholder("[shops out of stock]", ShopMessage::getShopsOutOfStockPlaceholder);
    }

    /**
     * Creates a hover event displaying item information.
     *
     * @param item The item stack to display on hover
     * @return The hover event, or null if item is null or hover is disabled
     */
    private static HoverEvent getItemHoverEvent(ItemStack item) {
        if (item == null || disableItemHover) { return null; }
        return ItemHoverEventHelper.createFrom(item);
    }

    /**
     * Converts legacy text to a Component, with fallback for older Bukkit versions.
     * Uses Adventure's LegacyComponentSerializer instead of Bungee's TextComponent.fromLegacyText.
     *
     * @param text The legacy formatted text to convert
     * @return The converted Component
     */
    public static Component componentFromLegacy(String text) {
        try {
            // Use Adventure's LegacyComponentSerializer for compatibility
            return LegacyComponentSerializer.legacy('&').deserialize(text);
        } catch (Exception e) {
            // Fallback: return empty component on parse failure
            plugin.getLogger().warning("Failed to parse legacy text: " + text + " - " + e.getMessage());
            return Component.empty();
        }
    }

    /**
     * Converts a legacy formatted string to an Adventure Component.
     * This is kept as a pass-through since componentFromLegacy now returns Component directly.
     */
    private static Component textComponentFromComponent(Component component) {
        return component;
    }

    /**
     * Embeds item hover information into a message.
     *
     * @param message The message text
     * @param item The item stack to embed
     * @return The message with item hover event attached
     */
    private static Component embedItem(String message, ItemStack item) {
        return embedItem(Component.text(message), item);
    }

    /**
     * Embeds item hover information into a Component.
     *
     * @param message The Component message
     * @param item The item stack to embed
     * @return The message with item hover event attached
     */
    private static Component embedItem(Component message, ItemStack item) {
        // If we have any NBT errors, don't try to embed the item hover text
        if (disableItemHover) { return message; }
        try {
            if (item == null) { return null; }
            Component msg = componentFromLegacy(UtilMethods.removeColorsIfOnlyWhite(LegacyComponentSerializer.legacy('§').serialize(message)));
            HoverEvent event = getItemHoverEvent(item);
            if (event != null) { msg = msg.hoverEvent(event); }
            return msg;
        } catch (Error | Exception e) {
            plugin.getLogger().severe("Unable to embed item hover text, disabling item hover text for all players! Your version of : " + e.getMessage());
            plugin.getLogger().debug("Error details: ", e);
            // disableItemHover = true;
            return message;
        }
    }

    /**
     * Creates a hover event displaying offline transaction information.
     *
     * @param context The placeholder context containing offline transactions
     * @return The hover event, or null if creation fails
     */
    private static HoverEvent getTransactionsHoverEvent(PlaceholderContext context) {
        try {
            if (context == null || context.getOfflineTransactions() == null) {
                return null;
            }
            Component hoverText = componentFromLegacy(context.getOfflineTransactions().getTransactionsLore());
            return HoverEvent.showText(hoverText);
        } catch (Exception e) {
            plugin.getLogger().debug("Failed to create transactions hover event", e);
        }
        return null;
    }

    /**
     * Creates a hover event displaying shop location information.
     *
     * @param context The placeholder context containing shop information
     * @return The hover event, or null if creation fails
     */
    private static HoverEvent getShopInfoHoverEvent(PlaceholderContext context) {
        try {
            if (context == null) {
                return null;
            }
            Component hoverText = Component.empty();
            List<String> hoverLines = getUnformattedMessageList("hover", "location");
            int i = 0;
            for (String line : hoverLines) {
                i++;
                // Add new lines between text
                hoverText = hoverText.append(format(line + (i == hoverLines.size() ? "" : "\n"), context));
            }
            Component flatText = componentFromLegacy(LegacyComponentSerializer.legacy('§').serialize(hoverText));
            return HoverEvent.showText(flatText);
        } catch (Exception e) {
            plugin.getLogger().debug("Failed to create shop info hover event", e);
        }
        return null;
    }

    /**
     * Helper method to handle the [shop types] placeholder.
     *
     * @param context The PlaceholderContext instance.
     * @return A comma-separated list of shop types the player can create.
     */
    private static Component getShopTypesPlaceholder(PlaceholderContext context) {
        List<ShopType> typeList = new ArrayList<>(Arrays.asList(ShopType.values()));
        Player player = context.getPlayer();

        if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
            typeList.remove(ShopType.GAMBLE);
        }

        if (plugin.usePerms()) {
            Iterator<ShopType> typeIterator = typeList.iterator();
            while (typeIterator.hasNext()) {
                ShopType type = typeIterator.next();
                if (
                    !player.hasPermission("shop.operator") 
                    && !player.hasPermission("shop.create." + type.toString()) 
                    && !player.hasPermission("shop.create")) {
                    typeIterator.remove();
                }
            }
        }

        StringBuilder types = new StringBuilder();
        for (int i = 0; i < typeList.size(); i++) {
            types.append(typeList.get(i).toCreationWord());
            if (i < typeList.size() - 1) {
                types.append(", ");
            }
        }
        return Component.text(types.toString());
    }

    /**
     * Helper method to handle the [item] placeholder with truncation for signs.
     *
     * @param context The PlaceholderContext instance.
     * @return The item name, potentially truncated to fit sign constraints.
     */
    private static Component getItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getItem() != null) {
            item = context.getItem();
        }
        else if (context.getProcess() != null) {
            item = context.getProcess().getItemStack(); 
        }
        else if (context.getShop() != null || context.getShop().getItemStack() != null) {
            item = context.getShop().getItemStack();
        }
        if (item == null) { return null; }

        Component itemName = plugin.getItemNameUtil().getName(item);
        if (context.isForSign()) {
            return Component.text(UtilMethods.trimForSign(PlainTextComponentSerializer.plainText().serialize(itemName)));
        }
        return embedItem(itemName, item);
    }

    /**
     * Helper method to handle the [barter item] placeholder with truncation for signs.
     *
     * @param context The PlaceholderContext instance.
     * @return The barter item name, potentially truncated to fit sign constraints.
     */
    private static Component getBarterItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getBarterItem() != null) {
            item = context.getBarterItem();
        }
        else if (context.getItem() != null) {
            item = context.getItem();
        }
        else if (context.getProcess() != null) {
            item = context.getProcess().getBarterItemStack();
        }
        else if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null) {
            if (context.getShop().getType() != ShopType.BARTER) {
                return null;
            }
            item = context.getShop().getSecondaryItemStack();
        }
        if (item == null) { return null; }

        Component itemName = plugin.getItemNameUtil().getName(item);
        if (context.isForSign()) {
            return Component.text(UtilMethods.trimForSign(PlainTextComponentSerializer.plainText().serialize(itemName)));
        }
        return embedItem(itemName, item);
    }

    private static Component getOfflineItemsPlaceholder(PlaceholderContext context, Map<ItemStack, Integer> items) {
        Component itemRowsText = Component.empty();
        String itemRow = getUnformattedMessage("offline", "itemRow");

        int i = 0;
        for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
            i++;
            // Add a new line character between our rows, don't add it if we are the last item (since we don't want an extra line!
            String addNewLine = i < (items.size()) ? "\n" : "";
            ItemStack item = entry.getKey();
            item.setAmount(entry.getValue());

            PlaceholderContext itemContext = new PlaceholderContext();
            itemContext.setPlayer(context.getPlayer());
            itemContext.setItem(item);

            Component currentRow = format(itemRow + addNewLine, itemContext);
            itemRowsText = itemRowsText.append(currentRow);
        }
        // If there were no lines added, just return null so that we don't log a blank line!
        if (i == 0) return null;

        return itemRowsText;
    }

    private static Component getShopsOutOfStockPlaceholder(PlaceholderContext context) {
        Component shopsOutOfStock = Component.empty();
        List<AbstractShop> playerShops = Shop.getPlugin().getShopHandler().getShops(context.getPlayer().getUniqueId());
        if (playerShops != null && !playerShops.isEmpty()) {
            // Collect all the out of stock shops
            List<AbstractShop> outOfStock = new ArrayList<>();
            for (AbstractShop shop : playerShops) {
                if (shop.getStock() == 0) {
                    outOfStock.add(shop);
                }
            }
            // No out of stock shops!
            if (outOfStock.isEmpty()) { return null; }

            // Add the lines for each
            int i = 0;
            List<String> remainingShopsMsgs = new ArrayList<>();
            for (AbstractShop shop : outOfStock) {
                i++;

                PlaceholderContext shopContext = new PlaceholderContext();
                shopContext.setPlayer(context.getPlayer());
                shopContext.setShop(shop);

                // For each item, generate a line based on the template line
                String addNewLine = (i < (outOfStock.size()) && i <= 3) ? "\n" : "";
                Component currentRow = format(getUnformattedMessage("offline", "outOfStockShop") + addNewLine, shopContext);
                // Limit out of stock shops to 3
                if (i > 3) {
                    remainingShopsMsgs.add(LegacyComponentSerializer.legacy('§').serialize(currentRow));
                } else {
                    shopsOutOfStock = shopsOutOfStock.append(currentRow);
                }
            }

            if (!remainingShopsMsgs.isEmpty()) {
                String remainingMsg = getUnformattedMessage("offline", "moreOutOfStock");
                Component remaining = format(remainingMsg.replace("[out of stock remaining]", "" + remainingShopsMsgs.size()), context);
                remaining = remaining.hoverEvent(HoverEvent.showText(Component.text(String.join("\n", remainingShopsMsgs))));
                shopsOutOfStock = shopsOutOfStock.append(remaining);
            }

            return shopsOutOfStock;
        }
        // No shops for player! don't add anything! p.s. should never get here.
        return null;
    }

    /**
     * Gets the creation word for the given shop type.
     *
     * @param type The shop type identifier
     * @return The creation word for the shop type
     */
    public static String getCreationWord(String type) {
        return creationWords.get(type);
    }

    /**
     * Gets the word used to represent a free price.
     *
     * @return The free price word
     */
    public static String getFreePriceWord() {
        return freePriceWord;
    }

    /**
     * Gets the word used to represent admin stock.
     *
     * @return The admin stock word
     */
    public static String getAdminStockWord() {
        return adminStockWord;
    }

    /**
     * Gets the server display name.
     *
     * @return The server display name
     */
    public static String getServerDisplayName() {
        return serverDisplayName;
    }

    /**
     * Gets an unformatted message from the message map.
     *
     * @param key The message key
     * @param subKey The message sub key, or null if no sub key
     * @return The unformatted message
     */
    public static String getUnformattedMessage(String key, String subKey) {
        String message;
        if (subKey != null)
            message = messageMap.get(key + "_" + subKey);
        else
            message = messageMap.get(key);
        return message;
    }

    /**
     * Formats a message for a shop.
     *
     * @param unformattedMessage The unformatted message to format
     * @param shop The shop context
     * @return The formatted message
     */
    public static String formatMessage(String unformattedMessage, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        Component formattedMessage = format(unformattedMessage, context);
        // Return the legacy version since we are requesting the legacy formatter!
        return LegacyComponentSerializer.legacy('§').serialize(formattedMessage);
    }

    /**
     * Formats a message for a shop with player context.
     *
     * @param unformattedMessage The unformatted message to format
     * @param shop The shop context
     * @param player The player context
     * @param forSign Whether the message is for a sign
     * @return The formatted message
     */
    public static String formatMessage(String unformattedMessage, AbstractShop shop, Player player, boolean forSign) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setShop(shop);
        context.setForSign(forSign);
        Component formattedMessage = format(unformattedMessage, context);
        // Return the legacy version since we are requesting the legacy formatter!
        return LegacyComponentSerializer.legacy('§').serialize(formattedMessage);
    }

    /**
     * Perform partial formatting to insert transaction purchase amounts since they might differ from shop amounts (partial sales)
     *
     * @param transactionType The type of transaction
     * @param subKey The message sub key
     * @param price The price of the transaction
     * @param amount The amount of items
     * @return The formatted message
     */
    public static String getMessageFromOrders(ShopType transactionType, String subKey, double price, int amount){
        String message = ShopMessage.getUnformattedMessage(transactionType.toString(), subKey);
        String priceStr = Shop.getPlugin().getPriceString(price, false);
        message = message.replace("[price]", priceStr);
        message = message.replace("[item amount]", "" + amount);
        if(transactionType == ShopType.BARTER) {
            message = message.replace("[barter item amount]", "" + (int) price);
        }
        return message;
    }

    /**
     * Gets the sign lines for a shop.
     * <p>
     * # [amount] : The amount of items the shop is selling/buying/bartering <br>
     * # [price] : The price of the items the shop is selling (adjusted to match virtual or physical currency) <br>
     * # [owner] : The name of the shop owner <br>
     * # [server name] : The name of the server
     * </p>
     *
     * @param shop The shop
     * @param shopType The shop type
     * @return The sign lines
     */
    public static String[] getSignLines(AbstractShop shop, ShopType shopType){

        DisplayType displayType = null;
        if (shop.getDisplay() != null)
            displayType = shop.getDisplay().getType();
        if (displayType == null)
            displayType = Shop.getPlugin().getDisplayType();

        String shopFormat;
        if(shop.isAdmin())
            shopFormat = "admin";
        else
            shopFormat = "normal";

        if(displayType == DisplayType.NONE){
            shopFormat += "_no_display";
        }

        String[] lines = getUnformattedShopSignLines(shopType, shopFormat);

        for(int i=0; i<lines.length; i++) {
            lines[i] = formatMessage(lines[i], shop, null, true);
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
            lines[i] = ChatColor.translateAlternateColorCodes('&', lines[i]);
            lines[i] = UtilMethods.trimForSign(lines[i]);
        }
        return lines;
    }

    /**
     * Gets the sign lines for a shop by key.
     *
     * @param key The sign text key
     * @param shop The shop
     * @return The sign lines
     */
    public static String[] getSignLines(String key, AbstractShop shop){
        String[] lines = shopSignTextMap.get(key);
        for(int i=0; i<lines.length; i++) {
            lines[i] = formatMessage(lines[i], shop, null, true);
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
            lines[i] = ChatColor.translateAlternateColorCodes('&', lines[i]);
            lines[i] = UtilMethods.trimForSign(lines[i]);
        }
        return lines;
    }

    /**
     * Gets the display tags for a shop.
     *
     * @param shop The shop
     * @param shopType The shop type
     * @return The display tags
     */
    public static ArrayList<String> getDisplayTags(AbstractShop shop, ShopType shopType){
        ArrayList<String> formattedLines = new ArrayList<>();
        List<String> lines = displayTextMap.get(shopType.toString().toUpperCase()+"_normal");

        String formattedLine;
        for(String line : lines) {
            formattedLine = formatMessage(line, shop, null, false);

            Boolean splitLine = formattedLine.contains("[split]");
            formattedLine = formattedLine.replace("[split]", "");
// TODO: Phase 3 - Replace ChatColor with Paper-native Adventure Component (migration from Spigot/Bukkit to PaperMC)
// See MIGRATION_PLAN.md Phase 3 for details
            if(formattedLine != null && !formattedLine.isEmpty() && !ChatColor.stripColor(formattedLine).trim().isEmpty()) {
                if (splitLine) {
                    List<String> splitLines = UtilMethods.splitStringIntoLines(formattedLine, targetMaxLength);
                    formattedLines.addAll(splitLines);
                } else {
                    formattedLines.add(formattedLine);
                }
            }
        }
        return formattedLines;
    }

    /**
     * Gets a list of unformatted messages.
     *
     * @param key The message key
     * @param subKey The message sub key
     * @return The list of unformatted messages
     */
    public static List<String> getUnformattedMessageList(String key, String subKey){
        List<String> messages = new ArrayList<>();

        int count = 1;
        String message = "-1";
        while (message != null && !message.isEmpty()) {
            message = getUnformattedMessage(key, subKey + count);
            if (message != null && !message.isEmpty())
                messages.add(message);
            count++;
        }
        return messages;
    }

    private static String[] getUnformattedShopSignLines(ShopType type, String subtype) {
        return shopSignTextMap.get(type.toString()+"_"+subtype).clone();
    }

    /**
     * Loads all messages from the chat configuration file.
     * Populates the messageMap with transaction, interaction, and issue messages
     * for all shop types, as well as command and hover messages.
     * This method should be called during the plugin's initialization phase.
     */
    private static void loadMessagesFromConfig() {

        for (ShopType type : ShopType.values()) {
            messageMap.put(type.toString() + "_user", chatConfig.getString("transaction." + type.toString().toUpperCase() + ".user"));
            messageMap.put(type.toString() + "_owner", chatConfig.getString("transaction." + type.toString().toUpperCase() + ".owner"));

            messageMap.put(type.toString() + "_initialize", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initialize"));
            if(type == ShopType.BUY || type == ShopType.COMBO)
                messageMap.put(type.toString() + "_initializeAlt", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeAlt"));
            else if(type == ShopType.BARTER) {
                messageMap.put(type.toString() + "_initializeInfo", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeInfo"));
                messageMap.put(type.toString() + "_initializeBarter", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeBarter"));
                messageMap.put(type.toString() + "_createHitChest", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChest"));
                messageMap.put(type.toString() + "_createHitChestBarterAmount", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestBarterAmount"));
                messageMap.put(type.toString() + "_initializeBarterAlt", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeBarterAlt"));
            }
            messageMap.put(type.toString() + "_create", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".create"));
            messageMap.put(type.toString() + "_destroy", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".destroy"));
            messageMap.put(type.toString() + "_opDestroy", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".opDestroy"));
            messageMap.put(type.toString() + "_opOpen", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".opOpen"));

            messageMap.put(type.toString() + "_shopNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".shopNoStock"));
            messageMap.put(type.toString() + "_ownerNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".ownerNoStock"));
            messageMap.put(type.toString() + "_shopNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".shopNoSpace"));
            messageMap.put(type.toString() + "_ownerNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".ownerNoSpace"));
            messageMap.put(type.toString() + "_playerNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".playerNoStock"));
            messageMap.put(type.toString() + "_playerNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".playerNoSpace"));

            if(type != ShopType.GAMBLE){
                messageMap.put(type.toString() + "_createHitChestAmount", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestAmount"));
            }
            if(type != ShopType.BARTER){
                messageMap.put(type.toString() + "_createHitChestPrice", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestPrice"));
            }
            if(type == ShopType.COMBO){
                messageMap.put(type.toString() + "_createHitChestPriceCombo", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestPriceCombo"));
            }

            int count = 1;
            for(String s : chatConfig.getStringList("description."+type.toString().toUpperCase())){
                messageMap.put(type.toString() + "_description"+count, s);
                count++;
            }
        }
        messageMap.put("initialCreateInstruction", chatConfig.getString("interaction.initialCreateInstruction"));
        messageMap.put("createHitChest", chatConfig.getString("interaction.createHitChest"));
        messageMap.put("adminCreateHitChest", chatConfig.getString("interaction.adminCreateHitChest"));
        messageMap.put("interaction_openTrusted", chatConfig.getString("interaction.openTrusted"));

        messageMap.put("permission_use", chatConfig.getString("permission.use"));
        messageMap.put("permission_create", chatConfig.getString("permission.create"));
        messageMap.put("permission_destroy", chatConfig.getString("permission.destroy"));
        messageMap.put("permission_destroyOther", chatConfig.getString("permission.destroyOther"));
        messageMap.put("permission_openOther", chatConfig.getString("permission.openOther"));
        messageMap.put("permission_buildLimit", chatConfig.getString("permission.buildLimit"));

        messageMap.put("creativeSelection_disabled", chatConfig.getString("creativeSelection.disabled"));

        messageMap.put("interactionIssue_line2", chatConfig.getString("interaction_issue.createLine2"));
        messageMap.put("interactionIssue_line3", chatConfig.getString("interaction_issue.createLine3"));
        messageMap.put("interactionIssue_noItem", chatConfig.getString("interaction_issue.createNoItem"));
        messageMap.put("interactionIssue_direction", chatConfig.getString("interaction_issue.createDirection"));
        messageMap.put("interactionIssue_sameItem", chatConfig.getString("interaction_issue.createSameItem"));
        messageMap.put("interactionIssue_displayRoom", chatConfig.getString("interaction_issue.createDisplayRoom"));
        messageMap.put("interactionIssue_signRoom", chatConfig.getString("interaction_issue.createSignRoom"));
        messageMap.put("interactionIssue_createOtherPlayer", chatConfig.getString("interaction_issue.createOtherPlayer"));
        messageMap.put("interactionIssue_createInsufficientFunds", chatConfig.getString("interaction_issue.createInsufficientFunds"));
        messageMap.put("interactionIssue_createCooldown", chatConfig.getString("interaction_issue.createCooldown"));
        messageMap.put("interactionIssue_destroyInsufficientFunds", chatConfig.getString("interaction_issue.destroyInsufficientFunds"));
        messageMap.put("interactionIssue_createCancel", chatConfig.getString("interaction_issue.createCancel"));
        messageMap.put("interactionIssue_teleportInsufficientFunds", chatConfig.getString("interaction_issue.teleportInsufficientFunds"));
        messageMap.put("interactionIssue_teleportInsufficientCooldown", chatConfig.getString("interaction_issue.teleportInsufficientCooldown"));
        messageMap.put("interactionIssue_initialize", chatConfig.getString("interaction_issue.initializeOtherShop"));
        messageMap.put("interactionIssue_destroyChest", chatConfig.getString("interaction_issue.destroyChest"));
        messageMap.put("interactionIssue_destroyUninitializedChest", chatConfig.getString("interaction_issue.destroyUninitializedChest"));
        messageMap.put("interactionIssue_destroyUninitializedChestCancel", chatConfig.getString("interaction_issue.destroyUninitializedChestCancel"));
        messageMap.put("interactionIssue_useOwnShop", chatConfig.getString("interaction_issue.useOwnShop"));
        messageMap.put("interactionIssue_useShopAlreadyInUse", chatConfig.getString("interaction_issue.useShopAlreadyInUse"));
        messageMap.put("interactionIssue_adminOpen", chatConfig.getString("interaction_issue.adminOpen"));
        messageMap.put("interactionIssue_worldBlacklist", chatConfig.getString("interaction_issue.worldBlacklist"));
        messageMap.put("interactionIssue_regionRestriction", chatConfig.getString("interaction_issue.regionRestriction"));
        messageMap.put("interactionIssue_itemListDeny", chatConfig.getString("interaction_issue.itemListDeny"));
        messageMap.put("interactionIssue_createHitChestTimeout", chatConfig.getString("interaction_issue.createHitChestTimeout"));


        int count = 1;
        for(String s : chatConfig.getStringList("hover.location")){
            messageMap.put("hover_location"+count, s);
            count++;
        }
        count = 1;
        for(String s : chatConfig.getStringList("creativeSelection.enter")){
            messageMap.put("creativeSelection_enter"+count, s);
            count++;
        }
        count = 1;
        for(String s : chatConfig.getStringList("creativeSelection.prompt")){
            messageMap.put("creativeSelection_prompt"+count, s);
            count++;
        }

        count = 1;
        for(String s : chatConfig.getStringList("guiSearchSelection.enter")){
            messageMap.put("guiSearchSelection_enter"+count, s);
            count++;
        }
        count = 1;
        for(String s : chatConfig.getStringList("guiSearchSelection.prompt")){
            messageMap.put("guiSearchSelection_prompt"+count, s);
            count++;
        }

        count = 1;
        for(String s : chatConfig.getStringList("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.summary")){
            messageMap.put("offline_summary"+count, s);
            count++;
        }
        messageMap.put("offline_itemRow", chatConfig.getString("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.itemRow"));
        messageMap.put("offline_outOfStockShop", chatConfig.getString("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.outOfStockShop"));
        messageMap.put("offline_moreOutOfStock", chatConfig.getString("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.moreOutOfStock"));

        messageMap.put("command_list", chatConfig.getString("command.list"));
        messageMap.put("command_list_output_total", chatConfig.getString("command.list_output_total"));
        messageMap.put("command_list_output_perms", chatConfig.getString("command.list_output_perms"));
        messageMap.put("command_list_output_noperms", chatConfig.getString("command.list_output_noperms"));
        messageMap.put("command_currency", chatConfig.getString("command.currency"));
        messageMap.put("command_currency_output", chatConfig.getString("command.currency_output"));
        messageMap.put("command_currency_output_tip", chatConfig.getString("command.currency_output_tip"));
        messageMap.put("command_setcurrency", chatConfig.getString("command.setcurrency"));
        messageMap.put("command_setcurrency_output", chatConfig.getString("command.setcurrency_output"));
        messageMap.put("command_setgamble", chatConfig.getString("command.setgamble"));
        messageMap.put("command_itemrefresh", chatConfig.getString("command.itemrefresh"));
        messageMap.put("command_itemrefresh_output", chatConfig.getString("command.itemrefresh_output"));
        messageMap.put("command_itemlist", chatConfig.getString("command.itemlist"));
        messageMap.put("command_itemlist_add", chatConfig.getString("command.itemlist_add"));
        messageMap.put("command_itemlist_remove", chatConfig.getString("command.itemlist_remove"));
        messageMap.put("command_reload", chatConfig.getString("command.reload"));
        messageMap.put("command_reload_output", chatConfig.getString("command.reload_output"));
        messageMap.put("command_error_novault", chatConfig.getString("command.error_novault"));
        messageMap.put("command_error_nohand", chatConfig.getString("command.error_nohand"));
        messageMap.put("command_not_authorized", chatConfig.getString("command.not_authorized"));
        messageMap.put("command_notify_user", chatConfig.getString("command.notify_user"));
        messageMap.put("command_notify_owner", chatConfig.getString("command.notify_owner"));
        messageMap.put("command_notify_stock", chatConfig.getString("command.notify_stock"));
        messageMap.put("command_notify_on", chatConfig.getString("command.notify_on"));
        messageMap.put("command_notify_off", chatConfig.getString("command.notify_off"));
    }

    private String[] getSignConfigLines(String key) { return getConfigLines(signConfig, key);  }
    private String[] getConfigLines(YamlConfiguration config, String key) {
        List<String> lines = new ArrayList<>();
        int count = 1;
        try {
            String message = config.getString(key + "." + count);
            while (message != null) {
                lines.add(message);
                count++;
                message = config.getString(key + "." + count);
            }
        } catch (Exception e) {
            plugin.getLogger().debug("Failed to load config lines for key: " + key, e);
        }
        return lines.toArray(new String[0]);
    }

    /**
     * Loads sign text from the sign configuration file.
     * Populates the shopSignTextMap with sign lines for each shop type,
     * including normal, admin, and no-display variants, as well as timeout and deleted messages.
     */
    private void loadSignTextFromConfig() {
        messageMap.put("signtext_instockcolor", signConfig.getString("stock_color.in_stock"));
        messageMap.put("signtext_outofstockcolor", signConfig.getString("stock_color.out_of_stock"));
        if (signConfig.getConfigurationSection("sign_text") != null) {
            Set<String> allTypes = signConfig.getConfigurationSection("sign_text").getKeys(false);
            for (String typeString : allTypes) {
                ShopType type = null;
                try { type = ShopType.valueOf(typeString);}
                catch (IllegalArgumentException e){
                    plugin.getLogger().debug("Unknown sign text type: " + typeString, e);
                }

                if (type != null) {
                    this.shopSignTextMap.put(type.toString() + "_normal", getSignConfigLines("sign_text." + typeString + ".normal"));
                    this.shopSignTextMap.put(type.toString() + "_admin", getSignConfigLines("sign_text." + typeString + ".admin"));
                    this.shopSignTextMap.put(type.toString() + "_normal_no_display", getSignConfigLines("sign_text." + typeString + ".normal_no_display"));
                    this.shopSignTextMap.put(type.toString() + "_admin_no_display", getSignConfigLines("sign_text." + typeString + ".admin_no_display"));
                }
            }
        }
        this.shopSignTextMap.put("timeout", getSignConfigLines("sign_text.timeout"));
        this.shopSignTextMap.put("deleted", getSignConfigLines("sign_text.deleted"));
    }

    /**
     * Loads display tag text from the display configuration file.
     * Populates the displayTextMap with formatted lines for each shop type.
     */
    private void loadDisplayTextFromConfig() {
        displayTextMap = new HashMap<>();
        if (displayConfig.getConfigurationSection("display_tag_text") == null) {
            return;
        }
        Set<String> allTypes = displayConfig.getConfigurationSection("display_tag_text").getKeys(false);
        for (String typeString : allTypes) {

            ShopType type = null;
            try { type = ShopType.valueOf(typeString);}
            catch (IllegalArgumentException e){
                plugin.getLogger().debug("Unknown display tag type: " + typeString, e);
            }

            if (type != null) {
                try {
                    List<String> normalLines = displayConfig.getStringList("display_tag_text." + typeString.toUpperCase() + ".normal");
                    this.displayTextMap.put(type.toString().toUpperCase() + "_normal", normalLines);
                } catch (Exception e) {
                    plugin.getLogger().debug("Failed to load display tag text for: " + typeString, e);
                }
            }
        }
    }

    /**
     * Loads creation words from the sign configuration file.
     * Maps each shop type (SHOP, SELL, BUY, BARTER, GAMBLE, ADMIN, COMBO)
     * to its corresponding creation word used for sign display.
     */
    private void loadCreationWords(){
        String shopString = signConfig.getString("sign_creation.SHOP");
        if(shopString != null)
            creationWords.put("SHOP", shopString.toLowerCase());
        else
            creationWords.put("SHOP", "[shop]");

        String sellString = signConfig.getString("sign_creation.SELL");
        if(sellString != null)
            creationWords.put("SELL", sellString.toLowerCase());
        else
            creationWords.put("SELL", "sell");

        String buyString = signConfig.getString("sign_creation.BUY");
        if(buyString != null)
            creationWords.put("BUY", buyString.toLowerCase());
        else
            creationWords.put("BUY", "buy");

        String barterString = signConfig.getString("sign_creation.BARTER");
        if(barterString != null)
            creationWords.put("BARTER", barterString.toLowerCase());
        else
            creationWords.put("BARTER", "barter");

        String gambleString = signConfig.getString("sign_creation.GAMBLE");
        if(gambleString != null)
            creationWords.put("GAMBLE", gambleString.toLowerCase());
        else
            creationWords.put("GAMBLE", "gamble");

        String adminString = signConfig.getString("sign_creation.ADMIN");
        if(adminString != null)
            creationWords.put("ADMIN", adminString.toLowerCase());
        else
            creationWords.put("ADMIN", "admin");

        String comboString = signConfig.getString("sign_creation.COMBO");
        if(comboString != null)
            creationWords.put("COMBO", comboString.toLowerCase());
        else
            creationWords.put("COMBO", "combo");
    }

    /**
     * Escapes angle brackets that are not valid MiniMessage tags.
     * Valid tags are like <red>, </red>, <bold>, <#FF0000>, etc.
     * Invalid tags like <(54, 65, 9)> are escaped to be literal text.
     */
    private static String escapeInvalidMiniMessageTags(String message) {
        if (message == null) {
            return null;
        }
        // First, protect placeholders wrapped in angle brackets like <[location]>
        Pattern placeholderAngleBracketPattern = Pattern.compile("<(\\[[^\\[\\]]+\\])>");
        Matcher pabMatcher = placeholderAngleBracketPattern.matcher(message);
        StringBuffer protectedMessage = new StringBuffer();
        while (pabMatcher.find()) {
            pabMatcher.appendReplacement(protectedMessage, Matcher.quoteReplacement("§§PLACEHOLDER_ANGLE_START§§" + pabMatcher.group(1) + "§§PLACEHOLDER_ANGLE_END§§"));
        }
        pabMatcher.appendTail(protectedMessage);
        message = protectedMessage.toString();
        
        // Pattern for valid MiniMessage tags: <tag> or </tag> or <tag:param> etc
        Pattern tagPattern = Pattern.compile("<(/?[a-zA-Z][a-zA-Z0-9_-]*(?::[^>]*)?)>");
        Matcher matcher = tagPattern.matcher(message);
        StringBuffer result = new StringBuffer();
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = message.substring(lastEnd, matcher.start());
                before = before.replace("<", "&#60;").replace(">", "&#62;");
                result.append(before);
            }
            String tagContent = matcher.group(1);
            if (isValidMiniMessageTag(tagContent)) {
                result.append(matcher.group());
            } else {
                result.append(matcher.group().replace("<", "&#60;").replace(">", "&#62;"));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < message.length()) {
            String after = message.substring(lastEnd);
            after = after.replace("<", "&#60;").replace(">", "&#62;");
            result.append(after);
        }
        String escaped = result.toString();
        // Restore protected placeholders
        escaped = escaped.replace("§§PLACEHOLDER_ANGLE_START§§", "<").replace("§§PLACEHOLDER_ANGLE_END§§", ">");
        return escaped;
    }

    private static boolean isValidMiniMessageTag(String tagContent) {
        if (tagContent == null || tagContent.isEmpty()) {
            return false;
        }
        String content = tagContent;
        if (content.startsWith("/")) {
            content = content.substring(1);
        }
        if (content.matches("#[0-9a-fA-F]{6}")) {
            return true;
        }
        if (content.matches("[a-zA-Z][a-zA-Z0-9_-]*(?::.*)?")) {
            return true;
        }
        if (content.equals("reset") || content.equals("r")) {
            return true;
        }
        // Common MiniMessage tags
        if (content.equals("black") || content.equals("dark_blue") || content.equals("dark_green") || content.equals("dark_aqua") ||
            content.equals("dark_red") || content.equals("dark_purple") || content.equals("gold") || content.equals("gray") ||
            content.equals("dark_gray") || content.equals("blue") || content.equals("green") || content.equals("aqua") ||
            content.equals("red") || content.equals("light_purple") || content.equals("yellow") || content.equals("white") ||
            content.equals("bold") || content.equals("b") || content.equals("italic") || content.equals("i") ||
            content.equals("underlined") || content.equals("u") || content.equals("strikethrough") || content.equals("st") ||
            content.equals("obfuscated") || content.equals("obf")) {
            return true;
        }
        return false;
    }

    /**
     * Gets the target max length for display tags.
     *
     * @return The target max length
     */
    public static int getTargetMaxLength() {
        return targetMaxLength;
    }
}
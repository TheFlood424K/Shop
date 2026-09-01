package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
    private static HashMap<String, String[]> shopSignTextMap = new HashMap<>();
    private static HashMap<String, List<String>> displayTextMap = new HashMap<>();
    private static String freePriceWord;
    private static String adminStockWord;
    private static String serverDisplayName;
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
        loadDisplayTextFromConfig();
        loadCreationWords();

        freePriceWord = signConfig.getString("sign_text.zeroPrice");
        adminStockWord = signConfig.getString("sign_text.adminStock");
        serverDisplayName = signConfig.getString("sign_text.serverDisplayName");
        targetMaxLength = displayConfig.getInt("targetMaxLength", 40);

        loadPlaceholders();
    }

    // -----------------------------------------------------------------------
    // Adventure helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a legacy-formatted string (§ / & colour codes) to an Adventure Component.
     * Used by ItemNameUtil and any caller that has a raw legacy string.
     */
    public static Component componentFromLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', legacy));
    }

    /**
     * Serialises an Adventure Component back to a legacy string (§ codes).
     * Used as a fallback when we need a plain string (e.g. for sign lines).
     */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Strips all formatting and returns plain text.
     */
    public static String toPlain(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // -----------------------------------------------------------------------
    // Colour helpers (replaces BungeeCord ChatColor methods)
    // -----------------------------------------------------------------------

    /** Parses a colour code string (e.g. "&a", "§c", "#RRGGBB") into a TextColor. */
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

    /** Returns true if the code string represents a formatting code (bold, italic, …). */
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

    public static Component replacePlaceholder(String placeholder, PlaceholderContext context) {
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] Attempting to replace placeholder: " + placeholder + " " + context);
        Function<PlaceholderContext, Component> valueFunction = placeholders.get(placeholder.toLowerCase());
        if (valueFunction != null) {
            try {
                plugin.getLogger().spam("[ShopMessage.replacePlaceholder]     Running placeholder function... " + placeholder);
                Component message = valueFunction.apply(context);
                if (message != null) {
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

            // --- colour / formatting code ---
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
                    // fall through — treat as literal text
                }
            }

            // --- placeholder ---
            Component partComponent;
            if (part.matches(PLACEHOLDER_REGEX) && placeholders.containsKey(part.toLowerCase())) {
                plugin.getLogger().hyper("[ShopMessage.format]     matched PLACEHOLDER_REGEX: " + part);
                partComponent = replacePlaceholder(part, context);
            } else {
                partComponent = Component.text(part);
            }

            // Apply accumulated formatting
            TextComponent.Builder partBuilder = partComponent.toBuilder();
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
        if (message != null && !message.isEmpty())
            sendMessage(message, player, shop);
    }

    public static void sendMessage(String key, String subkey, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        String message = getUnformattedMessage(key, subkey);
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
    // embedItem — builds a Component with an item-hover event
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Component embedItem(Component display, ItemStack item) {
        if (item == null || display == null) return display != null ? display : Component.empty();
        if (disableItemHover) return display;
        try {
            return display.hoverEvent(HoverEvent.showItem(
                    HoverEvent.ShowItem.showItem(
                            item.getType().getKey(),
                            item.getAmount(),
                            null)));
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
                return Component.text(ShopMessage.getCreationWord(context.getShop().getType().toString().toUpperCase()));
            return null;
        });
        registerPlaceholder("[shop types]", ShopMessage::getShopTypesPlaceholder);
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

        registerPlaceholder("[price]", context -> {
            if (context.getProcess() != null && context.getProcess().getPrice() > -1)
                return Component.text(UtilMethods.formatLongToKString(context.getProcess().getPrice(), false));
            if (context.getShop() != null)
                return Component.text(context.getShop().isInfinitePrice() ? getAdminStockWord()
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
        registerPlaceholder("[amount]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getAmount()));
            if (context.getProcess() != null) return Component.text(String.valueOf(context.getProcess().getItemAmount()));
            return null;
        });
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
        registerPlaceholder("[display amount]", context -> {
            if (context.getShop() != null) return Component.text(String.valueOf(context.getShop().getDisplay().getAmount()));
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
        registerPlaceholder("[combo buy shop owner]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                if (cs.getBuyShop() != null) return Component.text(cs.getBuyShop().getOwnerName());
            }
            return null;
        });
        registerPlaceholder("[combo sell shop owner]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                if (cs.getSellShop() != null) return Component.text(cs.getSellShop().getOwnerName());
            }
            return null;
        });
        registerPlaceholder("[combo buy price]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                if (cs.getBuyShop() != null) return Component.text(UtilMethods.formatLongToKString(cs.getBuyShop().getPrice(), false));
            }
            return null;
        });
        registerPlaceholder("[combo sell price]", context -> {
            if (context.getShop() instanceof ComboShop) {
                ComboShop cs = (ComboShop) context.getShop();
                if (cs.getSellShop() != null) return Component.text(UtilMethods.formatLongToKString(cs.getSellShop().getPrice(), false));
            }
            return null;
        });

        registerPlaceholder("[offline tx count]", context -> {
            if (context.getOfflineTransactions() != null) return Component.text(String.valueOf(context.getOfflineTransactions().getTransactionCount()));
            return null;
        });
        registerPlaceholder("[offline tx total]", context -> {
            if (context.getOfflineTransactions() != null) return Component.text(UtilMethods.formatLongToKString(context.getOfflineTransactions().getTotalAmount(), true));
            return null;
        });
        registerPlaceholder("[offline tx item]", context -> {
            if (context.getOfflineTransactions() != null)
                return embedItem(plugin.getItemNameUtil().getName(context.getOfflineTransactions().getItemStack()), context.getOfflineTransactions().getItemStack());
            return null;
        });
        registerPlaceholder("[offline tx type]", context -> {
            if (context.getOfflineTransactions() != null) return Component.text(context.getOfflineTransactions().getType().toString());
            return null;
        });
    }

    // -----------------------------------------------------------------------
    // Hover event builders
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static HoverEvent<?> getShopInfoHoverEvent(PlaceholderContext context) {
        // Build a multi-line Component describing the shop
        TextComponent.Builder hoverText = Component.text();
        AbstractShop shop = context.getShop();
        ShopCreationProcess process = context.getProcess();

        if (shop != null) {
            hoverText.append(Component.text("Owner: " + (shop.isAdmin() ? getServerDisplayName() : shop.getOwnerName())));
            hoverText.append(Component.newline());
            hoverText.append(Component.text("Type: " + shop.getType().toString()));
            hoverText.append(Component.newline());
            hoverText.append(Component.text("Item: "));
            hoverText.append(plugin.getItemNameUtil().getName(shop.getItemStack()));
            hoverText.append(Component.newline());
            String priceStr = shop.isInfinitePrice() ? getAdminStockWord() : UtilMethods.formatLongToKString(shop.getPrice(), false);
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
        if (chatConfig == null) return;
        for (String key : chatConfig.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = chatConfig.getConfigurationSection(key);
            if (section != null) {
                for (String subKey : section.getKeys(false)) {
                    messageMap.put(key + "." + subKey, section.getString(subKey, ""));
                }
            } else {
                messageMap.put(key, chatConfig.getString(key, ""));
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
            List<String> lines = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                lines.add(shopSection.getString("line" + i, ""));
            }
            shopSignTextMap.put(key, lines.toArray(new String[0]));
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
        return messageMap.get(key + "." + subkey);
    }

    public static String[] getShopSignText(String shopType) {
        return shopSignTextMap.getOrDefault(shopType.toLowerCase(), new String[]{"Buy", "[item]", "[price]", "[stock]"});
    }

    public static List<String> getDisplayText(String shopType) {
        return displayTextMap.getOrDefault(shopType.toLowerCase(), Collections.emptyList());
    }

    public static String getFreePriceWord() { return freePriceWord != null ? freePriceWord : "Free"; }
    public static String getAdminStockWord() { return adminStockWord != null ? adminStockWord : "\u221e"; }
    public static String getServerDisplayName() { return serverDisplayName != null ? serverDisplayName : "Server"; }
    public static HashMap<String, String> getCreationWords() { return creationWords; }
    public static String getCreationWord(String key) {
        return creationWords.getOrDefault(key.toUpperCase(), UtilMethods.capitalize(key.toLowerCase()));
    }
    public static int getTargetMaxLength() { return targetMaxLength; }
    public static YamlConfiguration getChatConfig() { return chatConfig; }
    public static YamlConfiguration getSignConfig() { return signConfig; }
    public static YamlConfiguration getDisplayConfig() { return displayConfig; }
}

package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Arrays;

public class TransactionCommandHandler extends BukkitCommand {

    private final Shop plugin;
    private final LogHandler logHandler;

    // Cache for player's last transaction lookup to support pagination
    private final Map<UUID, CachedLookup> lookupCache = new HashMap<>();

    public TransactionCommandHandler(Shop plugin) {
        super("tx", "View transaction history", "/tx [top] [sort:field_dir] [limit:N] [player]", Arrays.asList("tx", "transactions"));
        this.setPermission("shop.operator");
        this.plugin = plugin;
        this.logHandler = plugin.getLogHandler();
        try {
            register();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void register() throws ReflectiveOperationException {
        final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        bukkitCommandMap.setAccessible(true);

        CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
        commandMap.register(this.getName(), this);
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check if database logging is enabled
        if (!logHandler.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Transaction history is not available because database logging is disabled.");
            return true;
        }

        // Check permissions
        if (plugin.usePerms() && !player.hasPermission("shop.operator")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Handle page navigation
        if (args.length >= 1 && args[0].equalsIgnoreCase("page")) {
            CachedLookup cached = lookupCache.get(player.getUniqueId());
            if (cached == null) {
                player.sendMessage(ChatColor.RED + "Run /tx [selectors] or /tx top [selectors] first!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /tx page <page_number>");
                return true;
            }

            try {
                int page = Integer.parseInt(args[1]);
                if (page < 1) {
                    player.sendMessage(ChatColor.RED + "Page number must be at least 1.");
                    return true;
                }
                displayTransactionsPage(player, cached, page - 1); // zero-indexed internally
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid page number.");
            }
            return true;
        }

        // Parse arguments
        boolean topMode = false;
        String targetPlayerName = null;
        SortOption sortOption = SortOption.DATE_DESC; // default: newest first
        int limit = 20; // default page size

        int argIndex = 0;
        if (args.length > argIndex && args[argIndex].equalsIgnoreCase("top")) {
            topMode = true;
            argIndex++;
        }

        // Check for sort option
        if (args.length > argIndex && args[argIndex].startsWith("sort:")) {
            String sortArg = args[argIndex].substring(5); // remove "sort:"
            sortOption = parseSortOption(sortArg);
            if (sortOption == null) {
                player.sendMessage(ChatColor.RED + "Invalid sort option. Use: sort:price_asc, sort:price_desc, sort:amount_asc, sort:amount_desc, sort:date_asc, sort:date_desc, sort:item_asc, sort:item_desc, sort:type_asc, sort:type_desc, sort:player_asc, sort:player_desc, sort:shop_asc, sort:shop_desc");
                return true;
            }
            argIndex++;
        }

        // Check for limit
        if (args.length > argIndex && args[argIndex].startsWith("limit:")) {
            try {
                limit = Integer.parseInt(args[argIndex].substring(6));
                if (limit < 1 || limit > 100) {
                    player.sendMessage(ChatColor.RED + "Limit must be between 1 and 100.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid limit value.");
                return true;
            }
            argIndex++;
        }

        // Remaining argument is player name (for top mode or specific player lookup)
        if (args.length > argIndex) {
            targetPlayerName = args[argIndex];
        }

        // If top mode and no specific player, show global top
        if (topMode && targetPlayerName == null) {
            lookupAndDisplayTopTransactions(player, sortOption, limit);
        } else if (targetPlayerName != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player '" + targetPlayerName + "' not found.");
                return true;
            }
            if (topMode) {
                lookupAndDisplayPlayerTopTransactions(player, target.getUniqueId(), sortOption, limit);
            } else {
                lookupAndDisplayPlayerTransactions(player, target.getUniqueId(), sortOption, limit);
            }
        } else {
            // Default: show own transactions
            lookupAndDisplayPlayerTransactions(player, player.getUniqueId(), sortOption, limit);
        }

        return true;
    }

    private SortOption parseSortOption(String arg) {
        // Format: field_direction (e.g., "price_asc", "date_desc")
        // Also accept just "price" (default asc), "date" (default desc), etc.
        try {
            if (arg.contains("_")) {
                String[] parts = arg.split("_");
                String field = parts[0].toLowerCase();
                String direction = parts[1].toLowerCase();
                SortField fieldEnum = SortField.fromString(field);
                if (fieldEnum == null) return null;
                SortDirection directionEnum = SortDirection.fromString(direction);
                if (directionEnum == null) return null;
                return new SortOption(fieldEnum, directionEnum);
            } else {
                // Default direction based on field
                SortField fieldEnum = SortField.fromString(arg.toLowerCase());
                if (fieldEnum == null) return null;
                SortDirection defaultDir = getDefaultDirection(fieldEnum);
                return new SortOption(fieldEnum, defaultDir);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private SortDirection getDefaultDirection(SortField field) {
        // Date defaults to newest first (desc), price/amount/item default to asc, type/player/shop default to asc
        switch (field) {
            case DATE:
                return SortDirection.DESC;
            default:
                return SortDirection.ASC;
        }
    }

    private void lookupAndDisplayPlayerTransactions(Player player, UUID targetUUID, SortOption sortOption, int limit) {
        player.sendMessage(ChatColor.YELLOW + "Fetching transaction history...");
        plugin.getFoliaLib().getScheduler().runAsync(task -> {
            List<TransactionRecord> transactions = logHandler.getPlayerTransactions(
                    targetUUID, sortOption.field.name().toLowerCase(), sortOption.direction.name().toLowerCase(), limit * 10);
            plugin.getFoliaLib().getScheduler().run(task -> {
                if (transactions.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "No transactions found.");
                    return;
                }
                CachedLookup cached = new CachedLookup(targetUUID, transactions, sortOption, limit);
                lookupCache.put(player.getUniqueId(), cached);
                displayTransactionsPage(player, cached, 0);
            });
        });
    }

    private void lookupAndDisplayPlayerTopTransactions(Player player, UUID targetUUID, SortOption sortOption, int limit) {
        player.sendMessage(ChatColor.YELLOW + "Fetching top transactions...");
        plugin.getFoliaLib().getScheduler().runAsync(task -> {
            List<TransactionRecord> transactions = logHandler.getPlayerTopTransactions(
                    targetUUID, sortOption.field.name().toLowerCase(), sortOption.direction.name().toLowerCase(), limit);
            plugin.getFoliaLib().getScheduler().run(task -> {
                if (transactions.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "No transactions found.");
                    return;
                }
                CachedLookup cached = new CachedLookup(targetUUID, transactions, sortOption, limit);
                cached.isTopMode = true;
                lookupCache.put(player.getUniqueId(), cached);
                displayTransactionsPage(player, cached, 0);
            });
        });
    }

    private void lookupAndDisplayTopTransactions(Player player, SortOption sortOption, int limit) {
        player.sendMessage(ChatColor.YELLOW + "Fetching global top transactions...");
        plugin.getFoliaLib().getScheduler().runAsync(task -> {
            List<TransactionRecord> transactions = logHandler.getGlobalTopTransactions(
                    sortOption.field.name().toLowerCase(), sortOption.direction.name().toLowerCase(), limit);
            plugin.getFoliaLib().getScheduler().run(task -> {
                if (transactions.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "No transactions found.");
                    return;
                }
                CachedLookup cached = new CachedLookup(null, transactions, sortOption, limit);
                cached.isTopMode = true;
                cached.isGlobal = true;
                lookupCache.put(player.getUniqueId(), cached);
                displayTransactionsPage(player, cached, 0);
            });
        });
    }

    private void displayTransactionsPage(Player player, CachedLookup cached, int pageIndex) {
        int start = pageIndex * cached.pageSize;
        int end = Math.min(start + cached.pageSize, cached.allTransactions.size());

        if (start >= cached.allTransactions.size()) {
            player.sendMessage(ChatColor.RED + "Page " + (pageIndex + 1) + " does not exist. Total pages: " + getTotalPages(cached));
            return;
        }

        List<TransactionRecord> pageTransactions = cached.allTransactions.subList(start, end);

        StringBuilder header = new StringBuilder();
        if (cached.isGlobal) {
            header.append(ChatColor.GOLD).append("=== Global Top Transactions (Page ").append(pageIndex + 1).append("/").append(getTotalPages(cached)).append(") ===");
        } else if (cached.isTopMode) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(cached.targetUUID);
            header.append(ChatColor.GOLD).append("=== Top Transactions for ").append(target.getName()).append(" (Page ").append(pageIndex + 1).append("/").append(getTotalPages(cached)).append(") ===");
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(cached.targetUUID);
            header.append(ChatColor.GOLD).append("=== Transaction History for ").append(target.getName()).append(" (Page ").append(pageIndex + 1).append("/").append(getTotalPages(cached)).append(") ===");
        }
        header.append(" | Sort: ").append(ChatColor.AQUA).append(cached.sortOption.toString());
        player.sendMessage(header.toString());

        for (TransactionRecord tx : pageTransactions) {
            player.sendMessage(formatTransactionRecord(tx));
        }

        StringBuilder footer = new StringBuilder();
        footer.append(ChatColor.GRAY).append("Page ").append(pageIndex + 1).append(" of ").append(getTotalPages(cached));
        if (getTotalPages(cached) > 1) {
            footer.append(" | Use ").append(ChatColor.YELLOW).append("/tx page ").append(pageIndex + 2).append(ChatColor.GRAY).append(" for next page");
        }
        footer.append(" | Sort: ").append(ChatColor.YELLOW).append("/tx sort:<field>_<asc|desc>");
        player.sendMessage(footer.toString());
    }

    private int getTotalPages(CachedLookup cached) {
        return (int) Math.ceil((double) cached.allTransactions.size() / cached.pageSize);
    }

    private String formatTransactionRecord(TransactionRecord tx) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(tx.timestamp));
        String typeColor = getTypeColor(tx.type);
        String itemName = tx.itemName != null ? tx.itemName : "Unknown";
        String priceStr = plugin.getPriceString(tx.price, false);
        String shopStr = tx.shopLocation != null ? " at " + ChatColor.GRAY + UtilMethods.getCleanLocation(tx.shopLocation, true) : "";
        String otherPlayerStr = tx.otherPlayerName != null ? " with " + ChatColor.WHITE + tx.otherPlayerName : "";

        return ChatColor.GRAY + "[" + dateStr + "] " +
               typeColor + "[" + tx.type.name() + "]" + ChatColor.GRAY + " " +
               ChatColor.WHITE + itemName + ChatColor.GRAY + " x" + ChatColor.WHITE + tx.amount + ChatColor.GRAY + " " +
               "for " + ChatColor.GREEN + priceStr + ChatColor.GRAY +
               otherPlayerStr + shopStr;
    }

    private ChatColor getTypeColor(ShopType type) {
        switch (type) {
            case BUY: return ChatColor.RED;
            case SELL: return ChatColor.GREEN;
            case BARTER: return ChatColor.AQUA;
            case COMBO: return ChatColor.GOLD;
            case GAMBLE: return ChatColor.LIGHT_PURPLE;
            default: return ChatColor.WHITE;
        }
    }

    @Override
    public List<String> tabComplete(@NonNull CommandSender sender, @NonNull String alias, @NonNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("page");
            completions.add("top");
            completions.add("sort:price_asc");
            completions.add("sort:price_desc");
            completions.add("sort:amount_asc");
            completions.add("sort:amount_desc");
            completions.add("sort:date_asc");
            completions.add("sort:date_desc");
            completions.add("sort:item_asc");
            completions.add("sort:item_desc");
            completions.add("sort:type_asc");
            completions.add("sort:type_desc");
            completions.add("sort:player_asc");
            completions.add("sort:player_desc");
            completions.add("sort:shop_asc");
            completions.add("sort:shop_desc");
            completions.add("limit:10");
            completions.add("limit:20");
            completions.add("limit:50");
            // Add online player names for specific player lookup
            if (sender instanceof Player) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("page")) {
                // Could add page numbers, but dynamic
            } else if (args[0].equalsIgnoreCase("top")) {
                // Add sort options and player names
                completions.add("sort:price_asc");
                completions.add("sort:price_desc");
                completions.add("sort:amount_asc");
                completions.add("sort:amount_desc");
                completions.add("sort:date_asc");
                completions.add("sort:date_desc");
                completions.add("sort:item_asc");
                completions.add("sort:item_desc");
                completions.add("sort:type_asc");
                completions.add("sort:type_desc");
                completions.add("sort:player_asc");
                completions.add("sort:player_desc");
                completions.add("sort:shop_asc");
                completions.add("sort:shop_desc");
                completions.add("limit:10");
                completions.add("limit:20");
                completions.add("limit:50");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("top")) {
                // Player names after top [sort:] [limit:]
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        // Filter by what user has typed
        List<String> filtered = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], completions, filtered);
        Collections.sort(filtered);
        return filtered;
    }

    // Inner classes for sorting and caching
    private enum SortField {
        PRICE, AMOUNT, DATE, ITEM, TYPE, PLAYER, SHOP;

        static SortField fromString(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private enum SortDirection {
        ASC, DESC;

        static SortDirection fromString(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static class SortOption {
        final SortField field;
        final SortDirection direction;

        SortOption(SortField field, SortDirection direction) {
            this.field = field;
            this.direction = direction;
        }

        @Override
        public String toString() {
            return field.name().toLowerCase() + "_" + direction.name().toLowerCase();
        }
    }

    private static class CachedLookup {
        final UUID targetUUID;
        final List<TransactionRecord> allTransactions;
        final SortOption sortOption;
        final int pageSize;
        boolean isTopMode = false;
        boolean isGlobal = false;

        CachedLookup(UUID targetUUID, List<TransactionRecord> transactions, SortOption sortOption, int pageSize) {
            this.targetUUID = targetUUID;
            this.allTransactions = transactions;
            this.sortOption = sortOption;
            this.pageSize = pageSize;
        }
    }

    // Record class for transaction data (matches LogHandler query results)
    public static class TransactionRecord {
        public long timestamp;
        public ShopType type;
        public double price;
        public int amount;
        public String itemName;
        public String itemBase64;
        public String barterItemBase64;
        public String shopWorld;
        public int shopX, shopY, shopZ;
        public String otherPlayerName;
        public UUID otherPlayerUUID;
        public Location shopLocation;

        public Location getShopLocation() {
            if (shopLocation == null && shopWorld != null) {
                shopLocation = new Location(Bukkit.getWorld(shopWorld), shopX, shopY, shopZ);
            }
            return shopLocation;
        }
    }
}
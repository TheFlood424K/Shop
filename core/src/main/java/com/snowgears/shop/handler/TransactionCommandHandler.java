package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.*;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Arrays;
import java.util.Date;

public class TransactionCommandHandler extends BukkitCommand implements CommandExecutor, TabCompleter {

    private Shop plugin;

    private static final int PAGE_SIZE = 10;
    private static final long DEFAULT_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000L; // 1 day, used when no t: selector is given
    private static final long CLUMP_WINDOW_MILLIS = 5 * 60 * 1000L; // same-shop transactions within this span get combined into one line

    private static final SimpleDateFormat EXACT_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
    private static final SimpleDateFormat RELATIVE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public TransactionCommandHandler(Shop instance) {
        super("transactions", "View your transaction history", "/transactions [page] [t:<time>] [#verbose]", Arrays.asList("trans", "tx"));
        this.setPermission("shop.transactions");
        this.plugin = instance;
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
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.usePerms() && !player.hasPermission("shop.transactions")) {
            ShopMessage.sendMessage("permission", "use", player, null);
            return true;
        }

        // #verbose disables clumping of same-shop transactions made within a few minutes of each other.
        boolean verboseFlag = false;
        List<String> remainingArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equalsIgnoreCase("#verbose")) {
                verboseFlag = true;
            } else {
                remainingArgs.add(arg);
            }
        }
        final boolean verbose = verboseFlag;
        args = remainingArgs.toArray(new String[0]);

        // /trans
        if (args.length == 0) {
            showTransactions(player, 1, DEFAULT_LOOKBACK_MILLIS, verbose);
            return true;
        }

        // /trans <page>
        if (args.length == 1) {
            try {
                int page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
                showTransactions(player, page, DEFAULT_LOOKBACK_MILLIS, verbose);
            } catch (NumberFormatException e) {
                // Not a page number, could be a time selector
                if (args[0].startsWith("t:")) {
                    long lookbackMillis = parseTimeSelector(args[0]);
                    showTransactions(player, 1, lookbackMillis, verbose);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /transactions [page] [t:<time>] [#verbose]");
                    player.sendMessage(ChatColor.GRAY + "Example: /transactions 1 t:7d #verbose");
                }
            }
            return true;
        }

        // /trans <page> [t:<time>] [#verbose]
        if (args.length >= 2) {
            try {
                int page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;

                long lookbackMillis = DEFAULT_LOOKBACK_MILLIS;
                if (args[1].startsWith("t:")) {
                    lookbackMillis = parseTimeSelector(args[1]);
                }

                showTransactions(player, page, lookbackMillis, verbose);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Usage: /transactions [page] [t:<time>] [#verbose]");
                player.sendMessage(ChatColor.GRAY + "Example: /transactions 1 t:7d #verbose");
            }
            return true;
        }

        return true;
    }

    private long parseTimeSelector(String selector) {
        // Format: t:<number><unit> where unit is s, m, h, d, w
        // e.g., t:30m, t:2h, t:7d, t:1w
        String value = selector.substring(2).toLowerCase();
        if (value.isEmpty()) {
            return DEFAULT_LOOKBACK_MILLIS;
        }

        char unit = value.charAt(value.length() - 1);
        String numStr = value.substring(0, value.length() - 1);

        try {
            long num = Long.parseLong(numStr);
            switch (unit) {
                case 's': return num * 1000L;
                case 'm': return num * 60 * 1000L;
                case 'h': return num * 60 * 60 * 1000L;
                case 'd': return num * 24 * 60 * 60 * 1000L;
                case 'w': return num * 7 * 24 * 60 * 60 * 1000L;
                default: return DEFAULT_LOOKBACK_MILLIS;
            }
        } catch (NumberFormatException e) {
            return DEFAULT_LOOKBACK_MILLIS;
        }
    }

    private void showTransactions(Player player, int page, long lookbackMillis, boolean verbose) {
        if (!plugin.getLogHandler().isEnabled()) {
            player.sendMessage(ChatColor.RED + "Transaction history is not available (database logging is disabled).");
            return;
        }

        UUID playerUUID = player.getUniqueId();
        long since = System.currentTimeMillis() - lookbackMillis;

        List<TransactionEntry> entries = new ArrayList<>();

        String query = "SELECT sa.ts, sa.shop_uuid, sa.shop_world, sa.shop_x, sa.shop_y, sa.shop_z, " +
                "st.t_type, st.price, st.amount, st.item, st.barter_item " +
                "FROM shop_action sa " +
                "JOIN shop_transaction st ON sa.transaction_id = st.id " +
                "WHERE sa.player_uuid = ? AND sa.ts > ? " +
                "ORDER BY sa.ts DESC " +
                "LIMIT ? OFFSET ?";

        try (Connection conn = plugin.getLogHandler().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, playerUUID.toString());
            stmt.setTimestamp(2, new Timestamp(since));
            stmt.setInt(3, PAGE_SIZE);
            stmt.setInt(4, (page - 1) * PAGE_SIZE);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TransactionEntry entry = new TransactionEntry(
                        rs.getTimestamp("ts").getTime(),
                        rs.getString("shop_uuid"),
                        rs.getString("shop_world"),
                        rs.getInt("shop_x"),
                        rs.getInt("shop_y"),
                        rs.getInt("shop_z"),
                        rs.getString("t_type"),
                        rs.getDouble("price"),
                        rs.getInt("amount"),
                        rs.getString("item"),
                        rs.getString("barter_item")
                );
                entries.add(entry);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "SQL error while fetching transactions", e);
            player.sendMessage(ChatColor.RED + "An error occurred while fetching your transaction history.");
            return;
        }

        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No transactions found in the selected time range.");
            return;
        }

        // Clump entries: combine consecutive entries from the same shop within CLUMP_WINDOW_MILLIS
        List<TransactionEntry> displayEntries = new ArrayList<>();
        if (!verbose && entries.size() > 1) {
            TransactionEntry current = entries.get(0);
            int clumpCount = 1;
            double totalPrice = current.price;
            int totalAmount = current.amount;

            for (int i = 1; i < entries.size(); i++) {
                TransactionEntry next = entries.get(i);

                boolean sameShop = current.shopUuid != null && current.shopUuid.equals(next.shopUuid);
                boolean withinWindow = (current.timestamp - next.timestamp) <= CLUMP_WINDOW_MILLIS;

                if (sameShop && withinWindow) {
                    clumpCount++;
                    totalPrice += next.price;
                    totalAmount += next.amount;
                } else {
                    // Finalize current clump
                    current.clumpCount = clumpCount;
                    current.totalPrice = totalPrice;
                    current.totalAmount = totalAmount;
                    displayEntries.add(current);

                    // Start new clump
                    current = next;
                    clumpCount = 1;
                    totalPrice = current.price;
                    totalAmount = current.amount;
                }
            }

            // Add the last clump
            current.clumpCount = clumpCount;
            current.totalPrice = totalPrice;
            current.totalAmount = totalAmount;
            displayEntries.add(current);
        } else {
            // Verbose mode: no clumping
            for (TransactionEntry entry : entries) {
                entry.clumpCount = 1;
                entry.totalPrice = entry.price;
                entry.totalAmount = entry.amount;
                displayEntries.add(entry);
            }
        }

        // Display results
        player.sendMessage(ChatColor.GOLD + "=== Transaction History (Page " + page + ") ===");
        player.sendMessage(ChatColor.GRAY + "Time range: last " + formatDuration(lookbackMillis) + (verbose ? " (verbose)" : " (clumped)"));

        for (TransactionEntry entry : displayEntries) {
            String timeStr = EXACT_TIME_FORMAT.format(new Date(entry.timestamp));
            String shopLoc = entry.shopWorld != null ?
                    entry.shopWorld + " (" + entry.shopX + ", " + entry.shopY + ", " + entry.shopZ + ")" :
                    "Unknown";

            String typeColor;
            switch (entry.type.toUpperCase()) {
                case "BUY": typeColor = ChatColor.GREEN + ""; break;
                case "SELL": typeColor = ChatColor.RED + ""; break;
                case "BARTER": typeColor = ChatColor.AQUA + ""; break;
                case "GAMBLE": typeColor = ChatColor.LIGHT_PURPLE + ""; break;
                default: typeColor = ChatColor.WHITE + "";
            }

            String itemName = "Unknown Item";
            try {
                ItemStack itemStack = UtilMethods.itemStackFromBase64(entry.itemBase64);
                if (itemStack != null) {
                    itemName = new ItemNameUtil().getName(itemStack).toPlainText();
                }
            } catch (Exception e) {
                // Ignore, use default
            }

            String line = ChatColor.GRAY + "[" + timeStr + "] " +
                    typeColor + entry.type.toUpperCase() + ChatColor.GRAY + ": " +
                    ChatColor.WHITE + entry.totalAmount + "x " + itemName +
                    ChatColor.GRAY + " for " + ChatColor.YELLOW + plugin.getPriceString(entry.totalPrice, false) +
                    ChatColor.GRAY + " at " + ChatColor.AQUA + shopLoc;

            if (entry.clumpCount > 1) {
                line += ChatColor.GRAY + " (" + entry.clumpCount + " transactions clumped)";
            }

            player.sendMessage(line);
        }

        player.sendMessage(ChatColor.GRAY + "---");
        player.sendMessage(ChatColor.GRAY + "Page " + page + " of transactions. Use /trans " + (page + 1) + " for next page.");
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Page number or time selector
            completions.add("1");
            completions.add("t:1h");
            completions.add("t:6h");
            completions.add("t:1d");
            completions.add("t:7d");
            completions.add("#verbose");
        } else if (args.length == 2) {
            // Time selector or #verbose
            if (!args[0].equalsIgnoreCase("#verbose")) {
                completions.add("t:1h");
                completions.add("t:6h");
                completions.add("t:1d");
                completions.add("t:7d");
                completions.add("#verbose");
            }
        } else if (args.length == 3) {
            if (!args[1].equalsIgnoreCase("#verbose") && !args[0].equalsIgnoreCase("#verbose")) {
                completions.add("#verbose");
            }
        }

        return completions;
    }

    // Inner class to hold transaction data
    private static class TransactionEntry {
        final long timestamp;
        final String shopUuid;
        final String shopWorld;
        final int shopX, shopY, shopZ;
        final String type;
        final double price;
        final int amount;
        final String itemBase64;
        final String barterItemBase64;

        // Clumping fields
        int clumpCount = 1;
        double totalPrice;
        int totalAmount;

        TransactionEntry(long timestamp, String shopUuid, String shopWorld, int shopX, int shopY, int shopZ,
                         String type, double price, int amount, String itemBase64, String barterItemBase64) {
            this.timestamp = timestamp;
            this.shopUuid = shopUuid;
            this.shopWorld = shopWorld;
            this.shopX = shopX;
            this.shopY = shopY;
            this.shopZ = shopZ;
            this.type = type;
            this.price = price;
            this.amount = amount;
            this.itemBase64 = itemBase64;
            this.barterItemBase64 = barterItemBase64;
        }
    }
}
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ItemNameUtil;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.TransactionLookupFilter;
import com.snowgears.shop.util.UtilMethods;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Implements {@code /transactions} (alias {@code /tx}) — a player's own sales and purchase history.
 *
 * <p>Ported from the Izopropyl fork (commits f743628, 3d600ad, 16d5f7a, d22ea59), reduced to
 * self-only. The fork's {@code o:<player>} selector let a {@code shop.operator} browse any
 * player's transactions; that selector is **absent** rather than permission-gated, so there is no
 * argument a non-operator can pass to reach another player's rows. Cross-player browsing is a
 * separate change with its own permission node.
 *
 * <p>The subject UUID is taken from the executing player and never from the arguments.
 *
 * <p>Requires database logging to be enabled — without it {@link LogHandler#getShopTransactions}
 * invokes the callback with an empty list and the command reports no transactions.
 */
public class TransactionCommandHandler implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;
    private static final long DEFAULT_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000L;
    private static final long CLUMP_WINDOW_MILLIS = 5 * 60 * 1000L;

    private static final SimpleDateFormat EXACT_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
    static {
        EXACT_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final Map<UUID, CachedLookup> lookupCache = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Only players can execute this command!", NamedTextColor.RED));
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
        String[] selectorArgs = remainingArgs.toArray(new String[0]);

        // /transactions page <n>
        if (selectorArgs.length >= 1 && selectorArgs[0].equalsIgnoreCase("page")) {
            CachedLookup cached = lookupCache.get(p.getUniqueId());
            if (cached == null) {
                p.sendMessage(Component.text("Run /tx [selectors] or /tx top [selectors] first!", NamedTextColor.RED));
                return true;
            }
            int page = 1;
            if (selectorArgs.length >= 2) {
                try {
                    page = Integer.parseInt(selectorArgs[1]);
                } catch (NumberFormatException e) {
                    p.sendMessage(Component.text("Invalid page number!", NamedTextColor.RED));
                    return true;
                }
            }
            sendPaginatedMessage(p, cached.title, cached.lines, page);
            return true;
        }

        boolean topMode = selectorArgs.length >= 1 && selectorArgs[0].equalsIgnoreCase("top");
        String[] filteredArgs = topMode ? Arrays.copyOfRange(selectorArgs, 1, selectorArgs.length) : selectorArgs;

        ParsedSelectors selectors = parseSelectors(sender, filteredArgs);
        if (selectors == null) return true;

        // The subject is always the sender. No selector can change this.
        final UUID subjectUUID = p.getUniqueId();

        if (topMode) {
            ShopType action = selectors.action != null ? selectors.action : ShopType.SELL;
            if (action == ShopType.BARTER) {
                sender.sendMessage(Component.text("top doesn't support a:barter - barter trades have no common currency to rank by.", NamedTextColor.RED));
                return true;
            }
            TransactionLookupFilter filter = new TransactionLookupFilter(action, selectors.includeItems, selectors.excludeItems, selectors.customerName);
            Shop.getPlugin().getLogHandler().getShopTransactions(subjectUUID, selectors.startTime, selectors.endTime, filter, transactions -> {
                if (transactions.isEmpty()) {
                    p.sendMessage(Component.text("No " + (action == ShopType.BUY ? "purchases" : "sales") + " found at your shops in that time frame.", NamedTextColor.RED));
                    return;
                }
                assembleAndSendTopMessage(p, transactions, action,
                        selectors.sortType != null ? selectors.sortType : SortType.VALUE);
            });
        } else {
            TransactionLookupFilter filter = new TransactionLookupFilter(selectors.action, selectors.includeItems, selectors.excludeItems, selectors.customerName);
            Shop.getPlugin().getLogHandler().getShopTransactions(subjectUUID, selectors.startTime, selectors.endTime, filter, transactions -> {
                if (transactions.isEmpty()) {
                    p.sendMessage(Component.text("No transactions found at your shops in that time frame.", NamedTextColor.RED));
                    return;
                }
                assembleAndSendMessage(p, transactions, verbose);
            });
        }
        return true;
    }

    private static class ParsedSelectors {
        long startTime;
        long endTime;
        ShopType action;
        Set<Material> includeItems;
        Set<Material> excludeItems;
        String customerName;
        SortType sortType;
    }

    public enum SortType {
        VALUE, PURCHASES, QUANTITY, ALL
    }

    private ParsedSelectors parseSelectors(CommandSender sender, String[] args) {
        ParsedSelectors result = new ParsedSelectors();
        result.endTime = System.currentTimeMillis();
        result.startTime = result.endTime - DEFAULT_LOOKBACK_MILLIS;

        for (String arg : args) {
            int colonIndex = arg.indexOf(':');
            if (colonIndex <= 0 || colonIndex == arg.length() - 1) {
                sender.sendMessage(Component.text("Invalid selector '" + arg + "'. Expected t:<time> (e.g. t:1h, t:3d, t:30m)", NamedTextColor.RED));
                return null;
            }
            String key = arg.substring(0, colonIndex).toLowerCase(Locale.ROOT);
            String value = arg.substring(colonIndex + 1);

            switch (key) {
                case "t", "time": {
                    Long duration = parseDurationMillis(value);
                    if (duration == null) {
                        sender.sendMessage(Component.text("Invalid time frame '" + value + "'!", NamedTextColor.RED));
                        return null;
                    }
                    result.startTime = result.endTime - duration;
                    break;
                }
                case "a", "action": {
                    try {
                        result.action = ShopType.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text("Invalid action '" + value + "'. Expected sell, buy, or barter.", NamedTextColor.RED));
                        return null;
                    }
                    break;
                }
                case "i", "include": {
                    Set<Material> materials = parseMaterialList(value);
                    if (materials == null) {
                        sender.sendMessage(Component.text("Unknown material in '" + value + "'!", NamedTextColor.RED));
                        return null;
                    }
                    result.includeItems = materials;
                    break;
                }
                case "e", "exclude": {
                    Set<Material> materials = parseMaterialList(value);
                    if (materials == null) {
                        sender.sendMessage(Component.text("Unknown material in '" + value + "'!", NamedTextColor.RED));
                        return null;
                    }
                    result.excludeItems = materials;
                    break;
                }
                case "u", "user": {
                    result.customerName = value;
                    break;
                }
                case "s", "sort": {
                    try {
                        result.sortType = SortType.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text("Invalid sort '" + value + "'. Expected value, purchases, quantity, or all", NamedTextColor.RED));
                        return null;
                    }
                    break;
                }
                default:
                    sender.sendMessage(Component.text("Unknown selector '" + key + ":'. Valid selectors: t, a, i, e, u, s.", NamedTextColor.RED));
                    return null;
            }
        }
        return result;
    }

    private static Long parseDurationMillis(String timeFrame) {
        long unitMillis;
        String numberPart;
        if (timeFrame.endsWith("d")) {
            unitMillis = 24 * 60 * 60 * 1000L;
            numberPart = timeFrame.substring(0, timeFrame.length() - 1);
        } else if (timeFrame.endsWith("h")) {
            unitMillis = 60 * 60 * 1000L;
            numberPart = timeFrame.substring(0, timeFrame.length() - 1);
        } else if (timeFrame.endsWith("m")) {
            unitMillis = 60 * 1000L;
            numberPart = timeFrame.substring(0, timeFrame.length() - 1);
        } else if (timeFrame.endsWith("s")) {
            unitMillis = 1000L;
            numberPart = timeFrame.substring(0, timeFrame.length() - 1);
        } else {
            return null;
        }

        try {
            return Long.parseLong(numberPart) * unitMillis;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatRelativeTime(Date timestamp) {
        long elapsedMillis = Math.max(0, System.currentTimeMillis() - timestamp.getTime());
        long seconds = elapsedMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (seconds < 60) return seconds + "s ago";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        return String.format("%.1fd ago", hours / 24.0);
    }

    private static Set<Material> parseMaterialList(String value) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String token : value.split(",")) {
            Material material = Material.matchMaterial(token);
            if (material == null) return null;
            materials.add(material);
        }
        return materials;
    }

    public void assembleAndSendMessage(Player player, List<PlayerTransactionRecord> transactions, boolean verbose) {
        List<Component> lines = new ArrayList<>();
        if (verbose) {
            for (PlayerTransactionRecord tx : transactions) {
                lines.add(formatTransactionLine(tx));
            }
        } else {
            for (List<PlayerTransactionRecord> clump : clumpTransactions(transactions)) {
                lines.add(clump.size() == 1 ? formatTransactionLine(clump.get(0)) : formatClumpedLine(clump));
            }
        }

        String title = "Your Shop Transactions";
        lookupCache.put(player.getUniqueId(), new CachedLookup(title, lines));
        sendPaginatedMessage(player, title, lines, 1);
    }

    /** Groups consecutive same-shop/action/item transactions within CLUMP_WINDOW_MILLIS of each other. */
    private static List<List<PlayerTransactionRecord>> clumpTransactions(List<PlayerTransactionRecord> transactions) {
        List<List<PlayerTransactionRecord>> clumps = new ArrayList<>();
        List<PlayerTransactionRecord> current = null;
        for (PlayerTransactionRecord tx : transactions) {
            if (current != null && sameClumpKey(current.get(0), tx)
                    && current.get(0).getTimestamp().getTime() - tx.getTimestamp().getTime() <= CLUMP_WINDOW_MILLIS) {
                current.add(tx);
            } else {
                current = new ArrayList<>();
                current.add(tx);
                clumps.add(current);
            }
        }
        return clumps;
    }

    private static boolean sameClumpKey(PlayerTransactionRecord a, PlayerTransactionRecord b) {
        if (a.getTransactionType() != b.getTransactionType()) return false;
        if (!Objects.equals(a.getShopLocation(), b.getShopLocation())) return false;

        ItemStack itemA = a.getItem();
        ItemStack itemB = b.getItem();
        if (itemA == null || itemB == null) return itemA == itemB;
        if (!itemA.isSimilar(itemB)) return false;

        if (a.getTransactionType() == ShopType.BARTER) {
            ItemStack barterA = a.getBarterItem();
            ItemStack barterB = b.getBarterItem();
            if (barterA == null || barterB == null) return barterA == barterB;
            if (!barterA.isSimilar(barterB)) return false;
        }
        return true;
    }

    public void assembleAndSendTopMessage(Player player, List<PlayerTransactionRecord> transactions, ShopType action, SortType sortType) {
        String verb = action == ShopType.BUY ? "bought" : "sold";
        String title = action == ShopType.BUY ? "Top Items Bought" : "Top Items Sold";
        List<Component> lines = new ArrayList<>();

        if (sortType == SortType.ALL) {
            int totalPurchases = transactions.size();
            int totalQuantity = 0;
            double totalRevenue = 0;
            for (PlayerTransactionRecord tx : transactions) {
                totalQuantity += tx.getAmount();
                totalRevenue += tx.getPrice();
            }
            String priceStr = Shop.getPlugin().getPriceString(totalRevenue, false);
            lines.add(Component.text(totalPurchases + " purchases", NamedTextColor.WHITE)
                    .append(Component.text(", " + totalQuantity + " items " + verb + " for ", NamedTextColor.GRAY))
                    .append(Component.text(priceStr, NamedTextColor.YELLOW)));
        } else {
            Map<Material, MaterialTotals> totals = new LinkedHashMap<>();
            for (PlayerTransactionRecord tx : transactions) {
                Material material = tx.getItem() != null ? tx.getItem().getType() : Material.AIR;
                totals.computeIfAbsent(material, m -> new MaterialTotals(tx.getItem())).add(tx.getAmount(), tx.getPrice());
            }

            List<MaterialTotals> sorted = new ArrayList<>(totals.values());
            switch (sortType) {
                case QUANTITY:
                    sorted.sort((a, b) -> Integer.compare(b.amount, a.amount));
                    break;
                case PURCHASES:
                    sorted.sort((a, b) -> Integer.compare(b.purchases, a.purchases));
                    break;
                case VALUE:
                default:
                    sorted.sort((a, b) -> Double.compare(b.revenue, a.revenue));
                    break;
            }

            int rank = 1;
            for (MaterialTotals t : sorted) {
                String itemName = ChatColor.stripColor(ShopMessage.toPlain(new ItemNameUtil().getName(t.sampleItem)));
                String priceStr = Shop.getPlugin().getPriceString(t.revenue, false);
                Component line = Component.text(rank + ". ", NamedTextColor.GOLD)
                        .append(Component.text(itemName + " x" + t.amount, NamedTextColor.WHITE));
                if (sortType == SortType.PURCHASES) {
                    line = line.append(Component.text(" (" + t.purchases + "x)", NamedTextColor.GRAY));
                }
                line = line.append(Component.text(" " + verb + " for ", NamedTextColor.GRAY))
                        .append(Component.text(priceStr, NamedTextColor.YELLOW));
                lines.add(line);
                rank++;
            }
        }

        lookupCache.put(player.getUniqueId(), new CachedLookup(title, lines));
        sendPaginatedMessage(player, title, lines, 1);
    }

    private void sendPaginatedMessage(Player player, String title, List<Component> lines, int page) {
        int totalPages = (int) Math.ceil(lines.size() / (double) PAGE_SIZE);
        page = Math.max(1, Math.min(page, totalPages));

        player.sendMessage(Component.text("===== ", NamedTextColor.DARK_GRAY)
                .append(Component.text(title, NamedTextColor.GOLD))
                .append(Component.text(" (Page " + page + "/" + totalPages + ") ", NamedTextColor.GRAY))
                .append(Component.text("=====", NamedTextColor.DARK_GRAY)));

        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, lines.size());
        for (Component line : lines.subList(fromIndex, toIndex)) {
            player.sendMessage(line);
        }
        player.sendMessage(buildPaginationRow(page, totalPages));
    }

    private Component formatTransactionLine(PlayerTransactionRecord tx) {
        String itemName = ChatColor.stripColor(ShopMessage.toPlain(new ItemNameUtil().getName(tx.getItem())));
        String priceStr = Shop.getPlugin().getPriceString(tx.getPrice(), false);
        VerbInfo verbInfo = VerbInfo.of(tx.getTransactionType());

        Component timePrefix = Component.text("[" + formatRelativeTime(tx.getTimestamp()) + "] ", NamedTextColor.DARK_GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(EXACT_TIME_FORMAT.format(tx.getTimestamp()))));

        Component line = timePrefix
                .append(Component.text(verbInfo.verb + " ", verbInfo.color))
                .append(Component.text(itemName + " x" + tx.getAmount(), NamedTextColor.WHITE));

        if (tx.getTransactionType() == ShopType.BARTER && tx.getBarterItem() != null) {
            String barterName = ChatColor.stripColor(ShopMessage.toPlain(new ItemNameUtil().getName(tx.getBarterItem())));
            line = line.append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(barterName + " x" + (int) tx.getPrice(), NamedTextColor.WHITE));
        } else {
            line = line.append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(priceStr, NamedTextColor.YELLOW));
        }

        String customerName = tx.getCustomerUUID() != null ? Bukkit.getOfflinePlayer(tx.getCustomerUUID()).getName() : "an unknown player";
        Component locationHover = Component.text(UtilMethods.getCleanLocation(tx.getShopLocation(), true));
        return line.append(Component.text(" " + verbInfo.preposition + " " + customerName, NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(locationHover)));
    }

    private Component formatClumpedLine(List<PlayerTransactionRecord> clump) {
        PlayerTransactionRecord newest = clump.get(0);
        PlayerTransactionRecord oldest = clump.get(clump.size() - 1);
        VerbInfo verbInfo = VerbInfo.of(newest.getTransactionType());

        int totalAmount = 0;
        double totalPrice = 0;
        Set<UUID> customers = new HashSet<>();
        for (PlayerTransactionRecord tx : clump) {
            totalAmount += tx.getAmount();
            totalPrice += tx.getPrice();
            customers.add(tx.getCustomerUUID());
        }

        String itemName = ChatColor.stripColor(ShopMessage.toPlain(new ItemNameUtil().getName(newest.getItem())));

        Component timePrefix = Component.text("[" + formatRelativeTime(newest.getTimestamp()) + "] ", NamedTextColor.DARK_GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(
                        EXACT_TIME_FORMAT.format(oldest.getTimestamp()) + " - " + EXACT_TIME_FORMAT.format(newest.getTimestamp()))));

        Component line = timePrefix
                .append(Component.text(verbInfo.verb + " ", verbInfo.color))
                .append(Component.text(itemName + " x" + totalAmount, NamedTextColor.WHITE))
                .append(Component.text(" (" + clump.size() + "x) ", NamedTextColor.GRAY));

        if (newest.getTransactionType() == ShopType.BARTER && newest.getBarterItem() != null) {
            String barterName = ChatColor.stripColor(ShopMessage.toPlain(new ItemNameUtil().getName(newest.getBarterItem())));
            line = line.append(Component.text("for ", NamedTextColor.GRAY))
                    .append(Component.text(barterName + " x" + (int) totalPrice, NamedTextColor.WHITE));
        } else {
            line = line.append(Component.text("for ", NamedTextColor.GRAY))
                    .append(Component.text(Shop.getPlugin().getPriceString(totalPrice, false), NamedTextColor.YELLOW));
        }

        String customerName;
        if (customers.size() == 1) {
            UUID onlyCustomer = customers.iterator().next();
            customerName = onlyCustomer != null ? Bukkit.getOfflinePlayer(onlyCustomer).getName() : "an unknown player";
        } else {
            customerName = customers.size() + " different players";
        }
        Component locationHover = Component.text(UtilMethods.getCleanLocation(newest.getShopLocation(), true));
        return line.append(Component.text(" " + verbInfo.preposition + " " + customerName, NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(locationHover)));
    }

    private static class VerbInfo {
        private final String verb;
        private final String preposition;
        private final NamedTextColor color;

        private VerbInfo(String verb, String preposition, NamedTextColor color) {
            this.verb = verb;
            this.preposition = preposition;
            this.color = color;
        }

        private static VerbInfo of(ShopType type) {
            switch (type) {
                case SELL: // shop sold the item to the customer
                    return new VerbInfo("Sold", "to", NamedTextColor.GREEN);
                case BUY: // shop bought the item from the customer
                    return new VerbInfo("Bought", "from", NamedTextColor.RED);
                default: // BARTER
                    return new VerbInfo("Traded", "with", NamedTextColor.AQUA);
            }
        }
    }

    private Component buildPaginationRow(int page, int totalPages) {
        Component prev = page > 1
                ? Component.text("« Prev", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/transactions page " + (page - 1)))
                        .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1))))
                : Component.text("« Prev", NamedTextColor.DARK_GRAY);

        Component next = page < totalPages
                ? Component.text("Next »", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/transactions page " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1))))
                : Component.text("Next »", NamedTextColor.DARK_GRAY);

        return prev.append(Component.text("   ", NamedTextColor.GRAY))
                .append(Component.text(page + "/" + totalPages, NamedTextColor.GOLD))
                .append(Component.text("   ", NamedTextColor.GRAY))
                .append(next);
    }

    private static class MaterialTotals {
        private final ItemStack sampleItem;
        private int amount = 0;
        private double revenue = 0;
        private int purchases = 0;

        private MaterialTotals(ItemStack sampleItem) {
            this.sampleItem = sampleItem;
        }

        private void add(int amount, double price) {
            this.amount += amount;
            this.revenue += price;
            this.purchases++;
        }
    }

    /** Caches a rendered result so {@code /transactions page} can slice it without re-querying. */
    private static class CachedLookup {
        private final String title;
        private final List<Component> lines;

        private CachedLookup(String title, List<Component> lines) {
            this.title = title;
            this.lines = lines;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        if (args[0].equalsIgnoreCase("top")) {
            return filter(args[args.length - 1],
                    List.of("t:1d", "t:7d", "a:sell", "a:buy", "i:", "e:", "u:", "s:value", "s:purchases", "s:quantity", "s:all"));
        }
        return filter(args[args.length - 1],
                List.of("top", "t:1d", "t:7d", "a:sell", "a:buy", "a:barter", "i:", "e:", "u:", "page", "#verbose"));
    }

    private List<String> filter(String partial, Iterable<String> options) {
        String needle = partial.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(option);
            }
        }
        return result;
    }

    /** Registers this handler for the {@code transactions} command declared in plugin.yml. */
    public static void register(Shop plugin) {
        PluginCommand cmd = plugin.getCommand("transactions");
        if (cmd == null) {
            plugin.getLogger().warning("Command 'transactions' is not declared in plugin.yml — /tx will not work.");
            return;
        }
        TransactionCommandHandler handler = new TransactionCommandHandler();
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }
}
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.ShopActionType;
import com.snowgears.shop.util.TransactionLookupFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Command handler for {@code /shoplookup}, allowing operators to look up past shop
 * transactions for a given shop owner. Results are queried asynchronously from the
 * database and displayed using the Adventure API.
 */
public class TransactionCommandHandler extends BukkitCommand {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TextColor HEADER_COLOR = TextColor.color(0x7BFF8B);
    private static final TextColor INFO_COLOR = TextColor.color(0xFFFFFF);
    private static final TextColor ERROR_COLOR = TextColor.color(0xFF5555);

    private final Shop plugin;

    public TransactionCommandHandler(Shop instance) {
        super("shoplookup", "Look up past shop transactions for a shop owner",
                "/shoplookup <ownerName> [days]", java.util.List.of("txlookup"));
        this.setPermission("shop.operator");
        plugin = instance;
        try {
            register();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void register() {
        Bukkit.getCommandMap().register(this.getName(), this);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                    MINI_MESSAGE.deserialize("<red>This command can only be run by a player.</red>")));
            return true;
        }

        if (!player.hasPermission("shop.operator") && !player.isOp()) {
            player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                    MINI_MESSAGE.deserialize("<red>You do not have permission to use this command.</red>")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                    MINI_MESSAGE.deserialize("<gray>Usage: </gray><white>/shoplookup <ownerName> [days]</white>")));
            return true;
        }

        String ownerName = args[0];
        long startTime = 0;
        long endTime = System.currentTimeMillis();

        if (args.length >= 2) {
            try {
                int days = Integer.parseInt(args[1]);
                if (days > 0) {
                    startTime = endTime - (days * 24L * 60L * 60L * 1000L);
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid days, default to all time
            }
        }

        // Resolve owner name to UUID
        UUID ownerUUID = null;
        try {
            ownerUUID = java.util.UUID.fromString(ownerName);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, look up by name -> we still pass null ownerUUID and filter by customer name below
        }

        TransactionLookupFilter filter = TransactionLookupFilter.builder()
                .customerName(ownerUUID == null ? ownerName : null)
                .build();

        plugin.getLogHandler().getShopTransactions(ownerUUID, startTime, endTime, filter,
                records -> {
                    // Schedule UI update on the main thread (sending messages requires main thread on Folia)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (records.isEmpty()) {
                            player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                                    MINI_MESSAGE.deserialize(
                                            "<gray>No transactions found for </gray><white>" +
                                                    ownerName + "</white><gray> in the selected time range.</gray>")));
                            return;
                        }

                        player.sendMessage(Component.empty());
                        player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                                MINI_MESSAGE.deserialize("<green>=== Transactions for " + ownerName +
                                        " (" + records.size() + ") ===</green>")));

                        int shown = 0;
                        int maxShow = 15;
                        for (PlayerTransactionRecord record : records) {
                            if (shown >= maxShow) break;
                            Component line = buildRecordLine(record);
                            player.sendMessage(line);
                            shown++;
                        }

                        if (records.size() > maxShow) {
                            player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                                    MINI_MESSAGE.deserialize(
                                            "<gray>... and " + (records.size() - maxShow) + " more.</gray>")));
                        }

                        player.sendMessage(PlainTextComponentSerializer.plainText().serialize(
                                MINI_MESSAGE.deserialize("<green>=== End of transactions ===</green>")));
                        player.sendMessage(Component.empty());
                    });
                });

        return true;
    }

    private Component buildRecordLine(PlayerTransactionRecord record) {
        String time = new java.text.SimpleDateFormat("MM/dd HH:mm").format(
                new java.util.Date(record.getTimestamp()));
        String type = record.getActionType() != null ? record.getActionType().toString() : "UNKNOWN";
        String itemDesc = record.getItemBase64() != null ? record.getItemBase64() : "unknown item";

        return MINI_MESSAGE.deserialize(
                "<gray>[" + time + "] </gray>" +
                        "<white>" + record.getCustomerName() + "</white> " +
                        "<gray>-> </gray>" +
                        "<white>" + record.getOwnerName() + "</white> " +
                        "<gray>(" + type + ")</gray> " +
                        "<gray>x" + record.getAmount() + " " + itemDesc + " </gray>" +
                        "<gray>@" + record.getPrice() + "</gray>"
        );
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        java.util.List<String> results = new java.util.ArrayList<>();
        if (args.length == 1) {
            // Suggest online player names as shop owner candidates
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                results.add(player.getName());
            }
        }
        return sortedResults(args[args.length - 1], results);
    }

    private java.util.List<String> sortedResults(String arg, java.util.List<String> results) {
        final java.util.List<String> completions = new java.util.ArrayList<>();
        org.bukkit.util.StringUtil.copyPartialMatches(arg, results, completions);
        java.util.Collections.sort(completions);
        results.clear();
        results.addAll(completions);
        return results;
    }
}

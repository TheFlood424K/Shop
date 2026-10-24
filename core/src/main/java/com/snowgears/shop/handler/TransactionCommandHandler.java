package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.Transaction;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TransactionCommandHandler implements CommandExecutor {

    private final Shop plugin;

    public TransactionCommandHandler(Shop instance) {
        this.plugin = instance;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[Shop] This command can only be run as a player.");
            return true;
        }

        Player player = (Player) sender;
        if ((plugin.usePerms() && !player.hasPermission("shop.operator")) || (!plugin.usePerms() && !player.isOp())) {
            ShopMessage.sendMessage("command", "not_authorized", player, null);
            return true;
        }

        if (args.length == 0) {
            // Default to showing player's own transactions
            handleTransactionLookup(player, player.getUniqueId(), 0, 0,
                new TransactionLookupFilter(ShopType.BUY, new HashSet<>(), new HashSet<>(), null, null));
            return true;
        }

        handleTransactionLookupWithArgs(player, args);
        return true;
    }

    private void handleTransactionLookup(Player player, UUID ownerUUID, long startTime, long endTime, TransactionLookupFilter filter) {
        plugin.getLogHandler().getShopTransactions(ownerUUID, startTime, endTime, filter, transactions -> {
            if (transactions.isEmpty()) {
                player.sendMessage(ShopMessage.getUnformattedMessage("command", "transaction_none"));
                return;
            }

            player.sendMessage(ShopMessage.getUnformattedMessage("command", "transaction_header"));
            for (Transaction tx : transactions) {
                String message = ShopMessage.getMessageFromOrders(filter.getAction(), "user", tx.getPrice(), tx.getAmount());
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(message);
                }
            }
            String footer = ShopMessage.getUnformattedMessage("command", "transaction_footer");
            footer = footer.replace("[count]", String.valueOf(transactions.size()));
            player.sendMessage(footer);
        });
    }

    private void handleTransactionLookupWithArgs(Player player, String[] args) {
        UUID ownerUUID = player.getUniqueId(); // Default to player's own shops
        long startTime = 0;
        long endTime = 0;
        ShopType action = ShopType.BUY; // Default action
        Set<Material> includeItems = new HashSet<>();
        Set<Material> excludeItems = new HashSet<>();
        String customerName = null;

        if (args.length >= 1) {
            String ownerArg = args[0];
            if (ownerArg.equalsIgnoreCase("all")) {
                ownerUUID = null; // All owners
            } else {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerArg);
                if (offlinePlayer != null && offlinePlayer.getUniqueId() != null && offlinePlayer.hasPlayedBefore()) {
                    ownerUUID = offlinePlayer.getUniqueId();
                } else if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
                    // Also accept even if never played before (could be valid UUID lookup)
                    try {
                        UUID.fromString(ownerArg);
                        ownerUUID = offlinePlayer.getUniqueId();
                    } catch (IllegalArgumentException ex) {
                        // Try as UUID
                        try {
                            ownerUUID = UUID.fromString(ownerArg);
                        } catch (IllegalArgumentException e) {
                            player.sendMessage("§cInvalid owner: " + ownerArg);
                            return;
                        }
                    }
                } else {
                    try {
                        ownerUUID = UUID.fromString(ownerArg);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§cInvalid owner: " + ownerArg);
                        return;
                    }
                }
            }
        }

        TransactionLookupFilter filter = new TransactionLookupFilter(action, includeItems, excludeItems, customerName, ownerUUID);
        handleTransactionLookup(player, ownerUUID, startTime, endTime, filter);
    }
}

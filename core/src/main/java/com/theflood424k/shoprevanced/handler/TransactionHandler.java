package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.integration.eztax.EzTaxIntegration;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;


public class TransactionHandler {

    private ShopRevanced plugin;
    private ConcurrentHashMap<Location, UUID> ShopRevancedMessageCooldown = new ConcurrentHashMap<>(); //ShopRevanced location, ShopRevanced owner

    public TransactionHandler(ShopRevanced instance) {
        plugin = instance;
    }

    public void executeTransactionFromEvent(PlayerInteractEvent event, AbstractShop shop, boolean fullStackOrder){
        Player player = event.getPlayer();

        if(shop.isPerformingTransaction()) {
            ShopRevancedMessage.sendMessage("interactionIssue", "useShopAlreadyInUse", player, shop);
            event.setCancelled(true);
            return;
        }

        boolean canUseShopInRegion = true;
        try {
            canUseShopInRegion = WorldGuardHook.canUseShop(player, shop.getSignLocation());
        } catch(NoClassDefFoundError e) {}

        //check that player can use the ShopRevanced if it is in a WorldGuard region
        if(!canUseShopInRegion){
            ShopRevancedMessage.sendMessage("interactionIssue", "regionRestriction", player, shop);
            event.setCancelled(true);
            return;
        }

        //delete ShopRevanced if it does not have a chest attached to it
        if(!(plugin.getShopRevancedHandler().isChest(shop.getChestLocation().getBlock()))){
            plugin.getLogger().warning("Deleting shop because chest does not exist! " + shop);
            shop.delete();
            return;
        }

        //do not allow gamble shops to have full stack orders
        if(shop.getType() == ShopType.GAMBLE)
            fullStackOrder = false;

        //player did not click their own ShopRevanced
        if (!shop.getOwnerName().equals(player.getName()) || plugin.getDebug_allowUseOwnShop()) {

            if (plugin.usePerms() && !(player.hasPermission("shop.use."+shop.getType().toString().toLowerCase()) || player.hasPermission("shop.use"))) {
                if (!player.hasPermission("shop.operator")) {
                    ShopRevancedMessage.sendMessage("permission", "use", player, shop);
                    return;
                }
            }
            //for COMBO shops, shops can execute either a BUY or a SELL depending on the side of sign that was clicked
            if(shop.getType() == ShopType.COMBO){
                int clickedSide = UtilMethods.calculateSideFromClickedSign(player, event.getClickedBlock());
                //clicked left side of sign
                if(clickedSide >= 0){
                    if(plugin.inverseComboShops())
                        executeTransactionSequence(player, shop, ShopType.SELL, fullStackOrder);
                    else
                        executeTransactionSequence(player, shop, ShopType.BUY, fullStackOrder);
                }
                //clicked right side of sign
                else{
                    if(plugin.inverseComboShops())
                        executeTransactionSequence(player, shop, ShopType.BUY, fullStackOrder);
                    else
                        executeTransactionSequence(player, shop, ShopType.SELL, fullStackOrder);
                }
            }
            else {
                executeTransactionSequence(player, shop, shop.getType(), fullStackOrder);
            }
        } else {
            ShopRevancedMessage.sendMessage("interactionIssue", "useOwnShop", player, shop);
            shop.sendEffects(false, player);
        }
        event.setCancelled(true);
    }

    private void executeTransactionSequence(Player player, AbstractShop shop, ShopType actionType, boolean fullStackOrder){
        Transaction transaction = new Transaction(player, shop, actionType);

        // Reentrancy guard: mark the shop as performing a transaction BEFORE verify() is called.
        // verify() is the only place that checks shop.isPerformingTransaction(), so setting the
        // flag after verify() (inside Transaction.execute()) made the guard dead code: it could
        // only ever fire for re-entry from inside execute() itself, which never recurses.
        // The flag must be set before verify() so a second transaction on the same shop is blocked,
        // but verify() must NOT reject the transaction that set the flag (it tracks ownership).
        // Reset the flag in a finally block.
        shop.setPerformingTransaction(true);
        transaction.setGuardOwner(true);
        try {
            // Set the desired purchase amount if we are a full stack order
            if (fullStackOrder) {
                // Use either 64 (Minecraft's standard "full stack") or the ShopRevanced's amount, whichever is larger
                // This ensures players get 64 items when buying from shops with smaller default amounts
                // But still get the full amount from shops configured to sell larger quantities
                transaction.negotiatePurchase(Math.max(shop.getAmount(), 64));
            } else {
                transaction.negotiatePurchase();
            }

            // Calculate tax for BUY actions before verification so the funds check
            // uses the total price (base + tax). ezTax is a soft integration: when it
            // is unavailable or disabled, calculateTax returns 0.0 and checkout is
            // unaffected (graceful fallback).
            if (actionType == ShopType.BUY) {
                EzTaxIntegration ezTax = plugin.getEzTaxIntegration();
                if (ezTax != null) {
                    double basePrice = transaction.getPrice();
                    int amount = transaction.getAmount();
                    double taxAmount = ezTax.calculateTax(player, shop, amount, basePrice);
                    transaction.setTaxAmount(taxAmount);
                }
            }

            // Verify the transaction is possible
            TransactionError issue = transaction.verify();
            // If it is possible, go ahead and execute it, extra check just in case there is an issue (shouldn't ever happen, but who knows)
            if (issue == TransactionError.NONE) {
                // Display the tax breakdown to the customer before money moves
                if (actionType == ShopType.BUY) {
                    EzTaxIntegration ezTax = plugin.getEzTaxIntegration();
                    if (ezTax != null) {
                        ezTax.displayTaxBreakdown(
                                player,
                                transaction.getPrice(),
                                transaction.getTaxAmount(),
                                transaction.getTotalPrice());
                    }
                }
                issue = transaction.execute();
            }

            // Record collected tax for ezTax filing/remittance after a successful sale
            if (issue == TransactionError.NONE && actionType == ShopType.BUY) {
                EzTaxIntegration ezTax = plugin.getEzTaxIntegration();
                if (ezTax != null) {
                    ezTax.recordTax(
                            player,
                            shop,
                            transaction.getPrice(),
                            transaction.getTaxAmount());
                }
            }

            // If there was an issue with the transaction, send the error message and bail out early
            if (issue != TransactionError.NONE) {
                //there was an issue when checking transaction, send reason to player
                this.sendErrorMessage(player, shop, actionType, transaction);
                return;
            }

            //the transaction has finished and the exchange event has not been cancelled
            sendExchangeMessagesAndLog(shop, player, actionType, transaction);
            shop.sendEffects(true, player);
        } finally {
            transaction.setGuardOwner(false);
            shop.setPerformingTransaction(false);
        }
    }

    private void sendErrorMessage(Player player, AbstractShop shop, ShopType actionType, Transaction transaction) {
        String message = null;
        switch (transaction.getError()) {
            case INSUFFICIENT_FUNDS_SHOP:
                if (!shop.isAdmin()) {
                    Player owner = shop.getOwner().getPlayer();
                    //the ShopRevanced owner is online
                    if (owner != null && notifyOwner(shop)) {
                        ShopRevancedGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_STOCK);

                        if (guiIcon != null && guiIcon == ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) {
                            ShopRevancedMessage.sendMessage(actionType.toString(), "ownerNoStock", owner, shop);
                        }
                    }
                }
                message = ShopRevancedMessage.getUnformattedMessage(actionType.toString(), "shopNoStock");
                break;
            case INSUFFICIENT_FUNDS_PLAYER:
                message = ShopRevancedMessage.getUnformattedMessage(actionType.toString(), "playerNoStock");
                break;
            case INVENTORY_FULL_SHOP:
                if (!shop.isAdmin()) {
                    Player owner = shop.getOwner().getPlayer();
                    //the ShopRevanced owner is online
                    if (owner != null && notifyOwner(shop)) {
                        ShopRevancedGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_STOCK);

                        if (guiIcon != null && guiIcon == ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) {
                            ShopRevancedMessage.sendMessage(actionType.toString(), "ownerNoSpace", owner, shop);
                        }
                    }
                }
                message = ShopRevancedMessage.getUnformattedMessage(actionType.toString(), "shopNoSpace");
                break;
            case INVENTORY_FULL_PLAYER:
                message = ShopRevancedMessage.getUnformattedMessage(actionType.toString(), "playerNoSpace");
                break;
        }

        // Since there was an error during the transaction, send the message, then exit the transaction early.
        if (message != null && !message.isEmpty())
            ShopRevancedMessage.sendMessage(message, player, shop);
        shop.sendEffects(false, player);
    }

    private void sendExchangeMessagesAndLog(AbstractShop shop, Player player, ShopType transactionType, Transaction transaction) {

        double price = transaction.getPrice();
        String message = ShopRevancedMessage.getMessageFromOrders(transactionType, "user", price, transaction.getAmount());

        ShopRevancedGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_SALE_USER);
        if(guiIcon != null && guiIcon == ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON) {
            if(message != null && !message.isEmpty()) {
                ShopRevancedMessage.sendMessage(message, player, shop);
            }
        }

        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
        if ((owner != null) && (!shop.isAdmin())) {
            message = ShopRevancedMessage.getMessageFromOrders(transactionType, "owner", price, transaction.getAmount());

            guiIcon = plugin.getGuiHandler().getIconFromOption(owner, PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            if(guiIcon != null && guiIcon == ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON) {
                if(message != null && !message.isEmpty())
                    ShopRevancedMessage.sendMessage(message, owner, player, shop);
            }
        }

        int amount = transaction.getAmount();

        plugin.getLogHandler().logTransaction(player, shop, transactionType, price, amount);
    }

    private boolean notifyOwner(final AbstractShop shop){
        if(shop.isAdmin())
            return false;
        if(ShopRevancedMessageCooldown.containsKey(shop.getSignLocation()))
            return false;
        else{
            ShopRevancedMessageCooldown.put(shop.getSignLocation(), shop.getOwnerUUID());

            plugin.getFoliaLib().getScheduler().runLater(new BukkitRunnable() {
                @Override
                public void run() {
                    if(shop != null){
                        if(ShopRevancedMessageCooldown.containsKey(shop.getSignLocation())){
                            ShopRevancedMessageCooldown.remove(shop.getSignLocation());
                        }
                    }
                    //TODO if ShopRevanced is null, should you clear the entire cooldown list so that that location isn't messed up?
                }
            }, 2400); //make cooldown 2 minutes
        }
        return true;
    }
}









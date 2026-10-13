package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.GambleShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class Transaction {
    boolean TX_DEBUG_LOGGING = false;

    private Player player;
    private AbstractShop shop;
    private ShopType transactionType;

    private TransactionError error;

    // The buyer in the transaction
    private TransactionParty buyer;
    // The seller in the transaction
    private TransactionParty seller;

    // The original price of the transaction in currency (single qty)
    private double originalPrice;
    // The current price of the tx, might be a higher or lower quantity than originalPrice
    private double price;

    // The item that is being sold in the transaction
    private ItemStack itemBeingSold;
    // The original amount of the item being sold (single qty)
    private int originalAmountBeingSold;
    // The amount of the item being sold in the tx, might be a higher or lower quantity than originalAmountBeingSold
    private int amountBeingSold;

    public Transaction(Player player, AbstractShop shop, ShopType transactionType) {
        this.error = null;
        if (!TX_DEBUG_LOGGING) { TX_DEBUG_LOGGING = Shop.getPlugin().getDebug_transactionDebugLogs(); }

        this.player = player;
        this.shop = shop;
        this.transactionType = transactionType;

        this.price = shop.getPrice();
        this.itemBeingSold = shop.getItemStack();
        this.amountBeingSold = shop.getAmount();

        if (shop instanceof ComboShop) {
            // From players perspective, they are selling to the shop
            if (transactionType == ShopType.BUY) {
                this.buyer = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());
                this.seller = new TransactionParty(true, false, player, player.getInventory());
                // Player is selling to shop, so shop pays the buy price
                this.price = ((ComboShop)shop).getPriceBuy();
            } 
            // From players perspective, they are buying from the shop
            else if (transactionType == ShopType.SELL) {
                this.buyer = new TransactionParty(true, false, player, player.getInventory());
                this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());
                // Player is buying from shop, so player pays the sell price
                this.price = ((ComboShop)shop).getPriceSell();
            }
        }
        else if (transactionType == ShopType.GAMBLE) {
            this.buyer = new TransactionParty(true, false, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());

            ((GambleShop)shop).setGambleItem();
            this.itemBeingSold = ((GambleShop) shop).getGambleItem();
            this.amountBeingSold = ((GambleShop) shop).getGambleItem().getAmount();
        }
        else if (shop.getType() == ShopType.BARTER) {
            // Player is buying from the shop!
            // The buyer is going to use an item for their currency/payment
            this.buyer = new TransactionParty(true, false, player, player.getInventory(), shop.getSecondaryItemStack());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory(), shop.getSecondaryItemStack());
        }
        else if (transactionType == ShopType.BUY) {
            // Shop is buying from the player
            this.buyer = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());
            this.seller = new TransactionParty(true, false, player, player.getInventory());
        }
        else if (transactionType == ShopType.SELL) {
            // Shop is selling to the player
            this.buyer = new TransactionParty(true, false, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());
        }

        // Store the original values for the price/amount that comes from the shop directly
        // the price and amountBeingSold variables will change if there is a higher or lower qty being transacted
        // i.e. a partial sale, or a sale of a full stack of items
        this.originalPrice = this.price;
        this.originalAmountBeingSold = this.amountBeingSold;
    }

    public void negotiatePurchase() {
        this.negotiatePurchase(-1);
    }

    // Calculate the maximum amount the transaction can spend/purchase
    public void negotiatePurchase(int desiredAmount) {
        // Don't perform this processing if we are a gamble shop!
        if (shop.getType() == ShopType.GAMBLE) { return; }
        // Handle free transactions
        if (shop.getPrice() == 0) {
            this.price = 0;
            this.amountBeingSold = this.shop.getAmount();
            return;
        }

        PriceNegotiator negotiator = new PriceNegotiator(
            TX_DEBUG_LOGGING, 
            this.originalPrice, 
            this.originalAmountBeingSold, 
            Shop.getPlugin().getAllowFractionalCurrency()
        );

        negotiator.negotiatePurchase(
                Shop.getPlugin().getAllowPartialSales(),
                this.buyer.getAvailableFunds(),
                this.seller.getInventoryQuantity(this.itemBeingSold),
                desiredAmount
        );

        this.amountBeingSold = negotiator.getAmountBeingSold();
        this.price = negotiator.getPrice();

        if (TX_DEBUG_LOGGING) {
            System.out.println("-* amountBeingSold: " + this.amountBeingSold);
            System.out.println("-* price: " + this.price);
            System.out.println("-* originalAmountBeingSold: " + this.originalAmountBeingSold);
            System.out.println("-* originalPrice: " + this.originalPrice);
        }
    }

    // Verify there are no errors with the transaction, note, you must run negotiatePurchase before verifying!
    public TransactionError verify() {
        // Check if the transaction has already been cancelled
        if (this.error == TransactionError.CANCELLED) { return this.error; }

        // Don't process another transaction if there is one in progress!
        if (shop.isPerformingTransaction()) { return this.setError(TransactionError.CANCELLED); }

        // Check if the buyer has enough funds to pay for the tx
        if (this.buyer.getAvailableFunds() < this.price) {
            // Failed Verification: The buyer does not have enough funds to pay for the transaction!
            if (this.buyer.isPlayer()) {
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER);
            } else {
                // Failed Verification: The buyer does not have enough funds to pay for the transaction!
                shop.updateStock(); // Update sign to show out of stock in case we were out of sync and the shop showed as in-stock
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP);
            }
        }

        // Check if seller has enough items to sell
        if (this.seller.getInventoryQuantity(this.itemBeingSold) < this.amountBeingSold) {
            // Failed Verification: There was not enough items in the sellers inventory to cover the transaction
            if (this.seller.isPlayer()) {
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER);
            } else {
                shop.updateStock(); // Update sign to show out of stock in case we were out of sync and the shop showed as in-stock
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP);
            }
        }

        // Create the item being sold and set the amount we are selling in the tx
        ItemStack itemToBeAdded = this.itemBeingSold.clone();
        itemToBeAdded.setAmount(this.amountBeingSold);
        // Check if the buyer has inventory space to receive the item
        if (!this.buyer.hasRoomForItem(itemToBeAdded)) {
            // Failed Verification: The buyer does not have space to receive the item being bought
            if (this.buyer.isPlayer()) { return this.setError(TransactionError.INVENTORY_FULL_PLAYER); }
            else {  return this.setError(TransactionError.INVENTORY_FULL_SHOP); }
        }

        // Check if the seller can accept the payment amount, aka if they have space for a currency item to be placed into their inventory.
        // For shop sellers (non-player), use the "after-removal" check: the items being sold will be removed first,
        // potentially freeing up space for the incoming payment (fixes issue #45 - full shops).
        boolean sellerCanAcceptPayment;
        if (!this.seller.isPlayer()) {
            ItemStack itemToRemove = this.itemBeingSold.clone();
            itemToRemove.setAmount(this.amountBeingSold);
            sellerCanAcceptPayment = this.seller.canAcceptPaymentAfterRemoval(this.price, itemToRemove);
        } else {
            sellerCanAcceptPayment = this.seller.canAcceptPayment(this.price);
        }
        if (!sellerCanAcceptPayment) {
            // Failed Verification: The seller does not have space to receive the payment
            if (this.seller.isPlayer()) { return this.setError(TransactionError.INVENTORY_FULL_PLAYER); }
            else { return this.setError(TransactionError.INVENTORY_FULL_SHOP); }
        }

        // Check if the negotiation resulted in invalid values (-1, indicating insufficient inventory/funds)
        // This check comes after the specific checks above, so it handles cases where PriceNegotiator
        // determined the transaction was invalid for other reasons (e.g., partial sales restrictions)
        // Allow (price == 0) for "free" price transactions
        if (this.price < 0 || this.amountBeingSold <= 0) {
            // Failed Verification: The negotiation determined the transaction is not possible
            shop.updateStock(); // Update sign to show out of stock in case we were out of sync
            return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP);
        }

        // Check if any other plugins want to cancel the transaction
        double playerCurrency = this.buyer.isPlayer() ? this.buyer.getAvailableFunds() : this.price;
        String playerCurrencyName = Shop.getPlugin().getCurrencyType().name();
        String shopCurrency = Shop.getPlugin().getCurrencyType().name();
        ItemStack playerItem = this.buyer.isPlayer() ? new ItemStack(Material.AIR) : this.itemBeingSold;
        ItemStack shopItem = this.itemBeingSold.clone();
        shopItem.setAmount(this.amountBeingSold);

        PlayerExchangeShopEvent e = new PlayerExchangeShopEvent(player, shop, playerCurrency, playerCurrencyName, shopCurrency, playerItem, shopItem);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled()) {
            return this.setError(TransactionError.CANCELLED);
        }

        if (TX_DEBUG_LOGGING) { System.out.println("Transaction Verified Successfully!"); }
        return this.setError(TransactionError.NONE);
    }

    /**
     * Verifies the negotiated transaction, then transfers payment and items between the buyer and seller.
     * Item-currency transactions restore both inventories from snapshots if any transfer fails.
     * A refused economy deposit leaves stock untouched and attempts to refund the buyer.
     *
     * @return {@link TransactionError#NONE} on success, or the error that prevented completion
     */
    public TransactionError execute() {
        // Check to see if we had any errors, you must do this before performing the transaction!
        if (this.verify() != TransactionError.NONE) { return this.error; }

        // Perform the transaction, we are fully verified, so we can just directly run through the transaction
        ItemStack itemSold = this.itemBeingSold.clone();
        itemSold.setAmount(this.amountBeingSold);

        if (this.buyer.usesItemCurrency()) {
            TransactionError transferError = this.transferItemCurrency(itemSold);
            if (transferError != TransactionError.NONE) { return this.setError(transferError); }
        } else {
            // Step 1: Deduct the payment from the buyer.
            boolean paymentSuccessful = this.buyer.deductFunds(this.price);
            if (!paymentSuccessful) {
                if (this.buyer.isPlayer()) { return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER); }
                else { return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP); }
            }

            // Vault and experience deposits do not need inventory space. Accept payment before
            // removing stock so a refused deposit cannot require an item refund.
            if (!this.seller.depositFunds(this.price)) {
                boolean buyerRefunded = this.buyer.depositFunds(this.price);
                if (!buyerRefunded) {
                    Shop.getPlugin().getLogger().severe("Transaction refund failed for shop "
                            + shop.getSignLocation() + ", buyerUUID=" + player.getUniqueId()
                            + ", price=" + price + ", itemType=" + itemSold.getType()
                            + ", quantity=" + itemSold.getAmount()
                            + ". Manual intervention may be required.");
                }
                return this.setError(this.seller.isPlayer() ? TransactionError.INSUFFICIENT_FUNDS_SHOP
                        : TransactionError.INVENTORY_FULL_SHOP);
            }
            this.seller.deductItem(itemSold);
            this.buyer.depositItem(itemSold);
        }

        // Special handling for Gamble shops!
        if (shop.getType() == ShopType.GAMBLE) {
            // Cycle display to show won item
            if (shop instanceof GambleShop) { ((GambleShop) shop).shuffleGambleItem(player); }
            // Exit early
            return this.setError(TransactionError.NONE);
        }

        // Recompute stock from the inventory. This is the authoritative update: an incremental
        // counter cannot stand in for it, because it would have to account for every way a chest
        // can change (hoppers, player edits, other plugins) to stay correct.
        shop.updateStock();

        // Successful!
        return this.setError(TransactionError.NONE);
    }

    /**
     * Transfers item payment and stock synchronously, restoring both inventory snapshots if a
     * transfer fails or throws. No transaction events run between the snapshots and commit.
     * Restoration writes slots directly so recovery cannot overflow through a refund deposit.
     *
     * @param itemSold the item stack and quantity to deliver to the buyer
     * @return {@link TransactionError#NONE} on commit, or the error from the failed transfer
     */
    private TransactionError transferItemCurrency(ItemStack itemSold) {
        ItemStack[] buyerContents = this.buyer.snapshotInventory();
        ItemStack[] sellerContents = this.seller.snapshotInventory();
        boolean committed = false;
        try {
            if (!this.buyer.deductFunds(this.price)) {
                return this.buyer.isPlayer() ? TransactionError.INSUFFICIENT_FUNDS_PLAYER
                        : TransactionError.INSUFFICIENT_FUNDS_SHOP;
            }
            if (!this.seller.deductItem(itemSold)) {
                return this.seller.isPlayer() ? TransactionError.INSUFFICIENT_FUNDS_PLAYER
                        : TransactionError.INSUFFICIENT_FUNDS_SHOP;
            }
            if (!this.seller.depositFunds(this.price)) {
                return this.seller.isPlayer() ? TransactionError.INSUFFICIENT_FUNDS_SHOP
                        : TransactionError.INVENTORY_FULL_SHOP;
            }
            if (!this.buyer.depositItem(itemSold)) {
                return this.buyer.isPlayer() ? TransactionError.INVENTORY_FULL_PLAYER
                        : TransactionError.INVENTORY_FULL_SHOP;
            }
            committed = true;
            return TransactionError.NONE;
        } finally {
            if (!committed) {
                try {
                    this.seller.restoreInventory(sellerContents);
                } finally {
                    this.buyer.restoreInventory(buyerContents);
                }
            }
        }
    }

    public Player getPlayer() {
        return player;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public ShopType getType() {
        return transactionType;
    }

    public ItemStack getItemStack() {
        ItemStack item = this.itemBeingSold.clone();
        item.setAmount(this.amountBeingSold);
        return item;
    }

    public int getAmount() {
        return this.amountBeingSold;
    }

    public TransactionError getError(){
        return error;
    }

    public double getPrice(){
        return price;
    }

    // Gets a Map of both items in the transaction, for logging purposes
    public Map<ItemStack, Integer> getItems() {
        Map<ItemStack, Integer> items = new HashMap<>();
        items.put(this.itemBeingSold, this.amountBeingSold);
        if (shop.getSecondaryItemStack() != null) { items.put(shop.getSecondaryItemStack(), (int) this.price); }
        return items;
    }

    public TransactionError setError(TransactionError error){
        if (TX_DEBUG_LOGGING) { System.out.println("TX setError: TransactionError." + error.toString().toUpperCase()); }
        this.error = error;
        return this.error;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "type=" + transactionType +
                (price != originalPrice ? ", originalPrice=" + originalPrice : "") +
                (amountBeingSold != originalAmountBeingSold ? ", originalAmountBeingSold=" + originalAmountBeingSold : "") +
                ", price=" + price +
                ", amountBeingSold=" + amountBeingSold +
                ", itemBeingSold=" + itemBeingSold +
                ", buyer=" + buyer +
                ", seller=" + seller +
                ", player=" + player +
                ", shop=" + shop +
                '}';
    }
}

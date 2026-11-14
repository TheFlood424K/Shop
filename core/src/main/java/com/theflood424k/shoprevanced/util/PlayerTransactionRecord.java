package com.snowgears.shop.util;

import java.util.Objects;

/**
 * A single record of a player's past shop transaction, reconstructed from the
 * shop_action / shop_transaction database tables.
 */
public class PlayerTransactionRecord {

    private final int transactionId;
    private final long timestamp;
    private final String customerName;
    private final String ownerName;
    private final ShopActionType actionType;
    private final double price;
    private final int amount;
    private final String itemBase64;
    private final String barterItemBase64;

    public PlayerTransactionRecord(int transactionId, long timestamp, String customerName,
                                   String ownerName, ShopActionType actionType, double price,
                                   int amount, String itemBase64, String barterItemBase64) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.customerName = customerName;
        this.ownerName = ownerName;
        this.actionType = actionType;
        this.price = price;
        this.amount = amount;
        this.itemBase64 = itemBase64;
        this.barterItemBase64 = barterItemBase64;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public ShopActionType getActionType() {
        return actionType;
    }

    public double getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public String getItemBase64() {
        return itemBase64;
    }

    public String getBarterItemBase64() {
        return barterItemBase64;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerTransactionRecord that = (PlayerTransactionRecord) o;
        return transactionId == that.transactionId && timestamp == that.timestamp
                && Double.compare(that.price, price) == 0 && amount == that.amount
                && Objects.equals(customerName, that.customerName)
                && Objects.equals(ownerName, that.ownerName)
                && actionType == that.actionType
                && Objects.equals(itemBase64, that.itemBase64)
                && Objects.equals(barterItemBase64, that.barterItemBase64);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, timestamp, customerName, ownerName, actionType,
                price, amount, itemBase64, barterItemBase64);
    }

    @Override
    public String toString() {
        return "PlayerTransactionRecord{" +
                "transactionId=" + transactionId +
                ", timestamp=" + timestamp +
                ", customerName='" + customerName + '\'' +
                ", ownerName='" + ownerName + '\'' +
                ", actionType=" + actionType +
                ", price=" + price +
                ", amount=" + amount +
                ", itemBase64='" + (itemBase64 != null ? itemBase64.substring(0, Math.min(itemBase64.length(), 20)) + "..." : "null") + '\'' +
                ", barterItemBase64='" + (barterItemBase64 != null ? barterItemBase64.substring(0, Math.min(barterItemBase64.length(), 20)) + "..." : "null") + '\'' +
                '}';
    }
}

package com.snowgears.shop.util;

import com.snowgears.shop.shop.ShopType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Date;
import java.util.UUID;

public class PlayerTransactionRecord {

    private final Date timestamp;
    private final ShopType transactionType;
    private final double price;
    private final int amount;
    private final ItemStack item;
    private final ItemStack barterItem;
    private final UUID customerUUID;
    private final UUID shopOwnerUUID;
    private final Location shopLocation;

    public PlayerTransactionRecord(Date timestamp, ShopType transactionType, double price, int amount, ItemStack item, ItemStack barterItem, UUID customerUUID, UUID shopOwnerUUID, Location shopLocation) {
        this.timestamp = timestamp;
        this.transactionType = transactionType;
        this.price = price;
        this.amount = amount;
        this.item = item;
        this.barterItem = barterItem;
        this.customerUUID = customerUUID;
        this.shopOwnerUUID = shopOwnerUUID;
        this.shopLocation = shopLocation;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public ShopType getTransactionType() {
        return transactionType;
    }

    public double getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public ItemStack getItem() {
        return item;
    }

    public ItemStack getBarterItem() {
        return barterItem;
    }

    public UUID getCustomerUUID() {
        return customerUUID;
    }

    public UUID getShopOwnerUUID() {
        return shopOwnerUUID;
    }

    public Location getShopLocation() {
        return shopLocation;
    }
}
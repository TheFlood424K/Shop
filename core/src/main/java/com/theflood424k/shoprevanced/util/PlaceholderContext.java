package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderContext {
    private AbstractShop shop;
    private Player player;
    private OfflinePlayer offlinePlayer;
    private boolean forSign = false;
    private ItemStack item = null;
    private ItemStack barterItem = null;
    private ShopRevancedCreationProcess process;
    private OfflineTransactions offlineTransactions;
    private Location location;
        private int count;

    // Create empty Placeholder Context
    public PlaceholderContext() { }

    public void setShop(AbstractShop shop) {
        this.shop = shop;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }
    public void setOfflinePlayer(OfflinePlayer offlinePlayer) {
        this.offlinePlayer = offlinePlayer;
    }

    public Player getPlayer() {
        return player;
    }
    public OfflinePlayer getOfflinePlayer() {
        return offlinePlayer;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setBarterItem(ItemStack barterItem) { this.barterItem = barterItem; }

    public ItemStack getBarterItem() { return barterItem; }

    public void setProcess(ShopRevancedCreationProcess process) {
        this.process = process;
    }

    public ShopRevancedCreationProcess getProcess() {
        return process;
    }

    public void setOfflineTransactions(OfflineTransactions offlineTransactions) {
        this.offlineTransactions = offlineTransactions;
    }

    public OfflineTransactions getOfflineTransactions() {
        return offlineTransactions;
    }

        public void setCount(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

    public Location getLocation() { return location; }

    public void setLocation(Location location) { this.location = location; }

    public void setForSign(boolean forSign) {
        this.forSign = forSign;
    }

    public boolean isForSign() {
        return forSign;
    }
}









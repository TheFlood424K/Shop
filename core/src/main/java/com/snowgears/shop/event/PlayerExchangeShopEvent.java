package com.snowgears.shop.event;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class PlayerExchangeShopEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final AbstractShop shop;
    private boolean cancelled;
    private final double playerCurrency;
    private final String playerCurrencyName;
    private final String shopCurrency;
    private final ItemStack playerItem;
    private final ItemStack shopItem;

    public PlayerExchangeShopEvent(Player p, AbstractShop s, double playerCurrency, String playerCurrencyName, String shopCurrency, ItemStack playerItem, ItemStack shopItem) {
        player = p;
        shop = s;
        this.playerCurrency = playerCurrency;
        this.playerCurrencyName = playerCurrencyName;
        this.shopCurrency = shopCurrency;
        this.playerItem = playerItem;
        this.shopItem = shopItem;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public ShopType getType() {
        return shop.getType();
    }

    public double getPlayerCurrency() {
        return playerCurrency;
    }

    public String getPlayerCurrencyName() {
        return playerCurrencyName;
    }

    public String getShopCurrency() {
        return shopCurrency;
    }

    public ItemStack getPlayerItem() {
        return playerItem;
    }

    public ItemStack getShopItem() {
        return shopItem;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean set) {
        cancelled = set;
    }
}

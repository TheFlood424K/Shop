package com.snowgears.shop.event;

import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player attempts to open a shop's container by interacting with the chest/barrel/etc.
 *
 * This event allows external listeners (e.g., protection plugins) to request that the container
 * be opened directly for the player instead of executing the shop action. Listeners can also
 * cancel the event to hard-deny access.
 */
public class PlayerOpenShopEvent extends Event implements Cancellable {

    public enum OpenMode {
        SHOP_ACTION,
        OPEN_CONTAINER
    }

    public enum OpenTarget {
        CHEST,
        SIGN
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AbstractShop shop;
    private final OpenTarget target;
    private boolean cancelled;
    private OpenMode mode;

    public PlayerOpenShopEvent(Player player, AbstractShop shop, OpenTarget target, OpenMode defaultMode) {
        this.player = player;
        this.shop = shop;
        this.target = target;
        this.mode = defaultMode;
    }

    public Player getPlayer() {
        return player;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public OpenTarget getTarget() {
        return target;
    }

    public OpenMode getMode() {
        return mode;
    }

    public void setMode(OpenMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}



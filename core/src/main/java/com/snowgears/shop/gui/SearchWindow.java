package com.snowgears.shop.gui;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;

import java.util.UUID;

/**
 * GUI window for searching shops.
 * <p>
 * This window is used as a placeholder/instruction window for the shop search
 * functionality. When opened from the home window, the player is placed into
 * creative selection mode to choose an item to search for.
 */
public class SearchWindow extends ShopGuiWindow {

    public SearchWindow(UUID player){
        super(player);
        this.title = "Search";
        this.page = Bukkit.createInventory(null, InventoryType.ANVIL, "Search");
        initInvContents();
    }

    @Override
    protected void initInvContents(){/* no-op */}

    @Override
    protected void makeMenuBarUpper(){ /* no-op */}

    @Override
    protected void makeMenuBarLower(){ /* no-op */}
}


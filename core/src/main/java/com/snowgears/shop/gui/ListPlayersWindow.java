package com.snowgears.shop.gui;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Displays a list of all shop owners in a paginated GUI window.
 *
 * Performance improvement: Instead of getting all player heads and sorting them,
 * we now get shop owner UUIDs, sort those by player name (more efficient),
 * and only convert to ItemStacks for the subset we need to display.
 */
public class ListPlayersWindow extends ShopGuiWindow {

    public ListPlayersWindow(UUID player){
        super(player);
        this.title = Shop.getPlugin().getGuiHandler().getTitle(ShopGuiHandler.GuiTitle.LIST_PLAYERS);
        this.page = Bukkit.createInventory(null, INV_SIZE, title);
        initInvContents();
    }

    @Override
    protected void initInvContents(){
        super.initInvContents();
        this.clearInvBody();

        makeMenuBarUpper();
        makeMenuBarLower();

        // Performance improvement: Get UUIDs first, sort by player name, then convert to ItemStacks
        List<UUID> shopOwnerUUIDs = Shop.getPlugin().getShopHandler().getShopOwnerUUIDs();

        // Sort UUIDs by player name (more efficient than sorting ItemStacks)
        Collections.sort(shopOwnerUUIDs, Comparator.comparing(uuid -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            return (offlinePlayer.getName() != null) ? offlinePlayer.getName() : "Unknown";
        }));

        int startIndex = pageIndex * 36; // 36 items is a full page in the inventory
        ItemStack icon;
        boolean added = true;

        for (int i = startIndex; i < shopOwnerUUIDs.size(); i++) {
            UUID uuid = shopOwnerUUIDs.get(i);
            icon = Shop.getPlugin().getGuiHandler().getPlayerHeadIcon(uuid);

            if(!this.addIcon(icon)){
                added = false;
                break;
            }
        }

        if(added){
            page.setItem(53, null);
        }
        else{
            page.setItem(53, this.getNextPageIcon());
        }
    }
}
package com.theflood424k.shoprevanced.gui;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ComparatorShopItemNameLow;
import com.theflood424k.shoprevanced.util.PlayerNameCache;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ListPlayerShopsWindow extends ShopGuiWindow {

    private UUID playerToList;

    public ListPlayerShopsWindow(UUID player, UUID playerToList){
        super(player);

        if(ShopRevanced.getPlugin().getShopRevancedHandler().getAdminUUID().equals(playerToList)) {
            ItemStack is = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.LIST_PLAYER_ADMIN, ShopRevanced.getPlugin().getShopRevancedHandler().getAdminUUID(), null);
            this.title = is.getItemMeta().getDisplayName();
        }
        else {
            this.title = PlayerNameCache.getName(playerToList);
            if (this.title == null){
                String shortId = playerToList.toString();
                shortId = shortId.substring(0,3) + "..." + shortId.substring(shortId.length()-3);
                this.title = "Unknown Player (" + shortId + ")";
            }
        }

        this.page = Bukkit.createInventory(null, INV_SIZE, this.title);
        this.playerToList = playerToList;
        initInvContents();
    }

    @Override
    protected void initInvContents() {
        super.initInvContents();
        this.clearInvBody();

        makeMenuBarUpper();
        makeMenuBarLower();

        List<AbstractShop> shops = ShopRevanced.getPlugin().getShopRevancedHandler().getShops(playerToList);
        Collections.sort(shops, new ComparatorShopItemNameLow());

        int startIndex = pageIndex * 36; //36 items is a full page in the inventory
        ItemStack icon;
        boolean added = true;

        for (int i=startIndex; i< shops.size(); i++) {
            AbstractShop shop = shops.get(i);
            icon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.LIST_SHOP, null, shop);

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








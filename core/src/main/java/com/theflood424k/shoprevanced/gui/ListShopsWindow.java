package com.theflood424k.shoprevanced.gui;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ListShopsWindow extends ShopGuiWindow {

    private List<AbstractShop> allShops;

    public ListShopsWindow(UUID player){
        super(player);
        //TODO save a list of all shops to a collection when opening this window for you to modify with filters
        this.title = ShopRevanced.getPlugin().getGuiHandler().getTitle(ShopRevancedGuiHandler.GuiTitle.LIST_SHOPS);
        this.page = Bukkit.createInventory(null, INV_SIZE, this.title);
        allShops = ShopRevanced.getPlugin().getShopRevancedHandler().getAllShops();
        initInvContents();
    }

    @Override
    protected void initInvContents() {
        super.initInvContents();
        this.clearInvBody();

        makeMenuBarUpper();
        makeMenuBarLower();

        //first do ShopRevanced type filtering
        ShopRevancedGuiHandler.GuiIcon guiFilterTypeIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_FILTER_SHOP_TYPE);
        List<AbstractShop> filteredShops;
        switch(guiFilterTypeIcon){
            case MENUBAR_FILTER_TYPE_ALL:
                filteredShops = allShops;
                break;
            case MENUBAR_FILTER_TYPE_SELL:
                filteredShops = allShops.stream()
                        .filter(ShopRevanced -> (ShopRevanced.getType() == ShopType.SELL || ShopRevanced.getType() == ShopType.COMBO)).collect(Collectors.toList());
                break;
            case MENUBAR_FILTER_TYPE_BUY:
                filteredShops = allShops.stream()
                        .filter(ShopRevanced -> (ShopRevanced.getType() == ShopType.BUY || ShopRevanced.getType() == ShopType.COMBO)).collect(Collectors.toList());
                break;
            case MENUBAR_FILTER_TYPE_BARTER:
                filteredShops = allShops.stream()
                        .filter(ShopRevanced -> ShopRevanced.getType() == ShopType.BARTER).collect(Collectors.toList());
                break;
            case MENUBAR_FILTER_TYPE_GAMBLE:
                filteredShops = allShops.stream()
                        .filter(ShopRevanced -> ShopRevanced.getType() == ShopType.GAMBLE).collect(Collectors.toList());
                break;
            default:
                filteredShops = new ArrayList<>();
                break;
        }

        //first do ShopRevanced type filtering
        ShopRevancedGuiHandler.GuiIcon guiFilterStockIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_FILTER_SHOP_STOCK);
        switch(guiFilterStockIcon){
            case MENUBAR_FILTER_STOCK_IN:
                filteredShops = filteredShops.stream()
                        .filter(shop -> (shop.getStock() > 0)).collect(Collectors.toList());
                break;
            case MENUBAR_FILTER_STOCK_OUT:
                filteredShops = filteredShops.stream()
                        .filter(shop -> (shop.getStock() <= 0)).collect(Collectors.toList());
                break;
            default:
                break;
        }

        //now do sorting
        ShopRevancedGuiHandler.GuiIcon guiSortIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_SORT);
        switch (guiSortIcon){
            case MENUBAR_SORT_NAME_HIGH:
                Collections.sort(filteredShops, new ComparatorShopItemNameHigh());
                break;
            case MENUBAR_SORT_PRICE_LOW:
                Collections.sort(filteredShops, new ComparatorShopPriceLow());
                break;
            case MENUBAR_SORT_PRICE_HIGH:
                Collections.sort(filteredShops, new ComparatorShopPriceHigh());
                break;
            default:
                Collections.sort(filteredShops, new ComparatorShopItemNameLow());
                break;
        }

        int startIndex = pageIndex * 36; //36 items is a full page in the inventory
        ItemStack icon;
        boolean added = true;

        for (int i=startIndex; i< filteredShops.size(); i++) {
            AbstractShop shop = filteredShops.get(i);
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

    @Override
    protected void makeMenuBarUpper(){
        super.makeMenuBarUpper();

        //init the menu bar with the saved sort settings
        ShopRevancedGuiHandler.GuiIcon guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_SORT);
        ItemStack sortIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, player, null);
        page.setItem(3, sortIcon);

        //filter ShopRevanced type - all, sell, buy, barter, gamble
        guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_FILTER_SHOP_TYPE);
        ItemStack filterTypeIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, player, null);
        page.setItem(5, filterTypeIcon);

        //filter stock - in stock, out of stock, all
        guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_FILTER_SHOP_STOCK);
        ItemStack filterStockIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, player, null);
        page.setItem(6, filterStockIcon);

        //search icon
        if(ShopRevanced.getPlugin().allowCreativeSelection()) {
            ItemStack searchIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_SEARCH, null, null);
            page.setItem(8, searchIcon);
        }
    }
}









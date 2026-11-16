package com.theflood424k.shoprevanced.gui;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.util.ItemListType;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CommandsWindow extends ShopGuiWindow {

    public CommandsWindow(UUID player){
        super(player);
        this.title = ShopRevanced.getPlugin().getGuiHandler().getTitle(ShopRevancedGuiHandler.GuiTitle.COMMANDS);
        this.page = Bukkit.createInventory(null, INV_SIZE, title);
        initInvContents();
    }

    @Override
    protected void initInvContents(){

        ItemStack currencyIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_CURRENCY, null, null);
        page.setItem(10, currencyIcon);


        ItemStack setCurrencyIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_SET_CURRENCY, null, null);
        page.setItem(11, setCurrencyIcon);


        ItemStack setGambleIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_SET_GAMBLE, null, null);
        page.setItem(12, setGambleIcon);


        ItemStack refreshDisplaysIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_REFRESH_DISPLAYS, null, null);
        page.setItem(13, refreshDisplaysIcon);


        ItemStack reloadIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_RELOAD, null, null);
        page.setItem(14, reloadIcon);

        if(ShopRevanced.getPlugin().getItemListType() == ItemListType.DENY_LIST){
            ItemStack denyListAdd = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_ITEMLIST_DENY_ADD, null, null);
            page.setItem(15, denyListAdd);

            ItemStack denyListRemove = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_ITEMLIST_DENY_REMOVE, null, null);
            page.setItem(16, denyListRemove);
        }
        else if(ShopRevanced.getPlugin().getItemListType() == ItemListType.ALLOW_LIST){
            ItemStack allowListAdd = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_ITEMLIST_ALLOW_ADD, null, null);
            page.setItem(15, allowListAdd);

            ItemStack allowListRemove = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.COMMANDS_ITEMLIST_ALLOW_REMOVE, null, null);
            page.setItem(16, allowListRemove);
        }
    }
}




package com.theflood424k.shoprevanced.gui;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class HomeWindow extends ShopGuiWindow {

    public HomeWindow(UUID player){
        super(player);
        this.title = ShopRevanced.getPlugin().getGuiHandler().getTitle(ShopRevancedGuiHandler.GuiTitle.HOME);
        this.page = Bukkit.createInventory(null, INV_SIZE, title);
        initInvContents();
    }

    @Override
    protected void initInvContents(){

        int listAllShopIconSlot = 19;
        int listOwnShopIconSlot = 20;
        int listPlayersIconSlot = 21;
        int searchIconSlot = 22;
        int settingsIconSlot = 24;

        ItemStack listShopsIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_LIST_ALL_SHOPS, null, null);
        ItemStack listOwnShopsIcon = ShopRevanced.getPlugin().getGuiHandler().getPlayerHeadIcon(player);
        ItemStack listPlayersIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_LIST_PLAYERS, null, null);
        ItemStack searchIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_SEARCH, null, null);
        ItemStack settingsIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_SETTINGS, null, null);

        //if search icon is not allowed, rearrange other icons so they look nice
        if(!ShopRevanced.getPlugin().allowCreativeSelection()){
            listAllShopIconSlot = 20;
            listOwnShopIconSlot = 21;
            listPlayersIconSlot = 22;
            settingsIconSlot = 23;
        }

        page.setItem(listAllShopIconSlot, listShopsIcon);
        page.setItem(listOwnShopIconSlot, listOwnShopsIcon);
        page.setItem(listPlayersIconSlot, listPlayersIcon);
        //only put search icon on page if creative selection is allowed
        if(ShopRevanced.getPlugin().allowCreativeSelection()){
            page.setItem(searchIconSlot, searchIcon);
        }
        page.setItem(settingsIconSlot, settingsIcon);

        //list the commands if they have operator permission
        Player p = this.getPlayer();
        if(p != null) {
            if ((ShopRevanced.getPlugin().usePerms() && p.hasPermission("ShopRevanced.operator")) || p.isOp()) {

                ItemStack commandsIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(ShopRevancedGuiHandler.GuiIcon.HOME_COMMANDS, null, null);
                page.setItem(43, commandsIcon);
            }
        }
    }
}




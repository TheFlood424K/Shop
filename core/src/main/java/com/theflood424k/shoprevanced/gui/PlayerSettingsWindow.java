package com.theflood424k.shoprevanced.gui;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.util.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PlayerSettingsWindow extends ShopGuiWindow {

    public PlayerSettingsWindow(UUID player){
        super(player);
        this.title = ShopRevanced.getPlugin().getGuiHandler().getTitle(ShopRevancedGuiHandler.GuiTitle.SETTINGS);
        this.page = Bukkit.createInventory(null, INV_SIZE, title);
        initInvContents();
    }

    @Override
    protected void initInvContents(){

        Player p = this.getPlayer();
        if(p != null) {

            ShopRevancedGuiHandler.GuiIcon guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(p, PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            ItemStack ownerNotifyIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, p.getUniqueId(), null);
            page.setItem(10, ownerNotifyIcon);

            guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(p, PlayerSettings.Option.NOTIFICATION_SALE_USER);
            ItemStack userNotifyIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, p.getUniqueId(), null);
            page.setItem(11, userNotifyIcon);

            guiIcon = ShopRevanced.getPlugin().getGuiHandler().getIconFromOption(p, PlayerSettings.Option.NOTIFICATION_STOCK);
            ItemStack stockNotifyIcon = ShopRevanced.getPlugin().getGuiHandler().getIcon(guiIcon, p.getUniqueId(), null);
            page.setItem(12, stockNotifyIcon);
        }
    }

}





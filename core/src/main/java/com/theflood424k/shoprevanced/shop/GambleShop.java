package com.theflood424k.shoprevanced.shop;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.event.PlayerExchangeShopEvent;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class GambleShop extends AbstractShop {

    private ItemStack gambleItem;

    public GambleShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.isAdmin = true;
        this.type = ShopType.GAMBLE;
        this.signLines = ShopRevancedMessage.getSignLines(this, this.type);
        setGambleItem();
        this.setAmount(this.gambleItem.getAmount());
    }

    @Override
    protected int calculateStock() {
        return Integer.MAX_VALUE;
    }

    // Called upon a successful gamble transaction
    public void shuffleGambleItem(Player player){
        isPerformingTransaction = true;
        this.setItemStack(gambleItem.clone());
        this.setAmount(gambleItem.getAmount());
        final DisplayType initialDisplayType = this.getDisplay().getType();
        this.getDisplay().setType(DisplayType.ITEM, false);
        setGambleItem();
        this.getDisplay().spawn(player);

        ShopRevanced.getPlugin().getFoliaLib().getScheduler().runLater(() -> {
            setItemStack(ShopRevanced.getPlugin().getGambleDisplayItem());
            if(initialDisplayType == null) {
                display.setType(ShopRevanced.getPlugin().getDisplayType(), false);
                getDisplay().spawn(player);
            }
            else {
                display.setType(initialDisplayType, false);
                getDisplay().spawn(player);
            }
            isPerformingTransaction = false;
        }, 20);
    }

    public void setGambleItem(){
        this.gambleItem = ShopRevanced.getPlugin().getDisplayListener().getRandomItem(this);
    }

    public ItemStack getGambleItem(){
        return gambleItem;
    }
}






package com.theflood424k.shoprevanced.shop;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerExchangeShopEvent;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BuyShop extends AbstractShop {

    public BuyShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.BUY;
        this.signLines = ShopRevancedMessage.getSignLines(this, this.type);
    }

    @Override
    protected int calculateStock(){
        if(this.isAdmin) {
            stock = Integer.MAX_VALUE;
            return stock;
        }
        else {
            double funds = EconomyUtils.getFunds(this.getOwner(), this.getInventory());
            if (this.getPrice() == 0) {
                stock = Integer.MAX_VALUE;
                return stock;
            } else {
                // Check if the player has enough funds to cover a full transaction
                stock = (int) Math.floor(funds / this.getPrice());
                // If the player doesn't have enough funds for a full transaction, see if they can accept a partial one
                if(stock == 0 && ShopRevanced.getPlugin().getAllowPartialSales()){
                    if(this.getItemStack() == null)
                        stock = 0;
                    else {
                        double pricePer = this.getPricePerItem();
                        if (funds >= pricePer) {
                            stock = 1;
                        }
                    }
                }
            }
        }
        return stock;
    }
}






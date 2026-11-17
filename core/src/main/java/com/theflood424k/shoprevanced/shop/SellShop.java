package com.theflood424k.shoprevanced.shop;

import com.theflood424k.shoprevanced.util.*;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public class SellShop extends AbstractShop {

    public SellShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.SELL;
        this.signLines = ShopRevancedMessage.getSignLines(this, this.type);
    }

}






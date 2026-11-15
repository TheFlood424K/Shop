package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.shop.AbstractShop;

import java.util.Comparator;

public class ComparatorShopItemNameHigh implements Comparator<AbstractShop>{
	@Override
    public int compare(AbstractShop o1, AbstractShop o2) {
        return o2.getGuiIcon().getItemMeta().getDisplayName().compareTo(o1.getGuiIcon().getItemMeta().getDisplayName());
    }
}





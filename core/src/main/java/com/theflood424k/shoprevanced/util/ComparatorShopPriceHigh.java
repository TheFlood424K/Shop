package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.shop.AbstractShop;

import java.util.Comparator;

public class ComparatorShopPriceHigh implements Comparator<AbstractShop>{
	@Override
    public int compare(AbstractShop o1, AbstractShop o2) {
        return Double.compare(o2.getPrice(), o1.getPrice());
    }
}





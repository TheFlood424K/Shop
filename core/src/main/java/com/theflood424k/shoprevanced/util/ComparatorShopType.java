package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.shop.AbstractShop;

import java.util.Comparator;

public class ComparatorShopType implements Comparator<AbstractShop>{
	@Override
    public int compare(AbstractShop o1, AbstractShop o2) {
        return o1.getType().toString().compareTo(o2.getType().toString());
    }
}





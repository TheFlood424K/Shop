package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;

import java.util.Comparator;

public class ComparatorShopType implements Comparator<AbstractShop>{
	@Override
    public int compare(AbstractShop o1, AbstractShop o2) {
        String s1 = o1 != null && o1.getType() != null ? o1.getType().toString() : "";
        String s2 = o2 != null && o2.getType() != null ? o2.getType().toString() : "";
        return s1.compareTo(s2);
    }
}

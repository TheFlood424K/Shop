package com.snowgears.shop.util;

import com.snowgears.shop.shop.ShopType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

// Optional selectors ("a:", "i:", "e:", "u:") for narrowing a shop transaction lookup
public class TransactionLookupFilter {

    private final ShopType action;
    private final Set<Material> includeItems;
    private final Set<Material> excludeItems;
    private final String customerName;

    public TransactionLookupFilter(ShopType action, Set<Material> includeItems, Set<Material> excludeItems, String customerName) {
        this.action = action;
        this.includeItems = includeItems;
        this.excludeItems = excludeItems;
        this.customerName = customerName;
    }

    public ShopType getAction() {
        return action;
    }

    public String getCustomerName() {
        return customerName;
    }

    public boolean matchesItem(ItemStack item) {
        Material material = item == null ? Material.AIR : item.getType();
        if (includeItems != null && !includeItems.isEmpty() && !includeItems.contains(material)) return false;
        if (excludeItems != null && !excludeItems.isEmpty() && excludeItems.contains(material)) return false;
        return true;
    }
}
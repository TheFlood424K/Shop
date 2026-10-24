package com.snowgears.shop.util;

import com.snowgears.shop.shop.ShopType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

// Optional selectors ("a:", "i:", "e:", "u:") for narrowing a shop transaction lookup
public class TransactionLookupFilter {

    private final ShopType action;
    private final Set<Material> includeItems;
    private final Set<Material> excludeItems;
    private final String customerName;
    private final UUID ownerUUID;

    public TransactionLookupFilter(ShopType action, Set<Material> includeItems, Set<Material> excludeItems, String customerName, UUID ownerUUID) {
        this.action = action;
        this.includeItems = includeItems;
        this.excludeItems = excludeItems;
        this.customerName = customerName;
        this.ownerUUID = ownerUUID;
    }

    public ShopType getAction() {
        return action;
    }

    public Set<Material> getIncludeItems() {
        return includeItems;
    }

    public Set<Material> getExcludeItems() {
        return excludeItems;
    }

    public String getCustomerName() {
        return customerName;
    }

    public UUID getOwnerUUID() { return ownerUUID; }

    public boolean matchesItem(ItemStack item) {
        Material material = item.getType();
        if (includeItems != null && !includeItems.isEmpty() && !includeItems.contains(material)) {
            return false;
        }
        if (excludeItems != null && !excludeItems.isEmpty() && excludeItems.contains(material)) {
            return false;
        }
        return true;
    }

    public boolean matchesCustomer(String name) {
        return customerName == null || customerName.isEmpty() || customerName.equalsIgnoreCase(name);
    }

    public boolean matchesOwner(UUID uuid) {
        return ownerUUID == null || ownerUUID.equals(uuid);
    }
}
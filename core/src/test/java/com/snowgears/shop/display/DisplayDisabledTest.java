package com.snowgears.shop.display;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DisplayDisabledTest {
    @Test
    public void getItemNameNMSReturnsEmptyForNullItem() {
        DisplayDisabled display = new DisplayDisabled(null);
        Assertions.assertEquals("", display.getItemNameNMS(null));
    }

    @Test
    public void getItemNameNMSReturnsEmptyForNullMeta() {
        DisplayDisabled display = new DisplayDisabled(null);
        ItemStack item = new ItemStack(org.bukkit.Material.STONE);
        item.setItemMeta(null);
        Assertions.assertEquals("", display.getItemNameNMS(item));
    }
}

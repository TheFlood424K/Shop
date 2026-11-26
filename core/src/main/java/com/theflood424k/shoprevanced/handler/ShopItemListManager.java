package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.UtilMethods;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages the ShopRevanced item list (allow/deny list for shop items).
 * <p>
 * Provides methods to check if items pass the filter, add/remove items
 * from the list, and persist the list to disk.
 */
public class ShopItemListManager {

    private final ShopRevanced plugin;
    private final List<ItemStack> itemListItems = new ArrayList<>();

    public ShopItemListManager(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a block is a chest/container that can be used for shops.
     *
     * @param b The block
     * @return true if the block is a valid ShopRevanced container
     */
    public boolean isChest(Block b) {
        return plugin.getEnabledContainers().contains(b.getType());
    }

    /**
     * Checks if an item passes the item list filter.
     *
     * @param is The item stack
     * @return true if the item is allowed
     */
    public boolean passesItemListCheck(ItemStack is) {
        if (plugin.getItemListType() == ItemListType.NONE) return true;

        for (ItemStack itemInList : itemListItems) {
            if (itemInList.isSimilar(is)) {
                if (plugin.getItemListType() == ItemListType.ALLOW_LIST) return true;
                else if (plugin.getItemListType() == ItemListType.DENY_LIST) return false;
            }
        }

        if (plugin.getItemListType() == ItemListType.ALLOW_LIST) return false;
        return true;
    }

    /**
     * Adds all items from the inventory to the item list.
     *
     * @param inventory The inventory to add items from
     */
    public void addInventoryToItemList(Inventory inventory) {
        for (ItemStack is : inventory.getContents()) {
            if (is != null && is.getType() != Material.AIR) {
                ItemStack itemClone = is.clone();
                itemClone.setAmount(1);
                boolean doNotAdd = false;
                for (ItemStack itemInList : itemListItems) {
                    if (itemInList.isSimilar(itemClone)) {
                        doNotAdd = true;
                        break;
                    }
                }
                if (!doNotAdd) {
                    itemListItems.add(itemClone);
                }
            }
        }
        saveItemList();
    }

    /**
     * Removes all items from the inventory from the item list.
     *
     * @param inventory The inventory to remove items from
     */
    public void removeInventoryFromItemList(Inventory inventory) {
        Iterator<ItemStack> itemIterator = itemListItems.iterator();
        while (itemIterator.hasNext()) {
            ItemStack listItem = itemIterator.next();
            for (ItemStack toRemove : inventory.getContents()) {
                if (toRemove != null && toRemove.getType() != Material.AIR) {
                    ItemStack itemClone = toRemove.clone();
                    itemClone.setAmount(1);
                    if (listItem.isSimilar(itemClone)) {
                        itemIterator.remove();
                        break;
                    }
                }
            }
        }
        saveItemList();
    }

    /**
     * Initializes the item list from configuration.
     */
    public void initItemList() {
        if (plugin.getItemListType() == ItemListType.NONE) return;

        try {
            File itemListFile = new File(plugin.getDataFolder() + "/itemList.yml");

            if (!itemListFile.exists()) {
                itemListFile.createNewFile();
                return;
            }

            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(itemListFile);
            for (String key : config.getKeys(false)) {
                ItemStack is = config.getItemStack(key);
                if (is != null) {
                    is.setAmount(1);
                    itemListItems.add(is);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves the item list to configuration.
     */
    public void saveItemList() {
        if (plugin.getItemListType() == ItemListType.NONE) return;

        try {
            File itemListFile = new File(plugin.getDataFolder() + "/itemList.yml");
            itemListFile.getParentFile().mkdirs();

            if (!itemListFile.exists()) {
                itemListFile.createNewFile();
            } else {
                itemListFile.delete();
                itemListFile.createNewFile();
            }

            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(itemListFile);
            for (int i = 0; i < itemListItems.size(); i++) {
                ItemStack is = itemListItems.get(i);
                if (is != null) {
                    config.set("" + i, is);
                }
            }

            config.save(itemListFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

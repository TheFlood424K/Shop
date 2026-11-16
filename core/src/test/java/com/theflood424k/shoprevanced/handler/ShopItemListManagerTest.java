package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.util.ItemListType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopItemListManagerTest {

    private ShopRevanced plugin;
    private ShopItemListManager manager;
    private Field itemListItemsField;

    @BeforeEach
    void setUp() throws Exception {
        plugin = Mockito.mock(ShopRevanced.class);
        manager = new ShopItemListManager(plugin);
        itemListItemsField = ShopItemListManager.class.getDeclaredField("itemListItems");
        itemListItemsField.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getItemListItems() throws Exception {
        return (List<ItemStack>) itemListItemsField.get(manager);
    }

    private ItemStack mockItem(Material type) {
        ItemStack item = Mockito.mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.clone()).thenReturn(item);
        when(item.isSimilar(any())).thenAnswer(inv -> {
            Object other = inv.getArgument(0);
            if (other instanceof ItemStack is) {
                return is.getType() == type;
            }
            return false;
        });
        return item;
    }

    @DisplayName("isChest: block type in enabledContainers -> returns true")
    @Test
    void isChest_containerInList_returnsTrue() {
        when(plugin.getEnabledContainers()).thenReturn(List.of(Material.CHEST));
        Block block = Mockito.mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        assertTrue(manager.isChest(block));
    }

    @DisplayName("isChest: block type not in enabledContainers -> returns false")
    @Test
    void isChest_containerNotInList_returnsFalse() {
        when(plugin.getEnabledContainers()).thenReturn(List.of(Material.CHEST));
        Block block = Mockito.mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        assertFalse(manager.isChest(block));
    }

    @DisplayName("passesItemListCheck: NONE type -> returns true")
    @Test
    void passesItemListCheck_noneType_returnsTrue() {
        when(plugin.getItemListType()).thenReturn(ItemListType.NONE);
        ItemStack item = mockItem(Material.DIAMOND);
        assertTrue(manager.passesItemListCheck(item));
    }

    @DisplayName("passesItemListCheck: ALLOW_LIST, item in list -> returns true")
    @Test
    void passesItemListCheck_allowList_itemInList_returnsTrue() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.ALLOW_LIST);
        ItemStack allowed = mockItem(Material.DIAMOND);
        getItemListItems().add(allowed);
        ItemStack check = mockItem(Material.DIAMOND);
        assertTrue(manager.passesItemListCheck(check));
    }

    @DisplayName("passesItemListCheck: ALLOW_LIST, item NOT in list -> returns false")
    @Test
    void passesItemListCheck_allowList_itemNotInList_returnsFalse() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.ALLOW_LIST);
        ItemStack allowed = mockItem(Material.DIAMOND);
        getItemListItems().add(allowed);
        ItemStack check = mockItem(Material.GOLD_INGOT);
        assertFalse(manager.passesItemListCheck(check));
    }

    @DisplayName("passesItemListCheck: DENY_LIST, item in list -> returns false")
    @Test
    void passesItemListCheck_denyList_itemInList_returnsFalse() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.DENY_LIST);
        ItemStack denied = mockItem(Material.DIAMOND);
        getItemListItems().add(denied);
        ItemStack check = mockItem(Material.DIAMOND);
        assertFalse(manager.passesItemListCheck(check));
    }

    @DisplayName("passesItemListCheck: DENY_LIST, item NOT in list -> returns true")
    @Test
    void passesItemListCheck_denyList_itemNotInList_returnsTrue() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.DENY_LIST);
        ItemStack denied = mockItem(Material.DIAMOND);
        getItemListItems().add(denied);
        ItemStack check = mockItem(Material.GOLD_INGOT);
        assertTrue(manager.passesItemListCheck(check));
    }

    @DisplayName("addInventoryToItemList: adds unique items from inventory")
    @Test
    void addInventoryToItemList_addsUniqueItems() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.ALLOW_LIST);
        Inventory inventory = Mockito.mock(Inventory.class);
        ItemStack diamond = mockItem(Material.DIAMOND);
        ItemStack gold = mockItem(Material.GOLD_INGOT);
        ItemStack air = mockItem(Material.AIR);
        when(inventory.getContents()).thenReturn(new ItemStack[]{diamond, gold, null, air});

        manager.addInventoryToItemList(inventory);

        assertEquals(2, getItemListItems().size());
    }

    @DisplayName("removeInventoryFromItemList: removes items found in inventory")
    @Test
    void removeInventoryFromItemList_removesMatchingItems() throws Exception {
        when(plugin.getItemListType()).thenReturn(ItemListType.ALLOW_LIST);
        ItemStack diamond = mockItem(Material.DIAMOND);
        getItemListItems().add(diamond);
        getItemListItems().add(mockItem(Material.GOLD_INGOT));

        Inventory inventory = Mockito.mock(Inventory.class);
        ItemStack toRemove = mockItem(Material.DIAMOND);
        when(inventory.getContents()).thenReturn(new ItemStack[]{toRemove, null});

        manager.removeInventoryFromItemList(inventory);

        assertEquals(1, getItemListItems().size());
        assertEquals(Material.GOLD_INGOT, getItemListItems().get(0).getType());
    }
}

package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.integration.TownyHook;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.shop.ShopType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Validates whether a shop can be created or initialized by a player.
 * Handles permission checks, world blacklist, region restrictions,
 * and item list validation.
 */
public class CreationValidator {

    private final ShopRevanced plugin;

    public CreationValidator(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    public boolean shopCanBeCreated(Player player, org.bukkit.block.Block chest) {
        int numberOfShops = plugin.getShopRevancedHandler().getNumberOfShops(player);
        int buildPermissionNumber = plugin.getShopRevancedListener().getBuildLimit(player);

        if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("ShopRevanced.operator"))) {
            if (numberOfShops >= buildPermissionNumber) {
                ShopRevancedMessage.sendMessage("permission", "buildLimit", player, null);
                return false;
            }
        }

        if (plugin.getWorldBlacklist().contains(chest.getWorld().getName())) {
            if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("ShopRevanced.operator"))) {
                ShopRevancedMessage.sendMessage("interactionIssue", "worldBlacklist", player, null);
                return false;
            }
        }

        if (plugin.usePerms() && !player.hasPermission("ShopRevanced.operator")) {
            boolean canCreate = false;
            if (!player.hasPermission("ShopRevanced.create")) {
                for (ShopType shopType : ShopType.values()) {
                    if (player.hasPermission("ShopRevanced.create." + shopType.toString().toLowerCase()))
                        canCreate = true;
                }
            } else {
                canCreate = true;
            }
            if (!canCreate) {
                ShopRevancedMessage.sendMessage("permission", "create", player, null);
                return false;
            }
        }

        boolean canCreateShopInRegion = true;
        try {
            if (plugin.worldGuardExists()) {
                canCreateShopInRegion = WorldGuardHook.canCreateShop(player, chest.getLocation());
            }
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
        }

        try {
            if (plugin.hookTowny()) {
                canCreateShopInRegion = TownyHook.canCreateShop(player, chest.getLocation());
            }
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
        }

        if (!canCreateShopInRegion) {
            ShopRevancedMessage.sendMessage("interactionIssue", "regionRestriction", player, null);
            return false;
        }

        return true;
    }

    public boolean itemsCanBeInitialized(Player player, ItemStack itemStack, ItemStack barterItemStack) {
        boolean isAdmin = (!plugin.usePerms() && player.isOp()) || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"));

        if (!isAdmin) {
            boolean passesItemList = plugin.getShopRevancedHandler().passesItemListCheck(itemStack);
            if (!passesItemList) {
                ShopRevancedMessage.sendMessage("interactionIssue", "itemListDeny", player, null);
                return false;
            }
        }

        if (InventoryUtils.itemstacksAreSimilar(itemStack, barterItemStack)) {
            ShopRevancedMessage.sendMessage("interactionIssue", "sameItem", player, null);
            return false;
        }
        return true;
    }

    public boolean getShopIsAdmin(String input) {
        if (input == null) {
            return false;
        }
        // Require an exact (case-insensitive) match of the admin keyword rather than a
        // substring match, so a sign like "admin shop" no longer accidentally creates an admin shop.
        String keyword = ShopRevancedMessage.getCreationWord("ADMIN").toLowerCase();
        return input.trim().toLowerCase().equals(keyword);
    }
}

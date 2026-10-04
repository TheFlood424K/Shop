package com.snowgears.shop.hook;

import com.palmergames.bukkit.towny.utils.ShopPlotUtil;
import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class TownyHook {

    public static final String PLUGIN_NAME = "Towny";

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }


    /**
     * Returns true when the shop's sign could legally be placed on at least one face adjacent to
     * the chest.
     *
     * <p>A shop is a sign plus a container, and the sign goes on whichever wall face beside the
     * chest has room. Towny plots are block-scoped, so a chest and its sign can fall on different
     * plots — or the same shop can be claimable from one side only. At least one candidate sign
     * position must be allowed, otherwise no face would produce a legal shop.
     */
    public static boolean canCreateShopOnAnyNeighbour(Player player, Block chest) {
        if (!Shop.getPlugin().hookTowny()) {
            return true;
        }
        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        for (BlockFace face : SIGN_FACES) {
            if (canCreateShop(player, chest.getRelative(face).getLocation())) {
                return true;
            }
        }
        return false;
    }

    private static final BlockFace[] SIGN_FACES =
            {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static boolean canCreateShop(Player player, Location location) {
        if (!Shop.getPlugin().hookTowny()) {
            return true;
        }
        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
//            if (!TownyAPI.getInstance().isWilderness(player.getLocation())) {
//                Town town = TownyAPI.getInstance().getTownBlock(player.getLocation()).getTown();
//                Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
//                if (!resident.getTown().equals(town)) {
//                    return false;
//                }
//            }
            //this is what the Towny API said to use specifically for Shop developers
            if(!ShopPlotUtil.doesPlayerHaveAbilityToEditShopPlot(player, location)) {
                return false;
            }
        } catch (Exception | NoClassDefFoundError ignore) {
        }
        return true;
    }
}
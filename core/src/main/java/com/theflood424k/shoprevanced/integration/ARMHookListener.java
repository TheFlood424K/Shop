package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import net.alex9849.arm.events.RestoreRegionEvent;
import net.alex9849.arm.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

public class ARMHookListener implements Listener {

    private ShopRevanced plugin;
    private Plugin armPlugin;

    public ARMHookListener(ShopRevanced instance) {
        plugin = instance;
        armPlugin = Bukkit.getPluginManager().getPlugin("AdvancedRegionMarket");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegionRestore(RestoreRegionEvent event) {
        if (plugin == null || !plugin.isAdvancedRegionMarketIntegrationEnabled()) {
            return;
        }
        deleteAllShopsInRegion(event.getRegion());
    }

    private void deleteAllShopsInRegion(Region region){
        HashSet<UUID> shopOwnersToSave = new HashSet<>();
        int shopsDeleted = 0;
        for(UUID shopOwnerUUID : plugin.getShopRevancedHandler().getShopOwnerUUIDs()){
            for(AbstractShop shop : plugin.getShopRevancedHandler().getShops(shopOwnerUUID)) {
                if (shop != null && shop.getSignLocation() != null && shop.getSignLocation().getWorld().getName().equals(region.getRegionworld().getName())) {
                    if (region.getRegion().contains(shop.getSignLocation().getBlockX(), shop.getSignLocation().getBlockY(), shop.getSignLocation().getBlockZ())) {
                        plugin.getLogger().notice("Deleting ShopRevanced because ARM region is being restored! " + shop);
                        shop.delete(false); // delay the save and do it below
                        shopOwnersToSave.add(shopOwnerUUID);
                        shopsDeleted++;
                    }
                }
            }
        }

        // save any ShopRevanced owner files (since we delayed save earlier)
        for(UUID shopOwner : shopOwnersToSave) {
            plugin.getShopRevancedHandler().saveShops(shopOwner, true);
        }

        if (shopsDeleted > 0) {
            plugin.getLogger().notice("(ARM Hook) Deleted " + shopsDeleted + " Shops inside ARM Region `" + region.getRegion().getId() + "` during region restore");
        }
    }
}








package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.BoundingBox;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;

import java.util.UUID;

public class BentoBoxHookListener implements Listener {

    private ShopRevanced plugin;

    public BentoBoxHookListener(ShopRevanced instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent event) {
        if (plugin == null || !plugin.isBentoBoxIntegrationEnabled()) {
            return;
        }
        BoundingBox boundingBox = event.getIsland().getBoundingBox();
        World world = event.getIsland().getCenter().getWorld();

        for(UUID shopOwnerUUID : plugin.getShopRevancedHandler().getShopOwnerUUIDs()){
            for(AbstractShop shop : plugin.getShopRevancedHandler().getShops(shopOwnerUUID)){
                if(shop != null && shop.getSignLocation() != null && shop.getSignLocation().getWorld().getName().equals(world.getName())) {
                    if (boundingBox.contains(shop.getSignLocation().getX(), shop.getSignLocation().getY(), shop.getSignLocation().getZ())){
                        plugin.getLogger().notice("Deleting ShopRevanced because BentoBox Island is being deleted! " + shop);
                        shop.delete();
                    }
                }
            }
        }
    }
}








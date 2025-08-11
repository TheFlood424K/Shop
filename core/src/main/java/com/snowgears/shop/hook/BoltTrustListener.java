package com.snowgears.shop.hook;

import com.snowgears.shop.event.PlayerOpenShopEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.util.Permission;

/**
 * Listener that integrates with Bolt to allow trusted players to open a shop's container.
 */
public class BoltTrustListener implements Listener {

    private final BoltAPI boltApi;

    public BoltTrustListener() {
        BoltAPI api = Bukkit.getServicesManager().load(BoltAPI.class);
        this.boltApi = api;
    }

    private boolean hasOpenPermission(PlayerOpenShopEvent event) {
        if (boltApi == null) return false;
        Block chestBlock = event.getShop().getChestLocation().getBlock();
        try {
            return boltApi.canAccess(chestBlock, event.getPlayer(), Permission.OPEN);
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return; // already allowed

        if (hasOpenPermission(event)) {
            event.setMode(PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER);
        }
    }
}



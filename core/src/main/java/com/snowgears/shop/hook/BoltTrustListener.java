package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerCreateShopEvent;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import com.snowgears.shop.util.ShopMessage;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
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
            // Check if the chest is protected and if the player has permission to open it
            return boltApi.isProtected(chestBlock) && boltApi.canAccess(chestBlock, event.getPlayer(), Permission.OPEN);
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isBoltTrustIntegrationEnabled()) {
            return;
        }
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return; // already allowed

        if (hasOpenPermission(event)) {
            event.setMode(PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCreateShop(PlayerCreateShopEvent event) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isBoltTrustIntegrationEnabled()) {
            return;
        }
        if (boltApi == null) return;
        if (event.getShop() == null || event.getShop().getChestLocation() == null) return;

        Block chestBlock = event.getShop().getChestLocation().getBlock();
        try {
            BlockProtection protection = boltApi.loadProtection(chestBlock);
            // If an existing Bolt protection owner is not the creating player, deny creation
            if (protection != null && !event.getPlayer().isOp() && protection.getOwner() != null && !protection.getOwner().equals(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                // Reuse the same message key as LWC hook for consistency
                ShopMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
            }
        } catch (Exception e) {
            // Fail open: do not block shop creation if Bolt throws, but log at debug level if available
            plugin.getLogger().debug("Bolt check failed during PlayerCreateShopEvent: " + e.getMessage());
        }
    }
}



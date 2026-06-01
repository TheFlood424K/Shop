package com.snowgears.shop.hook;

import com.snowgears.shop.event.PlayerCreateShopEvent;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import com.snowgears.shop.util.ShopMessage;
import de.sean.blockprot.bukkit.BlockProtAPI;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class BlockProtTrustListener implements Listener {

    private final BlockProtAPI api;

    public BlockProtTrustListener() {
        this.api = BlockProtAPI.getInstance();
    }

    private boolean hasOpenPermission(PlayerOpenShopEvent event) {
        if (api == null) return false;
        Block chest = event.getShop().getChestLocation().getBlock();
        BlockNBTHandler handler = api.getBlockHandler(chest);
        String playerId = event.getPlayer().getUniqueId().toString();
        // Only allow if protected and the player has access (owner or friend with read)
        return handler.isProtected() && (handler.isOwner(playerId) || handler.canAccess(playerId));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        var plugin = com.snowgears.shop.Shop.getPlugin();
        if (plugin == null || !plugin.isBlockProtTrustIntegrationEnabled()) {
            return;
        }
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return;
        if (hasOpenPermission(event)) {
            event.setMode(PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCreateShop(PlayerCreateShopEvent event) {
        var plugin = com.snowgears.shop.Shop.getPlugin();
        if (plugin == null || !plugin.isBlockProtTrustIntegrationEnabled()) {
            return;
        }
        if (api == null) return;
        if (event.getShop() == null || event.getShop().getChestLocation() == null) return;

        Block chest = event.getShop().getChestLocation().getBlock();
        BlockNBTHandler handler = api.getBlockHandler(chest);
        // If this chest already has a BlockProt owner and it's not this player, deny
        if (handler.isProtected() && !handler.isOwner(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            ShopMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
        }
    }
}



package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

/**
 * Prevents emptying a bucket when clicking on a ShopRevanced sign or chest.
 * <p>
 * Extracted from {@code MiscListener} to isolate this single, focused behavior.
 */
public class BucketEmptyListener implements Listener {

    private final ShopRevanced plugin;

    public BucketEmptyListener(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    //prevent emptying of bucket when player clicks on ShopRevanced sign
    //also prevent when emptying on display item itself
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block b = event.getBlockClicked();

        if (b.getBlockData() instanceof WallSign) {
            AbstractShop shop = plugin.getShopRevancedHandler().getShop(b.getLocation());
            if (shop != null)
                event.setCancelled(true);
        }
        Block blockToFill = event.getBlockClicked().getRelative(event.getBlockFace());
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(blockToFill.getRelative(BlockFace.DOWN));
        if (shop != null)
            event.setCancelled(true);
    }
}

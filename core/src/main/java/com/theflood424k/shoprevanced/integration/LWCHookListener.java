package com.theflood424k.shoprevanced.integration;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerCreateShopEvent;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class LWCHookListener implements Listener {

    private ShopRevanced plugin;
    private LWC lwc;

    public LWCHookListener(ShopRevanced instance) {
        plugin = instance;
        lwc = ((LWCPlugin) plugin.getServer().getPluginManager().getPlugin("LWC")).getLWC();
    }

    //because LWC events do not use the Spigot event system, they apparently cannot be listened for
    //this is dumb, but in order to not hard code all scenarios where an LWCProtectionRegisterEvent could be registered, we are just gonna remove protections on shops when their chests are clicked
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChestClick(PlayerInteractEvent event){
        if (plugin == null || !plugin.isLwcIntegrationEnabled()) {
            return;
        }
        if(event.getClickedBlock() != null && plugin.getShopRevancedHandler().isChest(event.getClickedBlock())){
            AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getClickedBlock());
            if(shop != null){
                Protection protection = lwc.findProtection(event.getClickedBlock());
                if(protection != null) {
                    protection.remove();
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(PlayerCreateShopEvent event){
        if (plugin == null || !plugin.isLwcIntegrationEnabled()) {
            return;
        }
        if(event.getShop().getChestLocation() != null){
            Protection protection = lwc.findProtection(event.getShop().getChestLocation());
            if(protection != null) {
                //if the owner of the existing LWC protection is NOT the player creating the ShopRevanced
                if(!event.getPlayer().isOp() && !protection.getBukkitOwner().getUniqueId().equals(event.getPlayer().getUniqueId())) {
                    event.setCancelled(true);
                    ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
                }
            }
        }
    }

}







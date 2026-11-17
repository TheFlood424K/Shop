package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerDestroyShopEvent;
import com.theflood424k.shoprevanced.event.PlayerInitializeShopEvent;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import com.theflood424k.shoprevanced.util.UtilMethods;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.GenericMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerSet;

import java.io.File;
import java.util.UUID;

public class DynmapHookListener implements Listener {

    private ShopRevanced plugin;
    private DynmapAPI api;
    private MarkerSet shopMarkerSet;

    private boolean enabled;
    private String markerName;
    private String markerPreview;
    private String markerDescription;

    public DynmapHookListener(ShopRevanced plugin){
        this.plugin = plugin;
        readConfig();
        if(!enabled)
            return;
        try {
            api = (DynmapAPI) plugin.getServer().getPluginManager().getPlugin("dynmap");
            shopMarkerSet = api.getMarkerAPI().getMarkerSet("ShopRevanced");
            if (shopMarkerSet == null) {
                shopMarkerSet = api.getMarkerAPI().createMarkerSet("ShopRevanced", markerName, null, false);
            }

            plugin.getFoliaLib().getScheduler().runTimer(() -> updateMarkers(), 1, 20 * 120 * 60);
        } catch(NullPointerException e){
            enabled = false;
            plugin.getLogger().warning("Dynmap marker api was null. Disabling DynMap integration.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopRemoved(PlayerDestroyShopEvent event) {
        if(!enabled)
            return;
        plugin.getFoliaLib().getScheduler().runNextTick(task -> updateMarkers());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopCreated(PlayerInitializeShopEvent event) {
        if(!enabled)
            return;
        plugin.getFoliaLib().getScheduler().runNextTick(task -> updateMarkers());
    }

    private void updateMarkers() {
        if(!enabled)
            return;
        if (api == null || api.getMarkerAPI() == null) {
            plugin.getLogger().warning("Dynmap marker api not ready, skipping...");
            return;
        }
        shopMarkerSet.getMarkers().forEach(GenericMarker::deleteMarker);
        for(UUID shopOwner : ShopRevanced.getPlugin().getShopRevancedHandler().getShopOwnerUUIDs()){
            for (AbstractShop shop : ShopRevanced.getPlugin().getShopRevancedHandler().getShops(shopOwner)) {

                String preview = markerPreview;
                preview = ShopRevancedMessage.formatMessage(preview, shop, null, false);

                Marker marker = shopMarkerSet.createMarker(UtilMethods.getCleanLocation(shop.getSignLocation(), true),
                        preview
                        , shop.getSignLocation().getWorld().getName()
                        , shop.getSignLocation().getBlockX()
                        , shop.getSignLocation().getBlockY()
                        , shop.getSignLocation().getBlockZ()
                        , api.getMarkerAPI().getMarkerIcon("chest"), false);

                String desc = markerDescription;
                desc = ShopRevancedMessage.formatMessage(desc, shop, null, false);

                marker.setDescription(desc);
            }
        }
    }

    public void deleteMarkers() {
        if(!enabled)
            return;
        shopMarkerSet.deleteMarkerSet();
    }

    private void readConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("dynmap-marker.enabled");
        markerName = config.getString("dynmap-marker.name");
        markerPreview = config.getString("dynmap-marker.preview");
        markerDescription = config.getString("dynmap-marker.description");
    }
}









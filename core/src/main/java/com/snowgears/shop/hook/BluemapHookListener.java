package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerDestroyShopEvent;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.UtilMethods;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class BluemapHookListener implements Listener {

    private Shop plugin;

    private boolean enabled;
    private String markerIcon;
    private String markerLabel;
    private int markerMinDistance;
    private int markerMaxDistance;


    //docs say to use BlueMapAPI.getInstance() at time of to always have the current valid API instance

    public BluemapHookListener(Shop plugin){
        this.plugin = plugin;
        readConfig();
    }

    // Used on server startup to make sure all our shops are in sync with BlueMap
    // Useful if BlueMap is added as a plugin AFTER there are existing shops
    public void reloadMarkers(ShopHandler shopHandler) {
        final List<AbstractShop> allShops = shopHandler.getAllShops();
        for(AbstractShop shop : allShops){
            // Update every shops marker
            this.updateMarker(shop);
        }
    }

    public void updateMarker(AbstractShop shop){
        if(!enabled)
            return;

        BlueMapAPI.getInstance().ifPresent(api -> {
            String shopDetails = ChatColor.stripColor(ShopMessage.formatMessage(markerLabel, shop, null, false));
            // Remove any newline characters, or the literal string \n from the shop details
            shopDetails = shopDetails.replaceAll("\\n", "").replaceAll("\\\\n", "");

            // Remove any color codes from the shop details
            MarkerSet markerSet = getMarkerSet(api, shop);
            if(markerSet != null){
                String markerID = UtilMethods.getCleanLocation(shop.getSignLocation(), true);

                //adjust the markers so the icons line up nice with the actual chests on bluemap
                double x = shop.getSignLocation().getBlockX();
                double y = shop.getSignLocation().getBlockY() + 1.0;
                double z = shop.getSignLocation().getBlockZ();
                switch (shop.getFacing()){
                    case NORTH:
                        x += 1.0;
                        z += 1.0;
                        break;
                    case EAST:
                        z += 1.0;
                        break;
                    case WEST:
                        x += 1.0;
                        break;
                    default:
                        break;
                }

                // Use explicit double literals to avoid the deprecated int overload of position()
                POIMarker marker = POIMarker.builder()
                        .label(shopDetails)
                        .icon(markerIcon, 0, 0)
                        .position(x, y, z)
                        .minDistance(markerMinDistance)
                        .maxDistance(markerMaxDistance)
                        .build();

                markerSet.getMarkers().put(markerID, marker);
            }
        });
    }

    public void removeMarker(AbstractShop shop){
        if(!enabled)
            return;

        BlueMapAPI.getInstance().ifPresent(api -> {
            MarkerSet markerSet = getMarkerSet(api, shop);
            if(markerSet != null){
                String markerID = UtilMethods.getCleanLocation(shop.getSignLocation(), true);
                markerSet.getMarkers().remove(markerID);
            }
        });
    }

    @EventHandler
    public void onShopDestroy(PlayerDestroyShopEvent event){
        if(!event.isCancelled()){
            removeMarker(event.getShop());
        }
    }

    private MarkerSet getMarkerSet(BlueMapAPI api, AbstractShop shop) {
        String markerSetID = "shop-markers-"+shop.getSignLocation().getWorld().getUID();

        Optional<BlueMapWorld> world = api.getWorld(shop.getSignLocation().getWorld());
        if(world.isPresent()) {
            for (BlueMapMap map : world.get().getMaps()) {
                if (map.getMarkerSets().containsKey(markerSetID)) {
                    MarkerSet markerSet = map.getMarkerSets().get(markerSetID);
                    return markerSet;
                }
            }

            //if there was no markerset returned, create one
            MarkerSet markerSet = MarkerSet.builder()
                    .label("Shops")
                    .build();

            //apply it to all maps
            for (BlueMapMap map : world.get().getMaps()) {
                map.getMarkerSets().put(markerSetID, markerSet);
            }
            return markerSet;
        }
        return null;
    }

    private void readConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("bluemap-marker.enabled");
        markerIcon = config.getString("bluemap-marker.icon");
        markerLabel = config.getString("bluemap-marker.label");
        markerMinDistance = config.getInt("bluemap-marker.minDistance");
        markerMaxDistance = config.getInt("bluemap-marker.maxDistance");
    }
}

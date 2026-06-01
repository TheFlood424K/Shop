package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.events.PlotClearEvent;
import com.plotsquared.core.events.PlotDeleteEvent;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.google.common.eventbus.Subscribe;

public class PlotSquaredHookListener implements Listener {

    private Shop plugin;
    private PlotAPI plotAPI;

    public PlotSquaredHookListener(Shop instance) {
        plugin = instance;
        plotAPI = new PlotAPI();
        plotAPI.registerListener(this);
    }

    @Subscribe
    public void onPlotDelete(PlotDeleteEvent e) {
        if (plugin == null || !plugin.isPlotSquaredIntegrationEnabled()) {
            return;
        }
        deleteAllShopsInPlot(e.getPlot());
    }
    
    @Subscribe
    public void onPlotClear(PlotClearEvent e) {
        if (plugin == null || !plugin.isPlotSquaredIntegrationEnabled()) {
            return;
        }
        deleteAllShopsInPlot(e.getPlot());
    }

    private void deleteAllShopsInPlot(@Nullable Plot plot){
        if (plot == null) {
            return;
        }

        HashSet<UUID> shopOwnersToSave = new HashSet<>();
        int shopsDeleted = 0;
        for(UUID shopOwnerUUID : plugin.getShopHandler().getShopOwnerUUIDs()){
            for(AbstractShop shop : plugin.getShopHandler().getShops(shopOwnerUUID)) {
                if (shop == null) {
                    continue;
                }
                final Location signLocation = shop.getSignLocation();
                if (signLocation == null) {
                    continue;
                }
                if (!isShopSignInsidePlot(plot, signLocation)) {
                    continue;
                }

                plugin.getLogger().notice("Deleting Shop because PlotSquared Plot is being reset! " + shop);
                shop.delete(false); // delay the save and do it below
                shopOwnersToSave.add(shopOwnerUUID);
                shopsDeleted++;
            }
        }

        // save any shop owner files (since we delayed save earlier)
        for(UUID shopOwner : shopOwnersToSave) {
            plugin.getShopHandler().saveShops(shopOwner, true);
        }

        if (shopsDeleted > 0) {
            plugin.getLogger().notice("(PlotSquared Hook) Deleted " + shopsDeleted + " Shops inside Plot `" + plot.getId() + "` during plot reset");
        }
    }

    static boolean isShopSignInsidePlot(@Nullable Plot plot, @Nullable Location signLocation) {
        if (plot == null) {
            return false;
        }

        final PlotArea area = plot.getArea();
        final String plotWorldName = area == null ? null : area.getWorldName();
        return isShopSignInsidePlotRegions(plotWorldName, plot.getRegions(), signLocation);
    }

    static boolean isShopSignInsidePlotRegions(
        @Nullable String plotWorldName,
        @Nullable Set<CuboidRegion> regions,
        @Nullable Location signLocation
    ) {
        if (plotWorldName == null || signLocation == null || signLocation.getWorld() == null) {
            return false;
        }
        if (!signLocation.getWorld().getName().equals(plotWorldName)) {
            return false;
        }
        if (regions == null || regions.isEmpty()) {
            return false;
        }

        final BlockVector3 vec = BlockVector3.at(
            signLocation.getBlockX(),
            signLocation.getBlockY(),
            signLocation.getBlockZ()
        );

        for (CuboidRegion region : regions) {
            if (region != null && region.contains(vec)) {
                return true;
            }
        }

        return false;
    }
}
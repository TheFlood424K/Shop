package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NMSBullshitHandler {

    private Shop plugin;
    private double serverVersion = 0;

    public NMSBullshitHandler(Shop plugin){
        this.plugin = plugin;
        init();
    }

    public void init() {
        String mcVersion = plugin.getServer().getClass().getPackage().getName();
        Shop.getPlugin().getLogger().debug("mcVersion: " + mcVersion);

        // MockBukkit testing does not support NMS, so we need to just return early
        if (plugin.isMockBukkit()) {
            return;
        }

        // Check if we are on Paper 1.20.5 or later, it will not include the CB relocation version
        if (!mcVersion.equals("org.bukkit.craftbukkit")) {
            Shop.getPlugin().getLogger().warning("Minecraft version is old or Spigot, loaded version is: " + mcVersion);

            String[] mcVersionSplit = mcVersion.replace(".", ",").split(",");
            // Convert mcVersion into a number like 120.4 (1_20_R4) or 121.1 (1_21_R1) so that we can use it later
            String versionNumberString = mcVersionSplit[mcVersionSplit.length-1].replace("_R", ".").replaceAll("[rvV_]*", "");
            serverVersion = Double.parseDouble(versionNumberString);
        }

        // log the server version we are on, it will be 0 when we are on a Paper server
        Shop.getPlugin().getLogger().debug("Server Version: " + this.getServerVersion());
        Shop.getPlugin().getLogger().debug("Is Server Version over 117.0D: " + (Math.floor(this.getServerVersion()) >= 117.0D));
    }

    public double getServerVersion() {
        return this.serverVersion;
    }

    public String getFormattedChatMessage(String text) {
        // Return plain text - Paper API uses String for display entity text
        return text;
    }

    public net.minecraft.world.item.ItemStack getMCItemStack(ItemStack is) {
        // Not needed with Paper API displays
        // This is kept for any legacy code that might still call it
        return null;
    }

    public net.minecraft.world.level.Level getMCLevel(Location location) {
        // Not needed with Paper API displays
        return null;
    }

    public net.minecraft.server.level.ServerLevel getMCServerLevel(Location location) {
        // Not needed with Paper API displays
        return null;
    }

    public net.minecraft.server.network.ServerPlayerConnection getPlayerConnection(Player player) {
        // Not needed with Paper API displays
        return null;
    }
}
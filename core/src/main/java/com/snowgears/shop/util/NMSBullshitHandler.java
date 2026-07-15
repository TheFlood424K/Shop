package com.snowgears.shop.util;

import com.snowgears.shop.Shop;

public class NMSBullshitHandler {

    private Shop plugin;
    private double serverVersion;

    public NMSBullshitHandler(Shop plugin){
        this.plugin = plugin;
        Shop.getPlugin().getLogger().info("NMS is unsupport.");
    }

    public double getServerVersion() {
        return this.serverVersion;
    }

    public Class<?> getCraftItemStackClass() {
        return null;
    }

    public Class<?> getCraftWorldClass() {
        return null;
    }

    public Class<?> getCraftPlayerClass() {
        return null;
    }

    public Object getFormattedChatMessage(String text) {
        Shop.getPlugin().getLogger().warning("NMS is unsupported.");
        return null;
    }

    public Object getMCItemStack(org.bukkit.inventory.ItemStack is) {
        Shop.getPlugin().getLogger().warning("NMS is unsupported.");
        return null;
    }

    public Object getMCLevel(org.bukkit.Location location) {
        Shop.getPlugin().getLogger().warning("NMS is unsupported.");
        return null;
    }

    public Object getMCServerLevel(org.bukkit.Location location) {
        Shop.getPlugin().getLogger().warning("NMS is unsupported.");
        return null;
    }

    public Object getPlayerConnection(org.bukkit.entity.Player player) {
        Shop.getPlugin().getLogger().warning("NMS is unsupported.");
        return null;
    }
}

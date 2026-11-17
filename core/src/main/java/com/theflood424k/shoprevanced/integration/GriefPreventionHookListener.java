package com.theflood424k.shoprevanced.integration;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerCreateShopEvent;
import com.theflood424k.shoprevanced.event.PlayerDestroyShopEvent;
import com.theflood424k.shoprevanced.event.PlayerResizeShopEvent;
import com.theflood424k.shoprevanced.event.PlayerOpenShopEvent;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;

public class GriefPreventionHookListener implements Listener {

    private ShopRevanced plugin;

    public GriefPreventionHookListener(ShopRevanced instance) {
        plugin = instance;
    }

    private Claim getClaimAt(Location location) {
        if (GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null || location == null) {
            return null;
        }
        return GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
    }

    private boolean hasClaimPermission(Player player, Claim claim, ClaimPermission permission) {
        if (player == null || claim == null) {
            return false;
        }
        return claim.hasExplicitPermission(player, permission);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerCreateShop(PlayerCreateShopEvent event) {
        if (plugin == null || !plugin.isGriefPreventionIntegrationEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();
        if (player == null || shop == null) {
            return;
        }

        Location signLocation = shop.getSignLocation();
        if (signLocation == null) {
            return;
        }

        Claim claim = getClaimAt(signLocation);
        if (claim == null) {
            // No claim at the location, allow ShopRevanced creation
            return;
        }

        // Check if the player has permission to create a ShopRevanced in this claim
        if (!player.isOp() && !player.hasPermission("ShopRevanced.operator")) {
            if (!hasClaimPermission(player, claim, ClaimPermission.Build)) {
                event.setCancelled(true);
                ShopRevancedMessage.sendMessage("interactionIssue", "claimOtherPlayerShop", player, shop);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDestroyShop(PlayerDestroyShopEvent event) {
        if (plugin == null || !plugin.isGriefPreventionIntegrationEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();
        if (player == null || shop == null) {
            return;
        }

        Location signLocation = shop.getSignLocation();
        if (signLocation == null) {
            return;
        }

        Claim claim = getClaimAt(signLocation);
        if (claim == null) {
            // No claim at the location, allow ShopRevanced destruction
            return;
        }

        // Check if the player has permission to destroy this ShopRevanced
        if (!player.isOp() && !player.hasPermission("ShopRevanced.operator")) {
            if (!hasClaimPermission(player, claim, ClaimPermission.Build)) {
                event.setCancelled(true);
                ShopRevancedMessage.sendMessage("interactionIssue", "claimOtherPlayerShop", player, shop);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        if (plugin == null || !plugin.isGriefPreventionIntegrationEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();
        if (player == null || shop == null) {
            return;
        }

        Location signLocation = shop.getSignLocation();
        if (signLocation == null) {
            return;
        }

        Claim claim = getClaimAt(signLocation);
        if (claim == null) {
            // No claim at the location, allow ShopRevanced opening
            return;
        }

        // Check if the player has permission to open this ShopRevanced
        if (!player.isOp() && !player.hasPermission("ShopRevanced.operator")) {
            if (!hasClaimPermission(player, claim, ClaimPermission.Access)) {
                event.setCancelled(true);
                ShopRevancedMessage.sendMessage("interactionIssue", "claimAdminShop", player, shop);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerResizeShop(PlayerResizeShopEvent event) {
        if (plugin == null || !plugin.isGriefPreventionIntegrationEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();
        if (player == null || shop == null) {
            return;
        }

        Location signLocation = shop.getSignLocation();
        if (signLocation == null) {
            return;
        }

        Claim claim = getClaimAt(signLocation);
        if (claim == null) {
            // No claim at the location, allow ShopRevanced resize
            return;
        }

        // Check if the player has permission to resize this ShopRevanced
        if (!player.isOp() && !player.hasPermission("ShopRevanced.operator")) {
            if (!hasClaimPermission(player, claim, ClaimPermission.Build)) {
                event.setCancelled(true);
                ShopRevancedMessage.sendMessage("interactionIssue", "claimOtherPlayerShop", player, shop);
            }
        }
    }
}









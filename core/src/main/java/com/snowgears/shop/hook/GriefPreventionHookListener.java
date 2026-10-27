package com.snowgears.shop.hook;

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
import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerCreateShopEvent;
import com.snowgears.shop.event.PlayerDestroyShopEvent;
import com.snowgears.shop.event.PlayerResizeShopEvent;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ShopMessage;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimUpkeepEvent;

/**
 * Handles grief prevention integration for the Shop plugin.
 * Prevents shop creation, destruction, and resizing in claims where the player
 * does not have permission, and respects claim ownership for shop interactions.
 */
public class GriefPreventionHookListener implements Listener {

    private final Shop plugin;

    public GriefPreventionHookListener(Shop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCreateShop(PlayerCreateShopEvent event) {
        Player player = event.getPlayer();
        Block clickedChest = event.getClickedChest();
        AbstractShop shop = event.getShop();

        if (clickedChest == null || shop == null) {
            return;
        }

        Claim claim = GriefPrevention.getClaimAt(clickedChest.getLocation());

        if (claim == null) {
            // No claim at this location, allow shop creation
            return;
        }

        // Check if the player has permission to create shops in this claim
        if (!player.hasPermission("shop.ignoregriefprevention")) {
            if (!claim.hasAdminPermission(player)) {
                // Player does not have admin permission in this claim
                event.setCancelled(true);
                ShopMessage.sendMessage("interactionIssue_claimOtherPlayerShop", player);
                return;
            }
        }

        // Check if the player is the claim owner
        if (claim.isOwner(player.getUniqueId())) {
            // Claim owner can create shops
            return;
        }

        // Check if an admin shop already exists in this claim
        if (shop.isAdmin()) {
            // Admin shops are only allowed in admin claims
            if (!claim.hasAdminPermission(player)) {
                event.setCancelled(true);
                ShopMessage.sendMessage("interactionIssue_claimAdminShop", player);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDestroyShop(PlayerDestroyShopEvent event) {
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();

        if (shop == null) {
            return;
        }

        Location shopLocation = shop.getSignLocation();
        if (shopLocation == null) {
            return;
        }

        Claim claim = GriefPrevention.getClaimAt(shopLocation);

        if (claim == null) {
            // No claim at this location, allow destruction
            return;
        }

        // Check if the player has permission to destroy shops in this claim
        if (!player.hasPermission("shop.ignoregriefprevention")) {
            if (!claim.hasAdminPermission(player)) {
                // Player does not have admin permission in this claim
                // Check if the player is the shop owner
                if (shop.isAdmin()) {
                    // Admin shops can only be destroyed by admins or if the player has permission
                    event.setCancelled(true);
                    ShopMessage.sendMessage("interactionIssue_claimAdminShop", player);
                    return;
                }
                // Non-admin shops: check if player is the owner
                if (!shop.isOwner(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerResizeShop(PlayerResizeShopEvent event) {
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();

        if (shop == null) {
            return;
        }

        Location shopLocation = shop.getSignLocation();
        if (shopLocation == null) {
            return;
        }

        Claim claim = GriefPrevention.getClaimAt(shopLocation);

        if (claim == null) {
            // No claim at this location, allow resize
            return;
        }

        // Check if the player has permission to resize shops in this claim
        if (!player.hasPermission("shop.ignoregriefprevention")) {
            if (!claim.hasAdminPermission(player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();

        if (shop == null) {
            return;
        }

        Location shopLocation = shop.getSignLocation();
        if (shopLocation == null) {
            return;
        }

        Claim claim = GriefPrevention.getClaimAt(shopLocation);

        if (claim == null) {
            // No claim at this location, allow opening
            return;
        }

        // Check if the player has permission to open shops in this claim
        if (!player.hasPermission("shop.ignoregriefprevention")) {
            if (!claim.hasAdminPermission(player)) {
                // Check if the player is the shop owner
                if (shop.isOwner(player.getUniqueId())) {
                    // Shop owner can open their own shop
                    return;
                }
                // Admin shops: check admin permission
                if (shop.isAdmin() && !claim.hasAdminPermission(player)) {
                    event.setCancelled(true);
                    ShopMessage.sendMessage("interactionIssue_claimAdminShop", player);
                    return;
                }
                // Non-admin, non-owner shop in claim: block opening
                event.setCancelled(true);
                return;
            }
        }
    }
}
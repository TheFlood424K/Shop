package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener that integrates with GriefPrevention to allow players to buy from
 * shops located in claims where the player does not have build or container trust.
 *
 * <p>When a player right-clicks a shop chest that belongs to another player and the
 * chest is inside a GriefPrevention claim, GP normally blocks the interaction.
 * This hook fires on {@link PlayerOpenShopEvent} and, if the player's transaction is
 * purely a <em>shop action</em> (buy/sell), sets the mode to {@link PlayerOpenShopEvent.OpenMode#SHOP_ACTION}
 * so Shop's own logic handles the purchase without requiring GP container trust.</p>
 *
 * <p>The hook intentionally does <strong>not</strong> grant {@code OPEN_CONTAINER} access
 * (which would let the player freely browse the chest inventory). It only marks the
 * event as allowed so the shop transaction can proceed.</p>
 */
public class GriefPreventionTrustListener implements Listener {

    /**
     * Returns {@code true} if GriefPrevention has a registered claim at the given
     * location that would restrict the player, but we want to allow the shop action
     * anyway.
     *
     * <p>Logic:
     * <ol>
     *   <li>If no claim exists at the location, return {@code false} (GP won't block
     *       anything, so we have nothing to override).</li>
     *   <li>If the player is the claim owner, return {@code false} (they already have
     *       full access).</li>
     *   <li>If the player already has container trust or higher (build/access), return
     *       {@code false} (GP would allow them anyway).</li>
     *   <li>Otherwise the player is untrusted — return {@code true} so we can permit
     *       the shop transaction without full container access.</li>
     * </ol>
     * </p>
     */
    private boolean shouldAllowShopAccess(Player player, Location shopLocation) {
        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null) return false;

            Claim claim = gp.dataStore.getClaimAt(shopLocation, false, null);
            if (claim == null) {
                // Not inside any claim — GriefPrevention won't block, nothing to do.
                return false;
            }

            // Owner always has access — no override needed.
            if (claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId())) {
                return false;
            }

            // Check existing trust levels.
            // hasContainerTrust / hasBuildTrust / hasAccessTrust all return a denial
            // message String when NOT trusted, or null when trusted.
            String buildDenial     = claim.allowBuild(player, org.bukkit.Material.AIR);
            String containerDenial = claim.allowContainers(player);

            if (buildDenial == null || containerDenial == null) {
                // Player already has build or container trust — GP won't block them.
                return false;
            }

            // Player is in a claim and has no container/build trust.
            // Allow the shop action to proceed without granting full container access.
            return true;
        } catch (Exception e) {
            // Fail open: if anything goes wrong, don't block the shop interaction.
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isGriefPreventionTrustIntegrationEnabled()) {
            return;
        }
        // We only care about chest interactions (buy/sell actions).
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        // If another hook already upgraded to OPEN_CONTAINER, leave it alone.
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return;

        Location shopLocation = event.getShop().getChestLocation();
        if (shopLocation == null) return;

        if (shouldAllowShopAccess(event.getPlayer(), shopLocation)) {
            // Keep mode as SHOP_ACTION — this tells Shop to execute the buy/sell
            // transaction rather than open the container. GriefPrevention's own
            // PlayerInteractEvent listener runs at NORMAL priority; Shop's
            // onShopChestClick runs at HIGHEST and un-cancels the event when it
            // executes a shop action, so the purchase goes through cleanly.
            event.setMode(PlayerOpenShopEvent.OpenMode.SHOP_ACTION);
        }
    }
}

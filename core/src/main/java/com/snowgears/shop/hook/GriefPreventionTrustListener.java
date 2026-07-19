package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerOpenShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listener that integrates with GriefPrevention to allow players to buy from
 * shops located in claims where the player does not have build or container trust.
 *
 * <h3>Event priority chain</h3>
 * <ol>
 *   <li><b>LOW</b> – {@link #onShopChestPreempt}: if the clicked block is a shop chest
 *       and it is inside any GP claim, cancel the event early so that
 *       GriefPrevention's {@code PlayerInteractEvent} handler (which runs at {@code NORMAL}
 *       with {@code ignoreCancelled = true}) is skipped entirely. This prevents GP from
 *       sending its "no permission" message to the player regardless of trust level.</li>
 *   <li><b>NORMAL</b> – GriefPrevention's own handler. Skipped because the event is
 *       already cancelled.</li>
 *   <li><b>HIGHEST</b> – Shop's {@code onShopChestClick}: processes the buy/sell
 *       transaction normally (ignoreCancelled defaults to false so it still fires).</li>
 * </ol>
 */
public class GriefPreventionTrustListener implements Listener {

    /**
     * Returns {@code true} if the shop chest is inside a GriefPrevention claim,
     * meaning GP would potentially intercept the interaction and send a denial message.
     * We pre-cancel for ALL claim interactions (trusted or not) so GP never fires.
     * Shop's own HIGHEST handler decides whether the transaction is allowed.
     */
    private boolean isInGriefPreventionClaim(Location shopLocation) {
        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null) return false;

            Claim claim = gp.dataStore.getClaimAt(shopLocation, false, null);
            return claim != null;
        } catch (Exception e) {
            // Fail open: if anything goes wrong, don't interfere.
            return false;
        }
    }

    /**
     * Returns {@code true} if the player has at least container or build trust
     * in the claim at {@code shopLocation}, meaning they should be allowed to
     * open the shop chest as a container (trusted access).
     */
    private boolean hasTrustInClaim(Player player, Location shopLocation) {
        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null) return false;

            Claim claim = gp.dataStore.getClaimAt(shopLocation, false, null);
            if (claim == null) return false;

            // Owner always has full access.
            if (claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId())) {
                return true;
            }

            // allowBuild / allowContainers return null when the player IS trusted.
            String buildDenial     = claim.allowBuild(player, org.bukkit.Material.AIR);
            String containerDenial = claim.allowContainers(player);

            return buildDenial == null || containerDenial == null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fires at LOW priority — before GriefPrevention (NORMAL, ignoreCancelled=true).
     *
     * <p>If the right-clicked block is a shop chest inside any GP claim, pre-cancel
     * the event so GP's handler is skipped entirely and its denial message is never
     * sent. Shop's own HIGHEST-priority handler still fires (ignoreCancelled=false by
     * default) and completes or denies the transaction based on its own logic.</p>
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onShopChestPreempt(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        try {
            if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        } catch (NoSuchMethodError ignored) {}

        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isGriefPreventionTrustIntegrationEnabled()) return;

        // Only act on shop chests.
        if (!plugin.getShopHandler().isChest(event.getClickedBlock())) return;
        AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getClickedBlock());
        if (shop == null) return;

        Location shopLocation = shop.getChestLocation();
        if (shopLocation == null) return;

        // Pre-cancel for ANY shop chest inside a GP claim so GP never fires its
        // build-denial message. Trusted players and owners are handled below.
        if (isInGriefPreventionClaim(shopLocation)) {
            event.setCancelled(true);
        }
    }

    /**
     * Fires on {@link PlayerOpenShopEvent} at HIGHEST priority.
     *
     * <p>If the player has container or build trust in the claim, upgrade the mode
     * to {@link PlayerOpenShopEvent.OpenMode#OPEN_CONTAINER} so they can access the
     * chest directly. Otherwise leave the default {@code SHOP_ACTION} mode so the
     * normal buy/sell transaction runs.</p>
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isGriefPreventionTrustIntegrationEnabled()) {
            return;
        }
        // We only care about chest interactions.
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        // If another hook already set OPEN_CONTAINER, leave it alone.
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return;

        Location shopLocation = event.getShop().getChestLocation();
        if (shopLocation == null) return;

        if (hasTrustInClaim(event.getPlayer(), shopLocation)) {
            // Trusted player — let them open the chest as a container.
            event.setMode(PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER);
        }
        // Untrusted player — default SHOP_ACTION remains, normal buy/sell runs.
    }
}

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
 *       inside a GP claim AND the player is NOT trusted, cancel the event early so that
 *       GriefPrevention's {@code PlayerInteractEvent} handler (which runs at {@code NORMAL}
 *       with {@code ignoreCancelled = true}) is skipped entirely. This prevents GP from
 *       sending its "no permission" message to the player.
 *       Trusted players and the claim owner are NOT cancelled here — their interaction
 *       passes through normally so GP (and Bukkit) can open the chest for them.</li>
 *   <li><b>NORMAL</b> – GriefPrevention's own handler. Skipped for untrusted players
 *       because the event is already cancelled. Runs normally for trusted players.</li>
 *   <li><b>HIGHEST</b> – Shop's {@code onShopChestClick}: processes the buy/sell
 *       transaction for untrusted players (ignoreCancelled defaults to false so it still
 *       fires). Trusted players open the chest directly via Bukkit.</li>
 * </ol>
 */
public class GriefPreventionTrustListener implements Listener {

    /**
     * Returns {@code true} if the shop chest is inside a GriefPrevention claim,
     * meaning GP would potentially intercept the interaction and send a denial message
     * for players without trust.
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
     * <p>If the right-clicked block is a shop chest inside a GP claim, pre-cancel
     * the event ONLY for players who are NOT trusted. Trusted players and the claim
     * owner are left alone so their interaction flows through Bukkit normally and the
     * chest opens for them as expected.</p>
     *
     * <p>For untrusted players, pre-cancelling here skips GP's denial message while
     * still allowing Shop's HIGHEST-priority handler to run (ignoreCancelled=false) and
     * complete the buy/sell transaction.</p>
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

        if (isInGriefPreventionClaim(shopLocation)) {
            // Only pre-cancel for untrusted players so GP never fires its denial message.
            // Trusted players and the claim owner must NOT be cancelled — their event must
            // pass through so that Bukkit (and GP itself) opens the chest normally.
            if (!hasTrustInClaim(event.getPlayer(), shopLocation)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Fires on {@link PlayerOpenShopEvent} at HIGHEST priority.
     *
     * <p>If the player has container or build trust in the claim, upgrade the mode
     * to {@link PlayerOpenShopEvent.OpenMode#OPEN_CONTAINER} so they can access the
     * chest directly. Otherwise leave the default {@code SHOP_ACTION} mode so the
     * normal buy/sell transaction runs.</p>
     *
     * <p>Note: for trusted players the {@code PlayerInteractEvent} is no longer
     * pre-cancelled, so in most cases Bukkit will have already opened the chest
     * before this event fires. This handler acts as a safety net in case the Shop
     * plugin intercepts the open itself.</p>
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

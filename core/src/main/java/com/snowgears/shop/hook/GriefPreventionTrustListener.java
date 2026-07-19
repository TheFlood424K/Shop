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
 *       and the player is an untrusted visitor, cancel the event early so that
 *       GriefPrevention's {@code PlayerInteractEvent} handler (which runs at {@code NORMAL}
 *       with {@code ignoreCancelled = true}) is skipped entirely. This prevents GP from
 *       sending its "no permission" message to the player.</li>
 *   <li><b>NORMAL</b> – GriefPrevention's own handler. Skipped because the event is
 *       already cancelled.</li>
 *   <li><b>HIGHEST</b> – Shop's {@code onShopChestClick}: un-cancels the event, fires
 *       {@link PlayerOpenShopEvent}, and executes the buy/sell transaction.</li>
 * </ol>
 */
public class GriefPreventionTrustListener implements Listener {

    /**
     * Returns {@code true} if the player is inside a GriefPrevention claim at
     * {@code shopLocation} but has neither build nor container trust, meaning GP
     * would normally block the interaction and send a denial message.
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

            // allowBuild / allowContainers return null when the player IS trusted,
            // or a non-null denial message when they are NOT trusted.
            String buildDenial     = claim.allowBuild(player, org.bukkit.Material.AIR);
            String containerDenial = claim.allowContainers(player);

            if (buildDenial == null || containerDenial == null) {
                // Player already has build or container trust — GP won't block them.
                return false;
            }

            // Player is in a claim and has no container/build trust.
            return true;
        } catch (Exception e) {
            // Fail open: if anything goes wrong, don't block the shop interaction.
            return false;
        }
    }

    /**
     * Fires at LOW priority — before GriefPrevention (NORMAL, ignoreCancelled=true).
     *
     * <p>If the right-clicked block is a shop chest and the player would be blocked
     * by GP, we pre-cancel the event so GP's handler is skipped and its denial
     * message is never sent. Shop's own HIGHEST-priority handler will un-cancel
     * the event and complete the transaction.</p>
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

        Player player = event.getPlayer();

        // Owner clicking their own shop — let everything proceed normally.
        if (shop.getOwnerUUID().equals(player.getUniqueId())) return;

        Location shopLocation = shop.getChestLocation();
        if (shopLocation == null) return;

        if (shouldAllowShopAccess(player, shopLocation)) {
            // Cancel early so GP (NORMAL, ignoreCancelled=true) is skipped.
            // Shop's HIGHEST handler will un-cancel and run the transaction.
            event.setCancelled(true);
        }
    }

    /**
     * Fires on {@link PlayerOpenShopEvent} at HIGHEST priority.
     *
     * <p>Sets the event mode to {@link PlayerOpenShopEvent.OpenMode#SHOP_ACTION} so
     * Shop executes the buy/sell transaction rather than opening the container.</p>
     */
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
            event.setMode(PlayerOpenShopEvent.OpenMode.SHOP_ACTION);
        }
    }
}

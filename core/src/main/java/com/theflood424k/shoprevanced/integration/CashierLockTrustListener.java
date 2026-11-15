package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerCreateShopEvent;
import com.theflood424k.shoprevanced.event.PlayerOpenShopEvent;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Listener that integrates with CashierLock to allow trusted players to open/create ShopRevanced containers.
 * <p>
 * This provides a best-effort integration with the CashierLock plugin. If CashierLock is not installed,
 * the listener does nothing. If CashierLock is installed but the integration is disabled, the listener
 * also does nothing. If CashierLock is installed and integration is enabled, the listener checks if
 * chests are protected and enforces access controls similar to the BlockProt and Bolt integrations.
 * <p>
 * Note: The CashierLock API methods are accessed via reflection since the exact API may vary between
 * plugin versions. If the API methods are not found, the listener fails open (allows the operation)
 * to avoid blocking ShopRevanced creation/usage unnecessarily.
 */
public class CashierLockTrustListener implements Listener {

    private Object cashierLockApi;
    private Method isProtectedMethod;
    private Method getOwnerMethod;
    private Method canAccessMethod;

    public CashierLockTrustListener() {
        this.cashierLockApi = loadCashierLockAPI();
        this.isProtectedMethod = null;
        this.getOwnerMethod = null;
        this.canAccessMethod = null;

        if (cashierLockApi != null) {
            try {
                this.isProtectedMethod = cashierLockApi.getClass().getMethod("isProtected", Block.class);
                this.getOwnerMethod = cashierLockApi.getClass().getMethod("getOwner", Block.class);
                this.canAccessMethod = cashierLockApi.getClass().getMethod("canAccess", Block.class, org.bukkit.entity.Player.class, String.class);
            } catch (NoSuchMethodException e) {
                ShopRevanced.getPlugin().getLogger().debug("CashierLock API methods not found via reflection: " + e.getMessage());
                this.cashierLockApi = null;
            }
        }
    }

    /**
     * Attempts to load the CashierLock API.
     * Returns the API object if found, or null if CashierLock is not installed.
     */
    private Object loadCashierLockAPI() {
        // Try to get the plugin
        var plugin = Bukkit.getPluginManager().getPlugin("CashierLock");
        if (plugin == null) {
            return null;
        }

        // Try to get the API instance from the plugin
        try {
            // Common CashierLock API access patterns
            // 1. Get via getApi() method
            Method getApiMethod = plugin.getClass().getMethod("getApi");
            return getApiMethod.invoke(plugin);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 2. Try getting the class and instantiating
            try {
                Class<?> apiClass = Class.forName("net.cashierlock.api.CashierLockAPI");
                try {
                    return apiClass.getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e2) {
                    // Try to get the instance field
                    try {
                        var field = apiClass.getField("instance");
                        return field.get(null);
                    } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e3) {
                        return null;
                    }
                }
            } catch (ClassNotFoundException e2) {
                // CashierLock API class not found - no integration possible
                return null;
            }
        }
    }

    /**
     * Checks if a chest block is protected by CashierLock and if the player has access.
     * Returns true if the chest is not protected, or if the player has access.
     * Returns false only if the chest is protected and the player does NOT have access.
     * <p>
     * Logic order:
     * 1. If not protected → allow
     * 2. If player is owner → allow (owner always has access)
     * 3. If player is OP → allow (operators always have access)
     * 4. If canAccessMethod available → use it as additional check
     * 5. If getOwnerMethod available and owner is someone else → deny
     * 6. Fail closed: protection exists but access can't be determined → deny
     */
    private boolean hasOpenPermission(PlayerOpenShopEvent event) {
        if (cashierLockApi == null) return true; // CashierLock not installed, allow
        Block chest = event.getShop().getChestLocation().getBlock();

        try {
            boolean isProtected = false;
            if (isProtectedMethod != null) {
                isProtected = (Boolean) isProtectedMethod.invoke(cashierLockApi, chest);
            }

            // If chest is not protected, allow access
            if (!isProtected) {
                return true;
            }

            String playerId = event.getPlayer().getUniqueId().toString();

            // If player is the owner, allow access
            if (getOwnerMethod != null) {
                String ownerId = (String) getOwnerMethod.invoke(cashierLockApi, chest);
                if (ownerId != null && ownerId.equals(playerId)) {
                    return true; // Player is the owner, allow
                }
                // Owner is someone else - deny unless OP or explicitly granted via canAccess
                if (ownerId != null && !ownerId.equals(playerId)) {
                    if (event.getPlayer().isOp()) {
                        return true;
                    }
                    if (canAccessMethod != null) {
                        try {
                            boolean canAccess = (Boolean) canAccessMethod.invoke(cashierLockApi, chest, event.getPlayer(), playerId);
                            if (canAccess) {
                                return true;
                            }
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            // If canAccess fails, fall through to deny
                        }
                    }
                    return false; // Not owner, not OP, cannot access via canAccess
                }
                // Owner is unknown - continue checks
            }

            // If player is an operator, allow access
            if (event.getPlayer().isOp()) {
                return true;
            }

            // Chest is protected and player is not owner/operator
            // Now check canAccess method if available as additional validation
            if (canAccessMethod != null) {
                try {
                    boolean canAccess = (Boolean) canAccessMethod.invoke(cashierLockApi, chest, event.getPlayer(), playerId);
                    return canAccess;
                } catch (InvocationTargetException | IllegalAccessException e) {
                    // If canAccess fails, fall through to deny
                }
            }

            // Player is not the owner, not an operator, and cannot access via canAccess
            // Fail closed: deny access to protected chest
            return false;
        } catch (InvocationTargetException | IllegalAccessException e) {
            // If CashierLock API methods fail, fail open (allow the operation)
            ShopRevanced.getPlugin().getLogger().debug("CashierLock check failed during ShopRevanced event: " + e.getMessage());
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerOpenShop(PlayerOpenShopEvent event) {
        ShopRevanced plugin = ShopRevanced.getPlugin();
        if (plugin == null || !plugin.isCashierLockTrustIntegrationEnabled()) {
            return;
        }
        if (event.getTarget() != PlayerOpenShopEvent.OpenTarget.CHEST) return;
        if (event.getMode() == PlayerOpenShopEvent.OpenMode.OPEN_CONTAINER) return;

        if (!hasOpenPermission(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCreateShop(PlayerCreateShopEvent event) {
        ShopRevanced plugin = ShopRevanced.getPlugin();
        if (plugin == null || !plugin.isCashierLockTrustIntegrationEnabled()) {
            return;
        }
        if (event.getShop() == null || event.getShop().getChestLocation() == null) return;

        Block chest = event.getShop().getChestLocation().getBlock();

        try {
            if (isProtectedMethod != null) {
                boolean hasProtection = (Boolean) isProtectedMethod.invoke(cashierLockApi, chest);
                if (hasProtection) {
                    // Chest is protected by CashierLock
                    if (getOwnerMethod != null) {
                        String ownerId = (String) getOwnerMethod.invoke(cashierLockApi, chest);
                        // If we cannot determine the owner, deny ShopRevanced creation (fail closed)
                        if (ownerId == null) {
                            event.setCancelled(true);
                            ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
                            return;
                        }
                        // If player is the owner, allow ShopRevanced creation
                        if (ownerId.equals(event.getPlayer().getUniqueId().toString())) {
                            return;
                        }
                        // If player is an operator, allow ShopRevanced creation
                        if (event.getPlayer().isOp()) {
                            return;
                        }
                        // Player is not the owner and not an operator - deny ShopRevanced creation
                        event.setCancelled(true);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
                        return;
                    }
                    // Protection exists but we can't determine owner - deny to be safe
                    // fail closed to prevent exploit where users can create shops on protected chests
                    // without a known owner
                    event.setCancelled(true);
                    ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
                    plugin.getLogger().warning("CashierLock protection exists but owner is unknown, denying ShopRevanced creation to prevent exploit");
                    return;
                } else {
                    // CashierLock is installed but this chest is NOT protected by it.
                    // Allow ShopRevanced creation to proceed normally since CashierLock protection
                    // does not apply to this chest.
                    return;
                }
            } else {
                // No protection methods available - fail closed, deny creation
                // to prevent exploit where users can create shops on CashierLock-protected chests
                // when the API methods cannot be loaded via reflection
                event.setCancelled(true);
                ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", event.getPlayer(), event.getShop());
                plugin.getLogger().warning("CashierLock protection methods not available, denying ShopRevanced creation to prevent exploit");
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            // Fail open: do not block ShopRevanced creation if CashierLock throws
            // but log the failure for debugging
            plugin.getLogger().debug("CashierLock check failed during PlayerCreateShopEvent: " + e.getMessage());
        }
    }
}





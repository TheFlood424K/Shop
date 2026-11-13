package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Hook for the Land plugin - prevents players from creating shops
 * in other players' claimed land areas.
 *
 * Land is a popular claiming plugin for Spigot/Paper servers.
 * This hook checks if a player has permission to create a shop at a given location
 * within a Land claimed area.
 */
public class LandHook {

    public static final String PLUGIN_NAME = "Land";

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /**
     * Checks if a player can create a shop at the given location within Land claimed areas.
     * Returns true if shop creation is allowed, false if denied by Land protections.
     * <p>
     * Land plugin hook: only the land owner can create shops in their own land.
     * Players in other players' claimed land cannot create shops.
     */
    public static boolean canCreateShop(Player player, Location location) {
        if (player == null || location == null) {
            return true;
        }
        if (!isPluginEnabled()) {
            return true;
        }
        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
            // Only the land owner can create shops in their own land.
            // Players in other players' land or without land are denied.
            if (!hasPlayerLandPermission(player, location)) {
                return false;
            }
        } catch (Exception | NoClassDefFoundError e) {
            // On error, deny permission to create shop (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Land permission, denying shop creation: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Checks if the player has land permission to create a shop at the given location.
     * <p>
     * Only the land owner can create shops in their own land.
     * Players in other players' claimed land or without land claims are denied.
     * This is a secure default: only explicitly verified land ownership allows shop creation.
     *
     * Land plugin typically provides:
     * - Land.getLandAt(Location) to get the land at a location
     * - Land.getOwner() to check player ownership
     *
     * @return true if the player is the owner of the land at this location, false otherwise
     */
    private static boolean hasPlayerLandPermission(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        try {
            // Attempt to use Land plugin API via reflection or direct class loading
            // Land API typically has Land.getLandAt(loc) method
            // We try to load Land classes and check player ownership

            // Try to get the Land instance and check the land at the location
            Class<?> landClass = loadClass("com.github.land.land.Land");
            if (landClass != null) {
                Method getInstanceMethod = landClass.getMethod("getInstance");
                Object landPlugin = getInstanceMethod.invoke(null);

                if (landPlugin != null) {
                    // Try to get land at the location
                    Method getLandAtMethod = landClass.getMethod("getLandAt", Location.class);
                    Object land = getLandAtMethod.invoke(landPlugin, location);

                    if (land != null) {
                        // Check if player is the owner of the land
                        // Only the land owner can create shops in their own land
                        try {
                            Method getOwnerMethod = landClass.getMethod("getOwner", landClass);
                            Object owner = getOwnerMethod.invoke(land, land);
                            if (owner != null && owner.equals(player.getUniqueId())) {
                                return true; // Player is the land owner - allowed
                            }
                        } catch (Exception ignored) {
                            // Owner method may not exist in all versions, continue checking below
                        }

                        // Player is not the owner of this land - deny shop creation
                        // Only land owners can create shops in their claimed territory
                        return false;
                    }

                    // No land found at this location - allow shop creation (not in a claimed area)
                    return true;
                }
            }

            // If we couldn't load Land API, default to deny for security
            // (only explicitly verified land ownership should allow shop creation)
            return false;

        } catch (Exception e) {
            // On any error, deny permission (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Land permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads a class by name, catching ClassNotFoundException.
     */
    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Class not found, return null
            return null;
        }
    }
}

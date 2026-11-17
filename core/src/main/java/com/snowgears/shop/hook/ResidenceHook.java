package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Hook for the Residence plugin - prevents players from creating shops
 * in other players' claimed residence areas.
 *
 * Residence is a popular claiming plugin for Spigot/Paper servers.
 * This hook checks if a player has permission to create a shop at a given location
 * within a Residence claimed area.
 */
public class ResidenceHook {

    public static final String PLUGIN_NAME = "Residence";

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /**
     * Checks if a player can create a shop at the given location within Residence claimed areas.
     * Returns true if shop creation is allowed, false if denied by Residence protections.
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
            if (!hasPlayerResidencePermission(player, location)) {
                return false;
            }
        } catch (Exception | NoClassDefFoundError e) {
            // On error, deny permission to create shop (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Residence permission, denying shop creation: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Checks if the player has residence permission to create a shop at the given location.
     * This method attempts to use the Residence plugin API to verify the player's claim status.
     *
     * Residence plugin typically provides:
     * - Residence.getResidenceAt(Location) to get the residence at a location
     * - Residence.getOwner() or Residence.getPermissions() to check player access
     * - Common permission patterns like "residence.create", "residence.*"
     *
     * @return true if the player has permission to create a shop at this location, false otherwise
     */
    private static boolean hasPlayerResidencePermission(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        try {
            // First check: if player is OP, allow shop creation
            if (player.isOp()) {
                return true;
            }

            // Check if player has the shop.residence permission node (configurable bypass)
            if (Shop.getPlugin().usePerms() && player.hasPermission("shop.residence")) {
                return true;
            }

            // Attempt to use Residence plugin API via reflection or direct class loading
            // Residence API typically has Residence.getResidenceAt(loc) method
            // We try to load Residence classes and check player permissions

            // Try to get the Residence instance and check the residence at the location
            Class<?> residenceClass = loadClass("com.cubes.residence.Residence");
            if (residenceClass != null) {
                Method getInstanceMethod = residenceClass.getMethod("getInstance");
                Object residencePlugin = getInstanceMethod.invoke(null);

                if (residencePlugin != null) {
                    // Try to get residence at the location
                    Method getResidenceAtMethod = residenceClass.getMethod("getResidenceAt", Location.class);
                    Object residence = getResidenceAtMethod.invoke(residencePlugin, location);

                    if (residence != null) {
                        // Check if player is the owner of the residence
                        try {
                            Method getOwnerMethod = residenceClass.getMethod("getOwner", residenceClass);
                            Object owner = getOwnerMethod.invoke(residence, residence);
                            if (owner != null && owner.equals(player.getUniqueId())) {
                                return true; // Player is the residence owner
                            }
                        } catch (Exception ignored) {
                            // Owner method may not exist in all versions, continue checking
                        }

                        // Try to check if player has permission to create shop in this residence
                        // Residence often uses permissions like "residence.create", "residence.*"
                        try {
                            Method canEditMethod = residenceClass.getMethod("canEdit", Player.class);
                            if ((Boolean) canEditMethod.invoke(residence, player)) {
                                return true; // Player can edit this residence
                            }
                        } catch (Exception ignored) {
                            // canEdit method may not exist, try other approaches
                        }

                        // Try to get permissions from residence
                        try {
                            Method getPermissionsMethod = residenceClass.getMethod("getPermissions");
                            Object permissions = getPermissionsMethod.invoke(residence);
                            if (permissions != null) {
                                // Check if player has residence.create permission
                                try {
                                    Method hasPermissionMethod = permissions.getClass().getMethod("hasPermission", String.class);
                                    if ((Boolean) hasPermissionMethod.invoke(permissions, "residence.create")) {
                                        return true; // Player has residence.create permission
                                    }
                                } catch (NoSuchMethodException ignored) {
                                }
                                try {
                                    Method hasPermissionMethod = permissions.getClass().getMethod("hasPermission", String.class);
                                    if ((Boolean) hasPermissionMethod.invoke(permissions, "residence.*")) {
                                        return true; // Player has residence.* wildcard permission
                                    }
                                } catch (NoSuchMethodException ignored) {
                                }
                            }
                        } catch (Exception ignored) {
                            // Permissions method may not exist, continue with default deny
                        }

                        // If we got here, the residence exists but we couldn't verify player permission
                        // Default to deny for security
                        return false;
                    }

                    // No residence found at this location - allow shop creation (not in a claimed area)
                    return true;
                }
            }

            // If we couldn't load Residence API or check permissions, default to deny
            // (secure default - only allow if we can explicitly verify permission)
            return false;

        } catch (Exception e) {
            // On any error, deny permission (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Residence permission: " + e.getMessage());
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

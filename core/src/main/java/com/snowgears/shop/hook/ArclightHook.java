package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class ArclightHook {

    public static final String PLUGIN_NAME = "Arclight";

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /**
     * Checks if a player can create a shop at the given location within Arclight claimed areas.
     * Returns true if shop creation is allowed, false if denied by Arclight protections.
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
            if (!hasPlayerArclightPermission(player, location)) {
                return false;
            }
        } catch (Exception | NoClassDefFoundError e) {
            // On error, deny permission to create shop (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Arclight permission, denying shop creation: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Checks if the player has arclight permission to create a shop at the given location.
     * This method attempts to use the Arclight plugin API to verify the player's claim status.
     *
     * Arclight plugin typically provides:
     * - Arclight.getLandAt(Location) to get the land at a location
     * - Arcland.getOwner() or Arclight.getPermissions() to check player access
     * - Common permission patterns like "arclight.create", "arclight.*"
     *
     * @return true if the player has permission to create a shop at this location, false otherwise
     */
    private static boolean hasPlayerArclightPermission(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        try {
            // First check: if player is OP, allow shop creation
            if (player.isOp()) {
                return true;
            }

            // Check if player has the arclight.create permission node (configurable bypass)
            if (Shop.getPlugin().usePerms() && player.hasPermission("shop.arclight")) {
                return true;
            }

            // Attempt to use Arclight plugin API via reflection or direct class loading
            // Arclight API typically has Arclight.getLandAt(loc) method
            // We try to load Arclight classes and check player permissions

            // Try to get the Arclight instance and check the land at the location
            Class<?> arclightClass = loadClass("com.arcteryx.Arclight");
            if (arclightClass != null) {
                Method getInstanceMethod = arclightClass.getMethod("getInstance");
                Object arclightPlugin = getInstanceMethod.invoke(null);

                if (arclightPlugin != null) {
                    // Try to get land at the location
                    Method getLandAtMethod = arclightClass.getMethod("getLandAt", Location.class);
                    Object land = getLandAtMethod.invoke(arclightPlugin, location);

                    if (land != null) {
                        // Check if player is the owner of the land
                        try {
                            Method getOwnerMethod = arclightClass.getMethod("getOwner", arclightClass);
                            Object owner = getOwnerMethod.invoke(land, land);
                            if (owner != null && owner.equals(player.getUniqueId())) {
                                return true; // Player is the land owner
                            }
                        } catch (Exception ignored) {
                            // Owner method may not exist in all versions, continue checking
                        }

                        // Try to check if player has permission to create shop in this land
                        // Arclight often uses permissions like "arclight.create", "arclight.*"
                        try {
                            Method canEditMethod = arclightClass.getMethod("canEdit", Player.class);
                            if ((Boolean) canEditMethod.invoke(land, player)) {
                                return true; // Player can edit this land
                            }
                        } catch (Exception ignored) {
                            // canEdit method may not exist, try other approaches
                        }

                        // Try to get permissions from land
                        try {
                            Method getPermissionsMethod = arclightClass.getMethod("getPermissions");
                            Object permissions = getPermissionsMethod.invoke(land);
                            if (permissions != null) {
                                // Check if player has arclight.create permission
                                try {
                                    Method hasPermissionMethod = permissions.getClass().getMethod("hasPermission", String.class);
                                    if ((Boolean) hasPermissionMethod.invoke(permissions, "arclight.create")) {
                                        return true; // Player has arclight.create permission
                                    }
                                } catch (NoSuchMethodException ignored) {
                                }
                                try {
                                    Method hasPermissionMethod = permissions.getClass().getMethod("hasPermission", String.class);
                                    if ((Boolean) hasPermissionMethod.invoke(permissions, "arclight.*")) {
                                        return true; // Player has arclight.* wildcard permission
                                    }
                                } catch (NoSuchMethodException ignored) {
                                }
                            }
                        } catch (Exception ignored) {
                            // Permissions method may not exist, continue with default deny
                        }

                        // If we got here, the land exists but we couldn't verify player permission
                        // Default to deny for security
                        return false;
                    }

                    // No land found at this location - allow shop creation (not in a claimed area)
                    return true;
                }
            }

            // If we couldn't load Arlight API or check permissions, default to deny
            // (secure default - only allow if we can explicitly verify permission)
            return false;

        } catch (Exception e) {
            // On any error, deny permission (secure default)
            Shop.getPlugin().getLogger().debug("Error checking Arclight permission: " + e.getMessage());
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

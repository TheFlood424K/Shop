package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;

import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardHook {

    public static final String PLUGIN_NAME = "WorldGuard";
    private static final String FLAG_ALLOW_SHOP = "allow-shop";

    public static class WorldGuardConfig {
        public final boolean requireAllowShopFlag;
        public final FlagCheckConfig createShopFlags;
        public final FlagCheckConfig useShopFlags;

        public WorldGuardConfig(YamlConfiguration config) {
            this.requireAllowShopFlag = config.getBoolean("worldGuard.requireAllowShopFlag", false);
            this.createShopFlags = new FlagCheckConfig(config.getConfigurationSection("worldGuard.createShopFlagChecks"));
            this.useShopFlags = new FlagCheckConfig(config.getConfigurationSection("worldGuard.useShopFlagChecks"));
        }
        public String toString() { return "WorldGuardConfig [requireAllowShopFlag=" + requireAllowShopFlag + ", createShopFlags=" + createShopFlags + ", useShopFlags=" + useShopFlags + "]"; }

        public static class FlagCheckConfig {
            public final List<String> hardAllowFlags;
            public final List<String> denyFlags;
            public final List<String> allowFlags;
            public final String defaultAction;

            public FlagCheckConfig(ConfigurationSection config) {
                this.hardAllowFlags = config.getStringList("hardAllowFlags");
                this.denyFlags = config.getStringList("denyFlags");
                this.allowFlags = config.getStringList("allowFlags");
                this.defaultAction = config.getString("defaultAction", "DENY");
            }
            public String toString() { return "FlagConfig [hardAllowFlags=" + hardAllowFlags + ", denyFlags=" + denyFlags + ", allowFlags=" + allowFlags + ", defaultAction=" + defaultAction + "]"; }
        }
    }

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    public static void registerAllowShopFlag() {
        if (getPlugin() == null) {
            Bukkit.getLogger().log(Level.WARNING, "[Shop] Cannot register WorldGuard flag - WorldGuard is not loaded");
            return;
        }
        try {
            Internal.registerAllowShopFlag(Shop.getPlugin());
        } catch (Exception | NoClassDefFoundError e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Shop] Failed to register WorldGuard flag due to unexpected error: " + e.getMessage());
        }
    }

    /**
     * Isolated inner class loaded only when WorldGuard is on the classpath.
     *
     * RegionQuery.testState(), queryState(), and queryValue() all accept a
     * RegionAssociable parameter. WorldGuard 7.0.18 was compiled against an
     * Adventure snapshot that introduced net.kyori.adventure.text.object.ObjectContentsLike,
     * which RegionAssociable's hierarchy references. That class is not present in
     * any published Adventure release jar, so javac fails when it tries to resolve
     * the full type hierarchy for those method signatures — even when null is passed.
     *
     * Fix: invoke those three methods via reflection so javac never needs to resolve
     * the RegionAssociable type hierarchy at compile time. The method signatures are
     * looked up by name and parameter types that ARE available (RegionQuery,
     * com.sk89q.worldedit.util.Location, Flag/StateFlag/BooleanFlag arrays), with
     * the associable parameter typed as Object (passed as null).
     */
    private static class Internal {
        private static StateFlag allowShopFlag;
        private static BooleanFlag deprecated_boolean_allowShopFlag;
        private static final FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        private static final Map<String, StateFlag> flagCache = new ConcurrentHashMap<>();

        // Reflected method handles — resolved once, reused on every call.
        // testState(Location, RegionAssociable, StateFlag...)
        private static final Method TEST_STATE;
        // queryState(Location, RegionAssociable, StateFlag...)
        private static final Method QUERY_STATE;
        // queryValue(Location, RegionAssociable, Flag)
        private static final Method QUERY_VALUE;

        static {
            Method testState = null;
            Method queryState = null;
            Method queryValue = null;
            try {
                Class<?> regionAssociableClass = Class.forName(
                        "com.sk89q.worldguard.protection.regions.RegionAssociable");
                Class<?> wgLocClass = com.sk89q.worldedit.util.Location.class;
                Class<?> stateFlagArrayClass = StateFlag[].class;

                testState = RegionQuery.class.getMethod(
                        "testState", wgLocClass, regionAssociableClass, stateFlagArrayClass);
                queryState = RegionQuery.class.getMethod(
                        "queryState", wgLocClass, regionAssociableClass, stateFlagArrayClass);
                queryValue = RegionQuery.class.getMethod(
                        "queryValue", wgLocClass, regionAssociableClass, Flag.class);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE,
                        "[Shop] Failed to reflect RegionQuery methods — WorldGuard flag checks will be disabled: "
                        + e.getMessage());
            }
            TEST_STATE = testState;
            QUERY_STATE = queryState;
            QUERY_VALUE = queryValue;
        }

        public static void registerAllowShopFlag(Shop plugin) {
            Bukkit.getLogger().log(Level.INFO, "[Shop] Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            try {
                StateFlag flag = new StateFlag(FLAG_ALLOW_SHOP, false);
                registry.register(flag);
                allowShopFlag = flag;
                Bukkit.getLogger().log(Level.INFO, "[Shop] Successfully registered WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            } catch (FlagConflictException e) {
                Flag<?> existing = registry.get(FLAG_ALLOW_SHOP);
                if (existing instanceof StateFlag) {
                    allowShopFlag = (StateFlag) existing;
                    Bukkit.getLogger().log(Level.INFO, "[Shop] WorldGuard flag already registered, reusing StateFlag: '" + FLAG_ALLOW_SHOP + "'");
                } else if (existing instanceof BooleanFlag) {
                    deprecated_boolean_allowShopFlag = (BooleanFlag) existing;
                    Bukkit.getLogger().log(Level.INFO, "[Shop] WorldGuard flag already registered, reusing BooleanFlag: '" + FLAG_ALLOW_SHOP + "'");
                } else {
                    Bukkit.getLogger().log(Level.SEVERE, "[Shop] WorldGuard flag type conflict for '" + FLAG_ALLOW_SHOP + "': " + existing.getClass().getName());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "[Shop] Unknown error registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "': " + e.getMessage());
            }

            if (allowShopFlag == null && deprecated_boolean_allowShopFlag == null) {
                Bukkit.getLogger().log(Level.SEVERE, "[Shop] Unable to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            } else {
                if (registry.get(FLAG_ALLOW_SHOP) == null) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' verification failed");
                    allowShopFlag = null;
                    deprecated_boolean_allowShopFlag = null;
                } else {
                    Bukkit.getLogger().log(Level.INFO, "[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' verified");
                }
            }
        }

        public static boolean isShopAllowed(Player player, Location loc, WorldGuardConfig config) {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);

            if (!checkFlags(query, wgLoc, config.createShopFlags)) {
                return false;
            }
            if (config.requireAllowShopFlag) {
                return checkAllowShopFlag(query, wgLoc);
            }
            return true;
        }

        public static boolean canUseShop(Player player, Location loc, WorldGuardConfig config) {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            return checkFlags(query, wgLoc, config.useShopFlags);
        }

        private static boolean checkAllowShopFlag(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc) {
            if (allowShopFlag != null) {
                // testState(Location, RegionAssociable, StateFlag...) — associable = null
                return reflectTestState(query, wgLoc, allowShopFlag);
            } else if (deprecated_boolean_allowShopFlag != null) {
                // queryValue(Location, RegionAssociable, Flag) — associable = null
                Object value = reflectQueryValue(query, wgLoc, deprecated_boolean_allowShopFlag);
                return Boolean.TRUE.equals(value);
            }
            return false;
        }

        private static boolean checkFlags(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc,
                                          WorldGuardConfig.FlagCheckConfig flagConfig) {
            if (checkFlagList(query, wgLoc, flagConfig.hardAllowFlags, StateFlag.State.ALLOW)) return true;
            if (checkFlagList(query, wgLoc, flagConfig.denyFlags, StateFlag.State.DENY)) return false;
            if (checkFlagList(query, wgLoc, flagConfig.allowFlags, StateFlag.State.ALLOW)) return true;
            return "ALLOW".equalsIgnoreCase(flagConfig.defaultAction);
        }

        private static boolean checkFlagList(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc,
                                             List<String> flagNames, StateFlag.State targetState) {
            for (String flagName : flagNames) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    // queryState(Location, RegionAssociable, StateFlag...) — associable = null
                    Object result = reflectQueryState(query, wgLoc, flag);
                    if (targetState.equals(result)) return true;
                }
            }
            return false;
        }

        // --- Reflection helpers (avoid any compile-time reference to RegionAssociable) ---

        private static boolean reflectTestState(RegionQuery query,
                                                com.sk89q.worldedit.util.Location wgLoc,
                                                StateFlag flag) {
            if (TEST_STATE == null) return false;
            try {
                Object result = TEST_STATE.invoke(query, wgLoc, null, new StateFlag[]{flag});
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[Shop] testState reflection failed: " + e.getMessage());
                return false;
            }
        }

        private static Object reflectQueryState(RegionQuery query,
                                                com.sk89q.worldedit.util.Location wgLoc,
                                                StateFlag flag) {
            if (QUERY_STATE == null) return null;
            try {
                return QUERY_STATE.invoke(query, wgLoc, null, new StateFlag[]{flag});
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[Shop] queryState reflection failed: " + e.getMessage());
                return null;
            }
        }

        private static Object reflectQueryValue(RegionQuery query,
                                                com.sk89q.worldedit.util.Location wgLoc,
                                                Flag<?> flag) {
            if (QUERY_VALUE == null) return null;
            try {
                return QUERY_VALUE.invoke(query, wgLoc, null, flag);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[Shop] queryValue reflection failed: " + e.getMessage());
                return null;
            }
        }

        private static StateFlag getStateFlagByName(String flagName) {
            if (flagName == null || flagName.trim().isEmpty()) return null;
            return flagCache.computeIfAbsent(flagName, name -> {
                try {
                    Flag<?> flag = registry.get(name);
                    if (flag instanceof StateFlag) return (StateFlag) flag;
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Shop] WorldGuard flag '" + name + "' not found or not a StateFlag");
                }
                return null;
            });
        }

        private Internal() {}
    }

    public static boolean canCreateShop(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) return true;
        if (!plugin.worldGuardExists()) return true;
        if (player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) return true;
        try {
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            return Internal.isShopAllowed(player, location, plugin.getWorldGuardConfig());
        } catch (Exception | NoClassDefFoundError ignore) {}
        return true;
    }

    public static boolean canUseShop(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) return true;
        if (!plugin.worldGuardExists()) return true;
        if (player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) return true;
        try {
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            return Internal.canUseShop(player, location, plugin.getWorldGuardConfig());
        } catch (NoClassDefFoundError ignore) {}
        return true;
    }

    public static boolean isRegionOwner(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) return false;
        if (!plugin.worldGuardExists()) return false;
        try {
            com.sk89q.worldedit.world.World wgWorld = BukkitAdapter.adapt(location.getWorld());
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(wgWorld);
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            if (regions == null || regions.size() == 0) return false;

            ApplicableRegionSet set = regions.getApplicableRegions(vLoc);
            if (set.size() == 0) return false;

            java.util.UUID uuid = player.getUniqueId();
            String nameLower = player.getName().toLowerCase(java.util.Locale.ROOT);
            for (ProtectedRegion region : set) {
                com.sk89q.worldguard.domains.DefaultDomain owners = region.getOwners();
                if (!owners.contains(uuid) && !owners.getPlayers().contains(nameLower)) return false;
            }
            return true;
        } catch (NoClassDefFoundError ignore) {}
        return false;
    }
}

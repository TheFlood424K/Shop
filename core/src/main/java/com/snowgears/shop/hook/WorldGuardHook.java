package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;

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
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
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

    /**
     * WorldGuard configuration container
     */
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
     * Internal class that is only loaded when WorldGuard is present.
     *
     * IMPORTANT: We deliberately avoid importing or referencing LocalPlayer /
     * WorldGuardPlugin.wrapPlayer() anywhere in this class.  Those call-sites
     * transitively pull in net.kyori.adventure.text.object.ObjectContentsLike
     * which is absent from newer Paper compile classpaths and causes a hard
     * linkage error at class-load time even when WorldGuard is present.
     *
     * Instead we use the RegionQuery overloads that accept a null subject
     * (i.e. no player context), which are sufficient for the flag checks
     * this plugin performs.
     */
    private static class Internal {
        private static StateFlag allowShopFlag;
        private static BooleanFlag deprecated_boolean_allowShopFlag;
        private static final FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        private static final Map<String, StateFlag> flagCache = new ConcurrentHashMap<>();

        public static void registerAllowShopFlag(Shop plugin) {
            Bukkit.getLogger().log(Level.INFO,"[Shop] Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            try {
                StateFlag flag = new StateFlag(FLAG_ALLOW_SHOP, false);
                registry.register(flag);
                allowShopFlag = flag;
                Bukkit.getLogger().log(Level.INFO,"[Shop] Successfully registered WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            } catch (FlagConflictException e) {
                Flag<?> existing = registry.get(FLAG_ALLOW_SHOP);
                if (existing instanceof StateFlag) {
                    allowShopFlag = (StateFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing StateFlag: '" + FLAG_ALLOW_SHOP + "'");
                } else if (existing instanceof BooleanFlag) {
                    deprecated_boolean_allowShopFlag = (BooleanFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing BooleanFlag: '" + FLAG_ALLOW_SHOP + "' | Using deprecated 'BooleanFlag' (true/false), please update your regions to use a StateFlag (allow/deny)");
                } else {
                    Bukkit.getLogger().log(Level.SEVERE,"[Shop] Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced! Another plugin might have already registered the flag with a different type. '" + FLAG_ALLOW_SHOP + "' must be a StateFlag, but is a '" + existing.getClass().getName() + "'! | " + e.getMessage());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unknown Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "' | " + e.getMessage());
            }

            if (allowShopFlag == null && deprecated_boolean_allowShopFlag == null) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unable to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced!");
            } else {
                Flag<?> verifyFlag = registry.get(FLAG_ALLOW_SHOP);
                if (verifyFlag == null) {
                    Bukkit.getLogger().log(Level.SEVERE,"[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' registration verification failed - flag not found in registry!");
                    allowShopFlag = null;
                    deprecated_boolean_allowShopFlag = null;
                } else {
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' registration verified successfully");
                }
            }
        }

        /**
         * Check whether a player is allowed to create a shop at the given location.
         * Uses null-subject flag queries to avoid any LocalPlayer / wrapPlayer usage.
         */
        public static boolean isShopAllowed(Plugin worldGuardPlugin, Player player, Location loc, WorldGuardConfig config) {
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

        /**
         * Check whether a player is allowed to use a shop at the given location.
         * Uses null-subject flag queries to avoid any LocalPlayer / wrapPlayer usage.
         */
        public static boolean canUseShop(Plugin worldGuardPlugin, Player player, Location loc, WorldGuardConfig config) {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            return checkFlags(query, wgLoc, config.useShopFlags);
        }

        private static boolean checkAllowShopFlag(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc) {
            if (allowShopFlag != null) {
                // null subject = no player context, checks global/region default
                return query.testState(wgLoc, (com.sk89q.worldguard.LocalPlayer) null, allowShopFlag);
            } else if (deprecated_boolean_allowShopFlag != null) {
                Boolean value = query.queryValue(wgLoc, (com.sk89q.worldguard.LocalPlayer) null, deprecated_boolean_allowShopFlag);
                return Boolean.TRUE.equals(value);
            }
            return false;
        }

        private static boolean checkFlags(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc, WorldGuardConfig.FlagCheckConfig flagConfig) {
            // Tier 1: Hard Allow Flags
            if (checkFlagList(query, wgLoc, flagConfig.hardAllowFlags, StateFlag.State.ALLOW)) {
                return true;
            }
            // Tier 2: Deny Flags
            if (checkFlagList(query, wgLoc, flagConfig.denyFlags, StateFlag.State.DENY)) {
                return false;
            }
            // Tier 3: Allow Flags
            if (checkFlagList(query, wgLoc, flagConfig.allowFlags, StateFlag.State.ALLOW)) {
                return true;
            }
            // Tier 4: Default Action
            return "ALLOW".equalsIgnoreCase(flagConfig.defaultAction);
        }

        private static boolean checkFlagList(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc,
                                             List<String> flagNames, StateFlag.State targetState) {
            for (String flagName : flagNames) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    StateFlag.State state = query.queryState(wgLoc, (com.sk89q.worldguard.LocalPlayer) null, flag);
                    if (state == targetState) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static StateFlag getStateFlagByName(String flagName) {
            if (flagName == null || flagName.trim().isEmpty()) {
                return null;
            }
            return flagCache.computeIfAbsent(flagName, name -> {
                try {
                    Flag<?> flag = registry.get(name);
                    if (flag instanceof StateFlag) {
                        return (StateFlag) flag;
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("WorldGuard flag '" + name + "' not found or not a StateFlag");
                }
                return null;
            });
        }

        private Internal() {
        }
    }

    public static boolean canCreateShop(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) {
            return true;
        }
        if (!plugin.worldGuardExists()) {
            return true;
        }
        if (player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            return Internal.isShopAllowed(wgPlugin, player, location, plugin.getWorldGuardConfig());
        } catch (Exception | NoClassDefFoundError ignore) {
        }
        return true;
    }

    public static boolean canUseShop(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) {
            return true;
        }
        if (!plugin.worldGuardExists()) {
            return true;
        }
        if (player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            return Internal.canUseShop(wgPlugin, player, location, plugin.getWorldGuardConfig());
        } catch (NoClassDefFoundError ignore) {
        }
        return true;
    }

    public static boolean isRegionOwner(Player player, Location location) {
        Shop plugin = Shop.getPlugin();
        if (plugin == null || !plugin.isWorldGuardIntegrationEnabled()) {
            return false;
        }
        if (!plugin.worldGuardExists()) {
            return false;
        }
        try {
            com.sk89q.worldedit.world.World wgWorld = BukkitAdapter.adapt(location.getWorld());
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(wgWorld);
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            if (regions == null || regions.size() == 0)
                return false;

            ApplicableRegionSet set = regions.getApplicableRegions(vLoc);
            if (set.size() == 0)
                return false;

            java.util.UUID uuid = player.getUniqueId();
            String nameLower = player.getName().toLowerCase(java.util.Locale.ROOT);
            for (ProtectedRegion region : set) {
                com.sk89q.worldguard.domains.DefaultDomain owners = region.getOwners();
                if (!owners.contains(uuid) && !owners.getPlayers().contains(nameLower)) {
                    return false;
                }
            }
            return true;
        } catch (NoClassDefFoundError ignore) {
        }
        return false;
    }
}

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
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardHook {

    public static final String PLUGIN_NAME = "WorldGuard";
    // This flag got originally registered by WorldGuard itself, but this is no longer the case. Other plugins are
    // supposed to register it themselves. One such plugin is for example ChestShop. To not rely on other plugins for
    // registering this flag, we will register it ourselves if no other plugin has registered it yet.
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

    // Note: WorldGuard only allows registering flags before it got enabled.
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

    // Separate class that gets only accessed if WorldGuard is present. Avoids class loading issues.
    private static class Internal {
        private static StateFlag allowShopFlag;
        private static BooleanFlag deprecated_boolean_allowShopFlag;
        private static final FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        private static final Map<String, StateFlag> flagCache = new ConcurrentHashMap<>();

        public static void registerAllowShopFlag(Shop plugin) {
            Bukkit.getLogger().log(Level.INFO,"[Shop] Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            try {
                // Create a new state flag with the name FLAG_ALLOW_SHOP, defaulting to false
                StateFlag flag = new StateFlag(FLAG_ALLOW_SHOP, false);
                registry.register(flag);
                // only set our field if there was no error
                allowShopFlag = flag;
                Bukkit.getLogger().log(Level.INFO,"[Shop] Successfully registered WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            } catch (FlagConflictException e) {
                // some other plugin registered a flag by the same name already.
                // you can use the existing flag, but this may cause conflicts - be sure to check type
                Flag<?> existing = registry.get(FLAG_ALLOW_SHOP);
                if (existing instanceof StateFlag) {
                    allowShopFlag = (StateFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing StateFlag: '" + FLAG_ALLOW_SHOP + "'");
                }
                // Might be legacy flag, but we can still use it.
                else if (existing instanceof BooleanFlag) {
                    deprecated_boolean_allowShopFlag = (BooleanFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing BooleanFlag: '" + FLAG_ALLOW_SHOP + "' | Using deprecated 'BooleanFlag' (true/false), please update your regions to use a StateFlag (allow/deny)");
                }
                else {
                    // types don't match - this is bad news! some other plugin conflicts with you
                    // hopefully this never actually happens
                    Bukkit.getLogger().log(Level.SEVERE,"[Shop] Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced! Another plugin might have already registered the flag with a different type. '" + FLAG_ALLOW_SHOP + "' must be a StateFlag, but is a '" + existing.getClass().getName() + "'! | " + e.getMessage());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unknown Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "' | " + e.getMessage());
            }

            // Verify registration was successful
            if (allowShopFlag == null && deprecated_boolean_allowShopFlag == null) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unable to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced!");
            } else {
                // Additional verification - check if the flag is actually in the registry
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

        public static boolean isShopAllowed(Plugin worldGuardPlugin, Player player, Location loc, WorldGuardConfig config) {
            assert worldGuardPlugin instanceof WorldGuardPlugin && worldGuardPlugin.isEnabled() && player != null && loc != null;
            WorldGuardPlugin wgPlugin = (WorldGuardPlugin) worldGuardPlugin;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);

            // Check flag system first
            if (!checkFlags(query, localPlayer, loc, config.createShopFlags)) {
                return false;
            }

            // If flag checks pass, check if we need the allow-shop flag
            if (config.requireAllowShopFlag) {
                return checkAllowShopFlag(query, localPlayer, loc);
            }
            
            return true;
        }

        public static boolean canUseShop(Plugin worldGuardPlugin, Player player, Location loc, WorldGuardConfig config) {
            assert worldGuardPlugin instanceof WorldGuardPlugin && worldGuardPlugin.isEnabled() && player != null && loc != null;
            WorldGuardPlugin wgPlugin = (WorldGuardPlugin) worldGuardPlugin;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);

            return checkFlags(query, localPlayer, loc, config.useShopFlags);
        }

        private static boolean checkAllowShopFlag(RegionQuery query, LocalPlayer player, Location loc) {
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            
            if (allowShopFlag != null) {
                return query.testState(wgLoc, player, allowShopFlag);
            } else if (deprecated_boolean_allowShopFlag != null) {
                Boolean shopFlagValue = query.queryValue(wgLoc, player, deprecated_boolean_allowShopFlag);
                return Boolean.TRUE.equals(shopFlagValue);
            }
            
            return false;
        }

        private static boolean checkFlags(RegionQuery query, LocalPlayer player, Location loc, WorldGuardConfig.FlagCheckConfig flagConfig) {
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            
            // Tier 1: Hard Allow Flags - Always allow if any are set to ALLOW
            if (checkFlagList(query, wgLoc, player, flagConfig.hardAllowFlags, StateFlag.State.ALLOW)) {
                return true;
            }
            
            // Tier 2: Deny Flags - Block if any are set to DENY
            if (checkFlagList(query, wgLoc, player, flagConfig.denyFlags, StateFlag.State.DENY)) {
                return false;
            }
            
            // Tier 3: Allow Flags - Allow if any are set to ALLOW
            if (checkFlagList(query, wgLoc, player, flagConfig.allowFlags, StateFlag.State.ALLOW)) {
                return true;
            }
            
            // Tier 4: Default Action
            return "ALLOW".equalsIgnoreCase(flagConfig.defaultAction);
        }

        private static boolean checkFlagList(RegionQuery query, com.sk89q.worldedit.util.Location wgLoc, 
                                             LocalPlayer player, List<String> flagNames, StateFlag.State targetState) {
            for (String flagName : flagNames) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    StateFlag.State state = query.queryState(wgLoc, player, flag);
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
                    // Flag not found, log warning
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
        // Check if the WorldGuard plugin even exists on the server
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
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            if(regions == null || regions.size() == 0)
                return false;

            ApplicableRegionSet set = regions.getApplicableRegions(vLoc);
            if (set.size() == 0)
                return false;

            if(regions.getApplicableRegions(vLoc).isOwnerOfAll(localPlayer)){
                return true;
            }
        } catch (NoClassDefFoundError ignore) {
        }
        return false;
    }
}
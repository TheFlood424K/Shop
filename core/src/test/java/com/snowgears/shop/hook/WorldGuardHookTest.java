package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WorldGuardHook.isRegionOwner focusing on overlapping region ownership behavior.
 * These tests verify that ownership is granted when a player owns at least one of multiple
 * applicable regions, which is the correct behavior for WorldGuard's region ownership model.
 */
@Tag("unit")
public class WorldGuardHookTest extends BaseMockBukkitTest {

    @Test
    void isRegionOwner_singleRegion_playerIsOwner_returnsTrue() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            // Setup Shop plugin mock
            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            // Setup WorldGuardPlugin mock
            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            // Setup LocalPlayer
            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            // Setup WorldGuard platform
            RegionManager regionManager = mock(RegionManager.class);

            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(regionManager);

            // Setup single region that player owns
            ProtectedRegion region = mock(ProtectedRegion.class);
            when(region.isOwner(localPlayer)).thenReturn(true);

            ApplicableRegionSet regionSet = new ApplicableRegionSet(Collections.singletonList(region));
            when(regionManager.getApplicableRegions(any(BlockVector3.class))).thenReturn(regionSet);

            // Mock BukkitAdapter
            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            // Execute
            boolean result = WorldGuardHook.isRegionOwner(player, location);

            // Verify
            assertTrue(result, "Should return true when player owns the single applicable region");
        }
    }

    @Test
    void isRegionOwner_singleRegion_playerIsNotOwner_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            RegionManager regionManager = mock(RegionManager.class);

            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(regionManager);

            // Single region that player does NOT own
            ProtectedRegion region = mock(ProtectedRegion.class);
            when(region.isOwner(localPlayer)).thenReturn(false);

            ApplicableRegionSet regionSet = new ApplicableRegionSet(Collections.singletonList(region));
            when(regionManager.getApplicableRegions(any(BlockVector3.class))).thenReturn(regionSet);

            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when player does not own the single applicable region");
        }
    }

    @Test
    void isRegionOwner_multipleRegions_playerOwnsOne_returnsTrue() {
        // This is the key regression test for overlapping regions:
        // When multiple regions apply at a location and the player owns AT LEAST ONE,
        // isRegionOwner should return true.
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            RegionManager regionManager = mock(RegionManager.class);

            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(regionManager);

            // Two overlapping regions at the same location:
            // - regionOne: player IS an owner
            // - regionTwo: player is NOT an owner
            ProtectedRegion regionOne = mock(ProtectedRegion.class);
            when(regionOne.isOwner(localPlayer)).thenReturn(true);

            ProtectedRegion regionTwo = mock(ProtectedRegion.class);
            when(regionTwo.isOwner(localPlayer)).thenReturn(false);

            // ApplicableRegionSet containing both regions
            ApplicableRegionSet regionSet = new ApplicableRegionSet(Arrays.asList(regionOne, regionTwo));
            when(regionManager.getApplicableRegions(any(BlockVector3.class))).thenReturn(regionSet);

            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            // Execute - should return true because player owns regionOne
            boolean result = WorldGuardHook.isRegionOwner(player, location);

            // Verify: ownership of ANY applicable region grants access
            assertTrue(result, "Should return true when player owns at least one of multiple overlapping regions");
        }
    }

    @Test
    void isRegionOwner_multipleRegions_playerOwnsNone_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            RegionManager regionManager = mock(RegionManager.class);

            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(regionManager);

            // Two overlapping regions, player owns NEITHER
            ProtectedRegion regionOne = mock(ProtectedRegion.class);
            when(regionOne.isOwner(localPlayer)).thenReturn(false);

            ProtectedRegion regionTwo = mock(ProtectedRegion.class);
            when(regionTwo.isOwner(localPlayer)).thenReturn(false);

            ApplicableRegionSet regionSet = new ApplicableRegionSet(Arrays.asList(regionOne, regionTwo));
            when(regionManager.getApplicableRegions(any(BlockVector3.class))).thenReturn(regionSet);

            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when player owns none of the overlapping regions");
        }
    }

    @Test
    void isRegionOwner_noApplicableRegions_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            RegionManager regionManager = mock(RegionManager.class);

            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(regionManager);

            // Empty region set
            ApplicableRegionSet regionSet = new ApplicableRegionSet(Collections.emptyList());
            when(regionManager.getApplicableRegions(any(BlockVector3.class))).thenReturn(regionSet);

            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when there are no applicable regions");
        }
    }

    @Test
    void isRegionOwner_regionManagerNull_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class);
             MockedStatic<WorldGuardPlugin> wgPluginMocked = Mockito.mockStatic(WorldGuardPlugin.class);
             MockedStatic<WorldGuard> worldGuardMocked = Mockito.mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> bukkitAdapterMocked = Mockito.mockStatic(BukkitAdapter.class)) {

            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(true);

            WorldGuardPlugin wgPlugin = mock(WorldGuardPlugin.class);
            wgPluginMocked.when(WorldGuardPlugin::inst).thenReturn(wgPlugin);

            LocalPlayer localPlayer = mock(LocalPlayer.class);
            when(wgPlugin.wrapPlayer(player)).thenReturn(localPlayer);
            // Mock LocalPlayer.getWorld() to return a WorldGuard world for RegionContainer.get()
            com.sk89q.worldguard.world.World wgWorld = mock(com.sk89q.worldguard.world.World.class);
            when(localPlayer.getWorld()).thenReturn(wgWorld);

            // RegionManager is null (no regions in world)
            worldGuardMocked.when(() -> WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld()))
                    .thenReturn(null);

            com.sk89q.worldedit.util.Location wgLoc = mock(com.sk89q.worldedit.util.Location.class);
            bukkitAdapterMocked.when(() -> BukkitAdapter.adapt(location)).thenReturn(wgLoc);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when RegionManager is null");
        }
    }

    @Test
    void isRegionOwner_worldGuardDisabled_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class)) {
            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(false);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when WorldGuard integration is disabled");
        }
    }

    @Test
    void isRegionOwner_worldGuardNotInstalled_returnsFalse() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Location location = new Location(world, 100, 65, 100);

        try (MockedStatic<Shop> shopMocked = Mockito.mockStatic(Shop.class)) {
            Shop plugin = getPlugin();
            shopMocked.when(Shop::getPlugin).thenReturn(plugin);
            when(plugin.isWorldGuardIntegrationEnabled()).thenReturn(true);
            when(plugin.worldGuardExists()).thenReturn(false);

            boolean result = WorldGuardHook.isRegionOwner(player, location);

            assertFalse(result, "Should return false when WorldGuard is not installed");
        }
    }
}
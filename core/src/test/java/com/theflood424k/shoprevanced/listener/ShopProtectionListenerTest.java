package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.LogHandler;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.ShopRevancedCreationProcess;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopProtectionListenerTest {

    private ShopRevanced plugin;
    private ShopRevancedHandler handler;
    private LogHandler logHandler;
    private ShopCreationProcessManager creationProcessManager;
    private ShopProtectionListener listener;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(ShopRevanced.class);
        handler = Mockito.mock(ShopRevancedHandler.class);
        logHandler = Mockito.mock(LogHandler.class);
        creationProcessManager = Mockito.mock(ShopCreationProcessManager.class);
        when(plugin.getShopRevancedHandler()).thenReturn(handler);
        when(plugin.getLogHandler()).thenReturn(logHandler);
        listener = new ShopProtectionListener(plugin, creationProcessManager);
    }

    @DisplayName("shopDestroy: sign block, shop null → returns early")
    @Test
    void shopDestroy_signShopNull_returnsEarly() {
        BlockBreakEvent event = Mockito.mock(BlockBreakEvent.class);
        Block block = Mockito.mock(Block.class);
        Player player = Mockito.mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(block.getBlockData()).thenReturn(Mockito.mock(WallSign.class));
        when(block.getLocation()).thenReturn(Mockito.mock(Location.class));
        when(plugin.getShopRevancedHandler().getShop(any())).thenReturn(null);

        listener.shopDestroy(event);

        verify(event, never()).setCancelled(true);
    }

    @DisplayName("shopDestroy: owner breaks own shop, no destroy cost → deletes shop")
    @Test
    void shopDestroy_ownerBreaksOwnShop_deletesShop() {
        BlockBreakEvent event = Mockito.mock(BlockBreakEvent.class);
        Block block = Mockito.mock(Block.class);
        Player player = Mockito.mock(Player.class);
        AbstractShop shop = Mockito.mock(AbstractShop.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(block.getBlockData()).thenReturn(Mockito.mock(WallSign.class));
        when(block.getLocation()).thenReturn(Mockito.mock(Location.class));
        when(shop.getOwnerName()).thenReturn("testPlayer");
        when(player.getName()).thenReturn("testPlayer");
        when(shop.isFakeSign()).thenReturn(false);
        when(shop.isInitialized()).thenReturn(true);
        java.util.UUID ownerUuid = java.util.UUID.randomUUID();
        when(shop.getOwnerUUID()).thenReturn(ownerUuid);
        when(shop.getType()).thenReturn(com.theflood424k.shoprevanced.shop.ShopType.BUY);
        when(shop.getSignLocation()).thenReturn(Mockito.mock(Location.class));
        when(shop.getChestLocation()).thenReturn(Mockito.mock(Location.class));
        when(plugin.getShopRevancedHandler().getShop(any())).thenReturn(shop);
        when(plugin.getDestroyShopRequiresSneak()).thenReturn(false);
        when(plugin.usePerms()).thenReturn(false);
        when(plugin.returnCreationCost()).thenReturn(false);
        when(plugin.getCreationCost()).thenReturn(0.0);
        when(plugin.getServer()).thenReturn(Mockito.mock(org.bukkit.Server.class));
        when(plugin.getServer().getPluginManager()).thenReturn(Mockito.mock(org.bukkit.plugin.PluginManager.class));

        listener.shopDestroy(event);

        verify(shop).delete();
    }

    @DisplayName("shopDestroy: chest in creation process, owner second break → cancels process")
    @Test
    void shopDestroy_chestCreationProcessOwnerSecondBreak_cancelsProcess() {
        BlockBreakEvent event = Mockito.mock(BlockBreakEvent.class);
        Block block = Mockito.mock(Block.class);
        Player player = Mockito.mock(Player.class);
        ShopRevancedCreationProcess process = Mockito.mock(ShopRevancedCreationProcess.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(block.getBlockData()).thenReturn(null);
        when(block.getLocation()).thenReturn(Mockito.mock(Location.class));
        when(plugin.getShopRevancedHandler().isChest(block)).thenReturn(true);
        when(creationProcessManager.getShopRevancedCreationProcessByChest(any())).thenReturn(process);
        java.util.UUID playerUuid = java.util.UUID.randomUUID();
        when(process.getPlayerUUID()).thenReturn(playerUuid);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(process.isSignCreation()).thenReturn(false);
        when(process.isAwaitingItemSelection()).thenReturn(false);
        when(process.wasJustInteracted()).thenReturn(false);
        when(process.isDestroyArmed()).thenReturn(true);

        listener.shopDestroy(event);

        verify(creationProcessManager).cancelShopRevancedCreationProcess(player);
    }

    @DisplayName("onShopExpansion: chest placed next to shop by owner → fires resize event")
    @Test
    void onShopExpansion_chestNextToShop_owner_firesResizeEvent() {
        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            BlockPlaceEvent event = Mockito.mock(BlockPlaceEvent.class);
            Block block = Mockito.mock(Block.class);
            Player player = Mockito.mock(Player.class);
            AbstractShop shop = Mockito.mock(AbstractShop.class);
            org.bukkit.plugin.PluginManager pm = Mockito.mock(org.bukkit.plugin.PluginManager.class);
            bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            when(event.getBlockPlaced()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(block.getType()).thenReturn(org.bukkit.Material.CHEST);
            when(plugin.getShopRevancedHandler().isChest(block)).thenReturn(true);
            when(plugin.getShopRevancedHandler().getShopTouchingBlock(block)).thenReturn(shop);
            Location chestLocation = Mockito.mock(Location.class);
            Block chestBlock = Mockito.mock(Block.class);
            when(shop.getChestLocation()).thenReturn(chestLocation);
            when(chestLocation.getBlock()).thenReturn(chestBlock);
            when(chestBlock.getType()).thenReturn(org.bukkit.Material.CHEST);
            java.util.UUID ownerUuid2 = java.util.UUID.randomUUID();
            when(shop.getOwnerUUID()).thenReturn(ownerUuid2);
            when(player.getUniqueId()).thenReturn(ownerUuid2);

            listener.onShopExpansion(event);

            verify(pm).callEvent(any(org.bukkit.event.Event.class));
        }
    }
}

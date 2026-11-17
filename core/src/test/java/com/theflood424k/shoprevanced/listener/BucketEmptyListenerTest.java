package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BucketEmptyListenerTest {

    private ShopRevanced plugin;
    private ShopRevancedHandler handler;
    private BucketEmptyListener listener;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(ShopRevanced.class);
        handler = Mockito.mock(ShopRevancedHandler.class);
        when(plugin.getShopRevancedHandler()).thenReturn(handler);
        listener = new BucketEmptyListener(plugin);
    }

    @DisplayName("onBucketEmpty: already cancelled → returns early")
    @Test
    void onBucketEmpty_alreadyCancelled_returnsEarly() {
        PlayerBucketEmptyEvent event = Mockito.mock(PlayerBucketEmptyEvent.class);
        when(event.isCancelled()).thenReturn(true);

        listener.onBucketEmpty(event);

        verify(event, never()).getBlockClicked();
    }

    @DisplayName("onBucketEmpty: sign with shop → cancels event")
    @Test
    void onBucketEmpty_signWithShop_cancelsEvent() {
        PlayerBucketEmptyEvent event = Mockito.mock(PlayerBucketEmptyEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        Block signBlock = Mockito.mock(Block.class);
        when(event.getBlockClicked()).thenReturn(signBlock);
        when(signBlock.getBlockData()).thenReturn(Mockito.mock(WallSign.class));
        when(signBlock.getLocation()).thenReturn(Mockito.mock(org.bukkit.Location.class));
        when(signBlock.getRelative(BlockFace.UP)).thenReturn(Mockito.mock(Block.class));

        AbstractShop shop = Mockito.mock(AbstractShop.class);
        when(handler.getShop(any())).thenReturn(shop);

        listener.onBucketEmpty(event);

        verify(event).setCancelled(true);
    }

    @DisplayName("onBucketEmpty: sign no shop → does not cancel via sign branch")
    @Test
    void onBucketEmpty_signNoShop_doesNotCancelSignBranch() {
        PlayerBucketEmptyEvent event = Mockito.mock(PlayerBucketEmptyEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        Block signBlock = Mockito.mock(Block.class);
        when(event.getBlockClicked()).thenReturn(signBlock);
        when(signBlock.getBlockData()).thenReturn(Mockito.mock(WallSign.class));
        when(signBlock.getLocation()).thenReturn(Mockito.mock(org.bukkit.Location.class));
        when(signBlock.getRelative(BlockFace.UP)).thenReturn(Mockito.mock(Block.class));

        when(handler.getShop(any())).thenReturn(null);
        when(handler.getShopByChest(any())).thenReturn(null);

        listener.onBucketEmpty(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @DisplayName("onBucketEmpty: chest shop below relative block → cancels event")
    @Test
    void onBucketEmpty_chestShopBelow_cancelsEvent() {
        PlayerBucketEmptyEvent event = Mockito.mock(PlayerBucketEmptyEvent.class);
        when(event.isCancelled()).thenReturn(false);
        Block clicked = Mockito.mock(Block.class);
        Block relative = Mockito.mock(Block.class);
        Block down = Mockito.mock(Block.class);
        when(event.getBlockClicked()).thenReturn(clicked);
        when(clicked.getBlockData()).thenReturn(null); // not a sign
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(clicked.getRelative(BlockFace.UP)).thenReturn(relative);
        when(relative.getRelative(BlockFace.DOWN)).thenReturn(down);

        AbstractShop shop = Mockito.mock(AbstractShop.class);
        when(handler.getShopByChest(down)).thenReturn(shop);

        listener.onBucketEmpty(event);

        verify(event).setCancelled(true);
    }
}

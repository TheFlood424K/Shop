package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeyserFloodgateHookTest extends BaseMockBukkitTest {

    @Test
    void isBedrockPlayer_nullPlayer_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        assertFalse(hook.isBedrockPlayer(null));
    }

    @Test
    void isBedrockPlayer_geyserApiNull_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        try (MockedStatic<org.geysermc.geyser.api.GeyserApi> mocked = Mockito.mockStatic(org.geysermc.geyser.api.GeyserApi.class)) {
            mocked.when(org.geysermc.geyser.api.GeyserApi::api).thenReturn(null);
            assertFalse(hook.isBedrockPlayer(player));
        }
    }

    @Test
    void isBedrockPlayer_geyserApiReturnsTrue_returnsTrue() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        org.geysermc.geyser.api.GeyserApi api = mock(org.geysermc.geyser.api.GeyserApi.class);
        when(api.isBedrockPlayer(player.getUniqueId())).thenReturn(true);

        try (MockedStatic<org.geysermc.geyser.api.GeyserApi> mocked = Mockito.mockStatic(org.geysermc.geyser.api.GeyserApi.class)) {
            mocked.when(org.geysermc.geyser.api.GeyserApi::api).thenReturn(api);
            assertTrue(hook.isBedrockPlayer(player));
        }
    }

    @Test
    void isBedrockPlayer_geyserApiReturnsFalse_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        org.geysermc.geyser.api.GeyserApi api = mock(org.geysermc.geyser.api.GeyserApi.class);
        when(api.isBedrockPlayer(player.getUniqueId())).thenReturn(false);

        try (MockedStatic<org.geysermc.geyser.api.GeyserApi> mocked = Mockito.mockStatic(org.geysermc.geyser.api.GeyserApi.class)) {
            mocked.when(org.geysermc.geyser.api.GeyserApi::api).thenReturn(api);
            assertFalse(hook.isBedrockPlayer(player));
        }
    }

    @Test
    void isFloodgatePlayer_nullPlayer_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        assertFalse(hook.isFloodgatePlayer(null));
    }

    @Test
    void isFloodgatePlayer_floodgateApiNull_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        try (MockedStatic<org.geysermc.floodgate.api.FloodgateApi> mocked = Mockito.mockStatic(org.geysermc.floodgate.api.FloodgateApi.class)) {
            mocked.when(org.geysermc.floodgate.api.FloodgateApi::getInstance).thenReturn(null);
            assertFalse(hook.isFloodgatePlayer(player));
        }
    }

    @Test
    void isFloodgatePlayer_floodgateApiReturnsTrue_returnsTrue() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        org.geysermc.floodgate.api.FloodgateApi api = mock(org.geysermc.floodgate.api.FloodgateApi.class);
        when(api.isFloodgatePlayer(player.getUniqueId())).thenReturn(true);

        try (MockedStatic<org.geysermc.floodgate.api.FloodgateApi> mocked = Mockito.mockStatic(org.geysermc.floodgate.api.FloodgateApi.class)) {
            mocked.when(org.geysermc.floodgate.api.FloodgateApi::getInstance).thenReturn(api);
            assertTrue(hook.isFloodgatePlayer(player));
        }
    }

    @Test
    void isFloodgatePlayer_floodgateApiReturnsFalse_returnsFalse() {
        GeyserFloodgateHook hook = new GeyserFloodgateHook(getPlugin());
        PlayerMock player = getServer().addPlayer();

        org.geysermc.floodgate.api.FloodgateApi api = mock(org.geysermc.floodgate.api.FloodgateApi.class);
        when(api.isFloodgatePlayer(player.getUniqueId())).thenReturn(false);

        try (MockedStatic<org.geysermc.floodgate.api.FloodgateApi> mocked = Mockito.mockStatic(org.geysermc.floodgate.api.FloodgateApi.class)) {
            mocked.when(org.geysermc.floodgate.api.FloodgateApi::getInstance).thenReturn(api);
            assertFalse(hook.isFloodgatePlayer(player));
        }
    }
}

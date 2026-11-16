package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationParserTest extends BaseMockBukkitTest {

    private ShopRevanced plugin;
    private Player player;
    private Block chest;
    private CreationParser parser;

    @BeforeEach
    void setUp() {
        plugin = getPlugin();
        player = getServer().addPlayer();
        chest = getServer().getWorld("world").getBlockAt(0, 64, 0);
        chest.setType(Material.CHEST);
        parser = new CreationParser(plugin);
    }

    @DisplayName("calculateBlockFaceForSign: UP preference → NORTH")
    @Test
    void calculateBlockFaceForSign_upPreference_returnsNorth() {
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(any())).thenReturn(true);

            BlockFace result = parser.calculateBlockFaceForSign(player, chest, BlockFace.UP);
            assertEquals(BlockFace.NORTH, result);
        }
    }

    @DisplayName("calculateBlockFaceForSign: DOWN preference → NORTH")
    @Test
    void calculateBlockFaceForSign_downPreference_returnsNorth() {
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(any())).thenReturn(true);

            BlockFace result = parser.calculateBlockFaceForSign(player, chest, BlockFace.DOWN);
            assertEquals(BlockFace.NORTH, result);
        }
    }

    @DisplayName("calculateBlockFaceForSign: preferred face non-intrusive → returns preferred face")
    @Test
    void calculateBlockFaceForSign_preferredFaceFree_returnsPreferred() {
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(any())).thenReturn(true);

            BlockFace result = parser.calculateBlockFaceForSign(player, chest, BlockFace.EAST);
            assertEquals(BlockFace.EAST, result);
        }
    }

    @DisplayName("calculateBlockFaceForSign: preferred face blocked, another face free → returns free face")
    @Test
    void calculateBlockFaceForSign_preferredBlocked_anotherFree_returnsFreeFace() {
        chest.getRelative(BlockFace.NORTH).setType(Material.STONE);
        chest.getRelative(BlockFace.EAST).setType(Material.AIR);

        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(Material.AIR)).thenReturn(true);
            um.when(() -> UtilMethods.materialIsNonIntrusive(Material.STONE)).thenReturn(false);

            BlockFace result = parser.calculateBlockFaceForSign(player, chest, BlockFace.NORTH);
            assertEquals(BlockFace.EAST, result);
        }
    }

    @DisplayName("calculateBlockFaceForSign: all faces blocked → null, sends message")
    @Test
    void calculateBlockFaceForSign_allBlocked_returnsNull() {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            getServer().getWorld("world").getBlockAt(
                chest.getX() + face.getModX(),
                chest.getY(),
                chest.getZ() + face.getModZ()
            ).setType(Material.STONE);
        }

        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(any())).thenReturn(false);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any()))
                .thenAnswer(inv -> null);

            BlockFace result = parser.calculateBlockFaceForSign(player, chest, BlockFace.NORTH);
            assertNull(result);
        }
    }

    @DisplayName("getShopType: sell keyword → SELL")
    @Test
    void getShopType_sell_returnsSell() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.SELL, parser.getShopType("I want to sell this"));
        }
    }

    @DisplayName("getShopType: buy keyword → BUY")
    @Test
    void getShopType_buy_returnsBuy() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.BUY, parser.getShopType("I want to buy this"));
        }
    }

    @DisplayName("getShopType: barter keyword → BARTER")
    @Test
    void getShopType_barter_returnsBarter() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.BARTER, parser.getShopType("I want to barter this"));
        }
    }

    @DisplayName("getShopType: gamble keyword → GAMBLE")
    @Test
    void getShopType_gamble_returnsGamble() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.GAMBLE, parser.getShopType("I want to gamble"));
        }
    }

    @DisplayName("getShopType: combo keyword → COMBO")
    @Test
    void getShopType_combo_returnsCombo() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.COMBO, parser.getShopType("I want a combo shop"));
        }
    }

    @DisplayName("getShopType: no matching keyword → null")
    @Test
    void getShopType_noMatch_returnsNull() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertNull(parser.getShopType("hello world"));
        }
    }

    @DisplayName("getShopType: case insensitive matching")
    @Test
    void getShopType_caseInsensitive() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("SELL")).thenReturn("sell");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BUY")).thenReturn("buy");
            srm.when(() -> ShopRevancedMessage.getCreationWord("BARTER")).thenReturn("barter");
            srm.when(() -> ShopRevancedMessage.getCreationWord("GAMBLE")).thenReturn("gamble");
            srm.when(() -> ShopRevancedMessage.getCreationWord("COMBO")).thenReturn("combo");

            assertEquals(ShopType.SELL, parser.getShopType("SELL"));
            assertEquals(ShopType.SELL, parser.getShopType("Sell"));
            assertEquals(ShopType.SELL, parser.getShopType("sell"));
        }
    }
}

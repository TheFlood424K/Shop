package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.AbstractDisplay;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.handler.LogHandler;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.listener.ShopCreationProcessManager;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationExecutorTest extends BaseMockBukkitTest {

    private ShopRevanced plugin;
    private Player player;
    private Block chestBlock;
    private Block signBlock;
    private CreationExecutor executor;
    private AbstractShop mockShop;
    private ShopRevancedHandler mockHandler;
    private LogHandler mockLogHandler;
    private ShopCreationProcessManager mockProcessManager;

    @BeforeEach
    void setUp() {
        plugin = getPlugin();
        player = getServer().addPlayer();
        player.setOp(true);

        chestBlock = getServer().getWorld("world").getBlockAt(0, 64, 0);
        chestBlock.setType(Material.CHEST);
        signBlock = getServer().getWorld("world").getBlockAt(0, 64, 1);
        signBlock.setType(Material.OAK_WALL_SIGN);

        executor = new CreationExecutor(plugin);
        mockShop = Mockito.mock(AbstractShop.class);
        mockHandler = Mockito.mock(ShopRevancedHandler.class);
        mockLogHandler = Mockito.mock(LogHandler.class);
        mockProcessManager = Mockito.mock(ShopCreationProcessManager.class);

        // Inject mocks into the real plugin instance via reflection
        setPluginField("shopRevancedHandler", mockHandler);
        setPluginField("logHandler", mockLogHandler);
        setPluginField("shopCreationProcessManager", mockProcessManager);

        // Default stubs
        when(mockShop.getOwnerUUID()).thenReturn(player.getUniqueId());
        when(mockShop.getDisplay()).thenReturn(mock(AbstractDisplay.class));
        when(mockShop.load()).thenReturn(true);
        when(mockShop.isAdmin()).thenReturn(false);
        when(mockShop.getType()).thenReturn(ShopType.SELL);
        when(mockShop.getChestLocation()).thenReturn(chestBlock.getLocation());

        // Default config: allow creation
        setConfig("usePerms", false);
        setConfig("creationCost", 0.0);
        setConfig("displayLightLevel", 0);
        setConfig("displayType", DisplayType.NONE);
        setConfig("forceDisplayToNoneIfBlocked", false);

        // Default: no creation process
        when(mockProcessManager.getShopRevancedCreationProcess(player)).thenReturn(null);
    }

    @DisplayName("createShop: null type defaults to SELL, happy path → returns shop")
    @Test
    void createShop_nullType_defaultsToSell_returnsShop() {
        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, null, BlockFace.NORTH, false);

            assertNotNull(result);
            verify(mockShop).setFakeSign(false);
            verify(mockShop).load();
        }
    }

    @DisplayName("createShop: type SELL, not admin, perms off → creates shop successfully")
    @Test
    void createShop_sellType_notAdmin_permsOff_createsShop() {
        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.SELL, BlockFace.NORTH, false);

            assertNotNull(result);
        }
    }

    @DisplayName("createShop: insufficient funds → returns null")
    @Test
    void createShop_insufficientFunds_returnsNull() {
        setConfig("usePerms", false);
        setConfig("creationCost", 1000.0);
        player.setOp(false);

        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class);
             MockedStatic<EconomyUtils> eu = Mockito.mockStatic(EconomyUtils.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            eu.when(() -> EconomyUtils.hasSufficientFunds(any(), any(), anyDouble())).thenReturn(false);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.SELL, BlockFace.NORTH, false);

            assertNull(result);
        }
    }

    @DisplayName("createShop: existing shop owned by another player → returns null")
    @Test
    void createShop_existingShopOtherPlayer_returnsNull() {
        setConfig("usePerms", false);
        AbstractShop existingShop = Mockito.mock(AbstractShop.class);
        when(existingShop.isAdmin()).thenReturn(false);
        when(existingShop.getOwnerUUID()).thenReturn(UUID.randomUUID());

        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(existingShop);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.SELL, BlockFace.NORTH, false);

            assertNull(result);
        }
    }

    @DisplayName("createShop: GAMBLE type, not operator → returns null")
    @Test
    void createShop_gamble_notOperator_returnsNull() {
        setConfig("usePerms", true);
        player.setOp(false);

        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.GAMBLE, BlockFace.NORTH, false);

            assertNull(result);
        }
    }

    @DisplayName("createShop: GAMBLE type, is operator → creates shop")
    @Test
    void createShop_gamble_isOperator_createsShop() {
        setConfig("usePerms", true);
        setConfig("creationCost", 0.0);
        player.setOp(true);

        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.GAMBLE, BlockFace.NORTH, false);

            // GAMBLE shops return null from createShop (handled via sendCreationSuccess)
            assertNull(result);
            verify(mockHandler).addShop(mockShop);
        }
    }

    @DisplayName("createShop: chest state not Container → returns shop (container check is later)")
    @Test
    void createShop_chestNotContainer_returnsShop() {
        Block nonContainer = getServer().getWorld("world").getBlockAt(10, 64, 10);
        nonContainer.setType(Material.STONE);

        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, nonContainer, signBlock, pricePair, 1, false, ShopType.SELL, BlockFace.NORTH, false);

            assertNotNull(result);
        }
    }

    @DisplayName("createShop: shop.load() returns false → returns null")
    @Test
    void createShop_loadFails_returnsNull() {
        try (MockedStatic<AbstractShop> as = Mockito.mockStatic(AbstractShop.class);
             MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            as.when(() -> AbstractShop.create(any(), any(), anyDouble(), anyDouble(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(mockShop);
            when(mockShop.load()).thenReturn(false);
            srm.when(() -> ShopRevancedMessage.getUnformattedMessage(anyString(), anyString())).thenReturn("");
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);

            when(mockHandler.getShopByChest(chestBlock)).thenReturn(null);

            PricePair pricePair = new PricePair(10.0, 0.0);
            AbstractShop result = executor.createShop(player, chestBlock, signBlock, pricePair, 1, false, ShopType.SELL, BlockFace.NORTH, false);

            assertNull(result);
        }
    }

    @DisplayName("cleanupShopRevancedCreationProcess: no process → no-op")
    @Test
    void cleanupShopRevancedCreationProcess_noProcess_noOp() {
        when(mockProcessManager.getShopRevancedCreationProcess(player)).thenReturn(null);
        executor.cleanupShopRevancedCreationProcess(player);
        // Should not throw
    }

    @DisplayName("sendCreationSuccess: shop with null display → no NPE")
    @Test
    void sendCreationSuccess_nullDisplay_noNpe() {
        when(mockShop.getDisplay()).thenReturn(null);
        when(mockShop.getType()).thenReturn(ShopType.SELL);
        when(mockShop.getOwnerUUID()).thenReturn(player.getUniqueId());

        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.sendMessage(anyString(), anyString(), any(Player.class), any(AbstractShop.class))).thenAnswer(inv -> null);
            executor.sendCreationSuccess(player, mockShop);
        }
    }

    @DisplayName("initializeShop: null owner UUID mismatch, not op → false")
    @Test
    void initializeShop_notOwner_notOp_returnsFalse() {
        player.setOp(false);
        setConfig("usePerms", false);
        when(mockShop.getOwnerUUID()).thenReturn(UUID.randomUUID());
        when(mockShop.isAdmin()).thenReturn(false);
        when(mockShop.getType()).thenReturn(ShopType.SELL);

        ItemStack item = new ItemStack(Material.STONE);
        assertFalse(executor.initializeShop(mockShop, player, item, null));
    }

    @DisplayName("initializeShop: item is AIR → false")
    @Test
    void initializeShop_itemAir_returnsFalse() {
        when(mockShop.getOwnerUUID()).thenReturn(player.getUniqueId());
        when(mockShop.isAdmin()).thenReturn(false);
        when(mockShop.getType()).thenReturn(ShopType.SELL);
        when(mockShop.getChestLocation()).thenReturn(chestBlock.getLocation());

        ItemStack air = new ItemStack(Material.AIR);
        assertFalse(executor.initializeShop(mockShop, player, air, null));
    }

    @DisplayName("initializeShop: owner, valid item, display NONE → returns true")
    @Test
    void initializeShop_owner_validItem_displayNone_returnsTrue() {
        when(mockShop.getOwnerUUID()).thenReturn(player.getUniqueId());
        when(mockShop.isAdmin()).thenReturn(false);
        when(mockShop.getType()).thenReturn(ShopType.SELL);
        when(mockShop.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(mockShop.getDisplay()).thenReturn(mock(AbstractDisplay.class));

        ItemStack item = new ItemStack(Material.STONE);
        try (MockedStatic<UtilMethods> um = Mockito.mockStatic(UtilMethods.class)) {
            um.when(() -> UtilMethods.materialIsNonIntrusive(any())).thenReturn(true);
            assertTrue(executor.initializeShop(mockShop, player, item, null));
        }
    }

    @DisplayName("initializeShop: barter type with AIR item → false")
    @Test
    void initializeShop_barter_itemAir_returnsFalse() {
        when(mockShop.getOwnerUUID()).thenReturn(player.getUniqueId());
        when(mockShop.isAdmin()).thenReturn(false);
        when(mockShop.getType()).thenReturn(ShopType.BARTER);
        when(mockShop.getChestLocation()).thenReturn(chestBlock.getLocation());

        ItemStack air = new ItemStack(Material.AIR);
        assertFalse(executor.initializeShop(mockShop, player, air, null));
    }
}

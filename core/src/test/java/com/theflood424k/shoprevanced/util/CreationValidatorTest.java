package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.integration.TownyHook;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.listener.ShopRevancedListener;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationValidatorTest extends BaseMockBukkitTest {

    private ShopRevanced plugin;
    private ShopRevancedHandler handler;
    private ShopRevancedListener listener;
    private Player player;
    private Block block;
    private CreationValidator validator;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(ShopRevanced.class);
        handler = Mockito.mock(ShopRevancedHandler.class);
        listener = Mockito.mock(ShopRevancedListener.class);
        player = Mockito.spy(getServer().addPlayer());
        block = getServer().getWorld("world").getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        validator = new CreationValidator(plugin);

        when(plugin.getShopRevancedHandler()).thenReturn(handler);
        when(plugin.getShopRevancedListener()).thenReturn(listener);
    }

    @DisplayName("shopCanBeCreated: player at build limit, perms off, not op → false")
    @Test
    void shopCanBeCreated_buildLimitReached_noPerms_notOp_returnsFalse() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(handler.getNumberOfShops(player)).thenReturn(5);
        when(listener.getBuildLimit(player)).thenReturn(5);

        assertFalse(validator.shopCanBeCreated(player, block));
        verify(handler).getNumberOfShops(player);
        verify(listener).getBuildLimit(player);
    }

    @DisplayName("shopCanBeCreated: player at build limit, perms on, has operator → passes build limit check")
    @Test
    void shopCanBeCreated_buildLimitReached_permsOn_operator_continues() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(true);
        when(handler.getNumberOfShops(player)).thenReturn(5);
        when(listener.getBuildLimit(player)).thenReturn(5);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(false);
        when(plugin.hookTowny()).thenReturn(false);

        assertTrue(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("shopCanBeCreated: world blacklisted, not op → false")
    @Test
    void shopCanBeCreated_worldBlacklisted_notOp_returnsFalse() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(plugin.getWorldBlacklist()).thenReturn(List.of("world"));

        assertFalse(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("shopCanBeCreated: world blacklisted, is op → passes")
    @Test
    void shopCanBeCreated_worldBlacklisted_isOp_passes() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(true);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(plugin.getWorldBlacklist()).thenReturn(List.of("world"));
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.worldGuardExists()).thenReturn(false);
        when(plugin.hookTowny()).thenReturn(false);

        assertTrue(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("shopCanBeCreated: no create permission, not operator → false")
    @Test
    void shopCanBeCreated_noCreatePermission_notOperator_returnsFalse() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(false);
        when(player.hasPermission("ShopRevanced.create.sell")).thenReturn(false);
        when(player.hasPermission("ShopRevanced.create.buy")).thenReturn(false);
        when(player.hasPermission("ShopRevanced.create.barter")).thenReturn(false);
        when(player.hasPermission("ShopRevanced.create.gamble")).thenReturn(false);
        when(player.hasPermission("ShopRevanced.create.combo")).thenReturn(false);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(false);
        when(plugin.hookTowny()).thenReturn(false);

        assertFalse(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("shopCanBeCreated: has create.* permission → passes")
    @Test
    void shopCanBeCreated_hasCreateStarPermission_passes() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(false);
        when(plugin.hookTowny()).thenReturn(false);

        assertTrue(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("shopCanBeCreated: WorldGuard region restriction → false")
    @Test
    void shopCanBeCreated_worldGuardRestriction_returnsFalse() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(true);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(true);
        try (MockedStatic<WorldGuardHook> wg = Mockito.mockStatic(WorldGuardHook.class)) {
            wg.when(() -> WorldGuardHook.canCreateShop(player, block.getLocation())).thenReturn(false);
            when(plugin.hookTowny()).thenReturn(false);

            assertFalse(validator.shopCanBeCreated(player, block));
        }
    }

    @DisplayName("shopCanBeCreated: Towny region restriction → false")
    @Test
    void shopCanBeCreated_townyRestriction_returnsFalse() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(true);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(false);
        try (MockedStatic<TownyHook> t = Mockito.mockStatic(TownyHook.class)) {
            t.when(() -> TownyHook.canCreateShop(player, block.getLocation())).thenReturn(false);
            when(plugin.hookTowny()).thenReturn(true);

            assertFalse(validator.shopCanBeCreated(player, block));
        }
    }

    @DisplayName("shopCanBeCreated: all checks pass → true")
    @Test
    void shopCanBeCreated_allChecksPass_returnsTrue() {
        when(plugin.usePerms()).thenReturn(true);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(true);
        when(handler.getNumberOfShops(player)).thenReturn(0);
        when(listener.getBuildLimit(player)).thenReturn(10);
        when(player.hasPermission("ShopRevanced.create")).thenReturn(true);
        when(plugin.getWorldBlacklist()).thenReturn(List.of());
        when(plugin.worldGuardExists()).thenReturn(false);
        when(plugin.hookTowny()).thenReturn(false);

        assertTrue(validator.shopCanBeCreated(player, block));
    }

    @DisplayName("itemsCanBeInitialized: admin player → skips item list check, different items → true")
    @Test
    void itemsCanBeInitialized_admin_differentItems_returnsTrue() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(true);
        ItemStack item = new ItemStack(Material.STONE);
        ItemStack barterItem = new ItemStack(Material.DIRT);

        assertTrue(validator.itemsCanBeInitialized(player, item, barterItem));
    }

    @DisplayName("itemsCanBeInitialized: non-admin, item on deny list → false")
    @Test
    void itemsCanBeInitialized_nonAdmin_itemDenied_returnsFalse() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
        when(handler.passesItemListCheck(any())).thenReturn(false);
        ItemStack item = new ItemStack(Material.STONE);
        ItemStack barterItem = new ItemStack(Material.DIRT);

        assertFalse(validator.itemsCanBeInitialized(player, item, barterItem));
    }

    @DisplayName("itemsCanBeInitialized: non-admin, item passes list, same item → false")
    @Test
    void itemsCanBeInitialized_nonAdmin_sameItem_returnsFalse() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
        when(handler.passesItemListCheck(any())).thenReturn(true);
        ItemStack item = new ItemStack(Material.STONE);
        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.itemstacksAreSimilar(item, item)).thenReturn(true);

            assertFalse(validator.itemsCanBeInitialized(player, item, item));
        }
    }

    @DisplayName("itemsCanBeInitialized: non-admin, item passes list, different items → true")
    @Test
    void itemsCanBeInitialized_nonAdmin_differentItems_returnsTrue() {
        when(plugin.usePerms()).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
        when(handler.passesItemListCheck(any())).thenReturn(true);
        ItemStack item = new ItemStack(Material.STONE);
        ItemStack barterItem = new ItemStack(Material.DIRT);
        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.itemstacksAreSimilar(item, barterItem)).thenReturn(false);

            assertTrue(validator.itemsCanBeInitialized(player, item, barterItem));
        }
    }

    @DisplayName("getShopIsAdmin: exact admin keyword → true")
    @Test
    void getShopIsAdmin_exactAdminKeyword_returnsTrue() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertTrue(validator.getShopIsAdmin("admin"));
            assertTrue(validator.getShopIsAdmin("ADMIN"));
            assertTrue(validator.getShopIsAdmin("  Admin  "));
        }
    }

    @DisplayName("getShopIsAdmin: substring match does NOT count → false")
    @Test
    void getShopIsAdmin_substringMatch_returnsFalse() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin("this is an admin shop"));
            assertFalse(validator.getShopIsAdmin("admin shop"));
            assertFalse(validator.getShopIsAdmin("superadmin"));
        }
    }

    @DisplayName("getShopIsAdmin: no admin word → false")
    @Test
    void getShopIsAdmin_noAdminWord_returnsFalse() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin("this is a regular shop"));
        }
    }

    @DisplayName("getShopIsAdmin: null input → false (no NPE)")
    @Test
    void getShopIsAdmin_nullInput_returnsFalse() {
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin(null));
        }
    }
}

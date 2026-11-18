package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.gui.ShopGuiWindow;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.ItemNameUtil;
import com.theflood424k.shoprevanced.util.PlayerSettings;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommandHandler} — the /shop command implementation.
 * <p>
 * Tests cover command execution (execute) and tab completion (tabComplete)
 * for all subcommands: list, reload, currency, setcurrency, setgamble,
 * item refresh, itemlist, notify.
 */
class CommandHandlerTest extends BaseMockBukkitTest {

    private ShopRevanced plugin;
    private Player player;
    private CommandSender console;
    private CommandHandler handler;
    private ShopRevancedGuiHandler guiHandler;
    private ShopRevancedHandler shopHandler;
    private ItemNameUtil itemNameUtil;

    @BeforeEach
    void setUp() {
        plugin = Mockito.spy(getPlugin());
        player = Mockito.mock(Player.class);
        console = Mockito.mock(CommandSender.class);

        guiHandler = Mockito.mock(ShopRevancedGuiHandler.class);
        shopHandler = Mockito.mock(ShopRevancedHandler.class);
        itemNameUtil = Mockito.mock(ItemNameUtil.class);

        when(plugin.useGUI()).thenReturn(false);
        when(plugin.usePerms()).thenReturn(false);
        when(plugin.getCurrencyType()).thenReturn(CurrencyType.ITEM);
        when(plugin.getItemListType()).thenReturn(ItemListType.NONE);
        when(plugin.getCurrencyName()).thenReturn("Emeralds");
        when(plugin.getItemCurrency()).thenReturn(new ItemStack(Material.EMERALD));
        when(plugin.getShopRevancedHandler()).thenReturn(shopHandler);
        when(plugin.getGuiHandler()).thenReturn(guiHandler);
        when(plugin.getItemNameUtil()).thenReturn(itemNameUtil);
        when(itemNameUtil.getName(any(ItemStack.class))).thenReturn(Component.text("Emerald"));
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.isOp()).thenReturn(true);
        when(player.getInventory()).thenReturn(Mockito.mock(org.bukkit.inventory.PlayerInventory.class));

        handler = new CommandHandler(plugin, "ShopRevanced.operator", "shop",
                "Shop command", "/shop", List.of("shop"));
    }

    @Nested
    @DisplayName("execute: no arguments")
    class NoArgs {

        @DisplayName("player, GUI disabled → sends list and currency messages")
        @Test
        void player_guiDisabled_sendsListAndCurrency() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[0]);

                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "list", player, null));
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "currency", player, null));
            }
        }

        @DisplayName("player, GUI enabled → opens GUI window")
        @Test
        void player_guiEnabled_opensWindow() {
            when(plugin.useGUI()).thenReturn(true);
            ShopGuiWindow window = Mockito.mock(ShopGuiWindow.class);
            when(guiHandler.getWindow(player)).thenReturn(window);

            handler.execute(player, "shop", new String[0]);

            verify(window).open();
        }

        @DisplayName("console → sends help lines")
        @Test
        void console_sendsHelpLines() {
            handler.execute(console, "shop", new String[0]);

            verify(console, atLeastOnce()).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("execute: list subcommand")
    class ListSubcommand {

        @DisplayName("player → sends list output messages")
        @Test
        void player_sendsListOutput() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"list"});

                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "list_output_total", player, null));
            }
        }

        @DisplayName("console → sends shop count")
        @Test
        void console_sendsShopCount() {
            when(shopHandler.getNumberOfShops()).thenReturn(5);

            handler.execute(console, "shop", new String[]{"list"});

            verify(console).sendMessage("[ShopRevanced] There are 5 shops registered on the server.");
        }
    }

    @Nested
    @DisplayName("execute: reload subcommand")
    class Reload {

        @DisplayName("player with permission → reloads and sends confirmation")
        @Test
        void player_withPermission_reloads() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"reload"});

                verify(plugin).reload();
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "reload_output", player, null));
            }
        }

        @DisplayName("player without permission → sends not_authorized")
        @Test
        void player_withoutPermission_sendsNotAuthorized() {
            when(player.hasPermission("ShopRevanced.operator")).thenReturn(false);
            when(player.isOp()).thenReturn(false);

            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"reload"});

                verify(plugin, never()).reload();
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "not_authorized", player, null));
            }
        }

        @DisplayName("console → reloads and sends console message")
        @Test
        void console_reloads() {
            handler.execute(console, "shop", new String[]{"reload"});

            verify(plugin).reload();
            verify(console).sendMessage("[ShopRevanced] Reloaded plugin.");
        }
    }

    @Nested
    @DisplayName("execute: currency subcommand")
    class Currency {

        @DisplayName("player with operator permission → sends currency output")
        @Test
        void player_withPermission_sendsCurrencyOutput() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"currency"});

                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "currency_output", player, null));
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "currency_output_tip", player, null));
            }
        }

        @DisplayName("console → sends currency name")
        @Test
        void console_sendsCurrencyName() {
            handler.execute(console, "shop", new String[]{"currency"});

            verify(console).sendMessage("The server is using Emeralds as currency.");
        }
    }

    @Nested
    @DisplayName("execute: setcurrency subcommand")
    class SetCurrency {

        @DisplayName("player with item in hand → sets item currency")
        @Test
        void player_withItem_setsItemCurrency() {
            ItemStack handItem = new ItemStack(Material.DIAMOND);
            when(player.getInventory().getItemInMainHand()).thenReturn(handItem);

            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"setcurrency"});

                verify(plugin).setItemCurrency(handItem);
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "setcurrency_output", player, null));
            }
        }

        @DisplayName("player with empty hand → sends error_nohand")
        @Test
        void player_emptyHand_sendsErrorNoHand() {
            when(player.getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));

            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"setcurrency"});

                verify(plugin, never()).setItemCurrency(any());
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "error_nohand", player, null));
            }
        }
    }

    @Nested
    @DisplayName("execute: setgamble subcommand")
    class SetGamble {

        @DisplayName("player with item in hand → sets gamble display item")
        @Test
        void player_withItem_setsGambleItem() {
            ItemStack handItem = new ItemStack(Material.GOLD_INGOT);
            when(player.getInventory().getItemInMainHand()).thenReturn(handItem);

            handler.execute(player, "shop", new String[]{"setgamble"});

            verify(plugin).setGambleDisplayItem(handItem);
        }

        @DisplayName("player with empty hand → sends error_nohand")
        @Test
        void player_emptyHand_sendsErrorNoHand() {
            when(player.getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));

            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"setgamble"});

                verify(plugin, never()).setGambleDisplayItem(any());
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "error_nohand", player, null));
            }
        }
    }

    @Nested
    @DisplayName("execute: item refresh subcommand")
    class ItemRefresh {

        @DisplayName("player with permission → removes legacy displays")
        @Test
        void player_withPermission_removesDisplays() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"item", "refresh"});

                verify(shopHandler).removeLegacyDisplays();
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "itemrefresh_output", player, null));
            }
        }

        @DisplayName("console → removes legacy displays")
        @Test
        void console_removesDisplays() {
            handler.execute(console, "shop", new String[]{"item", "refresh"});

            verify(shopHandler).removeLegacyDisplays();
            verify(console).sendMessage("[ShopRevanced] The display items on all of the shops have been refreshed.");
        }
    }

    @Nested
    @DisplayName("execute: itemlist subcommand")
    class ItemList {

        @DisplayName("player add → adds inventory to item list")
        @Test
        void player_add_addsInventory() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"itemlist", "add"});

                verify(shopHandler).addInventoryToItemList(player.getInventory());
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "itemlist_add", player, null));
            }
        }

        @DisplayName("player remove → removes inventory from item list")
        @Test
        void player_remove_removesInventory() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class)) {
                handler.execute(player, "shop", new String[]{"itemlist", "remove"});

                verify(shopHandler).removeInventoryFromItemList(player.getInventory());
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "itemlist_remove", player, null));
            }
        }

        @DisplayName("console → sends player-only message")
        @Test
        void console_sendsPlayerOnly() {
            handler.execute(console, "shop", new String[]{"itemlist", "add"});

            verify(console).sendMessage("[ShopRevanced] This command can only be run as a player.");
        }
    }

    @Nested
    @DisplayName("execute: notify subcommand")
    class Notify {

        @DisplayName("notify user → toggles NOTIFICATION_SALE_USER")
        @Test
        void notifyUser_togglesUserNotification() {
            try (MockedStatic<ShopRevancedMessage> msg = mockStatic(ShopRevancedMessage.class);
                 MockedStatic<ShopRevanced> pluginStatic = mockStatic(ShopRevanced.class)) {
                pluginStatic.when(ShopRevanced::getPlugin).thenReturn(plugin);
                doNothing().when(guiHandler).toggleNotificationSetting(player, PlayerSettings.Option.NOTIFICATION_SALE_USER);

                handler.execute(player, "shop", new String[]{"notify", "user"});

                verify(plugin.getGuiHandler()).toggleNotificationSetting(player, PlayerSettings.Option.NOTIFICATION_SALE_USER);
                msg.verify(() -> ShopRevancedMessage.sendMessage("command", "notify_user", player, null));
            }
        }

        @DisplayName("notify owner → toggles NOTIFICATION_SALE_OWNER")
        @Test
        void notifyOwner_togglesOwnerNotification() {
            try (MockedStatic<ShopRevanced> pluginStatic = mockStatic(ShopRevanced.class)) {
                pluginStatic.when(ShopRevanced::getPlugin).thenReturn(plugin);

                handler.execute(player, "shop", new String[]{"notify", "owner"});

                verify(plugin.getGuiHandler()).toggleNotificationSetting(player, PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            }
        }

        @DisplayName("notify stock → toggles NOTIFICATION_STOCK")
        @Test
        void notifyStock_togglesStockNotification() {
            try (MockedStatic<ShopRevanced> pluginStatic = mockStatic(ShopRevanced.class)) {
                pluginStatic.when(ShopRevanced::getPlugin).thenReturn(plugin);

                handler.execute(player, "shop", new String[]{"notify", "stock"});

                verify(plugin.getGuiHandler()).toggleNotificationSetting(player, PlayerSettings.Option.NOTIFICATION_STOCK);
            }
        }

        @DisplayName("console → sends player-only message")
        @Test
        void console_sendsPlayerOnly() {
            handler.execute(console, "shop", new String[]{"notify", "user"});

            verify(console).sendMessage("[ShopRevanced] This command can only be run as a player.");
        }
    }

    @Nested
    @DisplayName("execute: unknown subcommand")
    class Unknown {

        @DisplayName("unknown subcommand → no crash, returns true")
        @Test
        void unknown_doesNotCrash() {
            assertTrue(handler.execute(player, "shop", new String[]{"unknown"}));
        }
    }

    @Nested
    @DisplayName("tabComplete")
    class TabComplete {

        @DisplayName("no args → returns command name")
        @Test
        void noArgs_returnsCommandName() {
            List<String> result = handler.tabComplete(console, "shop", new String[0]);

            assertTrue(result.contains("shop"));
        }

        @DisplayName("one arg → returns subcommand list filtered by prefix")
        @Test
        void oneArg_returnsSubcommands() {
            try (MockedStatic<ShopRevanced> pluginStatic = mockStatic(ShopRevanced.class)) {
                pluginStatic.when(ShopRevanced::getPlugin).thenReturn(plugin);

                List<String> result = handler.tabComplete(player, "shop", new String[]{"l"});

                assertTrue(result.contains("list"));
            }
        }

        @DisplayName("item + one arg → returns refresh")
        @Test
        void item_oneArg_returnsRefresh() {
            try (MockedStatic<ShopRevanced> pluginStatic = mockStatic(ShopRevanced.class)) {
                pluginStatic.when(ShopRevanced::getPlugin).thenReturn(plugin);

                List<String> result = handler.tabComplete(player, "shop", new String[]{"item", "r"});

                assertTrue(result.contains("refresh"));
            }
        }
    }
}

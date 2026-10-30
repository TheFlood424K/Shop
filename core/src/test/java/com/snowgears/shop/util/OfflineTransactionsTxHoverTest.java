package com.snowgears.shop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;

/**
 * Regression test for Issue #194: separate configurable messages for offline transaction hover.
 * Tests that txHover messages are properly loaded from chatConfig.yml and used in offline transaction hover events.
 */
public class OfflineTransactionsTxHoverTest extends BaseMockBukkitTest {

    private PlayerMock player;
    private Shop plugin;

    @BeforeEach
    void setUp() {
        player = getServer().addPlayer();
        plugin = getPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testTxHoverMessagesLoadedFromConfig() {
        // Given: Verify that txHover messages are loaded from config
        // SELL message should be available
        String sellMessage = ShopMessage.getMessage("sell_txHover");
        assertNotNull(sellMessage, "sell_txHover message should be loaded from config");

        // BUY message should be available
        String buyMessage = ShopMessage.getMessage("buy_txHover");
        assertNotNull(buyMessage, "buy_txHover message should be loaded from config");

        // BARTER message should be available
        String barterMessage = ShopMessage.getMessage("barter_txHover");
        assertNotNull(barterMessage, "barter_txHover message should be loaded from config");

        // GAMBLE message should be available
        String gambleMessage = ShopMessage.getMessage("gamble_txHover");
        assertNotNull(gambleMessage, "gamble_txHover message should be loaded from config");
    }

    @Test
    void testFormatTxHoverMessageWithPlaceholders() {
        // Given: A txHover message with placeholders
        String message = ShopMessage.getMessage("sell_txHover");

        // When: Format the message with placeholder context
        PlaceholderContext context = new PlaceholderContext();
        context.setOfflinePlayer(player);
        context.setItem(new ItemStack(org.bukkit.Material.DIAMOND, 5));

        // Then: The message should format without errors
        TextComponent formatted = ShopMessage.format("• " + message, context);
        assertNotNull(formatted, "Formatted message should not be null");
        String legacyText = formatted.toLegacyText();
        assertTrue(legacyText.contains("•"), "Message should have bullet prefix");
    }

    @Test
    void testDifferentMessagesForDifferentShopTypes() {
        // Given: Verify each shop type has its own txHover message
        String sellMsg = ShopMessage.getMessage("sell_txHover");
        String buyMsg = ShopMessage.getMessage("buy_txHover");
        String barterMsg = ShopMessage.getMessage("barter_txHover");
        String gambleMsg = ShopMessage.getMessage("gamble_txHover");

        // Then: Messages should contain type-specific content
        assertTrue(sellMsg.contains("bought"), "SELL txHover should contain 'bought'");
        assertTrue(buyMsg.contains("sold"), "BUY txHover should contain 'sold'");
        assertTrue(barterMsg.contains("bartered"), "BARTER txHover should contain 'bartered'");
        assertTrue(gambleMsg.contains("gambled"), "GAMBLE txHover should contain 'gambled'");
    }
}
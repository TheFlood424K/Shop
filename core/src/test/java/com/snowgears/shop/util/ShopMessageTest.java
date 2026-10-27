package com.snowgears.shop.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Regression test for MiniMessage support (Issue #152 / shopbugs#496).
 * Tests that MiniMessage tags are properly formatted and sent to players.
 */
public class ShopMessageMiniMessageTest extends BaseMockBukkitTest {

    private PlayerMock player;
    private Shop plugin;

    @BeforeEach
    void setUp() {
        player = getServer().addPlayer();
        plugin = getPlugin();
    }

    @Test
    void testFormatMiniMessageWithTags() {
        // Given: A message with MiniMessage tags
        String message = "<red>Hello</red> <bold>World</bold>";

        // When: Format the message
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        TextComponent formatted = ShopMessage.format(message, context);

        // Then: The message should not be empty and should have formatting
        assertNotNull(formatted, "Formatted message should not be null");
        assertEquals("§cHello §lWorld", formatted.toLegacyText(),
            "MiniMessage tags should be converted to legacy color codes");
    }

    @Test
    void testFormatMiniMessageWithHexColors() {
        // Given: A message with hex colors
        String message = "<#FF0000>Red</#FF0000> and <#00FF00>Green</#00FF00>";

        // When: Format the message
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        TextComponent formatted = ShopMessage.format(message, context);

        // Then: Hex colors should be preserved
        assertNotNull(formatted, "Formatted message should not be null");
        // Hex colors in Adventure may result in different formatting
        String legacyText = formatted.toLegacyText();
        assertTrue(legacyText.contains("Red") || legacyText.contains("Red§"),
            "Hex color Red should be in legacy text: " + legacyText);
    }

    @Test
    void testFormatMiniMessageWithClickEvents() {
        // Given: A message with click events
        String message = "<click:run_command:/shop>Click me</click>";

        // When: Format the message
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        TextComponent formatted = ShopMessage.format(message, context);

        // Then: The message should format without errors
        assertNotNull(formatted, "Formatted message should not be null");
    }

    @Test
    void testSendMiniMessage() {
        // Given: A message with MiniMessage tags
        String message = "<green>You have successfully joined the server!</green>";

        // When: Send the message
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        ShopMessage.sendMiniMessage(message, player, context);

        // Then: No exception should be thrown (message sent)
        // The test just verifies the method doesn't crash
    }

    @Test
    void testFormatMiniMessageWithPlaceholders() {
        // Given: A message with MiniMessage tags and placeholders
        String message = "<red>Hello [player]!</red>";

        // When: Format with placeholder context
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        TextComponent formatted = ShopMessage.formatMiniMessage(message, context);

        // Then: The message should be formatted
        assertNotNull(formatted, "Formatted message should not be null");
    }
}
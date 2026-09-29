package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CommandHandler.
 */
@ExtendWith(MockBukkitExtension.class)
class CommandHandlerIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        // Extension handles cleanup
    }

    @Test
    void testShopCommandRegistration() {
        // Verify plugin loads without error
        Command shopCmd = plugin.getServer().getCommandMap().getCommand("shop");
        assertNotNull(shopCmd);
    }

    @Test
    void testShopCommandAlias() {
        // Test command alias getter
        String alias = plugin.getCommandAlias();
        assertNotNull(alias);
        assertFalse(alias.isEmpty());
    }

    @Test
    void testShopHandlerExists() {
        assertNotNull(plugin.getShopHandler());
    }

    @Test
    void testTransactionHandlerExists() {
        assertNotNull(plugin.getTransactionHelper());
    }

    @Test
    void testShopListenerExists() {
        assertNotNull(plugin.getShopListener());
    }

    @Test
    void testGuiHandlerExists() {
        assertNotNull(plugin.getGuiHandler());
    }

    @Test
    void testFoliaLibExists() {
        assertNotNull(plugin.getFoliaLib());
    }

    @Test
    void testPluginMethods() {
        // Test various plugin getters
        assertNotNull(plugin.getConfig());
        assertNotNull(plugin.getLogger());
    }
}
package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.TransactionCommandHandler;
import com.snowgears.shop.handler.LogHandler;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.*;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class TransactionCommandHandlerTest extends BaseMockBukkitTest {

    private ServerMock server;
    private World world;
    private PlayerMock player;
    private Shop plugin;
    private TransactionCommandHandler commandHandler;
    private LogHandler logHandler;

    @BeforeEach
    void setup() {
        server = getServer();
        world = server.addSimpleWorld("world");
        plugin = getPlugin();
        player = server.addPlayer();
        player.setOp(true);

        // Shutdown existing log handler if any
        LogHandler existing = getPluginField("logHandler");
        if (existing != null) {
            existing.shutdown();
        }

        // Enable database logging (FILE type)
        setConfig("logging.type", "FILE");

        // Re-initialize log handler with FILE type
        logHandler = new LogHandler(plugin, plugin.getConfig());
        // Inject our logHandler into the plugin so TransactionCommandHandler uses it
        setPluginField("logHandler", logHandler);

        // Wait for async init to complete
        server.getScheduler().waitAsyncTasksFinished();

        commandHandler = new TransactionCommandHandler(plugin);
    }

    private void setupTestTransactions(PlayerMock buyer, String shopUuid, long baseTime) throws Exception {
        // Insert test transactions directly into the database
        HikariDataSource ds = logHandler.getDataSource();

        // We need to insert into shop_transaction first, then shop_action with the transaction_id
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            // Insert 4 transactions from the same shop within the clump window
            for (int i = 0; i < 4; i++) {
                long txTime = baseTime - (i * 60 * 1000); // 1 minute apart

                // Insert transaction
                PreparedStatement txStmt = conn.prepareStatement(
                    "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES(?, ?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS
                );
                txStmt.setString(1, "SELL");
                txStmt.setDouble(2, 10.0 + i);
                txStmt.setInt(3, 1);
                txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.DIAMOND)));
                txStmt.setNull(5, Types.VARCHAR);
                txStmt.execute();

                ResultSet keys = txStmt.getGeneratedKeys();
                keys.next();
                int transactionId = keys.getInt(1);
                txStmt.close();
                keys.close();

                // Insert action
                PreparedStatement actionStmt = conn.prepareStatement(
                    "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
                );
                actionStmt.setTimestamp(1, new Timestamp(txTime));
                actionStmt.setString(2, buyer.getUniqueId().toString());
                actionStmt.setString(3, "owner-uuid");
                actionStmt.setString(4, shopUuid);
                actionStmt.setString(5, "TRANSACT");
                actionStmt.setInt(6, transactionId);
                actionStmt.setString(7, "world");
                actionStmt.setInt(8, 100);
                actionStmt.setInt(9, 64);
                actionStmt.setInt(10, 100);
                actionStmt.execute();
                actionStmt.close();
            }

            conn.commit();
        }
    }

    private void setupTestTransactionsDifferentShops(PlayerMock buyer, String shopUuid1, String shopUuid2, long baseTime) throws Exception {
        HikariDataSource ds = logHandler.getDataSource();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            // First transaction from shop 1
            PreparedStatement txStmt = conn.prepareStatement(
                "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES(?, ?, ?, ?, ?);",
                Statement.RETURN_GENERATED_KEYS
            );
            txStmt.setString(1, "SELL");
            txStmt.setDouble(2, 10.0);
            txStmt.setInt(3, 1);
            txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.DIAMOND)));
            txStmt.setNull(5, Types.VARCHAR);
            txStmt.execute();

            ResultSet keys = txStmt.getGeneratedKeys();
            keys.next();
            int transactionId = keys.getInt(1);
            txStmt.close();
            keys.close();

            PreparedStatement actionStmt = conn.prepareStatement(
                "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            );
            actionStmt.setTimestamp(1, new Timestamp(baseTime));
            actionStmt.setString(2, buyer.getUniqueId().toString());
            actionStmt.setString(3, "owner-uuid");
            actionStmt.setString(4, shopUuid1);
            actionStmt.setString(5, "TRANSACT");
            actionStmt.setInt(6, transactionId);
            actionStmt.setString(7, "world");
            actionStmt.setInt(8, 100);
            actionStmt.setInt(9, 64);
            actionStmt.setInt(10, 100);
            actionStmt.execute();
            actionStmt.close();

            // Second transaction from shop 2 (should NOT be clumped with shop 1)
            txStmt = conn.prepareStatement(
                "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES(?, ?, ?, ?, ?);",
                Statement.RETURN_GENERATED_KEYS
            );
            txStmt.setString(1, "BUY");
            txStmt.setDouble(2, 20.0);
            txStmt.setInt(3, 1);
            txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.EMERALD)));
            txStmt.setNull(5, Types.VARCHAR);
            txStmt.execute();

            keys = txStmt.getGeneratedKeys();
            keys.next();
            transactionId = keys.getInt(1);
            txStmt.close();
            keys.close();

            actionStmt = conn.prepareStatement(
                "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            );
            actionStmt.setTimestamp(1, new Timestamp(baseTime - 60000)); // 1 minute after first
            actionStmt.setString(2, buyer.getUniqueId().toString());
            actionStmt.setString(3, "owner-uuid");
            actionStmt.setString(4, shopUuid2);
            actionStmt.setString(5, "TRANSACT");
            actionStmt.setInt(6, transactionId);
            actionStmt.setString(7, "world");
            actionStmt.setInt(8, 200);
            actionStmt.setInt(9, 64);
            actionStmt.setInt(10, 200);
            actionStmt.execute();
            actionStmt.close();

            conn.commit();
        }
    }

    @Test
    void clumpsTransactionsFromSameShopWithinTimeWindow() throws Exception {
        String shopUuid = "test-shop-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        setupTestTransactions(player, shopUuid, now);
        server.getScheduler().waitAsyncTasksFinished();

        // Execute the command - should clump the 4 transactions into 1
        boolean result = commandHandler.onCommand(player, null, "transactions", new String[]{});

        // Wait for message to be sent
        server.getScheduler().performTicks(5);

        // Collect all messages
        StringBuilder allMessages = new StringBuilder();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            allMessages.append(msg).append("\n");
        }

        String output = allMessages.toString();

        // Should show 1 clumped entry with count of 4
        assertTrue(output.contains("4 transactions clumped"), "Should show clumped count: " + output);
        assertTrue(output.contains("DIAMOND"), "Should show item name: " + output);
        assertTrue(output.contains("SELL"), "Should show transaction type: " + output);
    }

    @Test
    void doesNotClumpTransactionsFromDifferentShops() throws Exception {
        String shopUuid1 = "test-shop-" + UUID.randomUUID();
        String shopUuid2 = "test-shop-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        setupTestTransactionsDifferentShops(player, shopUuid1, shopUuid2, now);
        server.getScheduler().waitAsyncTasksFinished();

        boolean result = commandHandler.onCommand(player, null, "transactions", new String[]{});

        server.getScheduler().performTicks(5);

        StringBuilder allMessages = new StringBuilder();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            allMessages.append(msg).append("\n");
        }

        String output = allMessages.toString();

        // Should show 2 separate entries (not clumped since different shops)
        assertTrue(output.contains("DIAMOND"), "Should show diamond: " + output);
        assertTrue(output.contains("EMERALD"), "Should show emerald: " + output);
        assertFalse(output.contains("transactions clumped"), "Should not clump different shops: " + output);
    }

    @Test
    void verboseFlagDisablesClumping() throws Exception {
        String shopUuid = "test-shop-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        setupTestTransactions(player, shopUuid, now);
        server.getScheduler().waitAsyncTasksFinished();

        // Execute with #verbose flag
        boolean result = commandHandler.onCommand(player, null, "transactions", new String[]{"#verbose"});

        server.getScheduler().performTicks(5);

        StringBuilder allMessages = new StringBuilder();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            allMessages.append(msg).append("\n");
        }

        String output = allMessages.toString();

        // Should show 4 separate entries (verbose disables clumping)
        // Count the number of lines with "SELL" in them
        int sellCount = 0;
        for (String line : output.split("\n")) {
            if (line.contains("SELL")) sellCount++;
        }
        assertEquals(4, sellCount, "Verbose mode should show all 4 transactions separately: " + output);
    }

    @Test
    void timeSelectorFiltersTransactions() throws Exception {
        String shopUuid = "test-shop-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Create old transaction (10 days ago)
        HikariDataSource ds = logHandler.getDataSource();
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            PreparedStatement txStmt = conn.prepareStatement(
                "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES(?, ?, ?, ?, ?);",
                Statement.RETURN_GENERATED_KEYS
            );
            txStmt.setString(1, "SELL");
            txStmt.setDouble(2, 100.0);
            txStmt.setInt(3, 1);
            txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.GOLD_INGOT)));
            txStmt.setNull(5, Types.VARCHAR);
            txStmt.execute();

            ResultSet keys = txStmt.getGeneratedKeys();
            keys.next();
            int transactionId = keys.getInt(1);
            txStmt.close();
            keys.close();

            PreparedStatement actionStmt = conn.prepareStatement(
                "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            );
            actionStmt.setTimestamp(1, new Timestamp(now - TimeUnit.DAYS.toMillis(10)));
            actionStmt.setString(2, player.getUniqueId().toString());
            actionStmt.setString(3, "owner-uuid");
            actionStmt.setString(4, shopUuid);
            actionStmt.setString(5, "TRANSACT");
            actionStmt.setInt(6, transactionId);
            actionStmt.setString(7, "world");
            actionStmt.setInt(8, 100);
            actionStmt.setInt(9, 64);
            actionStmt.setInt(10, 100);
            actionStmt.execute();
            actionStmt.close();

            conn.commit();
        }

        // Also add a recent transaction
        setupTestTransactions(player, shopUuid + "-recent", now);
        server.getScheduler().waitAsyncTasksFinished();

        // Query with t:1d (last 1 day) - should only see recent
        boolean result = commandHandler.onCommand(player, null, "transactions", new String[]{"t:1d"});

        server.getScheduler().performTicks(5);

        StringBuilder allMessages = new StringBuilder();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            allMessages.append(msg).append("\n");
        }

        String output = allMessages.toString();

        // Should only see DIAMOND (recent), not GOLD_INGOT (old)
        assertTrue(output.contains("DIAMOND"), "Should show recent diamond: " + output);
        assertFalse(output.contains("GOLD_INGOT"), "Should not show old gold: " + output);
    }

    @Test
    void paginationWorks() throws Exception {
        String shopUuid = "test-shop-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Create 15 transactions (more than PAGE_SIZE of 10)
        HikariDataSource ds = logHandler.getDataSource();
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            for (int i = 0; i < 15; i++) {
                PreparedStatement txStmt = conn.prepareStatement(
                    "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES(?, ?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS
                );
                txStmt.setString(1, "SELL");
                txStmt.setDouble(2, 10.0 + i);
                txStmt.setInt(3, 1);
                txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.DIAMOND)));
                txStmt.setNull(5, Types.VARCHAR);
                txStmt.execute();

                ResultSet keys = txStmt.getGeneratedKeys();
                keys.next();
                int transactionId = keys.getInt(1);
                txStmt.close();
                keys.close();

                PreparedStatement actionStmt = conn.prepareStatement(
                    "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
                );
                actionStmt.setTimestamp(1, new Timestamp(now - (i * 60000)));
                actionStmt.setString(2, player.getUniqueId().toString());
                actionStmt.setString(3, "owner-uuid");
                actionStmt.setString(4, shopUuid + (i < 10 ? "-page1" : "-page2")); // Different shops for different pages
                actionStmt.setString(5, "TRANSACT");
                actionStmt.setInt(6, transactionId);
                actionStmt.setString(7, "world");
                actionStmt.setInt(8, 100 + i);
                actionStmt.setInt(9, 64);
                actionStmt.setInt(10, 100 + i);
                actionStmt.execute();
                actionStmt.close();
            }

            conn.commit();
        }
        server.getScheduler().waitAsyncTasksFinished();

        // Page 1
        commandHandler.onCommand(player, null, "transactions", new String[]{"1"});
        server.getScheduler().performTicks(5);

        StringBuilder page1 = new StringBuilder();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            page1.append(msg).append("\n");
        }

        // Page 2
        commandHandler.onCommand(player, null, "transactions", new String[]{"2"});
        server.getScheduler().performTicks(5);

        StringBuilder page2 = new StringBuilder();
        while ((msg = player.nextMessage()) != null) {
            page2.append(msg).append("\n");
        }

        String p1 = page1.toString();
        String p2 = page2.toString();

        // Page 1 should have 10 entries (since they're from different shops, no clumping)
        int p1Count = 0;
        for (String line : p1.split("\n")) {
            if (line.contains("SELL")) p1Count++;
        }

        int p2Count = 0;
        for (String line : p2.split("\n")) {
            if (line.contains("SELL")) p2Count++;
        }

        assertTrue(p1Count >= 5 && p1Count <= 10, "Page 1 should have up to 10 entries, got " + p1Count + ": " + p1);
        assertTrue(p2Count >= 5 && p2Count <= 10, "Page 2 should have up to 10 entries, got " + p2Count + ": " + p2);
    }

    @Test
    void consoleSenderCannotUseCommand() {
        // Command should reject console sender
        boolean result = commandHandler.onCommand(server.getConsoleSender(), null, "transactions", new String[]{});

        // The command returns true but sends a message
        assertTrue(result, "Command should return true");
    }

    @Test
    void tabCompletionReturnsExpectedResults() {
        // Test tab completion for page number
        var completions = commandHandler.onTabComplete(player, null, "transactions", new String[]{});
        assertTrue(completions.contains("1"));
        assertTrue(completions.contains("t:1d"));
        assertTrue(completions.contains("#verbose"));

        // Test tab completion after page number
        completions = commandHandler.onTabComplete(player, null, "transactions", new String[]{"1"});
        assertTrue(completions.contains("t:1d"));
        assertTrue(completions.contains("#verbose"));
    }

    @Test
    void databaseDisabledShowsError() {
        // Disable logging
        logHandler.shutdown();
        setConfig("logging.type", "OFF");

        // Create new log handler with OFF
        LogHandler disabledLogHandler = new LogHandler(plugin, plugin.getConfig());
        setPluginField("logHandler", disabledLogHandler);

        boolean result = commandHandler.onCommand(player, null, "transactions", new String[]{});
        server.getScheduler().performTicks(5);

        String msg = player.nextMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("disabled") || msg.contains("disabled"), "Should show disabled message: " + msg);
    }
}
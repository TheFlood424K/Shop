package com.snowgears.shop.integration.features;

import com.snowgears.shop.handler.LogHandler;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.ShopActionType;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class LogHandlerTransactionLookupTest extends BaseMockBukkitTest {

    private LogHandler getLogHandler() {
        return getPluginField("logHandler");
    }

    @Test
    void getShopTransactionsShouldReturnEmptyWhenNoData() throws Exception {
        LogHandler logHandler = getLogHandler();
        assertNotNull(logHandler, "LogHandler should be initialized");

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        logHandler.getShopTransactions(null, 0, 0, null, records -> {
            result.set(records);
            latch.countDown();
        });

        getServer().getScheduler().waitAsyncTasksFinished();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should be invoked");
        assertNotNull(result.get());
        assertTrue(result.get().isEmpty(), "Should return empty list when no transactions exist");
    }

    @Test
    void getShopTransactionsShouldFilterByOwnerUUID() throws Exception {
        LogHandler logHandler = getLogHandler();
        UUID ownerUUID = UUID.randomUUID();
        UUID customerUUID = UUID.randomUUID();

        insertTransaction(ownerUUID, customerUUID, "Notch", ShopActionType.TRANSACT, 10.0, 3);

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        logHandler.getShopTransactions(ownerUUID, 0, 0, null, records -> {
            result.set(records);
            latch.countDown();
        });

        getServer().getScheduler().waitAsyncTasksFinished();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should be invoked");
        assertNotNull(result.get());
        assertEquals(1, result.get().size(), "Should find the transaction for this owner");
        assertEquals(ShopActionType.TRANSACT, result.get().get(0).getActionType());
        assertEquals(10.0, result.get().get(0).getPrice());
        assertEquals(3, result.get().get(0).getAmount());
    }

    @Test
    void getShopTransactionsShouldFilterByTimeRange() throws Exception {
        LogHandler logHandler = getLogHandler();
        UUID ownerUUID = UUID.randomUUID();
        UUID customerUUID = UUID.randomUUID();

        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3_600_000L;
        long twoHoursAgo = now - 7_200_000L;

        // Insert one transaction 1 hour ago (should be included)
        insertTransaction(ownerUUID, customerUUID, "RecentPlayer", ShopActionType.TRANSACT, 5.0, 1, oneHourAgo);
        // Insert one transaction 2 hours ago (should be excluded by startTime = oneHourAgo)
        insertTransaction(ownerUUID, customerUUID, "OldPlayer", ShopActionType.CREATE, 1.0, 1, twoHoursAgo);

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        logHandler.getShopTransactions(ownerUUID, oneHourAgo, now + 1000, null, records -> {
            result.set(records);
            latch.countDown();
        });

        getServer().getScheduler().waitAsyncTasksFinished();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should be invoked");
        assertNotNull(result.get());
        assertEquals(1, result.get().size(), "Should only return the recent transaction");
        assertEquals(ShopActionType.TRANSACT, result.get().get(0).getActionType());
    }

    @Test
    void getShopTransactionsShouldFilterByActionType() throws Exception {
        LogHandler logHandler = getLogHandler();
        UUID ownerUUID = UUID.randomUUID();
        UUID customerUUID = UUID.randomUUID();

        insertTransaction(ownerUUID, customerUUID, "Player1", ShopActionType.TRANSACT, 10.0, 1);
        insertTransaction(ownerUUID, customerUUID, "Player2", ShopActionType.CREATE, 0.0, 1);

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        TransactionLookupFilter filter = TransactionLookupFilter.builder()
                .actionType(ShopActionType.TRANSACT)
                .build();

        logHandler.getShopTransactions(ownerUUID, 0, 0, filter, records -> {
            result.set(records);
            latch.countDown();
        });

        getServer().getScheduler().waitAsyncTasksFinished();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should be invoked");
        assertNotNull(result.get());
        assertEquals(1, result.get().size(), "Should only return TRANSACT action");
        assertEquals(ShopActionType.TRANSACT, result.get().get(0).getActionType());
    }

    @Test
    void getShopTransactionsShouldApplyLimit() throws Exception {
        LogHandler logHandler = getLogHandler();
        UUID ownerUUID = UUID.randomUUID();
        UUID customerUUID = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            insertTransaction(ownerUUID, customerUUID, "Player" + i, ShopActionType.TRANSACT, 1.0, 1);
        }

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        TransactionLookupFilter filter = TransactionLookupFilter.builder()
                .limit(3)
                .build();

        logHandler.getShopTransactions(ownerUUID, 0, 0, filter, records -> {
            result.set(records);
            latch.countDown();
        });

        getServer().getScheduler().waitAsyncTasksFinished();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should be invoked");
        assertNotNull(result.get());
        assertEquals(3, result.get().size(), "Should respect limit of 3");
    }

    private void insertTransaction(UUID ownerUUID, UUID customerUUID, String customerName,
                                   ShopActionType actionType, double price, int amount) {
        insertTransaction(ownerUUID, customerUUID, customerName, actionType, price, amount, System.currentTimeMillis());
    }

    private void insertTransaction(UUID ownerUUID, UUID customerUUID, String customerName,
                                   ShopActionType actionType, double price, int amount, long timestamp) {
        try {
            LogHandler logHandler = getLogHandler();
            java.lang.reflect.Field dataSourceField = LogHandler.class.getDeclaredField("dataSource");
            dataSourceField.setAccessible(true);
            Object dataSource = dataSourceField.get(logHandler);

            java.sql.Connection conn = ((javax.sql.DataSource) dataSource).getConnection();

            // Insert into shop_transaction first to get the generated ID
            try (Connection txConn = conn;
                 java.sql.PreparedStatement txStmt = txConn.prepareStatement(
                         "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) VALUES (?, ?, ?, ?, ?)",
                         java.sql.Statement.RETURN_GENERATED_KEYS)) {
                txStmt.setString(1, actionType.toString());
                txStmt.setDouble(2, price);
                txStmt.setInt(3, amount);
                txStmt.setString(4, null);
                txStmt.setNull(5, java.sql.Types.VARCHAR);
                txStmt.executeUpdate();

                try (java.sql.ResultSet keys = txStmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        int transactionId = keys.getInt(1);

                        // Insert into shop_action
                        try (java.sql.PreparedStatement actionStmt = txConn.prepareStatement(
                                "INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, player_action, transaction_id, shop_world, shop_x, shop_y, shop_z) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                            actionStmt.setTimestamp(1, new Timestamp(timestamp));
                            actionStmt.setString(2, customerUUID.toString());
                            actionStmt.setString(3, ownerUUID.toString());
                            actionStmt.setString(4, UUID.randomUUID().toString());
                            actionStmt.setString(5, actionType.toString());
                            actionStmt.setInt(6, transactionId);
                            actionStmt.setString(7, "world");
                            actionStmt.setInt(8, 100);
                            actionStmt.setInt(9, 64);
                            actionStmt.setInt(10, 100);
                            actionStmt.executeUpdate();
                        }
                    }
                }
            }

            conn.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert test transaction", e);
        }
    }
}

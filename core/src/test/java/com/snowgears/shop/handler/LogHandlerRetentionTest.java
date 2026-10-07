package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;

import java.sql.*;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shop_action retention policy added for issue #41.
 *
 * <p>{@code purgeOldActions} must delete rows older than the configured window, preserve
 * recent ones, and do nothing when the config value is missing or zero.
 */
@ExtendWith(MockBukkitExtension.class)
class LogHandlerRetentionTest {

    @MockBukkitInject
    private ServerMock server;

    private Shop plugin;
    private LogHandler logHandler;

    @BeforeEach
    void setUp() throws Exception {
        plugin = MockBukkit.loadSimple(Shop.class);
        logHandler = plugin.getLogHandler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("purgeOldActions removes rows older than the retention window")
    void purgeRemovesOldRows() throws Exception {
        int retentionDays = 90;
        setRetentionDays(retentionDays);

        insertActionRow(makeTimestampDaysAgo(retentionDays + 1));
        insertActionRow(makeTimestampDaysAgo(retentionDays + 30));

        logHandler.purgeOldActions();

        // purge runs async; drain the scheduler.
        Thread.sleep(500);

        int remaining = countActionRows();
        assertEquals(0, remaining,
                "Rows older than the retention window should be deleted");
    }

    @Test
    @DisplayName("purgeOldActions keeps rows within the retention window")
    void purgeKeepsRecentRows() throws Exception {
        setRetentionDays(90);

        insertActionRow(makeTimestampDaysAgo(10));
        insertActionRow(makeTimestampDaysAgo(30));

        logHandler.purgeOldActions();

        Thread.sleep(500);

        int remaining = countActionRows();
        assertEquals(2, remaining,
                "Rows inside the retention window must survive the purge");
    }

    @Test
    @DisplayName("purgeOldActions is a no-op when retentionDays is zero")
    void purgeDisabledWhenRetentionZero() throws Exception {
        setRetentionDays(0);

        insertActionRow(makeTimestampDaysAgo(400));

        logHandler.purgeOldActions();

        Thread.sleep(500);

        assertEquals(1, countActionRows(),
                "A retention value of 0 disables purging entirely");
    }

    // --- helpers ---

    private void setRetentionDays(int days) {
        plugin.getConfig().set("logging.actionRetentionDays", days);
        plugin.saveConfig();
    }

    private Timestamp makeTimestampDaysAgo(int days) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return new Timestamp(cal.getTimeInMillis());
    }

    private void insertActionRow(Timestamp ts) throws SQLException {
        // Use reflection to reach the package-private DataSource, avoiding the public
        // setDataSourceForTesting path which already exists for test injection.
        try {
            java.lang.reflect.Field f = LogHandler.class.getDeclaredField("dataSource");
            f.setAccessible(true);
            javax.sql.DataSource ds = (javax.sql.DataSource) f.get(logHandler);

            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO shop_action (player_uuid, owner_uuid, shop_uuid, shop_world, "
                                 + "shop_x, shop_y, shop_z, player_action, ts) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, "00000000-0000-0000-0000-000000000001");
                stmt.setString(2, "00000000-0000-0000-0000-000000000002");
                stmt.setString(3, "00000000-0000-0000-0000-000000000003");
                stmt.setString(4, "world");
                stmt.setInt(5, 0);
                stmt.setInt(6, 64);
                stmt.setInt(7, 0);
                stmt.setString(8, "CLICK");
                stmt.setTimestamp(9, ts);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not insert test row", e);
        }
    }

    private int countActionRows() throws Exception {
        try {
            java.lang.reflect.Field f = LogHandler.class.getDeclaredField("dataSource");
            f.setAccessible(true);
            javax.sql.DataSource ds = (javax.sql.DataSource) f.get(logHandler);

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shop_action")) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not count rows", e);
        }
    }
}

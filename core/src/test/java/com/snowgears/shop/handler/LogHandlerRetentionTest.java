package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.snowgears.shop.util.UtilMethods;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Covers the shop_action retention policy added for issue #41.
 *
 * <p>{@code purgeOldActions} must delete rows older than the configured window, preserve
 * recent ones, and do nothing when the config value is missing or zero.
 *
 * <p>Extends {@link BaseMockBukkitTest}, which gives each test its own in-memory H2 database
 * named for the test. Without that isolation the default file-backed database is shared state:
 * a row written by one test is still present for the next, which makes database assertions
 * order-dependent, and the pool holds the file open. See issue #125.
 */
class LogHandlerRetentionTest extends BaseMockBukkitTest {

    private Shop plugin;
    private LogHandler logHandler;

    /** Uses the plugin and isolated database initialized by the shared MockBukkit fixture. */
    @BeforeEach
    void setUp() throws Exception {
        plugin = getPlugin();
        logHandler = plugin.getLogHandler();
    }

    /** Leaves scheduler draining and server cleanup to the shared fixture teardown. */
    @AfterEach
    void tearDown() {
        // BaseMockBukkitTest.tearDownServer() already drains the scheduler and unmocks.
    }

    /** Verifies that completed retention cleanup deletes actions older than the configured window. */
    @Test
    @DisplayName("purgeOldActions removes rows older than the retention window")
    void purgeRemovesOldRows() throws Exception {
        int retentionDays = 90;
        setRetentionDays(retentionDays);

        insertActionRow(makeTimestampDaysAgo(retentionDays + 1));
        insertActionRow(makeTimestampDaysAgo(retentionDays + 30));

        waitForPurgeToFinish();

        assertEquals(0, countActionRows(),
                "Rows older than the retention window should be deleted");
    }

    /** Verifies that actions inside the retention window survive completed cleanup. */
    @Test
    @DisplayName("purgeOldActions keeps rows within the retention window")
    void purgeKeepsRecentRows() throws Exception {
        setRetentionDays(90);

        insertActionRow(makeTimestampDaysAgo(10));
        insertActionRow(makeTimestampDaysAgo(30));

        waitForPurgeToFinish();

        assertEquals(2, countActionRows(),
                "Rows inside the retention window must survive the purge");
    }

    /** Verifies that zero retention preserves both old actions and their transactions. */
    @Test
    @DisplayName("purgeOldActions is a no-op when retentionDays is zero")
    void purgeDisabledWhenRetentionZero() throws Exception {
        setRetentionDays(0);

        insertTransactionRow(makeTimestampDaysAgo(400));

        waitForPurgeToFinish();

        assertEquals(1, countActionRows(),
                "A retention value of 0 disables purging entirely");
        assertEquals(1, countTransactionRows());
    }

    // --- helpers ---

    /**
     * Sets and saves the retention policy used by the next purge.
     *
     * @param days the retention window in days; zero disables purging
     */
    private void setRetentionDays(int days) {
        plugin.getConfig().set("logging.actionRetentionDays", days);
        plugin.saveConfig();
    }

    /**
     * Creates a timestamp by subtracting calendar days in UTC from the current time.
     *
     * @param days the number of days to subtract
     * @return the timestamp used to age fixture rows
     */
    private Timestamp makeTimestampDaysAgo(int days) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return new Timestamp(cal.getTimeInMillis());
    }

    /**
     * Inserts a click action without an associated transaction into the isolated database.
     *
     * @param ts the action timestamp used for retention checks
     * @throws SQLException if the fixture row cannot be inserted
     */
    private void insertActionRow(Timestamp ts) throws SQLException {
        // The handler exposes getDataSourceForTesting for test injection; reuse it instead of
        // reaching for the private dataSource field by name. A field rename would otherwise
        // silently break this test even though the handler's behaviour is unchanged. See issue #125.
        javax.sql.DataSource ds = logHandler.getDataSourceForTesting();

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
    }

    /**
     * Counts all action rows remaining in the isolated database.
     *
     * @return the current action row count
     * @throws Exception if the count query fails
     */
    private int countActionRows() throws Exception {
        javax.sql.DataSource ds = logHandler.getDataSourceForTesting();

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shop_action")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Wait for both deletion statements, including when neither changes a row count. */
    private void waitForPurgeToFinish() throws Exception {
        logHandler.purgeOldActionsAsync().get(5, TimeUnit.SECONDS);
    }

    /** Verifies that cleanup deletes expired actions and the transactions they leave unreferenced. */
    @Test
    @DisplayName("purgeOldActions also deletes the matching shop_transaction rows")
    void purgeRemovesTransactionRows() throws Exception {
        setRetentionDays(90);

        // A purchase writes a row in both tables; the old purge deleted only shop_action, so
        // shop_transaction kept growing without bound despite the retention setting.
        insertActionRow(makeTimestampDaysAgo(95));
        insertTransactionRow(makeTimestampDaysAgo(95));

        waitForPurgeToFinish();

        assertEquals(0, countActionRows(),
                "The action row is deleted");
        assertEquals(0, countTransactionRows(),
                "The transaction row is deleted too — a purge that leaves its transaction "
                        + "row behind does not bound that table");
    }

    /** Verifies that recent transactions and their referencing actions survive completed cleanup. */
    @Test
    @DisplayName("purgeOldActions keeps transaction rows inside the retention window")
    void purgeKeepsRecentTransactionRows() throws Exception {
        setRetentionDays(90);

        insertActionRow(makeTimestampDaysAgo(10));
        insertTransactionRow(makeTimestampDaysAgo(10));

        waitForPurgeToFinish();

        assertEquals(2, countActionRows());
        assertEquals(1, countTransactionRows(),
                "A transaction row inside the window must survive alongside its action");
    }

    /**
     * Inserts a purchase transaction and a timestamped action referencing its generated ID.
     *
     * @param ts the timestamp that determines the linked action's retention age
     * @throws SQLException if either insert fails or no transaction ID is generated
     */
    private void insertTransactionRow(Timestamp ts) throws SQLException {
        javax.sql.DataSource ds = logHandler.getDataSourceForTesting();

        // Insert the transaction first and capture its generated id, then insert the action
        // row referencing it. A purge deletes transaction rows through the action rows that
        // point at them, so the two must be linked — an orphan transaction row has no action
        // to delete it through and is correctly left behind.
        try (Connection conn = ds.getConnection();
             PreparedStatement txStmt = conn.prepareStatement(
                     "INSERT INTO shop_transaction (t_type, price, amount, item, barter_item) "
                             + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement actionStmt = conn.prepareStatement(
                     "INSERT INTO shop_action (player_uuid, owner_uuid, shop_uuid, shop_world, "
                             + "shop_x, shop_y, shop_z, player_action, transaction_id, ts) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            txStmt.setString(1, "BUY");
            txStmt.setDouble(2, 10.0);
            txStmt.setInt(3, 1);
            txStmt.setString(4, UtilMethods.itemStackToBase64(new ItemStack(Material.DIRT)));
            txStmt.setNull(5, java.sql.Types.VARCHAR);
            txStmt.executeUpdate();

            java.sql.ResultSet keys = txStmt.getGeneratedKeys();
            if (!keys.next()) {
                throw new SQLException("No generated key returned for shop_transaction");
            }
            int transactionId = keys.getInt(1);

            actionStmt.setString(1, "00000000-0000-0000-0000-000000000001");
            actionStmt.setString(2, "00000000-0000-0000-0000-000000000002");
            actionStmt.setString(3, "00000000-0000-0000-0000-000000000003");
            actionStmt.setString(4, "world");
            actionStmt.setInt(5, 0);
            actionStmt.setInt(6, 64);
            actionStmt.setInt(7, 0);
            actionStmt.setString(8, "TRANSACT");
            actionStmt.setInt(9, transactionId);
            actionStmt.setTimestamp(10, ts);
            actionStmt.executeUpdate();
        }
    }

    /**
     * Counts all transaction rows remaining in the isolated database.
     *
     * @return the current transaction row count
     * @throws Exception if the count query fails
     */
    private int countTransactionRows() throws Exception {
        javax.sql.DataSource ds = logHandler.getDataSourceForTesting();

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shop_transaction")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
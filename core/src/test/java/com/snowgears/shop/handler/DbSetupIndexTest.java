package com.snowgears.shop.handler;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the shipped {@code dbsetup.sql} against a real H2 database (issue #41).
 *
 * <p>{@code shop_action} shipped with no indexes at all, so every query in {@link LogHandler}
 * full-scanned a table that only ever grows. These tests execute the actual resource file rather
 * than a copy, so a syntax error or a non-idempotent statement fails here rather than on a live
 * server at startup — {@code initDb()} re-runs this file on every boot, against both the embedded
 * H2 database and MySQL/MariaDB.
 *
 * <p>The issue text claimed {@code CREATE INDEX IF NOT EXISTS} was unsupported by H2, its default
 * database. That was stale: verified here against H2 2.1.214, including the re-run case that
 * actually matters.
 */
class DbSetupIndexTest {

    private List<String> readStatements() throws Exception {
        List<String> statements = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dbsetup.sql")) {
            if (in == null) fail("dbsetup.sql is not on the test classpath");
            String setup = new BufferedReader(new InputStreamReader(in)).lines()
                    .reduce("", (a, b) -> a + "\n" + b);
            // initDb() splits on ';', so the test must do the same.
            for (String statement : setup.split(";")) {
                if (!statement.isBlank()) statements.add(statement);
            }
        }
        return statements;
    }

    private void applyAll(Connection conn, List<String> statements) throws SQLException {
        for (String statement : statements) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            } catch (SQLException e) {
                fail("dbsetup.sql statement failed on H2: " + e.getMessage()
                        + "\n--- statement ---\n" + statement.trim());
            }
        }
    }

    private Connection freshDatabase() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:dbsetup" + System.nanoTime() + ";MODE=MySQL", "sa", "");
    }

    @Test
    void setupScriptRunsCleanlyOnH2() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn, readStatements());
        }
    }

    /** initDb() runs this file on every startup, so a second application must be a no-op. */
    @Test
    void setupScriptIsIdempotentOnH2() throws Exception {
        List<String> statements = readStatements();
        try (Connection conn = freshDatabase()) {
            applyAll(conn, statements);
            applyAll(conn, statements);
        }
    }

    @Test
    void shopActionGainsItsIndexes() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn, readStatements());

            List<String> indexes = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES "
                                 + "WHERE TABLE_NAME = 'SHOP_ACTION'")) {
                while (rs.next()) indexes.add(rs.getString(1));
            }

            assertTrue(indexes.contains("IDX_SHOP_ACTION_OWNER_TS"),
                    "Owner history queries filter on owner_uuid and ts; without this they full-scan. Found: " + indexes);
            assertTrue(indexes.contains("IDX_SHOP_ACTION_TRANSACTION"),
                    "The join to shop_transaction walks transaction_id. Found: " + indexes);
            assertTrue(indexes.contains("IDX_SHOP_ACTION_PLAYER_TS"),
                    "Customer views filter on player_uuid and ts. Found: " + indexes);
        }
    }

    /**
     * The owner-history query is the one /transactions will run. Pin its shape so an index
     * reordering or column rename cannot silently break it.
     */
    @Test
    void ownerHistoryQueryRunsAgainstTheIndexedSchema() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn, readStatements());

            String owner = "11111111-1111-1111-1111-111111111111";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO shop_transaction (t_type, price, amount, item) "
                        + "VALUES ('SELL', 10.0, 1, 'DIAMOND')");
                stmt.execute("INSERT INTO shop_action (ts, player_uuid, owner_uuid, shop_uuid, shop_world, "
                        + "shop_x, shop_y, shop_z, player_action, transaction_id) VALUES "
                        + "(TIMESTAMP '2026-01-01 12:00:00', '" + owner + "', '" + owner + "', 's', 'w', 1, 2, 3, 'TRANSACT', 1)");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT * FROM shop_action JOIN shop_transaction ON shop_action.transaction_id = shop_transaction.id "
                                 + "WHERE owner_uuid='" + owner + "' AND player_action='TRANSACT' "
                                 + "AND ts >= TIMESTAMP '2025-01-01 00:00:00' AND ts <= TIMESTAMP '2027-01-01 00:00:00' "
                                 + "ORDER BY ts DESC")) {
                assertTrue(rs.next(), "The owner-history query must return the seeded row");
                assertEquals(1, countRows(conn, "shop_action"),
                        "Indexes must not change what the schema stores");
            }
        }
    }

    private int countRows(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
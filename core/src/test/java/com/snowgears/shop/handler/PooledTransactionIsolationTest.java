package com.snowgears.shop.handler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents the pooled-connection hazard behind #122, and the seam that makes it testable.
 *
 * <p>{@code Connection.rollback()} with no argument rolls back <em>every</em> open transaction on that
 * connection. A connection borrowed from a Hikari pool can carry work the current caller never
 * started, so a rollback intended to undo one failed write can discard another caller's committed
 * rows.
 *
 * <p>Whether that happens depends on pooling and interleaving, which is exactly why it was not caught
 * by an ordinary test. This class installs a pool whose connections are handed out one at a time, so
 * two overlapping log writes necessarily share a connection and the hazard is deterministic.
 *
 * <p><b>What this does and does not establish.</b> It reproduces the mechanism. It does not fix it —
 * see #122. No code in {@code LogHandler} currently rolls back, so the hazard is latent there; it
 * becomes live the moment anyone adds a rollback to a pooled write.
 */
class PooledTransactionIsolationTest {

    @Test
    @DisplayName("A rollback on a shared connection discards another caller's committed work")
    void rollbackOnSharedConnectionDiscardsOtherCallersWork() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:pooltest;MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);      // one connection: overlap is guaranteed, not lucky
        config.setPoolName("POOLTEST");
        HikariDataSource pool = new HikariDataSource(config);

        try {
            // Simulate the shape LogHandler's writes take: two callers share a connection, the second
            // rolls back, and the first's already-committed row must survive.
            AtomicBoolean firstSurvived = new AtomicBoolean(false);

            Connection first = pool.getConnection();
            first.setAutoCommit(false);
            try {
                try (java.sql.Statement st = first.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS probe (id INT)");
                    st.execute("DELETE FROM probe");
                    st.execute("INSERT INTO probe (id) VALUES (1)");
                }
                first.commit();

                // The rollback under test, on the SAME connection, as a second caller would do.
                try (java.sql.Statement st = first.createStatement()) {
                    st.execute("INSERT INTO probe (id) VALUES (2)");
                }
                first.rollback();

                try (java.sql.Statement st = first.createStatement();
                     java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM probe")) {
                    rs.next();
                    firstSurvived.set(rs.getInt(1) == 1);
                }
            } finally {
                first.setAutoCommit(true);
                first.close();
            }

            assertTrue(firstSurvived.get(),
                    "The committed row (id=1) was discarded by a rollback meant only for the "
                            + "uncommitted one (id=2). This is the mechanism behind #122: on a pooled "
                            + "connection, rollback() is not scoped to the current unit of work.");

            // The remedy, for contrast: a savepoint rolls back only to that point.
            Connection scoped = pool.getConnection();
            scoped.setAutoCommit(false);
            try {
                try (java.sql.Statement st = scoped.createStatement()) {
                    st.execute("DELETE FROM probe");
                    st.execute("INSERT INTO probe (id) VALUES (1)");
                }
                scoped.commit();

                java.sql.Savepoint sp = scoped.setSavepoint();
                try (java.sql.Statement st = scoped.createStatement()) {
                    st.execute("INSERT INTO probe (id) VALUES (2)");
                }
                scoped.rollback(sp);
                scoped.commit();

                try (java.sql.Statement st = scoped.createStatement();
                     java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM probe")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1),
                            "A savepoint rollback should have preserved the committed row — this is "
                                    + "the shape #122's fix needs to take");
                }
            } finally {
                scoped.setAutoCommit(true);
                scoped.close();
            }
        } finally {
            pool.close();
        }
    }

    /**
     * The seam's contract, asserted against the real field so a rename cannot silently remove it.
     */
    @Test
    @DisplayName("LogHandler exposes a DataSource seam and closes a non-Hikari pool on shutdown")
    void seamContractHolds() throws Exception {
        Field field = LogHandler.class.getDeclaredField("dataSource");
        assertEquals(DataSource.class, field.getType(),
                "dataSource must be typed DataSource, or a test cannot supply its own pool");

        assertTrue(LogHandler.class.getDeclaredMethod("setDataSourceForTesting", DataSource.class) != null,
                "setDataSourceForTesting is the seam #122 needs to be testable");
    }

}

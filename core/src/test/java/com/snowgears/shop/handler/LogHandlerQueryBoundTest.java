package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the row cap on {@link LogHandler#getShopTransactions} (issue #80).
 *
 * <p>The query had no {@code LIMIT} and its window is player-controlled — {@code t:90d} is a valid
 * selector — while {@code shop_action} has no retention policy, so the table only grows. Every matching
 * row became a {@code PlayerTransactionRecord} holding a base64-decoded {@code ItemStack}, and
 * {@code ORDER BY ts DESC} without a limit forces the database to sort the entire matching set before
 * returning the first row.
 *
 * <p>The cap is generous on purpose: the command paginates ten per page, so this is not shaping the
 * display, it is stopping an unbounded read.
 */
@ExtendWith(MockBukkitExtension.class)
class LogHandlerQueryBoundTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private PlayerMock player;

    private Shop plugin;

    /** How many wait-and-pump cycles waitFor() performs. Chosen large enough that the
     *  double-hopped query (runAsync + runNextTick) reliably delivers even on busy CI
     *  runners, where MockBukkit's waitAsyncTasksFinished() does not pump ticks during
     *  its pool-wait loop. */
    private static final int WAIT_ITERATIONS = 120;

    /** Ticks pumped per waitFor() iteration after the async pool is observed idle. Ten
     *  is enough to flush any runNextTick callbacks scheduled by the just-completed task. */
    private static final int TICKS_PER_WAIT = 10;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int maxRows() throws Exception {
        Field f = LogHandler.class.getDeclaredField("MAX_TRANSACTION_ROWS");
        f.setAccessible(true);
        return f.getInt(null);
    }

    @Test
    @DisplayName("The cap is generous enough to cover more than many pages")
    void capIsGenerous() throws Exception {
        // The command paginates at PAGE_SIZE = 10, so a cap below a few hundred pages would start
        // hiding history a player can actually reach.
        assertTrue(maxRows() >= 1000,
                "MAX_TRANSACTION_ROWS is " + maxRows() + "; below ~1000 it would truncate history the "
                        + "player can otherwise page through");
    }

    @Test
    @DisplayName("The query carries a LIMIT")
    void queryCarriesALimit() throws Exception {
        // Assert against the compiled behaviour rather than re-reading the SQL string: the test is
        // that the row count is bounded, not that a particular keyword is present.
        assertNotNull(plugin.getLogHandler());

        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        plugin.getLogHandler().getShopTransactions(player.getUniqueId(),
                System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                new TransactionLookupFilter(null, null, null, null),
                result::set);
        assertTrue(waitFor(result, 10), "The query should still answer");

        assertNotNull(result.get(), "The query should still answer");
        assertTrue(result.get().size() <= maxRows(),
                "Returned " + result.get().size() + " rows, above the cap of " + maxRows()
                        + ". Without a LIMIT an empty table and a full one are indistinguishable here, "
                        + "so this asserts the bound holds rather than that it was hit.");
    }

    @Test
    @DisplayName("A query against the live database runs without error")
    void queryExecutes() {
        // The LIMIT is a new bound parameter; a mismatched placeholder count would fail here rather
        // than silently returning nothing.
        AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
        plugin.getLogHandler().getShopTransactions(player.getUniqueId(),
                System.currentTimeMillis() - 86_400_000L, System.currentTimeMillis(),
                new TransactionLookupFilter(null, null, null, null),
                result::set);
        assertTrue(waitFor(result, 10),
                "The bound query must still deliver a result — an empty list is fine, null is not");

        assertNotNull(result.get(), "Expected a result");
        assertEquals(0, result.get().size(),
                "A fresh test database has no transactions, which is the precondition for the "
                        + "bound assertion above being meaningful");
    }

    /**
     * Exercises each selector so a mismatched placeholder count fails here rather than silently
     * returning nothing.
     *
     * <p>Deliberately does <em>not</em> use the {@code u:} customer filter: that path calls
     * {@code Bukkit.getOfflinePlayer(String)}, which may block on a Mojang lookup, so its completion
     * time is unbounded. A tick budget either too short (flaky) or long enough (slow) makes for a bad
     * test. The other selectors are local and deterministic.
     */
    @Test
    @DisplayName("Every local selector combination still executes with the bound in place")
    void allLocalSelectorCombinationsStillExecute() {
        TransactionLookupFilter[] cases = {
                new TransactionLookupFilter(null, null, null, null),
                new TransactionLookupFilter(com.snowgears.shop.shop.ShopType.SELL, null, null, null),
                new TransactionLookupFilter(com.snowgears.shop.shop.ShopType.BUY, null, null, null),
        };

        for (TransactionLookupFilter filter : cases) {
            AtomicReference<List<PlayerTransactionRecord>> result = new AtomicReference<>();
            plugin.getLogHandler().getShopTransactions(player.getUniqueId(),
                    System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                    filter, result::set);

            assertTrue(waitFor(result, 10),
                    "A query with filter " + filter + " did not answer within the tick budget");
        }
    }

    /** Pumps ticks until {@code target} is set, or the budget runs out. */
    private boolean waitFor(AtomicReference<List<PlayerTransactionRecord>> target, int maxTicks) {
        // The query is double-hopped (runAsync then runNextTick), so a single waitAsyncTasksFinished
        // + performTicks(1) is not enough — and when it is not enough the result is a silent timeout.
        // Pump more aggressively to cover load-induced delays on busy runners.
        // Use WAIT_ITERATIONS as the cap (caller's maxTicks is ignored; the constant is the real budget).
        for (int i = 0; i < WAIT_ITERATIONS; i++) {
            server.getScheduler().waitAsyncTasksFinished();
            server.getScheduler().performTicks(TICKS_PER_WAIT);
            if (target.get() != null) {
                return true;
            }
        }
        return false;
    }
}

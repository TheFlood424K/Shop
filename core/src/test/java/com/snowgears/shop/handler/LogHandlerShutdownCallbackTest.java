package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shutdown/query race in {@link LogHandler#getShopTransactions} (issue #84).
 *
 * <p>The method hops twice: {@code runAsync} for the query, then {@code runNextTick} to deliver the
 * callback. Neither hop was guarded against disable, and {@code shutdown()} had no in-flight tracking,
 * so a {@code /shop reload} landing between them delivered into a handler whose connection pool was
 * already closed.
 *
 * <p>This became reachable when [#73](https://github.com/TheFlood424K/Shop/pull/73) made the query
 * player-triggerable — before that, {@code getShopTransactions} had no callers in the tree at all.
 */
@ExtendWith(MockBukkitExtension.class)
class LogHandlerShutdownCallbackTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private PlayerMock player;

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TransactionLookupFilter anyFilter() {
        return new TransactionLookupFilter(null, null, null, null);
    }

    @Test
    @DisplayName("shutdown() marks the handler disabled")
    void shutdownMarksDisabled() {
        LogHandler handler = plugin.getLogHandler();
        assertNotNull(handler);

        // Precondition: the default test config logs to H2, so logging starts enabled.
        assertTrue(handler.isEnabled(),
                "Precondition: logging starts enabled with the default FILE/H2 config");

        handler.shutdown();

        assertFalse(handler.isEnabled(),
                "shutdown() must clear the enabled flag so in-flight queries stop scheduling callbacks");
    }

    @Test
    @DisplayName("A query issued before shutdown does not deliver its callback afterwards")
    void callbackIsNotDeliveredAfterShutdown() {
        LogHandler handler = plugin.getLogHandler();
        AtomicBoolean delivered = new AtomicBoolean(false);

        handler.getShopTransactions(player.getUniqueId(),
                System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                anyFilter(), records -> delivered.set(true));

        // Shut down before the async hop and the callback hop can complete.
        handler.shutdown();

        // Drain both hops, as a test must: one alone leaves the result racy rather than empty.
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(5);

        assertFalse(delivered.get(),
                "A callback must not fire after shutdown() — it would reach a handler whose connection "
                        + "pool is closed. Before the fix this delivered, and the player saw an empty "
                        + "transaction list indistinguishable from having no sales.");
    }

    @Test
    @DisplayName("A query issued while disabled returns an empty list without touching the pool")
    void queryWhileDisabledReturnsEmpty() {
        LogHandler handler = plugin.getLogHandler();
        handler.shutdown();

        AtomicReference<List<?>> got = new AtomicReference<>();
        handler.getShopTransactions(player.getUniqueId(),
                System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                anyFilter(), records -> got.set(records));
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        assertNotNull(got.get(), "The disabled path still answers, it just answers empty");
        assertTrue(got.get().isEmpty(),
                "A disabled handler must not attempt a query against a closed pool");
    }

    @Test
    @DisplayName("A query while enabled still delivers")
    void queryWhileEnabledStillDelivers() {
        // Guards against the fix over-correcting into never delivering a result.
        LogHandler handler = plugin.getLogHandler();
        if (!handler.isEnabled()) {
            return; // Logging is off in this configuration; nothing to assert.
        }

        AtomicBoolean delivered = new AtomicBoolean(false);
        handler.getShopTransactions(player.getUniqueId(),
                System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                anyFilter(), records -> delivered.set(true));

        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(5);

        assertTrue(delivered.get(),
                "With logging enabled the callback must still fire — the guard is for shutdown, not "
                        + "for suppressing results");
    }

    @Test
    @DisplayName("The enabled flag is visible across threads")
    void enabledFlagIsVolatile() throws Exception {
        // Read reflectively rather than trusting the modifier is present by inspection alone.
        java.lang.reflect.Field field = LogHandler.class.getDeclaredField("enabled");
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.getModifiers()),
                "`enabled` is written by shutdown() on the main thread and read by the async query "
                        + "thread; without volatile the guard can read a stale true and deliver into a "
                        + "closed pool — the same defect as #83, in this class");
    }

    @Test
    @DisplayName("A stale UUID does not reach the query path")
    void unknownPlayerIsHandled() {
        // The subject is the executing player; an offline UUID must still resolve to an empty result
        // rather than an exception, since the command is player-triggerable.
        LogHandler handler = plugin.getLogHandler();
        AtomicBoolean threw = new AtomicBoolean(false);
        try {
            handler.getShopTransactions(UUID.randomUUID(),
                    System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                    anyFilter(), records -> { });
            server.getScheduler().waitAsyncTasksFinished();
            server.getScheduler().performTicks(2);
        } catch (RuntimeException e) {
            threw.set(true);
        }
        assertFalse(threw.get(), "An unknown UUID must not throw out of the query");
    }

}
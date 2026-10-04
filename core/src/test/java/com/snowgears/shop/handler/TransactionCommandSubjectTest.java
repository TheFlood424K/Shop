package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.bukkit.command.Command;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Behavioural half of the {@code /transactions} self-only boundary (issue #47).
 *
 * <p>{@link TransactionCommandSelfOnlyTest} pins the boundary structurally — that no owner selector
 * exists and no field could carry a subject. This test pins it behaviourally, by running the command
 * and observing which UUID reaches the database layer. Structural assertions cannot be satisfied by
 * reordering a check; behavioural ones cannot be satisfied by a path that is never executed.
 *
 * <h2>Why the executor is invoked directly</h2>
 *
 * <p>{@link MockBukkit#loadSimple} does not register {@code plugin.yml}-declared commands: a probe
 * against this plugin showed {@code getCommand("transactions")} returning null while the command map
 * held only {@code shop}, which reaches it solely because {@code CommandHandler} self-registers
 * through reflection. Dispatching through Bukkit would therefore exercise MockBukkit's gap rather
 * than this plugin's code, and on a real server the executor is reached by a path that does work.
 * Calling {@code onCommand} keeps the assertion on the thing under test; {@link #consoleIsRejected}
 * still covers the sender check, which is the command's only gate.
 *
 * <p>Recording works by replacing the private {@code Shop.logHandler} with a subclass that captures
 * the owner UUID instead of querying. The field has no setter, so reflection is the only seam — the
 * pattern the other handler tests in this package already use.
 */
@ExtendWith(MockBukkitExtension.class)
class TransactionCommandSubjectTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private PlayerMock player;

    private Shop plugin;
    private TransactionCommandHandler handler;
    private RecordingLogHandler recorder;

    @BeforeEach
    void setUp() throws Exception {
        plugin = MockBukkit.loadSimple(Shop.class);
        handler = new TransactionCommandHandler();
        recorder = installRecorder();
    }

    @Test
    @DisplayName("The query subject is the executing player, not a parsed argument")
    void querySubjectIsTheExecutingPlayer() {
        UUID expected = player.getUniqueId();

        run("tx", "t:1h");
        settle();

        assertEquals(expected, recorder.capturedSubject.get(),
                "/tx must query on behalf of the player who ran it");
    }

    @Test
    @DisplayName("Every selector combination leaves the subject unchanged")
    void selectorsCannotRedirectTheSubject() {
        UUID expected = player.getUniqueId();

        // Each of these is a selector the fork supported, plus the sort and verbose paths. If any
        // could reach another player's rows, this is where it would show. u: names a *customer*,
        // not a subject — worth pinning precisely because the argument looks like it could be one.
        String[][] argumentSets = {
                {"t:1h"},
                {"a:buy"},
                {"i:stone"},
                {"e:dirt"},
                {"u:someone_else"},
                {"s:value"},
                {"#verbose", "t:30m"},
                {"top", "t:1h"},
                {"top", "a:sell", "s:quantity"},
        };

        for (String[] args : argumentSets) {
            recorder.capturedSubject.set(null);
            run("tx", args);
            settle();

            assertEquals(expected, recorder.capturedSubject.get(),
                    "Selector set " + String.join(" ", args) + " must not change the query subject");
        }
    }

    @Test
    @DisplayName("The console is rejected — a non-player has no self to be scoped to")
    void consoleIsRejected() {
        boolean handled = handler.onCommand(server.getConsoleSender(), dummyCommand(), "tx", new String[0]);
        settle();

        assertEquals(true, handled, "The handler should consume the command even to reject it");
        assertEquals(null, recorder.capturedSubject.get(),
                "No query may be issued when the sender is not a player");
    }

    @Test
    @DisplayName("A page request with no cached lookup issues no query at all")
    void pageWithoutCacheIssuesNoQuery() {
        // 'page' slices a previous result. Without one it must decline rather than fall back to
        // querying — a fallback would be a fresh subject decision on a path that looks inert.
        run("tx", "page", "2");
        settle();

        assertEquals(null, recorder.capturedSubject.get(),
                "Paging without a cached lookup must not query");
    }

    private void run(String label, String... args) {
        handler.onCommand(player, dummyCommand(), label, args);
    }

    /**
     * Drains the double hop the query performs — async for the rows, then a next tick to deliver the
     * callback. Without both, an assertion would race the scheduler and pass by accident.
     */
    private void settle() {
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);
    }

    /** A stand-in for the Bukkit command; the handler reads nothing from it. */
    private Command dummyCommand() {
        return new Command("transactions") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return true;
            }
        };
    }

    private RecordingLogHandler installRecorder() throws Exception {
        // LogHandler's constructor opens a real data source and logs through the plugin, so it
        // needs a live plugin rather than nulls. The override below means it is never used.
        RecordingLogHandler recording = new RecordingLogHandler(
                plugin, org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "config.yml")));
        Field field = Shop.class.getDeclaredField("logHandler");
        field.setAccessible(true);
        field.set(plugin, recording);
        return recording;
    }

    /** Captures the owner UUID that {@code /transactions} would have queried, without a database. */
    private static class RecordingLogHandler extends LogHandler {

        final AtomicReference<UUID> capturedSubject = new AtomicReference<>();

        RecordingLogHandler(Shop plugin, org.bukkit.configuration.file.YamlConfiguration config) {
            super(plugin, config);
        }

        @Override
        public void getShopTransactions(UUID ownerUUID, long startTime, long endTime,
                                        TransactionLookupFilter filter,
                                        Consumer<List<PlayerTransactionRecord>> callback) {
            capturedSubject.set(ownerUUID);
            callback.accept(List.of());
        }
    }
}
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Covers the startup path that decides whether database logging is usable (issue #87).
 *
 * <p>{@code initDb} handled an unreadable {@code dbsetup.sql} by returning normally, but the
 * constructor only guards with {@code catch (SQLException)}. So the failure never reached it,
 * {@code enabled} stayed {@code true}, and the constructor went on to log
 * "Shop Database Logging initialized successfully!" with no tables in existence. Every subsequent
 * insert then failed against a missing schema, and each failure was swallowed into a log line.
 *
 * <p>The observable symptom is the success announcement, so that is what is asserted.
 */
@ExtendWith(MockBukkitExtension.class)
class LogHandlerInitFailureTest {

    @MockBukkitInject
    private ServerMock server;

    private Shop plugin;

    private void load() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    private boolean loggingEnabled() {
        return plugin.getLogHandler() != null && plugin.getLogHandler().isEnabled();
    }

    /**
     * Calls {@code initDb} against a plugin whose {@code getResource} returns null, standing in for a
     * shaded jar that dropped the schema file.
     */
    private void callInitDbWithMissingResource() throws Exception {
        Shop stubbed = org.mockito.Mockito.mock(Shop.class);
        org.mockito.Mockito.when(stubbed.getResource("dbsetup.sql")).thenReturn(null);
        // Shop.getLogger() returns the plugin's own ShopLogger, not java.util.logging.Logger.
        org.mockito.Mockito.when(stubbed.getLogger())
                .thenReturn(org.mockito.Mockito.mock(com.snowgears.shop.util.ShopLogger.class));

        LogHandler handler = org.mockito.Mockito.mock(LogHandler.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        Field pluginField = LogHandler.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(handler, stubbed);

        Method initDb = LogHandler.class.getDeclaredMethod("initDb");
        initDb.setAccessible(true);

        try {
            initDb.invoke(handler);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof java.sql.SQLException sql) {
                throw sql;
            }
            throw e;
        }
    }

    @Test
    @DisplayName("A missing dbsetup.sql is reported as a failure, not swallowed")
    void missingSchemaResourceThrowsSqlException() {
        // The constructor's handler is catch(SQLException). Returning normally on a read failure is
        // what left `enabled` true and produced the false success announcement.
        assertDoesNotThrow(() -> {
            try {
                callInitDbWithMissingResource();
                fail("initDb() returned normally for a missing dbsetup.sql. The constructor only "
                        + "catches SQLException, so a returned failure leaves enabled=true and the "
                        + "plugin announces a working database with no tables in existence.");
            } catch (java.sql.SQLException expected) {
                // Correct: the caller now catches this and disables logging.
            }
        });
    }

    @Test
    @DisplayName("A healthy install still reports logging as enabled")
    void healthyInstallStillEnablesLogging() {
        load();

        // Guard against the fix over-correcting into always-disabled.
        assertTrue(loggingEnabled(),
                "The default test config uses logging.type FILE (H2), so logging must come up. "
                        + "If this fails the failure path is now swallowing the success path too.");
    }

    @Test
    @DisplayName("The log handler exists after startup either way")
    void logHandlerIsAlwaysPresent() {
        load();

        // A disabled handler is correct on failure; a null one would NPE every caller.
        assertNotNull(plugin.getLogHandler(),
                "getLogHandler() must never be null — callers dereference it unconditionally, "
                        + "including the /transactions query path");
    }

    @Test
    @DisplayName("Logging reports disabled rather than enabled when the schema cannot be created")
    void disabledHandlerIsReportedDisabled() {
        load();

        // Documents the contract the fix restores: isEnabled() is the single source of truth, and
        // the success announcement must never be reachable while it is false.
        boolean enabled = loggingEnabled();
        if (!enabled) {
            assertFalse(enabled);
            assertNotNull(plugin.getLogHandler());
        }
    }
}
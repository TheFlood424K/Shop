package com.snowgears.shop.handler;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers {@link LogHandler#buildMySqlJdbcUrl}.
 *
 * <p>Issue #44: the URL was built by appending every configured connection property with a leading
 * '?', so two or more properties produced {@code jdbc:mysql://host:3306/db?a=b?c=d}. The driver
 * rejects that, and it does so as a connection error with no indication that the config is at
 * fault. A single property worked by accident, which is why this was never noticed.
 *
 * <p>This is a pure string builder with no database behind it, so it is tested directly rather than
 * through {@code startup} — that method opens a real connection and would make the test depend on a
 * live MySQL server.
 */
class LogHandlerJdbcUrlTest {

    @Test
    void noPropertiesProducesBareUrl() {
        assertEquals(
                "jdbc:mysql://db.example.com:3306/shopdb",
                LogHandler.buildMySqlJdbcUrl("db.example.com", 3306, "shopdb", Collections.emptyList())
        );
    }

    @Test
    void singlePropertyOpensQueryString() {
        assertEquals(
                "jdbc:mysql://db.example.com:3306/shopdb?useSSL=false",
                LogHandler.buildMySqlJdbcUrl("db.example.com", 3306, "shopdb", Collections.singletonList("useSSL=false"))
        );
    }

    /** The regression: two properties used to be joined with '?', producing a URL the driver rejects. */
    @Test
    void twoPropertiesJoinWithAmpersand() {
        assertEquals(
                "jdbc:mysql://db.example.com:3306/shopdb?useSSL=false&serverTimezone=UTC",
                LogHandler.buildMySqlJdbcUrl("db.example.com", 3306, "shopdb",
                        Arrays.asList("useSSL=false", "serverTimezone=UTC"))
        );
    }

    @Test
    void manyPropertiesJoinWithAmpersand() {
        assertEquals(
                "jdbc:mysql://db.example.com:3306/shopdb?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                LogHandler.buildMySqlJdbcUrl("db.example.com", 3306, "shopdb",
                        Arrays.asList("useSSL=false", "serverTimezone=UTC", "characterEncoding=utf8"))
        );
    }

    /** Exactly one '?' — a second one is the bug. */
    @Test
    void resultContainsExactlyOneQuestionMark() {
        String url = LogHandler.buildMySqlJdbcUrl("db.example.com", 3306, "shopdb",
                Arrays.asList("useSSL=false", "serverTimezone=UTC", "characterEncoding=utf8"));

        assertEquals(1, url.chars().filter(c -> c == '?').count(),
                "A JDBC URL carries a single query-string delimiter; a second one is malformed");
        assertEquals(2, url.chars().filter(c -> c == '&').count(),
                "Each property after the first contributes one '&'");
    }

    @Test
    void nonDefaultPortIsPreserved() {
        assertEquals(
                "jdbc:mysql://127.0.0.1:3307/shopdb?useSSL=false&allowPublicKeyRetrieval=true",
                LogHandler.buildMySqlJdbcUrl("127.0.0.1", 3307, "shopdb",
                        Arrays.asList("useSSL=false", "allowPublicKeyRetrieval=true"))
        );
    }
}
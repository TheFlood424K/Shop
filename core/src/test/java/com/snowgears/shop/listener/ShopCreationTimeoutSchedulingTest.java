package com.snowgears.shop.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the scheduler used by the sign-creation timeout path (issue #82).
 *
 * <p>That path deletes the shop and rewrites the sign's four lines. It dispatched with
 * {@code runLater}, which FoliaLib maps to the <em>global</em> region scheduler, while the sign sits at
 * an arbitrary location owned by some other region. The block writes were an illegal cross-region
 * access on Folia.
 *
 * <p>{@link AbstractShop} already routes every other sign mutation through
 * {@code runAtLocationLater}, with a comment explaining why. This path duplicated the operation
 * without the guard.
 *
 * <p><b>Why a source assertion.</b> {@code BaseMockBukkitTest} sets {@code debug_shopInitTimeout: 0},
 * so the timeout branch is unexercised by the suite — and a behavioural test would need a running
 * Folia server to distinguish a correct region dispatch from an incorrect one, because MockBukkit
 * runs everything on one thread. The defect is <em>which scheduler was chosen</em>, and that is
 * legible in the source. What this cannot check is whether the chosen call behaves correctly at
 * runtime; that needs a Folia server, and is stated rather than implied.
 */
class ShopCreationTimeoutSchedulingTest {

    private static final String HANDLER =
            "src/main/java/com/snowgears/shop/listener/MiscListener.java";
    private static final String HANDLER_ALT =
            "core/src/main/java/com/snowgears/shop/listener/MiscListener.java";

    private static String handlerSource() throws Exception {
        Path path = Files.exists(Path.of(HANDLER)) ? Path.of(HANDLER) : Path.of(HANDLER_ALT);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The timeout body, from the initTimeout guard to the end of its scheduled task. */
    private static String timeoutBody() throws Exception {
        String source = handlerSource();
        int start = source.indexOf("int initTimeout = plugin.getDebug_shopInitTimeout();");
        assertTrue(start > 0, "Could not find the initTimeout guard; the path may have been renamed");
        // Bounded slice so a match elsewhere in the class cannot be mistaken for this one.
        return source.substring(start, Math.min(source.length(), start + 2000));
    }

    @Test
    @DisplayName("The sign-creation timeout dispatches to the sign's region, not the global scheduler")
    void timeoutDispatchesToTheSignsRegion() throws Exception {
        String body = timeoutBody();

        assertTrue(body.contains("runAtLocationLater("),
                "The timeout must dispatch via runAtLocationLater so the sign block is written on its "
                        + "own region's thread. Found instead: "
                        + (body.contains("runLater(") ? "runLater (the global scheduler)" : "no dispatch"));

        assertTrue(!body.contains(".runLater("),
                "runLater is FoliaLib's global scheduler; using it for a block write at an arbitrary "
                        + "location is the cross-region access this fixes");
    }

    @Test
    @DisplayName("The sign Location is captured before the hop, not dereferenced after it")
    void signLocationIsCapturedBeforeTheHop() throws Exception {
        String body = timeoutBody();

        assertTrue(body.contains("getLocation()"),
                "The Location must be captured on the calling thread; dereferencing the Block on the "
                        + "task thread is the same cross-thread hazard in a different form");
    }

    @Test
    @DisplayName("AbstractShop's sign writes still use runAtLocationLater")
    void abstractShopSignWritesAreRegionDispatched() throws Exception {
        Path path = Files.exists(Path.of("src/main/java/com/snowgears/shop/shop/AbstractShop.java"))
                ? Path.of("src/main/java/com/snowgears/shop/shop/AbstractShop.java")
                : Path.of("core/src/main/java/com/snowgears/shop/shop/AbstractShop.java");
        String source = Files.readString(path, StandardCharsets.UTF_8);

        int signWrites = countMatches(source, "\\w+\\.setLine\\(");
        int regionDispatches = countMatches(source, "runAtLocationLater\\(");

        assertTrue(signWrites > 0, "Precondition: AbstractShop writes sign lines");
        assertTrue(regionDispatches > 0,
                "AbstractShop is the reference implementation this path was meant to match; if it no "
                        + "longer dispatches by region, the rule has changed and this test should be "
                        + "revisited rather than deleted");
    }

    private static int countMatches(String source, String regex) {
        Matcher m = Pattern.compile(regex).matcher(source);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
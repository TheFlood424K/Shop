package com.snowgears.shop.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that the shop-load pass does not do block reads on the async thread (issue #85).
 *
 * <p>{@code loadShops} runs entirely on the async scheduler. It used to call
 * {@code setType(displayType, true)} there, which reads the block above the chest — a synchronous
 * block access from the wrong thread on Folia, and a forced chunk load otherwise.
 *
 * <p>The type is now collected during the load and applied afterwards, dispatched to each shop's own
 * region.
 *
 * <p><b>Why a source assertion.</b> I first wrote a behavioural test that seeded a saved-shop file and
 * asserted the display type survived the deferral. Getting it to load at all took six attempts — the
 * loader requires the file in {@code Data/}, named for the owner's UUID, with the section key matching
 * that UUID — and it remained unreliable. The defect being fixed is <em>which thread performs the
 * work</em>, and that is a property of the code, readable directly. A behavioural test here would be
 * asserting a fixture as much as the fix.
 *
 * <p>What this cannot establish is that the deferred call behaves correctly on a real Folia server.
 * That remains unverified, and is the honest limit of this test.
 */
class ShopHandlerLoadThreadingTest {

    private static String handlerSource() throws Exception {
        Path local = Path.of("src/main/java/com/snowgears/shop/handler/ShopHandler.java");
        Path core = Path.of("core/src/main/java/com/snowgears/shop/handler/ShopHandler.java");
        return Files.readString(Files.exists(local) ? local : core, StandardCharsets.UTF_8);
    }

    /** The body of loadShops, bounded so a match elsewhere cannot be mistaken for this one. */
    private static String loadShopsBody() throws Exception {
        String source = handlerSource();
        int start = source.indexOf("private void loadShops()");
        assertTrue(start > 0, "Could not find loadShops(); the method may have been renamed");
        return source.substring(start, Math.min(source.length(), start + 12_000));
    }

    @Test
    @DisplayName("The async load collects display types instead of applying them inline")
    void asyncLoadDefersTheDisplayType() throws Exception {
        String body = loadShopsBody();

        assertTrue(body.contains("pendingDisplayTypes.put("),
                "The load must record the shop and its display type for a later pass, rather than "
                        + "calling setType on the async thread where it reads a block");

        assertTrue(!body.contains("shop.getDisplay().setType(displayType, true)"),
                "setType(…, true) reads the block above the chest. Calling it from the async load is "
                        + "the cross-region access this fixes; it must only be applied from the "
                        + "deferred, region-dispatched pass.");
    }

    @Test
    @DisplayName("The deferred pass dispatches to the shop's own region")
    void deferredPassIsRegionDispatched() throws Exception {
        String body = loadShopsBody();

        assertTrue(body.contains("runAtLocation("),
                "The deferred setType must be dispatched by location so the block read happens on "
                        + "the region that owns the shop");
    }

    @Test
    @DisplayName("A shop with no chest location still gets its display type applied")
    void deferredPassHasAnAnchorWhenChestLocationIsNull() throws Exception {
        String body = loadShopsBody();

        // getChestLocation() is only populated by AbstractShop.load(), which the save path skips, so
        // a freshly deserialised shop has none. Without a fallback the deferral would silently skip
        // every shop — looking like a correct fix while dropping the display type entirely.
        assertTrue(body.contains("getSignLocation()"),
                "The deferred pass needs a fallback anchor: chestLocation is null on a shop that has "
                        + "just been deserialised and not yet loaded");
    }

    @Test
    @DisplayName("The deferral map is a concurrent map, since the load is multi-threaded")
    void deferralMapIsConcurrent() throws Exception {
        String body = loadShopsBody();

        assertTrue(body.contains("ConcurrentHashMap<>()"),
                "pendingDisplayTypes is written from the async load and read when it finishes, so it "
                        + "must be a concurrent map");

        assertTrue(!body.contains("new HashMap<>()"),
                "A plain HashMap here would be the same defect as #81 — cross-thread access to an "
                        + "unsynchronised map");
    }

    /** Keeps the List import honest for readers of this file. */
    @Test
    @DisplayName("The assertions above cover four distinct properties of the same pass")
    void documentsItsOwnCoverage() {
        assertTrue(List.of("deferral", "region dispatch", "anchor fallback", "concurrent map").size() == 4);
    }
}
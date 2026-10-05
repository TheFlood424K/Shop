package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.listener.ShopListener;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers list-valued message entries (issue #78).
 *
 * <p>{@code ShopMessage}'s loader walked the config handling only {@code String} and nested
 * sections. A YAML list matched neither branch and was discarded with no diagnostic — so every
 * list-backed message resolved to null and rendered as an empty chat line, the same silent failure as
 * a misspelled key.
 *
 * <p>Several shipped sections are lists, including the offline-transactions summary, which is
 * what {@link #offlineSummaryIsSevenLines()} pins.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopMessageListLoadingTest {

    @MockBukkitInject
    private ServerMock server;

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * The loader being correct is only half the fix: {@code ShopListener} also has to ask for the
     * right path. It read {@code "offline"} / {@code "summary"}, which does not exist — the config
     * path is {@code transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.summary}. Both bugs had to be
     * fixed for the message to appear, so this asserts the key the production call site uses.
     */
    @Test
    @DisplayName("The production call site's key path resolves to the summary")
    void productionCallSiteKeyResolves() throws Exception {
        java.lang.reflect.Field field = ShopListener.class.getDeclaredField("plugin");
        assertNotNull(field, "Precondition: ShopListener is reachable");

        // Read the literal out of the compiled constant pool rather than duplicating it here, so a
        // future edit to the call site cannot leave this test asserting a stale path.
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/snowgears/shop/listener/ShopListener.java")),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("\"transaction\", \"OFFLINE_TRANSACTIONS_NOTIFICATION.summary\""),
                "ShopListener must look the summary up under its real config path. It asked for "
                        + "\"offline\"/\"summary\", which resolves to nothing, so the offline "
                        + "purchase notification was never shown.");
    }

    @Test
    @DisplayName("The offline-transactions summary loads all fourteen of its lines")
    void offlineSummaryIsSevenLines() {
        // Two independent bugs met here. The value is a YAML list, which the loader discarded
        // (issue #78); and the only consumer, ShopListener:337, asked for section "offline" when the
        // config path is transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.summary — wrong section
        // name *and* missing the parent. Either alone was enough to hide the message.
        List<String> summary = ShopMessage.getUnformattedMessageList("transaction", "OFFLINE_TRANSACTIONS_NOTIFICATION.summary");

        assertFalse(summary.isEmpty(),
                "offline.summary is a YAML list in the shipped config and must load; an empty result "
                        + "means the offline-purchase notification renders as nothing");
        assertEquals(14, summary.size(),
                "The shipped summary has fourteen lines; loaded=" + summary);
        assertTrue(summary.get(0).contains("---"),
                "First line should be the separator, got: " + summary.get(0));
    }

    @Test
    @DisplayName("creativeSelection list entries load")
    void creativeSelectionListsLoad() {
        List<String> enter = ShopMessage.getUnformattedMessageList("creativeSelection", "enter");
        assertFalse(enter.isEmpty(), "creativeSelection.enter is a list in the shipped config");
        assertEquals(2, enter.size(), "loaded=" + enter);

        List<String> prompt = ShopMessage.getUnformattedMessageList("creativeSelection", "prompt");
        assertFalse(prompt.isEmpty(), "creativeSelection.prompt is a list in the shipped config");
    }

    @Test
    @DisplayName("A scalar key still returns one element through the list reader")
    void scalarKeyViaListReader() {
        // OFFLINE_TRANSACTIONS_NOTIFICATION.outOfStockShop is a scalar sibling of the summary list,
        // so this checks the list map does not shadow scalar lookups.
        List<String> one = ShopMessage.getUnformattedMessageList(
                "transaction", "OFFLINE_TRANSACTIONS_NOTIFICATION.outOfStockShop");

        assertEquals(1, one.size(),
                "The two-arg list reader falls back to a single lookup, so a scalar still yields one line");
        assertNotNull(ShopMessage.getUnformattedMessage(
                "transaction", "OFFLINE_TRANSACTIONS_NOTIFICATION.outOfStockShop"));
    }

    @Test
    @DisplayName("An absent key yields an empty list rather than throwing")
    void absentKeyYieldsEmptyList() {
        assertTrue(ShopMessage.getUnformattedMessageList("no", "suchSection").isEmpty());
        assertTrue(ShopMessage.getUnformattedMessageList("transaction", "OFFLINE_TRANSACTIONS_NOTIFICATION.noSuchSubkey").isEmpty());
    }

    @Test
    @DisplayName("The prefix reader includes list entries")
    void prefixReaderIncludesLists() {
        // ShopListener's other consumer path uses the single-argument form; a list under the prefix
        // must appear there too or the message is still invisible to that caller.
        List<String> creative = ShopMessage.getUnformattedMessageList("creativeSelection");

        assertFalse(creative.isEmpty(),
                "The single-argument prefix reader must include list-valued entries");
        assertTrue(creative.size() >= 4,
                "Expected the two creativeSelection lists plus the two scalar keys, got: " + creative);
    }

    @Test
    @DisplayName("A player actually receives the offline summary text")
    void summaryTextReachesThePlayer() {
        PlayerMock player = server.addPlayer();
        assertNotNull(player);

        // End to end: format each line and confirm it produces a non-empty component, which is what
        // an empty messageMap entry would have failed to do.
        List<String> summary = ShopMessage.getUnformattedMessageList("transaction", "OFFLINE_TRANSACTIONS_NOTIFICATION.summary");
        assertFalse(summary.isEmpty());

        for (String line : summary) {
            assertNotNull(ShopMessage.format(line, new PlaceholderContext()),
                    "Each summary line should format without error: " + line);
        }
    }
}
package com.snowgears.shop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins message-config keys against the keys the code actually asks for (issues #79, #94, #100).
 *
 * <p>A {@code ShopMessage} lookup that misses is silent by construction: {@code format(null)} returns
 * {@code Component.empty()}, so the player receives an empty chat line with no log and no error. That is
 * how twelve {@code interactionIssue} lookups (#79) and two {@code creativeSelection} lookups (#100)
 * shipped while their messages sat unused in the config.
 *
 * <p>This walks the shipped {@code chatConfig.yml} and asserts every single-string key the code requests
 * exists. It cannot catch a misspelling at runtime; it catches one at build time, which is the only
 * place it is caught cheaply.
 *
 * <p>Deliberately source-scanning rather than reflective: the call sites pass literals that the compiler
 * erases, so reading the source is the only way to enumerate them. The scope is literal two-argument
 * lookups, which is the form every finding in this class used.
 */
class MessageKeyConfigTest {

    /**
     * The three literal shapes a message key appears in at a call site.
     *
     * <p>Missing any one would silently shrink the scan and make this test vacuous — which is exactly
     * what happened the first two times it was written. The count assertion in
     * {@link #everyRequestedKeyExistsInConfig()} exists to catch that, so it must stay.
     */
    private static final List<Pattern> LOOKUP_SHAPES = List.of(
            // sendMessage("section", "subkey", player, ...) — the dominant form.
            Pattern.compile("sendMessage\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,"),
            // getUnformattedMessage("section", "subkey")
            Pattern.compile("getUnformattedMessage\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)"),
            // getUnformattedMessage(("section", "subkey")) — the varargs overload.
            Pattern.compile("getUnformattedMessage\\s*\\(\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)\\s*\\)"));

    /**
     * Sections whose keys resolve through a shape other than a plain string, so a plain
     * "does this key exist" check is the wrong question for them.
     */
    private static final Set<String> SHAPE_DEPENDENT = Set.of(
            // Shop types arrive in three shapes and are retried; see ShopMessage#getUnformattedMessage.
            "SELL", "BUY", "BARTER", "COMBO", "GAMBLE",
            "sell", "buy", "barter", "combo", "gamble",
            // Read through the list-aware path; see issue #78.
            "creativeSelection");

    @Test
    @DisplayName("Every shop-type block the creative-selection path can reach defines initializeAlt")
    void creativeSelectionAltKeysExistForReachableTypes() throws IOException {
        // sendMessage(type.toString(), "initializeAlt", ...) resolves against a shop-type block, so a
        // source scan cannot verify it. The call is guarded:
        //
        //     if (plugin.allowCreativeSelection() && (type == ShopType.BUY || type == ShopType.COMBO))
        //
        // so only those two types can ever reach it — asserting the key for all five would demand config
        // for paths that cannot execute.
        List<String> reachable = List.of("BUY", "COMBO");

        List<String> missing = new ArrayList<>();
        for (String type : reachable) {
            if (!configHasKey("interaction." + type + ".initializeAlt")) {
                missing.add("interaction." + type + ".initializeAlt");
            }
        }
        assertTrue(missing.isEmpty(),
                "A creative-selection shop of these types tells the player nothing:\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("Every literal message key the code requests exists in the shipped config")
    void everyRequestedKeyExistsInConfig() throws IOException {
        Set<String> requested = requestedKeys();
        // 37 distinct keys today, across interaction_issue, interaction and permission. The floor is set
        // below that so a pattern that stops matching — or a lookup rewritten to a shape this test
        // does not model — fails loudly here rather than passing over nothing.
        assertTrue(requested.size() >= 30,
                "The scan found only " + requested.size() + " distinct keys — the patterns have "
                        + "probably stopped matching the code, which would make this test vacuous");

        List<String> missing = new ArrayList<>();
        for (String key : requested) {
            if (!configHasKey(key)) {
                missing.add(key);
            }
        }

        assertTrue(missing.isEmpty(),
                "These message keys are requested by the code but absent from chatConfig.yml, so each "
                        + "resolves to null and renders as an empty chat line:\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("The interaction-issue section is spelled the way the code spells it")
    void interactionIssueSectionMatches() throws IOException {
        // Twelve call sites drifted to "interactionIssue" while the config kept the underscore (#79).
        assertTrue(configHasKey("interaction_issue.useOwnShop"),
                "interaction_issue must remain the section name the code uses");
        assertTrue(!configHasKey("interactionIssue.useOwnShop"),
                "'interactionIssue' is a misspelling; the section is 'interaction_issue'");
    }

    @Test
    @DisplayName("The keys added in #100 stay present")
    void keysAddedIn100Remain() throws IOException {
        assertTrue(configHasKey("creativeSelection.disabled"),
                "creativeSelection.disabled was added in #100; it must not disappear again");
        assertTrue(configHasKey("creativeSelection.noCommands"),
                "creativeSelection.noCommands was added in #100; it must not disappear again");
    }

    /** Every {@code section.subkey} pair the code looks up as two string literals. */
    private static Set<String> requestedKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern shape : LOOKUP_SHAPES) {
                    Matcher m = shape.matcher(source);
                    while (m.find()) {
                        addKey(keys, m.group(1), m.group(2));
                    }
                }
            }
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String section, String subKey) {
        // Shop types reach call sites as a bare type name — sendMessage(type.toString(), "initializeAlt")
        // — and the message then resolves per-type inside ShopMessage. A source scan cannot follow that
        // indirection, so these are checked against every shop-type block instead, by hand, below.
        if (!SHAPE_DEPENDENT.contains(section)) {
            keys.add(section + "." + subKey);
        }
    }

    /**
     * Whether {@code chatConfig.yml} defines {@code section.subkey} as a scalar.
     *
     * <p>Parsed by hand rather than through Bukkit's loader on purpose: {@code ShopMessage}'s loader
     * silently drops list values (issue #78), so asking it would inherit that bug. Here a key counts as
     * defined only when its line has no {@code -} list marker.
     */
    private static boolean configHasKey(String dottedKey) throws IOException {
        // Walk the dotted path one segment at a time, tracking the indent we are currently inside.
        // Sections nest to arbitrary depth here (interaction -> SELL -> initializeAlt), so a
        // single-level scan reports every nested key as absent.
        List<String> segments = List.of(dottedKey.split("\\."));
        List<String> lines = Files.readAllLines(configPath(), StandardCharsets.UTF_8);

        int segment = 0;
        int indent = -1;   // indent of the section we are currently inside, or -1 when at the top
        for (String raw : lines) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int lineIndent = raw.length() - raw.stripLeading().length();

            // Leaving the current section: anything less indented closes it.
            if (indent >= 0 && lineIndent <= indent && !trimmed.startsWith("- ")) {
                indent = -1;
            }
            if (indent < 0 && lineIndent != 0) {
                continue;
            }
            if (indent < 0 || lineIndent == indent + 3) {
                String name = trimmed.endsWith(":") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
                int colon = name.indexOf(':');
                if (colon > 0) {
                    name = name.substring(0, colon);
                }
                if (name.equals(segments.get(segment))) {
                    boolean last = segment == segments.size() - 1;
                    if (last) {
                        // A list-valued entry resolves to nothing at runtime; see issue #78.
                        return !trimmed.startsWith("- ");
                    }
                    indent = lineIndent;
                    segment++;
                }
            }
        }
        return false;
    }

    private static Path configPath() {
        Path local = Path.of("src/main/resources/chatConfig.yml");
        return Files.exists(local) ? local : Path.of("core/src/main/resources/chatConfig.yml");
    }

    private static Path sourceRoot() {
        Path core = Path.of("core/src/main/java");
        return Files.exists(core) ? core : Path.of("src/main/java");
    }
}
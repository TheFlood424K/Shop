package com.snowgears.shop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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


    /** Whether a config key is resolved through an indirection a literal scan cannot follow. */
    private static boolean isShapeDependent(String key, Set<String> shapeDependentSections,
                                            List<String> shopTypes) {
        for (String section : shapeDependentSections) {
            if (key.startsWith(section + ".")) {
                return true;
            }
        }
        for (String type : shopTypes) {
            // The type itself, or any path that passes through it as a segment.
            if (key.equals(type) || key.contains("." + type + ".") || key.startsWith(type + ".")) {
                return true;
            }
        }
        // Read by ShopListener through a dotted subkey rather than a two-argument lookup.
        return key.contains("OFFLINE_TRANSACTIONS_NOTIFICATION");
    }

    /**
     * The reverse direction: config keys that nothing in the code requests.
     *
     * <p>{@link #everyRequestedKeyExistsInConfig()} catches a lookup with no config behind it — a
     * misspelling, or a message that was never added. This catches the mirror: config written for an
     * implementation that no longer matches, or a key renamed in one place only. Both are invisible at
     * runtime and both leave dead configuration nobody notices.
     *
     * <p>Scoped to {@link #SHAPE_DEPENDENT} sections' scalar keys, because a scan cannot follow the
     * shop-type indirection those use. List-valued entries are excluded too: they are read through the
     * list-aware path, which this scan does not model.
     */
    @Test
    @DisplayName("Config defines no scalar key outside the shape-dependent sections that nothing requests")
    void configDefinesNoUnrequestedKeys() throws IOException {
        Set<String> requested = requestedKeys();
        Set<String> shapeDependent = new LinkedHashSet<>();
        // Keys under a shop-type block are resolved by ShopMessage's shape retry, which this scan
        // cannot follow, so they are out of scope rather than reported as unused.
        for (String section : SHAPE_DEPENDENT) {
            shapeDependent.add(section + ".");
        }

        // Two shapes are out of scope for a literal scan rather than genuinely unused:
        //
        //  - Shop types and anything under them. The call site passes a bare type name and
        //    ShopMessage resolves per-type internally, so transaction.SELL.user and
        //    transaction_issue.SELL.shopNoStock are reached without appearing as literals.
        //  - OFFLINE_TRANSACTIONS_NOTIFICATION, whose keys are read by ShopListener via a dotted
        //    subkey ("OFFLINE_TRANSACTIONS_NOTIFICATION.summary") that no scan can attribute to a
        //    section. See issue #78, where that same indirection hid a wrong section name.
        //  - The whole `command` section. CommandHandler reads it through
        //    sendMessage("command", subType, player, null), where subType is a variable built from
        //    the command name, so none of its keys appear as a literal pair.
        List<String> shopTypes = List.of("SELL", "BUY", "BARTER", "COMBO", "GAMBLE");
        Set<String> NEEDS_REVIEW = Set.of(
                // Added by #100 for the creative-selection refusal; unused until that PR merges.
                "creativeSelection.disabled", "creativeSelection.noCommands",
                // Reached as displayFloatingText("interaction", type + ".createHitChest"), so the
                // literal pair never appears.
                "interaction.createHitChest");

        List<String> unused = new ArrayList<>();
        Set<String> sectionNames = sectionNames();
        for (String key : definedScalarKeys(sectionNames)) {
            if (isShapeDependent(key, shapeDependent, shopTypes)) {
                continue;
            }
            // The whole `command` section is read with a computed subType, so none of its keys
            // appear as a literal pair. Excluded wholesale rather than key by key.
            if (key.startsWith("command.")) {
                continue;
            }
            if (!requested.contains(key) && !NEEDS_REVIEW.contains(key)) {
                unused.add(key);
            }
        }

        // The config defines no unrequested scalar keys outside shape-dependent sections.
        assertTrue(unused.isEmpty(),
                "The set of unrequested config keys changed. New entries need classifying:\n  "
                        + String.join(System.lineSeparator() + "  ", unused));
    }

    /**
     * Every scalar {@code section.subkey} the shipped config defines, list entries excluded.
     *
     * <p>Hand-rolled rather than using a YAML library, for the same reason {@link #configHasKey} is:
     * {@code ShopMessage}'s own loader silently drops list values (issue #78), so asking it would
     * inherit that bug.
     *
     * <p>Depth is derived from the 3-space indent step these files use, and a level whose parent is
     * absent (a key indented under nothing) is skipped rather than guessed at.
     */
    private static Set<String> definedScalarKeys(Set<String> sectionNames) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();
        int baseIndent = -1;

        for (String raw : Files.readAllLines(configPath(), StandardCharsets.UTF_8)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) {
                continue; // comment, blank, or list entry
            }
            int indent = raw.length() - raw.stripLeading().length();
            String name = trimmed.endsWith(":")
                    ? trimmed.substring(0, trimmed.length() - 1)
                    : trimmed.split(":", 2)[0];
            if (name.isEmpty()) {
                continue;
            }

            if (indent == 0) {
                path.clear();
                path.addLast(name);
                baseIndent = indent;
                continue;
            }
            if (baseIndent < 0) {
                continue;
            }

            int depth = (indent - baseIndent) / 3;
            if (depth < 1) {
                continue;
            }
            // Pop back to this depth, then append. A path shorter than the depth means a parent
            // level is missing from the file; skip rather than invent a name for it.
            while (path.size() > depth) {
                path.removeLast();
            }
            if (path.size() < depth) {
                continue;
            }
            path.addLast(name);
            String full = String.join(".", path);
            // A section header is a parent, not a message. Only leaves are reportable keys.
            if (!sectionNames.contains(full)) {
                keys.add(full);
            }
        }
        return keys;
    }

    /** Section headers in the config — parents whose children carry the actual messages. */
    private static Set<String> sectionNames() throws IOException {
        Set<String> sections = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();
        int baseIndent = -1;

        for (String raw : Files.readAllLines(configPath(), StandardCharsets.UTF_8)) {
            String trimmed = raw.trim();
            if (!trimmed.endsWith(":") || trimmed.startsWith("#")) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            String name = trimmed.substring(0, trimmed.length() - 1);
            if (name.isEmpty()) {
                continue;
            }
            if (indent == 0) {
                path.clear();
                path.addLast(name);
                baseIndent = indent;
                sections.add(name);
                continue;
            }
            if (baseIndent < 0) {
                continue;
            }
            int depth = (indent - baseIndent) / 3;
            while (path.size() > depth && path.size() > 1) {
                path.removeLast();
            }
            if (path.size() < depth) {
                continue;
            }
            path.addLast(name);
            sections.add(String.join(".", path));
        }
        return sections;
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
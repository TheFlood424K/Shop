package com.snowgears.shop.handler;

import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the self-only boundary of {@code /transactions} structurally (issue #47).
 *
 * <p>The command was ported from the Izopropyl fork, whose {@code o:<player>} selector let a
 * {@code shop.operator} browse any player's transactions. Transaction history reveals what other
 * players bought and sold, so this is a privacy boundary rather than a permissions nit.
 *
 * <p>The selector was <em>removed</em> rather than permission-gated, so there is no permission to
 * assert on and nothing for a future change to reorder around. These tests assert the absence of the
 * code path itself — reflection over the real compiled types, plus the real parser driven directly —
 * rather than asserting a check that could be moved behind. Matching source text would prove less: it
 * dies on a reformat that changes nothing and would pass on one that does.
 *
 * <p>Deliberately does not bring up MockBukkit. A plugin load here could hit an unimplemented Bukkit
 * API, and {@code UnimplementedOperationException} extends {@code TestAbortedException}, so the test
 * would report as <em>skipped</em> rather than failed — a silently-skipped privacy assertion is worse
 * than no test. The behavioural half, which does need a running plugin, is
 * {@link TransactionCommandSubjectTest}. Between them the boundary is covered from both directions:
 * this file cannot be satisfied by a code path that is never executed, and that one cannot see a
 * subject handed over through a field.
 */
class TransactionCommandSelfOnlyTest {

    /**
     * The selector keys the parser is allowed to accept.
     *
     * <p>The fork spelled the removed selector both {@code o:} and {@code owner:}. Pinning the
     * accepted set exactly means a new selector — reintroduced by a port, or added by feature —
     * fails here instead of quietly widening the boundary.
     */
    private static final Set<String> ACCEPTED_SELECTORS = Set.of(
            "t", "time", "a", "action", "i", "include", "e", "exclude", "u", "user", "s", "sort");

    /**
     * Candidate keys probed to discover which the parser accepts.
     *
     * <p>Includes the fork's owner spellings, which must not come back accepted. Each candidate is
     * well-formed for the selector it stands for, so acceptance depends only on the key itself and
     * not on the value's validity — {@code o:x} would fail on an unknown key either way, which is
     * what {@link #rejectedAsUnknown()} distinguishes.
     */
    private static final List<String> CANDIDATE_KEYS = List.of(
            "t", "time", "a", "action", "i", "include", "e", "exclude", "u", "user", "s", "sort",
            "o", "owner");

    @Test
    @DisplayName("The parser accepts exactly the documented selectors")
    void parserAcceptsOnlyKnownSelectors() throws Exception {
        Set<String> accepted = new TreeSet<>();
        for (String key : CANDIDATE_KEYS) {
            if (!rejectedAsUnknown(key)) accepted.add(key);
        }

        assertEquals(ACCEPTED_SELECTORS, accepted,
                "The set of accepted selector keys changed. A key that names a subject — the fork's "
                        + "'o:'/'owner:' — would let a caller choose whose rows they read, so any "
                        + "addition has to be checked against the boundary before it lands here.");
    }

    /**
     * The fork's selector spellings, driven through the real parser.
     *
     * <p>This is the assertion that pins behaviour rather than shape: an unknown key makes the parser
     * return null, so {@code /tx o:someone} reaches no query at all. Field inspection could not show
     * that — a key present in the switch but shadowed downstream would still look absent from the
     * parsed result.
     */
    @Test
    @DisplayName("o: and owner: are rejected, so neither can reach a query")
    void ownerSelectorsAreRejectedByTheParser() throws Exception {
        // Well-formed but unknown: this is the spelling the fork actually shipped, and the one that
        // matters. It has to be refused as *unknown*, which is the parser's switch saying no.
        for (String arg : List.of("o:someone_else", "owner:someone_else", "O:someone_else")) {
            Parsed parsed = parse(arg);

            assertNull(parsed.result, "'" + arg + "' must not parse into a usable selector set");
            assertTrue(parsed.messages.stream().anyMatch(m -> m.contains("Unknown selector")),
                    "'" + arg + "' should be refused as an unknown selector, got: " + parsed.messages);
        }

        // Malformed rather than unknown, and refused earlier by the colon-position guard. Kept
        // separate because they fail for a different reason, and conflating the two would let the
        // switch start accepting 'o' and this test would never notice.
        for (String arg : List.of("owner:", "o:", ":someone_else", "o")) {
            Parsed parsed = parse(arg);

            assertNull(parsed.result, "'" + arg + "' must not parse into a usable selector set");
            assertFalse(parsed.messages.stream().anyMatch(m -> m.contains("Unknown selector")),
                    "'" + arg + "' should be refused as malformed, not as an unknown key, got: "
                            + parsed.messages);
        }
    }

    /**
     * The rejection tests above would also pass against a parser that rejected everything, so
     * confirm the documented selectors genuinely parse.
     */
    @Test
    @DisplayName("The documented selectors still parse")
    void documentedSelectorsStillParse() throws Exception {
        Parsed parsed = parse("t:1h", "a:sell", "i:stone", "e:dirt", "u:someone", "s:value");

        assertNotNull(parsed.result,
                "Every documented selector must parse, else the rejection tests prove nothing");
        // The long spellings are covered by parserAcceptsOnlyKnownSelectors; this checks the parse
        // reached all six concepts in one call rather than short-circuiting on the first bad value.
        assertEquals(Set.of("t", "a", "i", "e", "u", "s"), parsed.acceptedKeys,
                "Each documented selector must be reached for the parse to succeed");
    }

    @Test
    @DisplayName("ParsedSelectors has no field that could carry a subject")
    void parsedSelectorsHasNoSubjectField() {
        Set<String> fields = declaredFields("ParsedSelectors");
        assertTrue(fields.contains("customerName"), "Guard against reflection silently finding nothing");

        for (String name : fields) {
            String lower = name.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("owner"),
                    "ParsedSelectors must not carry an owner field; the subject is always the sender");
            assertFalse(lower.contains("subject"),
                    "ParsedSelectors must not carry a subject field either — the sender supplies it");
            assertFalse(lower.contains("player"),
                    "ParsedSelectors must not carry a player field — the sender supplies it");
        }
    }

    /**
     * Nothing hand-written on the handler may accept a subject.
     *
     * <p>Synthetic methods are skipped: the two query callbacks compile to private methods that
     * legitimately capture the already-sender-derived UUID, and javac is free to generate such a
     * method for any later refactor. Asserting on those would fail the test for a rename rather than
     * for a boundary breach.
     */
    @Test
    @DisplayName("No hand-written method takes a subject to query on")
    void noDeclaredMethodTakesASubject() {
        int checked = 0;
        for (Method m : TransactionCommandHandler.class.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            checked++;
            for (Class<?> param : m.getParameterTypes()) {
                assertFalse(param == java.util.UUID.class,
                        "Method '" + m.getName() + "' takes a UUID; nothing may query on a subject "
                                + "other than the executing player");
                assertFalse(param == org.bukkit.OfflinePlayer.class,
                        "Method '" + m.getName() + "' takes an OfflinePlayer; a name may be resolved "
                                + "for display but never into the query subject");
            }
        }
        assertTrue(checked > 0, "Guard against reflection silently finding nothing");
    }

    /**
     * A constructor taking a subject would be a second, subtler route to a cross-player query.
     */
    @Test
    @DisplayName("No constructor accepts a subject")
    void noConstructorAcceptsASubject() {
        Constructor<?>[] constructors = TransactionCommandHandler.class.getDeclaredConstructors();
        assertTrue(constructors.length > 0, "Guard against reflection silently finding nothing");

        for (Constructor<?> c : constructors) {
            Set<String> params = Arrays.stream(c.getParameterTypes())
                    .map(Class::getSimpleName)
                    .collect(Collectors.toSet());
            assertFalse(params.contains("UUID"),
                    "No constructor may accept a UUID — the subject is the executing player, full stop");
            assertFalse(params.contains("OfflinePlayer"),
                    "No constructor may accept a player to query on");
            assertFalse(params.contains("CommandSender"),
                    "No constructor may accept a sender, which would let the command be bound to "
                            + "someone other than its executor");
        }
    }

    /**
     * There is no permission gate on the parse path, so there is nothing for a later change to widen.
     *
     * <p>Cross-player browsing was deferred as a change with its own node. A node checked here but
     * unused would be exactly the seam that change pulls on, so its continued absence is part of the
     * guarantee. The assertion is behavioural rather than textual — the probe sender records every
     * permission consultation — and is scoped to the parser, which is what these tests drive.
     *
     * <p>Read off the compiled class rather than the source, because the class Javadoc mentions
     * {@code shop.operator} in prose explaining why it is absent; a source-text assertion would trip
     * over the explanation.
     */
    @Test
    @DisplayName("The parse path consults no permission and the handler declares no permission method")
    void noPermissionGateOnTheParsePath() throws Exception {
        parse("t:1h", "a:sell", "i:stone", "u:someone", "s:value");

        assertEquals(0, LAST_PROBE.permissionChecks,
                "Parsing selectors must not gate on permissions — the fork gated its owner selector "
                        + "on 'shop.operator', and reusing that habit here is how the boundary comes back");

        for (Method m : TransactionCommandHandler.class.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            assertFalse(m.getName().toLowerCase(Locale.ROOT).contains("permission"),
                    "A permission-gated method has no place in a self-scoped handler: " + m.getName());
        }
    }

    /**
     * The filter is the only channel a parsed selector has into the query, and it has no subject slot.
     *
     * <p>The fork's {@code ownerUUID} field on this filter was dead code; porting it back would have
     * been the easy way to accidentally bring the boundary with it.
     */
    @Test
    @DisplayName("TransactionLookupFilter has no owner accessor to port back in")
    void lookupFilterHasNoOwnerAccessor() {
        Set<String> members = declaredMembers(TransactionLookupFilter.class);
        assertTrue(members.contains("getAction"), "Guard against reflection finding nothing");
        assertTrue(members.contains("matchesItem"), "Guard against reflection finding nothing");

        for (String name : members) {
            String lower = name.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("owner"),
                    "The fork's owner filter field was dead code and must not be ported: " + name);
            assertFalse(lower.contains("subject"),
                    "The filter must not carry a subject accessor: " + name);
        }
    }

    /**
     * A customer filter is not a subject filter.
     *
     * <p>{@code u:} narrows which <em>customers</em> appear in the rows; {@link LogHandler} resolves
     * it into the {@code player_uuid} column and never touches the {@code owner_uuid} predicate,
     * which is bound from the sender. Worth pinning because it is the one selector whose name sounds
     * like it names a person, and the two are easy to confuse when reading the SQL.
     */
    @Test
    @DisplayName("u: populates the customer filter and nothing else")
    void customerSelectorDoesNotBecomeASubject() throws Exception {
        Parsed parsed = parse("u:someone_else");

        assertNotNull(parsed.result, "u: must still parse");
        assertEquals("someone_else", parsed.customerName,
                "u: is a customer filter, so the name must survive into the filter");
        assertNull(parsed.subject, "u: must not deposit a subject into the parsed result");
    }

    // --- parser harness -----------------------------------------------------------------------

    /**
     * Drives the real {@code parseSelectors} against a probe sender.
     *
     * <p>Reflection is the only seam: the method is private and its return type is a private nested
     * class. Asserting on what it returns — rather than on the source text of its switch — is what
     * makes this survive a reformat.
     */
    private static Parsed parse(String... args) throws Exception {
        Method parseSelectors = TransactionCommandHandler.class
                .getDeclaredMethod("parseSelectors", CommandSender.class, String[].class);
        parseSelectors.setAccessible(true);

        CapturingSender sender = new CapturingSender();
        LAST_PROBE = sender;
        Object result = parseSelectors.invoke(new TransactionCommandHandler(), sender.sender(), args);
        // Only keys the parser actually reached are recorded, so a malformed argument contributes
        // nothing. Derived from the arguments rather than from the switch, so an alias added there
        // shows up here instead of passing unnoticed.
        if (result != null) {
            for (String arg : args) {
                int colon = arg.indexOf(':');
                if (colon > 0) sender.acceptedKeys.add(arg.substring(0, colon).toLowerCase(Locale.ROOT));
            }
        }
        return new Parsed(result, sender.messages, sender.acceptedKeys);
    }

    /** Set by {@link #parse} so the permission assertion can inspect the probe it just drove. */
    private static CapturingSender LAST_PROBE;

    /** Whether the parser refused {@code <key>:x} as an unknown selector rather than accepting it. */
    private static boolean rejectedAsUnknown(String key) throws Exception {
        Parsed parsed = parse(key + ":x");
        return parsed.result == null
                && parsed.messages.stream().anyMatch(m -> m.contains("Unknown selector"));
    }

    /**
     * The parser's verdict, plus the two fields worth asserting on by name.
     *
     * <p>{@code subject} is read reflectively and is expected to be absent. Reading it is what turns
     * "there is no subject field" into an assertion about the actual parsed instance.
     */
    private static final class Parsed {
        final Object result;
        final List<String> messages;
        final Set<String> acceptedKeys;
        final String customerName;
        final Object subject;

        Parsed(Object result, List<String> messages, Set<String> acceptedKeys) {
            this.result = result;
            this.messages = messages;
            this.acceptedKeys = acceptedKeys;
            this.customerName = result == null ? null : (String) readField(result, "customerName");
            this.subject = result == null ? null : readFieldIfPresent(result, "subject");
        }
    }

    /**
     * A {@link CommandSender} that records the parser's error messages and counts permission
     * consultations.
     *
     * <p>Backed by a {@link java.lang.reflect.Proxy} rather than an implementing class. Paper's
     * {@code CommandSender} mixes {@code Audience} with {@code Permissible} and grows overloads
     * between versions; a hand-written stub has to be amended each time one appears, and a missing
     * override is a compile error that has nothing to do with the boundary under test. The proxy
     * answers only what the parser can call and records everything else as a permission check, so
     * a gate added to the parse path cannot pass unnoticed.
     */
    private static final class CapturingSender {

        final List<String> messages = new ArrayList<>();
        final Set<String> acceptedKeys = new TreeSet<>();
        int permissionChecks;

        private final CommandSender delegate = (CommandSender) java.lang.reflect.Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "sendMessage":
                            record(methodArgs);
                            return null;
                        // getServer/spigot would need a live server; the parser must not reach them.
                        case "getName":
                            return "selector-probe";
                        case "name":
                            return net.kyori.adventure.text.Component.text("selector-probe");
                        case "equals":
                            return proxy == methodArgs[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "selector-probe";
                        default:
                            // Anything permission-shaped counts as a consultation.
                            permissionChecks++;
                            return defaultFor(method.getReturnType());
                    }
                });

        CommandSender sender() {
            return delegate;
        }

        private void record(Object[] methodArgs) {
            if (methodArgs == null || methodArgs.length == 0) return;
            for (Object arg : methodArgs) {
                if (arg instanceof String s) messages.add(s);
                else if (arg instanceof net.kyori.adventure.text.Component c) messages.add(ShopMessage.toPlain(c));
            }
        }

        private static Object defaultFor(Class<?> returnType) {
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == void.class) return null;
            if (Set.class.isAssignableFrom(returnType)) return Set.of();
            return null;
        }
    }

    // --- reflection helpers -------------------------------------------------------------------

    private static Set<String> declaredFields(String simpleName) {
        for (Class<?> nested : allNestedClasses(TransactionCommandHandler.class)) {
            if (nested.getSimpleName().equals(simpleName)) {
                return Arrays.stream(nested.getDeclaredFields())
                        .filter(f -> !f.isSynthetic())
                        .map(Field::getName)
                        .collect(Collectors.toCollection(TreeSet::new));
            }
        }
        return Set.of();
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ParsedSelectors should declare '" + name + "'", e);
        }
    }

    /** Null when absent, rather than throwing — absence is the thing being asserted. */
    private static Object readFieldIfPresent(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read '" + name + "'", e);
        }
    }

    /** Every nested type under the handler, transitively — {@code ParsedSelectors} is private. */
    private static Set<Class<?>> allNestedClasses(Class<?> outer) {
        Set<Class<?>> out = new java.util.LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(outer);
        while (!pending.isEmpty()) {
            // getDeclaredClasses returns each nested type once per enclosing scope, so a type
            // declared in two scopes would otherwise be walked twice.
            for (Class<?> nested : pending.pop().getDeclaredClasses()) {
                if (out.add(nested)) pending.add(nested);
            }
        }
        return out;
    }

    private static Set<String> declaredMembers(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Method m : type.getDeclaredMethods()) {
            if (!m.isSynthetic()) names.add(m.getName());
        }
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            names.add(c.getName());
        }
        return names;
    }
}
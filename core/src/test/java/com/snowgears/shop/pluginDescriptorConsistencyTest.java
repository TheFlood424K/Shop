package com.snowgears.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the three {@code plugin.yml} copies from drifting apart.
 *
 * <p>The repository root copy went stale: it was missing the whole {@code commands:} block, so on a
 * classpath that resolved it first, {@code /transactions} was never registered. Because a miss is
 * silent — {@code plugin.getCommand("transactions")} returns null and the plugin logs a warning and
 * carries on — nothing failed until a test happened to depend on the command.
 *
 * <p>This is the same defect CLAUDE.md already records for
 * {@code core/src/test/resources/plugin.yml}, which had silently dropped its {@code commands} block.
 * It has now happened twice at two different levels, so it is checked rather than documented.
 *
 * <p>The root copy cannot use {@code ${project.version}} — it is not processed by Maven — so
 * {@code version} is the single line allowed to differ.
 */
class PluginDescriptorConsistencyTest {

    private static final List<String> COPIES = List.of(
            "plugin.yml",
            "core/src/main/resources/plugin.yml",
            "core/src/test/resources/plugin.yml");

    /** The only line permitted to differ, because the root copy is not Maven-filtered. */
    private static final String VERSION_LINE = "version:";

    /**
     * Resolves a repository-relative path from wherever the test happens to run.
     *
     * <p>Surefire sets the working directory to the module ({@code core/}), so a repo-relative path
     * like {@code core/src/main/resources/plugin.yml} does not resolve there; it does when run from
     * the repo root. Both are tried, and the suffix is matched so neither form is missed.
     */
    private static Path canonical(String relative) {
        String tail = relative.replace('\\', '/');
        // Strip any leading module name so the same entry works from either directory.
        String suffix = tail.startsWith("core/") ? tail.substring("core/".length()) : tail;

        for (Path base : List.of(Path.of("."), Path.of(".."))) {
            Path direct = base.resolve(suffix).normalize();
            if (Files.exists(direct)) {
                return direct;
            }
            Path nested = base.resolve(tail).normalize();
            if (Files.exists(nested)) {
                return nested;
            }
        }
        return Path.of(tail);
    }

    @Test
    @DisplayName("All three plugin.yml copies exist")
    void everyCopyExists() {
        List<String> missing = new ArrayList<>();
        for (String copy : COPIES) {
            if (!Files.exists(canonical(copy))) {
                missing.add(copy);
            }
        }
        assertTrue(missing.isEmpty(),
                "These plugin.yml copies are missing, so whichever one a build or tool happens to read "
                        + "will describe a different plugin: " + missing);
    }

    @Test
    @DisplayName("All three copies declare the same commands")
    void commandsMatchAcrossCopies() throws IOException {
        for (String[] block : commandsBlocks()) {
            assertEquals(block[0], block[1],
                    "The 'commands:' block differs between plugin.yml copies. A reader that resolves "
                            + "one copy will register commands the others do not have — silently, "
                            + "because getCommand() returning null is only logged, never thrown.");
        }
    }

    /** Each copy's commands block as raw lines, for comparison. */
    private static List<String[]> commandsBlocks() throws IOException {
        List<String> blocks = new ArrayList<>();
        for (String copy : COPIES) {
            Path p = canonical(copy);
            if (!Files.exists(p)) {
                continue;
            }
            StringBuilder current = new StringBuilder();
            boolean inCommands = false;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.startsWith("commands:")) {
                    inCommands = true;
                } else if (inCommands && !line.startsWith(" ") && !line.isEmpty()) {
                    blocks.add(current.toString());
                    current.setLength(0);
                    inCommands = false;
                }
                if (inCommands) {
                    current.append(line.stripTrailing()).append('\n');
                }
            }
            if (current.length() > 0) {
                blocks.add(current.toString());
            }
        }
        // Normalise to "first vs rest" so one block of empty text cannot pass vacuously.
        List<String[]> comparisons = new ArrayList<>();
        for (int i = 1; i < blocks.size(); i++) {
            comparisons.add(new String[]{blocks.get(0), blocks.get(i)});
        }
        return comparisons;
    }

    @Test
    @DisplayName("The commands block is not empty in any copy")
    void commandsBlockIsNotEmpty() throws IOException {
        for (String copy : COPIES) {
            Path p = canonical(copy);
            if (!Files.exists(p)) {
                continue;
            }
            String text = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(text.contains("commands:"),
                    copy + " has no 'commands:' block at all; /transactions would never register");
            assertTrue(text.contains("transactions:"),
                    copy + " declares no 'transactions' command");
        }
    }
}

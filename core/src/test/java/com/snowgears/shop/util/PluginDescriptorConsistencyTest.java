package com.snowgears.shop.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that test/resources/plugin.yml is kept in sync with main/resources/plugin.yml.
 *
 * Historically, the test copy silently dropped the {@code commands:} block, which caused
 * {@code getCommand("transactions")} to return null under MockBukkit and made {@code /tx}
 * untestable. This test ensures that any future changes to the main plugin.yml are reflected
 * in the test resources, preventing silent divergence.
 */
class PluginDescriptorConsistencyTest {

    private static final Path MAIN_PLUGIN_YML = Paths.get("src/main/resources/plugin.yml");
    private static final Path TEST_PLUGIN_YML = Paths.get("src/test/resources/plugin.yml");

    @Test
    void testPluginDescriptorConsistency() throws IOException {
        // Both files must exist
        assertTrue(Files.exists(MAIN_PLUGIN_YML), "Main plugin.yml not found at " + MAIN_PLUGIN_YML);
        assertTrue(Files.exists(TEST_PLUGIN_YML), "Test plugin.yml not found at " + TEST_PLUGIN_YML);

        // Read both files
        String mainContent = Files.readString(MAIN_PLUGIN_YML);
        String testContent = Files.readString(TEST_PLUGIN_YML);

        // They must be byte-identical
        assertEquals(mainContent, testContent,
            "test/resources/plugin.yml has diverged from main/resources/plugin.yml. " +
            "Copy the main plugin.yml to test resources to restore consistency.");
    }
}
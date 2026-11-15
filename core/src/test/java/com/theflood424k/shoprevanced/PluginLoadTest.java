package com.theflood424k.shoprevanced;

import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic sanity check to verify the plugin can be loaded and enabled inside a MockBukkit environment.
 * <p>
 * This ensures that MockBukkit is correctly wired into the build and that the plugin’s lifecycle
 * methods do not immediately throw exceptions when run on a mock server.
 */
public class PluginLoadTest extends BaseMockBukkitTest {
    @Test
    void pluginShouldEnable() {
        assertNotNull(getPlugin(), "Plugin should be loaded by MockBukkit");
        assertTrue(getPlugin().isEnabled(), "Plugin should be enabled inside MockBukkit environment");
    }
}

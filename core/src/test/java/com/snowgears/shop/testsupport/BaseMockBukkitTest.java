package com.snowgears.shop.testsupport;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.util.CurrencyType;
import com.snowgears.shop.util.ShopCreationUtil;
import com.snowgears.shop.util.ShopMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;

/**
 * Base class for MockBukkit based tests.
 * <p>
 * Brings up a single {@link ServerMock} once per test-class and tears it down afterwards.
 * Sub-classes can call {@link #getServer()} or {@link #getPlugin()}.
 * Example usage:
 * <pre>
 * public class PluginLoadTest extends BaseMockBukkitTest {
 *     @Test
 *     void pluginShouldEnable() {
 *         assertNotNull(getPlugin(), "Plugin should be loaded by MockBukkit");
 *         assertTrue(getPlugin().isEnabled(), "Plugin should be enabled inside MockBukkit environment");
 *     }
 * }
 * </pre>
 */
public abstract class BaseMockBukkitTest {

    private static ServerMock server;
    private static Shop plugin;

    @BeforeEach
    public void initServer() {
        // Clean up data folder from previous test runs to avoid loading stale shops
        cleanDataFolder();

        server = MockBukkit.mock();
        plugin = MockBukkit.load(Shop.class);

        // Copy config files from test resources to plugin data folder so ShopMessage can load them
        copyTestConfigsToDataFolder();

        // Reload ShopMessage configs since they were copied after plugin initialization
        ShopMessage.reloadConfigs(plugin);

        server.getScheduler().waitAsyncTasksFinished();

        setConfig("checkUpdates", false);
        // setRawConfig("logging.type", "OFF"); // happens after the plugin is loaded so kinda useless...
        // Disable displays to avoid NMS/NBT code paths in tests
        setConfig("displayType", DisplayType.NONE);
        // No cooldown between shop creations to allow us to create multiple. We can change this in tests if needed.
        setConfig("debug_shopCreateCooldown", 0);
        // Disable shop initialization timeout in tests
        setConfig("debug_shopInitTimeout", 0);

        // Test worlds can opt into a chunk auto-load patch via addSimpleWorldPatched(name)
    }

    /**
     * Creates a {@link StubbedPlayerMock} and registers it with the server.
     *
     * <p>MockBukkit v26.2 (4.116.1) implements several entity APIs the Shop plugin depends on —
     * {@code setVisibleByDefault}, {@code getTargetBlockExact}, and friends — as
     * {@code UnimplementedOperationException} stubs. That exception extends JUnit's
     * {@code TestAbortedException}, so any test touching one is reported as <em>skipped</em> rather
     * than failed, silently hiding coverage. {@link StubbedPlayerMock} supplies real implementations.
     *
     * <p>Prefer this over {@code getServer().addPlayer(name)}: MockBukkit's {@code PlayerMockFactory}
     * is {@code final} with no setter, so its own {@code addPlayer} cannot be redirected.
     */
    protected static PlayerMock addStubbedPlayer(String name) {
        PlayerMock player = new StubbedPlayerMock(server, name);
        server.addPlayer(player);
        return player;
    }

    /** As {@link #addStubbedPlayer(String)}, but with operator permissions. */
    protected static PlayerMock addStubbedOpPlayer(String name) {
        PlayerMock player = addStubbedPlayer(name);
        player.setOp(true);
        return player;
    }

    private void copyTestConfigsToDataFolder() {
        String[] configFiles = {"chatConfig.yml", "signConfig.yml", "displayConfig.yml", "guiConfig.yml", "config.yml"};
        File dataFolder = plugin.getDataFolder();
        if (dataFolder != null && !dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        for (String configFile : configFiles) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(configFile)) {
                if (in != null) {
                    File outFile = new File(dataFolder, configFile);
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        in.transferTo(out);
                    }
                    plugin.getLogger().info("[DEBUG] Copied config: " + configFile + " to " + outFile.getAbsolutePath());
                } else {
                    plugin.getLogger().warning("[DEBUG] Config file not found in resources: " + configFile);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[DEBUG] Could not copy test config: " + configFile + " - " + e.getMessage());
            }
        }
    }

    @AfterEach
    public void tearDownServer() {
        // Must disable, otherwise shutdown is slow at test end
        plugin.onDisable();
        server.getScheduler().waitAsyncTasksFinished();

        // Clean up data folder after test
        cleanDataFolder();

        // Unmock the server to cleanup after ourselves
        MockBukkit.unmock();

        server = null;
        plugin = null;
    }

    private void cleanDataFolder() {
        if (plugin != null) {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder != null && dataFolder.exists()) {
                deleteRecursive(dataFolder);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }

    protected ServerMock getServer() {
        return server;
    }

    protected Shop getPlugin() {
        return plugin;
    }

    // ---------- Test tooling helpers ----------
    /**
     * Feeds a line of chat to the plugin's chat-creation handler.
     * <p>
     * Fires {@link AsyncChatEvent}, which is what {@code MiscListener#onPlayerChat} actually
     * listens for. The older {@code AsyncPlayerChatEvent} it replaces is still accepted by the
     * server for compatibility, but no Shop handler subscribes to it, so firing it silently
     * delivers the input to nobody.
     */
    protected static void sendChatMessage(PlayerMock player, String message) {
        AsyncChatEvent chatEvent = new AsyncChatEvent(
                true,
                player,
                Collections.emptySet(),
                ChatRenderer.defaultRenderer(),
                Component.text(message),
                Component.text(message),
                null
        );
        try {
            server.getScheduler().executeAsyncEvent(chatEvent).get();
            // The handler runs async and hops its display work back onto the main thread;
            // run those scheduled tasks so their messages/entities land before we assert.
            server.getScheduler().performTicks(1);
            server.getScheduler().waitAsyncTasksFinished();
        } catch (Error | Exception e) {
            throw new RuntimeException("Failed to send chat message", e);
        }
    }

    protected static String waitForNextMessage(PlayerMock player) {
        String nextMessage = null;
        int attempts = 0;
        while (nextMessage == null && attempts < 1000) {
            nextMessage = player.nextMessage();
            if (nextMessage == null) {
                server.getScheduler().performTicks(1);
                attempts++;
            }
        }
        System.out.println("ticks: " + attempts + ", message: `" + nextMessage + "`");
        return nextMessage;
    }

    protected static void setRawConfig(String fieldName, String value) {
        plugin.getConfig().set(fieldName, value);
    }

    protected static <T> T getPluginField(String fieldName) {
        try {
            var field = Shop.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(plugin);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field '" + fieldName + "' on Shop", e);
        }
    }

    protected static void setPluginField(String fieldName, Object value) {
        try {
            var field = Shop.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(plugin, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on Shop", e);
        }
    }

    protected static void setConfig(String fieldName, Object value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, boolean value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, int value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, double value) {
        setPluginField(fieldName, value);
    }

    protected static <E extends Enum<E>> void setConfig(String fieldName, E value) {
        setPluginField(fieldName, value);
    }

    // Allow tests to stub calculateBlockFaceForSign to avoid MockBukkit material checks
    protected static void stubCalculateBlockFaceForSign(BlockFace face) {
        ShopCreationUtil original = getPluginField("shopCreationUtil");
        if (original != null && !Mockito.mockingDetails(original).isMock()) {
            ShopCreationUtil spy = Mockito.spy(original);
            Mockito.doReturn(face).when(spy).calculateBlockFaceForSign(Mockito.any(), Mockito.any(), Mockito.any());
            setPluginField("shopCreationUtil", spy);
        }
    }

    protected static void setupEconomy() {
        // Inject Economy mock if Vault currency is enabled
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            Economy mockedEconomy = Mockito.mock(Economy.class);
            Mockito.when(mockedEconomy.getBalance(Mockito.any(org.bukkit.OfflinePlayer.class))).thenReturn(10_000.0);
            Mockito.when(mockedEconomy.withdrawPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                    .thenAnswer(inv -> new EconomyResponse(inv.getArgument(1), 10_000.0, ResponseType.SUCCESS, "ok"));
            Mockito.when(mockedEconomy.depositPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                    .thenAnswer(inv -> new EconomyResponse(inv.getArgument(1), 10_000.0, ResponseType.SUCCESS, "ok"));
            setPluginField("econ", mockedEconomy);
        }
    }

    // ---------- World helpers to simulate Bukkit's implicit chunk loading ----------
    /**
     * Creates a WorldMock and returns a spy that auto-loads chunks on common accessors
     * (getBlockAt, getChunkAt). Tests should prefer this over server.addSimpleWorld(...)
     * when verifying chunk-load behavior.
     * This can be removed once we have pulled our changes into MockBukkit.
     */
    protected WorldMock addSimpleWorldPatched(String name) {
        WorldMock world = getServer().addSimpleWorld(name);
        WorldMock spyWorld = Mockito.spy(world);

        // getBlockAt(Location) -> ensure chunk loaded
        Mockito.doAnswer(inv -> {
            Location loc = inv.getArgument(0);
            if (loc != null && loc.getWorld() != null) {
                int cx = loc.getBlockX() >> 4;
                int cz = loc.getBlockZ() >> 4;
                spyWorld.loadChunk(cx, cz);
            }
            return inv.callRealMethod();
        }).when(spyWorld).getBlockAt(Mockito.any(Location.class));

        // getBlockAt(int, int, int) -> ensure chunk loaded
        Mockito.doAnswer(inv -> {
            int x = inv.getArgument(0);
            int z = inv.getArgument(2);
            int cx = x >> 4;
            int cz = z >> 4;
            spyWorld.loadChunk(cx, cz);
            return inv.callRealMethod();
        }).when(spyWorld).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());

        // getChunkAt(Location)
        Mockito.doAnswer(inv -> {
            Location loc = inv.getArgument(0);
            if (loc != null && loc.getWorld() != null) {
                int cx = loc.getBlockX() >> 4;
                int cz = loc.getBlockZ() >> 4;
                spyWorld.loadChunk(cx, cz);
            }
            return inv.callRealMethod();
        }).when(spyWorld).getChunkAt(Mockito.any(Location.class));

        // getChunkAt(int, int)
        Mockito.doAnswer(inv -> {
            int cx = inv.getArgument(0);
            int cz = inv.getArgument(1);
            spyWorld.loadChunk(cx, cz);
            return inv.callRealMethod();
        }).when(spyWorld).getChunkAt(Mockito.anyInt(), Mockito.anyInt());

        return spyWorld;
    }
}
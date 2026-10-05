package com.snowgears.shop.util;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.StubbedPlayers;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers corrupt and half-written {@code PlayerData} files (issue #99).
 *
 * <p>{@code PlayerExperience.loadFromFile}, one package over, was hardened against exactly this and
 * carries a comment naming the requirement: <em>"a corrupt file must not throw out of the caller."</em>
 * {@code PlayerData} violated that contract on the very next lines.
 *
 * <p>The silent half is the {@code getBoolean} fields — a missing key reads as {@code false} rather
 * than throwing, so a player who had flight enabled loses it with no error at all. The writer amends
 * its file in place, so a crash mid-write produces exactly that shape.
 */
class PlayerDataCorruptFileTest extends BaseMockBukkitTest {

    private File dataFileFor(PlayerMock player) {
        return new File(new File(getPlugin().getDataFolder(), "Data/LimitedCreative"),
                player.getUniqueId() + ".yml");
    }

    /** Seeds a file with only the given keys, mimicking a write interrupted partway through. */
    private void seedFile(PlayerMock player, java.util.Map<String, Object> keys) {
        File file = dataFileFor(player);
        file.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        keys.forEach(config::set);
        try {
            config.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to seed PlayerData for " + player.getUniqueId(), e);
        }
    }

    /** The format locationFromString expects: world,x,y,z. */
    private String locationString() {
        return getServer().getWorlds().get(0).getName() + ",0,64,0";
    }

    private PlayerMock playerNamed(String name) {
        return StubbedPlayers.add(getServer(), name);
    }

    @Test
    @DisplayName("A file missing the UUID and gamemode keys returns null instead of throwing")
    void missingKeysReturnNull() {
        PlayerMock player = playerNamed("MissingKeys");
        seedFile(player, java.util.Map.of(
                "player.allowFlight", true,
                "player.isFlying", true));

        assertDoesNotThrow(() -> PlayerData.loadFromFile(player),
                "A corrupt file must not throw out of the caller");
        assertNull(PlayerData.loadFromFile(player),
                "Both call sites treat null as 'nothing to restore', so null is the correct signal");
    }

    @Test
    @DisplayName("A file with an unparseable UUID returns null instead of throwing")
    void corruptUuidReturnsNull() {
        PlayerMock player = playerNamed("CorruptUuid");
        seedFile(player, java.util.Map.of(
                "player.UUID", "not-a-uuid",
                "player.gamemode", "SURVIVAL"));

        assertDoesNotThrow(() -> PlayerData.loadFromFile(player));
        assertNull(PlayerData.loadFromFile(player));
    }

    @Test
    @DisplayName("A file with an unknown gamemode returns null instead of throwing")
    void corruptGamemodeReturnsNull() {
        PlayerMock player = playerNamed("CorruptGamemode");
        seedFile(player, java.util.Map.of(
                "player.UUID", player.getUniqueId().toString(),
                "player.gamemode", "FLYINGG"));

        assertDoesNotThrow(() -> PlayerData.loadFromFile(player));
        assertNull(PlayerData.loadFromFile(player));
    }

    @Test
    @DisplayName("A null UUID or gamemode is reported as incomplete rather than parsed")
    void nullValuesAreCaught() {
        // getString returns null for an absent key, which NPEs UUID.fromString and GameMode.valueOf.
        PlayerMock a = playerNamed("NullUuid");
        seedFile(a, java.util.Map.of("player.gamemode", "SURVIVAL"));
        assertDoesNotThrow(() -> PlayerData.loadFromFile(a));
        assertNull(PlayerData.loadFromFile(a));

        PlayerMock b = playerNamed("NullGamemode");
        seedFile(b, java.util.Map.of("player.UUID", b.getUniqueId().toString()));
        assertDoesNotThrow(() -> PlayerData.loadFromFile(b));
        assertNull(PlayerData.loadFromFile(b));
    }

    @Test
    @DisplayName("A complete file still loads, so the guard does not over-reject")
    void completeFileStillLoads() {
        PlayerMock player = playerNamed("CompleteFile");
        seedFile(player, java.util.Map.of(
                "player.UUID", player.getUniqueId().toString(),
                "player.gamemode", "SURVIVAL",
                "player.shopSignLocation", locationString(),
                "player.allowFlight", true,
                "player.isFlying", false,
                "player.guiSearch", true));

        PlayerData loaded = PlayerData.loadFromFile(player);
        assertNotNull(loaded, "A well-formed file must still load — the guard rejects only what it cannot parse");
        assertEquals(player.getUniqueId(), loaded.getPlayerUUID(),
                "The stored UUID must survive the parse");
        assertEquals(GameMode.SURVIVAL, loaded.getOldGameMode());
        assertTrue(loaded.isGuiSearch(), "The stored guiSearch flag must survive the parse");
    }
}
package com.snowgears.shop.util;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the floor on offline experience removal (issue #66).
 *
 * <p>{@code removeExperienceAmount} subtracted with no clamp and saved the result, so an offline
 * player paying an experience cost could be driven to a negative stored balance. The online path
 * could not: {@code setTotalExperience} recomputes level and exp, which floors at level 0. That
 * asymmetry — same charge, different outcome depending on whether the player happened to be logged
 * in — is the defect.
 */
class PlayerExperienceTest extends BaseMockBukkitTest {

    private File expFileFor(UUID uuid) {
        return new File(new File(getPlugin().getDataFolder(), "Data/OfflineExperience"), uuid + ".yml");
    }

    /** Writes a stored balance directly, bypassing the plugin's own save path. */
    private void writeStoredExperience(UUID uuid, int amount) {
        File file = expFileFor(uuid);
        file.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("player.UUID", uuid.toString());
        config.set("player.experience", amount);
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to seed experience file for " + uuid, e);
        }
    }

    @Test
    void removingExactBalanceLeavesZero() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, 500);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        assertNotNull(exp);

        exp.removeExperienceAmount(500);

        assertEquals(0, exp.getExperience(), "Paying an exact balance should leave zero");
    }

    /** The regression: this drove the stored balance negative and persisted it. */
    @Test
    void removingMoreThanTheBalanceClampsToZero() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, 100);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        assertNotNull(exp);

        exp.removeExperienceAmount(1000);

        assertEquals(0, exp.getExperience(),
                "An offline player must not be driven to a negative balance, matching the online path");
    }

    @Test
    void removingPartOfTheBalanceLeavesTheRemainder() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, 500);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        assertNotNull(exp);

        exp.removeExperienceAmount(200);

        assertEquals(300, exp.getExperience());
    }

    /** The clamp must survive a round-trip — the old code persisted the negative value. */
    @Test
    void clampedValueIsPersisted() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, 50);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        exp.removeExperienceAmount(5000);

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(expFileFor(uuid));
        assertEquals(0, reread.getInt("player.experience"),
                "The clamp has to be written to disk, not just held in memory");
    }

    /** Data written by the unclamped version is repaired on load rather than left negative forever. */
    @Test
    void negativeStoredBalanceIsRepairedOnLoad() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, -250);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));

        assertNotNull(exp);
        assertEquals(0, exp.getExperience(),
                "A negative balance from a previous version must be clamped when read");
        assertEquals(0, YamlConfiguration.loadConfiguration(expFileFor(uuid)).getInt("player.experience"),
                "The repair is written back, so the value is fixed rather than re-derived each read");
    }

    @Test
    void addingAfterAClampedRemovalStillWorks() {
        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, 10);

        PlayerExperience exp = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        exp.removeExperienceAmount(1000);
        exp.addExperienceAmount(250);

        assertEquals(250, exp.getExperience(), "A clamp must not wedge the balance permanently at zero");
    }

    /**
     * The asymmetry this issue is about: the same over-draw against an online player lands at zero,
     * so the offline path must agree.
     */
    @Test
    void offlineAndOnlinePathsAgreeOnOverdraw() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock online = addStubbedPlayer("Spender");
        online.setLevel(10);
        online.setExp(0f);
        int balance = EconomyUtils.getTotalExperience(online);
        assertTrue(balance > 0, "Precondition: the player starts with experience");

        EconomyUtils.removeFunds(online, online.getInventory(), balance * 4);

        int onlineResult = EconomyUtils.getTotalExperience(online);

        UUID uuid = UUID.randomUUID();
        writeStoredExperience(uuid, balance);
        PlayerExperience offline = PlayerExperience.loadFromFile(getServer().getOfflinePlayer(uuid));
        offline.removeExperienceAmount(balance * 4);

        assertEquals(0, onlineResult, "An online player cannot hold a negative experience total");
        assertEquals(offline.getExperience(), onlineResult,
                "Both paths must land in the same place for the same over-draw");
    }

    @Test
    void missingFileYieldsNoStoredExperience() {
        assertEquals(null, PlayerExperience.loadFromFile(getServer().getOfflinePlayer(UUID.randomUUID())),
                "A player with no stored experience file has no offline balance to spend");
    }

    /**
     * Found while testing this issue. {@code setTotalExperience} solved its quadratic only for
     * amounts above zero, so an over-draw produced a negative remainder and handed it to
     * {@code giveExp}, which rejects it — the online path threw rather than clamping.
     */
    @Test
    void setTotalExperienceWithZeroDoesNotThrow() {
        PlayerMock player = addStubbedPlayer("ZeroSetter");

        EconomyUtils.setTotalExperience(player, 0);

        assertEquals(0, player.getLevel());
        assertEquals(0f, player.getExp(), 0.0001f, "Zero total experience is level 0 with a full bar");
    }

    @Test
    void setTotalExperienceWithNegativeDoesNotThrow() {
        PlayerMock player = addStubbedPlayer("NegativeSetter");

        EconomyUtils.setTotalExperience(player, -500);

        assertEquals(0, player.getLevel(), "A negative total clamps to zero rather than propagating");
    }
}
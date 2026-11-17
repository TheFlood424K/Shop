package com.snowgears.shop;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import net.milkbowl.vault.economy.Economy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Spigot-to-Paper migration compatibility changes:
 * VaultUnlocked economy integration, GriefPrevention3D hook, and GeyserMC/Floodgate compatibility.
 * <p>
 * Note: {@link Shop#setupEconomy()} calls {@code getServer().getPluginManager().getPlugin("VaultUnlocked")}
 * and {@code getServer().getServicesManager().getRegistration(Economy.class)}. The MockBukkit
 * {@code ServerMock.pluginManager} field is final, so it cannot be replaced with a stub to simulate
 * VaultUnlocked being installed. The positive VaultUnlocked path is therefore verified by directly
 * injecting the {@code econ} field (which setupEconomy populates) and checking {@link Shop#getEconomy()}.
 */
class ShopCompatibilityTest extends BaseMockBukkitTest {

    // ---------- VaultUnlocked / Economy ----------

    @Test
    void setupEconomy_noVaultUnlockedPlugin_returnsFalse() throws Exception {
        // VaultUnlocked is not installed in the MockBukkit test environment,
        // so getPlugin("VaultUnlocked") returns null and setupEconomy returns false.
        Method setupEconomy = Shop.class.getDeclaredMethod("setupEconomy");
        setupEconomy.setAccessible(true);
        boolean result = (boolean) setupEconomy.invoke(getPlugin());

        assertFalse(result, "setupEconomy should return false when VaultUnlocked is not installed");
    }

    @Test
    void economy_injected_viaEconField_getEconomyReturnsIt() throws Exception {
        // Simulates what setupEconomy() does on success: sets the econ field from the Economy service.
        Economy mockedEconomy = Mockito.mock(Economy.class);
        setPluginField("econ", mockedEconomy);

        assertSame(mockedEconomy, getPlugin().getEconomy(),
                "getEconomy() should return the economy instance set by setupEconomy");
    }

    // ---------- GriefPrevention3D ----------

    @Test
    void griefPreventionIntegrationEnabled_defaultsToTrue() throws Exception {
        boolean enabled = getPluginField("griefPreventionIntegrationEnabled");
        assertTrue(enabled, "griefPreventionIntegrationEnabled should default to true");
    }

    @Test
    void isGriefPreventionIntegrationEnabled_getterReturnsFieldValue() throws Exception {
        setPluginField("griefPreventionIntegrationEnabled", false);
        assertFalse(getPlugin().isGriefPreventionIntegrationEnabled(),
                "isGriefPreventionIntegrationEnabled() should return false when field is false");

        setPluginField("griefPreventionIntegrationEnabled", true);
        assertTrue(getPlugin().isGriefPreventionIntegrationEnabled(),
                "isGriefPreventionIntegrationEnabled() should return true when field is true");
    }

    // ---------- GeyserMC / Floodgate ----------

    @Test
    void geyserIntegrationEnabled_defaultsToTrue() throws Exception {
        boolean enabled = getPluginField("geyserIntegrationEnabled");
        assertTrue(enabled, "geyserIntegrationEnabled should default to true");
    }

    @Test
    void isGeyserIntegrationEnabled_getterReturnsFieldValue() throws Exception {
        setPluginField("geyserIntegrationEnabled", false);
        assertFalse(getPlugin().isGeyserIntegrationEnabled(),
                "isGeyserIntegrationEnabled() should return false when field is false");

        setPluginField("geyserIntegrationEnabled", true);
        assertTrue(getPlugin().isGeyserIntegrationEnabled(),
                "isGeyserIntegrationEnabled() should return true when field is true");
    }
}

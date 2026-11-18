package com.theflood424k.shoprevanced.config;

import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.ShopAction;
import com.theflood424k.shoprevanced.util.ShopClickType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigValidator}.
 *
 * <p>The validator is a pure class (no Bukkit/server APIs), so these tests
 * instantiate {@code new ConfigValidator()} directly and call the raw
 * {@code validate(double, ...)} method. A few tests additionally exercise the
 * {@link ConfigValidator#validate(ConfigManager, Economy)} overload with a
 * mocked {@link ConfigManager}.</p>
 */
class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();

    // All-valid defaults
    private static final double VALID_CREATION_COST = 10.0;
    private static final double VALID_DESTRUCTION_COST = 5.0;
    private static final double VALID_TELEPORT_COST = 2.0;
    private static final double VALID_TELEPORT_COOLDOWN = 60.0;
    private static final double VALID_TAX_PERCENT = 5.0;
    private static final double VALID_DISPLAY_PROCESS_INTERVAL = 1.0;
    private static final double VALID_MAX_SHOP_DISPLAY_DISTANCE = 100.0;
    private static final int VALID_SHOP_SEARCH_RADIUS = 50;
    private static final int VALID_DISPLAY_BATCH_SIZE = 10;
    private static final int VALID_DISPLAY_BATCH_DELAY = 100;
    private static final Double VALID_PRICE_SUFFIX_MINIMUM_VALUE = 0.0;

    private static NavigableMap<Double, String> validPriceSuffixes() {
        return new TreeMap<>(Map.of(1.0, "k", 1_000.0, "M", 1_000_000.0, "B"));
    }

    private static Map<ShopClickType, ShopAction> validClickTypeActionMap() {
        return Map.of(ShopClickType.RIGHT_CLICK_CHEST, ShopAction.TRANSACT);
    }

    private void validateAllValidDefaults() {
        validator.validate(
            VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
            VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
            VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
            validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
            validClickTypeActionMap(), "ITEM", "NONE");
    }

    private static void expectInvalid(Runnable action, String... expectedSubstrings) {
        try {
            action.run();
            fail("Expected IllegalStateException but none was thrown");
        } catch (IllegalStateException e) {
            for (String substring : expectedSubstrings) {
                assertTrue(e.getMessage().contains(substring),
                    () -> "Expected message to contain [" + substring + "] but was: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("validate(double, ...) - raw values")
    class RawValidateTests {

        @Test
        @DisplayName("all valid values -> no exception")
        void allValidValuesNoException() {
            assertDoesNotThrow(() -> validateAllValidDefaults());
        }

        @Test
        @DisplayName("boundary: taxPercent 0 and 100 -> no exception")
        void taxPercentBoundariesAreValid() {
            assertDoesNotThrow(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                0.0, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"));
            assertDoesNotThrow(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                100.0, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"));
        }

        @Test
        @DisplayName("boundary: costs of 0 and teleportCooldown of 0 -> no exception")
        void zeroCostsAndCooldownAreValid() {
            assertDoesNotThrow(() -> validator.validate(
                0.0, 0.0, 0.0, 0.0, VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL,
                VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE,
                VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE,
                CurrencyType.ITEM, null, validClickTypeActionMap(), "ITEM", "NONE"));
        }

        @Test
        @DisplayName("creationCost < 0 -> IllegalStateException mentioning creationCost")
        void negativeCreationCost() {
            expectInvalid(() -> validator.validate(
                -1.0, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN, VALID_TAX_PERCENT,
                VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "creationCost");
        }

        @Test
        @DisplayName("destructionCost < 0 -> IllegalStateException mentioning destructionCost")
        void negativeDestructionCost() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, -1.0, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN, VALID_TAX_PERCENT,
                VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "destructionCost");
        }

        @Test
        @DisplayName("teleportCost < 0 -> IllegalStateException mentioning teleportCost")
        void negativeTeleportCost() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, -1.0, VALID_TELEPORT_COOLDOWN, VALID_TAX_PERCENT,
                VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "teleportCost");
        }

        @Test
        @DisplayName("teleportCooldown < 0 -> IllegalStateException mentioning teleportCooldown")
        void negativeTeleportCooldown() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, -1.0, VALID_TAX_PERCENT,
                VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "teleportCooldown");
        }

        @Test
        @DisplayName("taxPercent < 0 -> IllegalStateException mentioning taxPercent")
        void negativeTaxPercent() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                -0.01, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "taxPercent");
        }

        @Test
        @DisplayName("taxPercent > 100 -> IllegalStateException mentioning taxPercent")
        void taxPercentOver100() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                100.01, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "taxPercent");
        }

        @Test
        @DisplayName("displayProcessInterval <= 0 -> IllegalStateException")
        void nonPositiveDisplayProcessInterval() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, 0.0, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "displayProcessInterval");
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, -5.0, VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "displayProcessInterval");
        }

        @Test
        @DisplayName("maxShopDisplayDistance <= 0 -> IllegalStateException")
        void nonPositiveMaxShopDisplayDistance() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, 0.0, VALID_SHOP_SEARCH_RADIUS,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "maxShopDisplayDistance");
        }

        @Test
        @DisplayName("shopSearchRadius <= 0 -> IllegalStateException")
        void nonPositiveShopSearchRadius() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE, 0,
                VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "shopSearchRadius");
        }

        @Test
        @DisplayName("displayBatchSize <= 0 -> IllegalStateException")
        void nonPositiveDisplayBatchSize() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, 0, VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "displayBatchSize");
        }

        @Test
        @DisplayName("displayBatchDelay <= 0 -> IllegalStateException")
        void nonPositiveDisplayBatchDelay() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, 0, validPriceSuffixes(),
                VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "displayBatchDelay");
        }

        @Test
        @DisplayName("price suffixes with negative value -> IllegalStateException")
        void negativePriceSuffixValue() {
            Map<Double, String> suffixes = new TreeMap<>(Map.of(-1.0, "k"));
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                suffixes, VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "priceSuffixes", "negative");
        }

        @Test
        @DisplayName("price suffixes out of order -> IllegalStateException")
        void unsortedPriceSuffixes() {
            // LinkedHashMap preserves insertion order, which is deliberately out of order here
            Map<Double, String> suffixes = new java.util.LinkedHashMap<>();
            suffixes.put(1_000.0, "M");
            suffixes.put(1.0, "k");
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                suffixes, VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "priceSuffixes", "sorted");
        }

        @Test
        @DisplayName("price suffixes with blank label -> IllegalStateException")
        void blankPriceSuffixLabel() {
            Map<Double, String> suffixes = new TreeMap<>(Map.of(1.0, ""));
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                suffixes, VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "priceSuffixes", "blank");
        }

        @Test
        @DisplayName("priceSuffixMinimumValue null -> IllegalStateException mentioning minimumValue")
        void nullPriceSuffixMinimumValue() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), null, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "priceSuffixes.minimumValue");
        }

        @Test
        @DisplayName("priceSuffixMinimumValue < 0 -> IllegalStateException mentioning minimumValue")
        void negativePriceSuffixMinimumValue() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), -1.0, CurrencyType.ITEM, null, validClickTypeActionMap(),
                "ITEM", "NONE"), "priceSuffixes.minimumValue");
        }

        @Test
        @DisplayName("currencyType VAULT with null economy -> IllegalStateException mentioning Economy provider")
        void vaultRequiresEconomy() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.VAULT, null,
                validClickTypeActionMap(), "ITEM", "NONE"), "Economy");
        }

        @Test
        @DisplayName("currencyType VAULT with non-null economy -> no exception")
        void vaultWithEconomyNoException() {
            Economy economy = mock(Economy.class);
            assertDoesNotThrow(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.VAULT, economy,
                validClickTypeActionMap(), "ITEM", "NONE"));
        }

        @Test
        @DisplayName("currencyType ITEM or EXPERIENCE with null economy -> no exception")
        void itemAndExperienceWithoutEconomy() {
            assertDoesNotThrow(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "NONE"));
            assertDoesNotThrow(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.EXPERIENCE, null,
                validClickTypeActionMap(), "ITEM", "NONE"));
        }

        @Test
        @DisplayName("clickTypeActionMap null -> IllegalStateException mentioning actionMappings")
        void nullClickTypeActionMap() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                null, "ITEM", "NONE"), "actionMappings");
        }

        @Test
        @DisplayName("clickTypeActionMap empty -> IllegalStateException mentioning actionMappings")
        void emptyClickTypeActionMap() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                Map.of(), "ITEM", "NONE"), "actionMappings");
        }

        @Test
        @DisplayName("clickTypeActionMap null key -> IllegalStateException mentioning null click type")
        void nullClickTypeKey() {
            Map<ShopClickType, ShopAction> map = new java.util.HashMap<>();
            map.put(null, ShopAction.TRANSACT);
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                map, "ITEM", "NONE"), "null click type");
        }

        @Test
        @DisplayName("clickTypeActionMap null value -> IllegalStateException mentioning null shop action")
        void nullClickTypeActionValue() {
            Map<ShopClickType, ShopAction> map = new java.util.HashMap<>();
            map.put(ShopClickType.RIGHT_CLICK_CHEST, null);
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                map, "ITEM", "NONE"), "null shop action");
        }

        @Test
        @DisplayName("displayType invalid enum value -> IllegalStateException mentioning displayType")
        void invalidDisplayType() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "BOGUS", "NONE"), "displayType", "DisplayType");
        }

        @Test
        @DisplayName("displayNameTags invalid enum value -> IllegalStateException mentioning displayNameTags")
        void invalidDisplayNameTags() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "ITEM", "BOGUS"), "displayNameTags", "DisplayTagOption");
        }

        @Test
        @DisplayName("blank or null displayType -> IllegalStateException")
        void blankOrNullDisplayType() {
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), null, "NONE"), "displayType", "must not be blank");
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), "", "NONE"), "displayType", "must not be blank");
            expectInvalid(() -> validator.validate(
                VALID_CREATION_COST, VALID_DESTRUCTION_COST, VALID_TELEPORT_COST, VALID_TELEPORT_COOLDOWN,
                VALID_TAX_PERCENT, VALID_DISPLAY_PROCESS_INTERVAL, VALID_MAX_SHOP_DISPLAY_DISTANCE,
                VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE, VALID_DISPLAY_BATCH_DELAY,
                validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE, CurrencyType.ITEM, null,
                validClickTypeActionMap(), " ", "NONE"), "displayType", "must not be blank");
        }

        @Test
        @DisplayName("multiple problems at once -> single IllegalStateException listing all problems")
        void multipleProblemsConsolidated() {
            IllegalStateException ex = expectInvalidReturning(() -> validator.validate(
                -10.0, VALID_DESTRUCTION_COST, -2.0, VALID_TELEPORT_COOLDOWN, 150.0, 0.0,
                VALID_MAX_SHOP_DISPLAY_DISTANCE, VALID_SHOP_SEARCH_RADIUS, VALID_DISPLAY_BATCH_SIZE,
                VALID_DISPLAY_BATCH_DELAY, validPriceSuffixes(), VALID_PRICE_SUFFIX_MINIMUM_VALUE,
                CurrencyType.VAULT, null, validClickTypeActionMap(), "ITEM", "NONE"));
            assertTrue(ex.getMessage().contains("creationCost"), "missing creationCost problem: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("teleportCost"), "missing teleportCost problem: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("taxPercent"), "missing taxPercent problem: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("displayProcessInterval"), "missing displayProcessInterval problem: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("Economy"), "missing Economy problem: " + ex.getMessage());
        }

        private IllegalStateException expectInvalidReturning(Runnable action) {
            try {
                action.run();
                fail("Expected IllegalStateException but none was thrown");
                return null;
            } catch (IllegalStateException e) {
                return e;
            }
        }
    }

    @Nested
    @DisplayName("validate(ConfigManager, Economy)")
    class ConfigManagerDelegateTests {

        private ConfigManager validConfigMock() {
            ConfigManager config = mock(ConfigManager.class);
            when(config.getCreationCost()).thenReturn(VALID_CREATION_COST);
            when(config.getDestructionCost()).thenReturn(VALID_DESTRUCTION_COST);
            when(config.getTeleportCost()).thenReturn(VALID_TELEPORT_COST);
            when(config.getTeleportCooldown()).thenReturn(VALID_TELEPORT_COOLDOWN);
            when(config.getTaxPercent()).thenReturn(VALID_TAX_PERCENT);
            when(config.getDisplayProcessInterval()).thenReturn(VALID_DISPLAY_PROCESS_INTERVAL);
            when(config.getMaxShopDisplayDistance()).thenReturn(VALID_MAX_SHOP_DISPLAY_DISTANCE);
            when(config.getShopSearchRadius()).thenReturn(VALID_SHOP_SEARCH_RADIUS);
            when(config.getDisplayBatchSize()).thenReturn(VALID_DISPLAY_BATCH_SIZE);
            when(config.getDisplayBatchDelay()).thenReturn(VALID_DISPLAY_BATCH_DELAY);
            when(config.getPriceSuffixes()).thenReturn(validPriceSuffixes());
            when(config.getPriceSuffixMinimumValue()).thenReturn(VALID_PRICE_SUFFIX_MINIMUM_VALUE);
            when(config.getCurrencyType()).thenReturn(CurrencyType.ITEM);
            when(config.getClickTypeActionMap()).thenReturn(validClickTypeActionMap());
            YamlConfiguration yaml = mock(YamlConfiguration.class);
            when(yaml.getString("displayType")).thenReturn("ITEM");
            when(yaml.getString("displayNameTags")).thenReturn("NONE");
            when(config.getConfig()).thenReturn(yaml);
            return config;
        }

        @Test
        @DisplayName("all valid config values -> no exception")
        void allValidConfigValuesNoException() {
            ConfigManager config = validConfigMock();
            assertDoesNotThrow(() -> validator.validate(config, null));
        }

        @Test
        @DisplayName("currency type VAULT with null economy -> IllegalStateException mentioning Economy provider")
        void vaultRequiresEconomyViaConfigManager() {
            ConfigManager config = validConfigMock();
            when(config.getCurrencyType()).thenReturn(CurrencyType.VAULT);
            expectInvalid(() -> validator.validate(config, null), "Economy");
        }

        @Test
        @DisplayName("currency type VAULT with non-null economy -> no exception")
        void vaultWithEconomyViaConfigManagerNoException() {
            ConfigManager config = validConfigMock();
            when(config.getCurrencyType()).thenReturn(CurrencyType.VAULT);
            assertDoesNotThrow(() -> validator.validate(config, mock(Economy.class)));
        }

        @Test
        @DisplayName("currency type ITEM with null economy -> no exception")
        void itemWithoutEconomyViaConfigManagerNoException() {
            ConfigManager config = validConfigMock();
            when(config.getCurrencyType()).thenReturn(CurrencyType.ITEM);
            assertDoesNotThrow(() -> validator.validate(config, null));
        }
    }
}

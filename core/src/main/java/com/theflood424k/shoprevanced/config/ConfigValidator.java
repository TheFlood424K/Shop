package com.theflood424k.shoprevanced.config;

import com.theflood424k.shoprevanced.display.DisplayTagOption;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.ShopAction;
import com.theflood424k.shoprevanced.util.ShopClickType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Fail-fast validation of ShopRevanced configuration values.
 *
 * <p>Every check is a pure function of the loaded configuration: no Bukkit or
 * server APIs are touched, so the validator can be unit tested without a mock
 * server. {@link #validate(ConfigManager, Economy)} throws
 * {@link IllegalStateException} with a consolidated message listing every
 * problem found, rather than failing on the first one.
 */
public class ConfigValidator {

    /**
     * Validates raw configuration values.
     *
     * @param creationCost             cost to create a shop
     * @param destructionCost          cost to destroy a shop
     * @param teleportCost             cost to teleport to a shop
     * @param teleportCooldown         cooldown between teleports
     * @param taxPercent               tax percentage (0-100)
     * @param displayProcessInterval   interval between display updates
     * @param maxShopDisplayDistance   max distance for shop displays
     * @param shopSearchRadius         radius for shop searches
     * @param displayBatchSize         batch size for display updates
     * @param displayBatchDelay        delay between display batches
     * @param priceSuffixes            sorted map of price suffixes (value -> label)
     * @param priceSuffixMinimumValue  minimum value for price suffixes
     * @param currencyType             type of currency (VAULT, ITEM, EXPERIENCE)
     * @param itemCurrency             item used as currency (may be null for VAULT)
     * @param clickTypeActionMap       mapping from click types to shop actions
     * @param itemListType             item list type string (e.g. "ITEM", "NONE")
     * @param currencyName             currency name tag option string (e.g. "NONE")
     * @throws IllegalStateException if any value is invalid, with all problems listed
     */
    public void validate(
            double creationCost,
            double destructionCost,
            double teleportCost,
            double teleportCooldown,
            double taxPercent,
            double displayProcessInterval,
            double maxShopDisplayDistance,
            int shopSearchRadius,
            int displayBatchSize,
            int displayBatchDelay,
            NavigableMap<Double, String> priceSuffixes,
            Double priceSuffixMinimumValue,
            CurrencyType currencyType,
            ItemStack itemCurrency,
            Map<ShopClickType, ShopAction> clickTypeActionMap,
            String itemListType,
            String currencyName) {

        StringBuilder problems = new StringBuilder();

        if (creationCost < 0) {
            problems.append("creationCost must not be negative\n");
        }
        if (destructionCost < 0) {
            problems.append("destructionCost must not be negative\n");
        }
        if (teleportCost < 0) {
            problems.append("teleportCost must not be negative\n");
        }
        if (teleportCooldown < 0) {
            problems.append("teleportCooldown must not be negative\n");
        }
        if (taxPercent < 0 || taxPercent > 100) {
            problems.append("taxPercent must be between 0 and 100\n");
        }
        if (displayProcessInterval <= 0) {
            problems.append("displayProcessInterval must be positive\n");
        }
        if (maxShopDisplayDistance <= 0) {
            problems.append("maxShopDisplayDistance must be positive\n");
        }
        if (shopSearchRadius <= 0) {
            problems.append("shopSearchRadius must be positive\n");
        }
        if (displayBatchSize <= 0) {
            problems.append("displayBatchSize must be positive\n");
        }
        if (displayBatchDelay <= 0) {
            problems.append("displayBatchDelay must be positive\n");
        }

        validatePriceSuffixes(priceSuffixes, priceSuffixMinimumValue, problems);

        if (currencyType == CurrencyType.ITEM && itemCurrency == null) {
            problems.append("Item currency is required when economy.type is ITEM\n");
        }

        if (clickTypeActionMap == null || clickTypeActionMap.isEmpty()) {
            problems.append("actionMappings must not be null or empty\n");
        } else {
            for (Map.Entry<ShopClickType, ShopAction> entry : clickTypeActionMap.entrySet()) {
                if (entry.getKey() == null) {
                    problems.append("actionMappings contains a null click type\n");
                }
                if (entry.getValue() == null) {
                    problems.append("actionMappings contains a null shop action\n");
                }
            }
        }

        validateDisplayValue(itemListType, ItemListType.class, "itemListType", problems);
        validateDisplayValue(currencyName, DisplayTagOption.class, "displayNameTags", problems);

        if (problems.length() > 0) {
            throw new IllegalStateException(problems.toString().trim());
        }
    }

    /**
     * Validates configuration by delegating to the raw {@code validate(double, ...)}
     * method after extracting values from {@link ConfigManager}.
     *
     * @param config  the configuration manager (must not be null)
     * @param economy the economy provider (may be null for non-VAULT currencies)
     * @throws IllegalStateException if any value is invalid
     * @throws NullPointerException  if config is null
     */
    public void validate(ConfigManager config, Economy economy) {
        if (config == null) {
            throw new NullPointerException("ConfigManager must not be null");
        }

        ItemStack itemCurrency = config.getItemCurrency();
        CurrencyType currencyType = config.getCurrencyType();

        validate(
                config.getCreationCost(),
                config.getDestructionCost(),
                config.getTeleportCost(),
                config.getTeleportCooldown(),
                config.getTaxPercent(),
                config.getDisplayProcessInterval(),
                config.getMaxShopDisplayDistance(),
                config.getShopSearchRadius(),
                config.getDisplayBatchSize(),
                config.getDisplayBatchDelay(),
                config.getPriceSuffixes(),
                config.getPriceSuffixMinimumValue(),
                currencyType,
                itemCurrency,
                config.getClickTypeActionMap(),
                config.getConfig().getString("itemList"),
                config.getConfig().getString("displayNameTags")
        );

        if (currencyType == CurrencyType.VAULT && economy == null) {
            throw new IllegalStateException("Economy provider is required when economy.type is VAULT");
        }
    }

    private void validatePriceSuffixes(
            NavigableMap<Double, String> priceSuffixes,
            Double priceSuffixMinimumValue,
            StringBuilder problems) {

        if (priceSuffixes == null || priceSuffixes.isEmpty()) {
            return;
        }

        Double previous = null;
        for (Map.Entry<Double, String> entry : priceSuffixes.entrySet()) {
            Double value = entry.getKey();
            String label = entry.getValue();

            if (value < 0) {
                problems.append("priceSuffixes contains a negative value\n");
            }
            if (label == null || label.isBlank()) {
                problems.append("priceSuffixes contains a blank label\n");
            }
            if (previous != null && value <= previous) {
                problems.append("priceSuffixes must be sorted in ascending order\n");
            }
            previous = value;
        }

        if (priceSuffixMinimumValue == null) {
            problems.append("priceSuffixes.minimumValue must not be null\n");
        } else if (priceSuffixMinimumValue < 0) {
            problems.append("priceSuffixes.minimumValue must not be negative\n");
        }
    }

    /**
     * Converts a string to an enum value, appending a problem message if the
     * string is blank, null, or not a valid enum constant.
     *
     * @param <T>        the enum type
     * @param value      the string to convert
     * @param enumType   the enum class
     * @param fieldName  the field name for error messages
     * @param problems   the string builder to append problems to
     * @return the parsed enum value, or null if invalid
     */
    private <T extends Enum<T>> T validateDisplayValue(
            String value,
            Class<T> enumType,
            String fieldName,
            StringBuilder problems) {

        if (value == null || value.isBlank()) {
            problems.append(fieldName).append(" must not be blank\n");
            return null;
        }

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            problems.append(fieldName).append(" must be a valid ").append(enumType.getSimpleName()).append("\n");
            return null;
        }
    }
}

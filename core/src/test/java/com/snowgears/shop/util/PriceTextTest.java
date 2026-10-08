package com.snowgears.shop.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure unit tests for the distinct amount, price, and multiplier input formats. */
class PriceTextTest {

    /** Verifies that amount cleaning normalizes spaces and removes decoration without validating decimal syntax. */
    @ParameterizedTest
    @CsvSource({
            "'  $1,234.50  ', 1234.50",
            "'  64   1  ', '64 1'",
            "' -10   500 ', '-10 500'",
            "'1.2.3', '1.2.3'",
            "'words only', ''"
    })
    void amountCleaningPreservesSpacesAndMalformedDecimals(String input, String expected) {
        assertEquals(expected, UtilMethods.cleanNumberText(input));
    }

    /** Verifies that price cleaning removes grouping and multiplier suffixes while preserving the numeric token. */
    @ParameterizedTest
    @CsvSource({
            "'  $1,234.50  ', 1234.50",
            "'  10   500  ', 10500",
            "'-10 500x2', -10500",
            "'100.25x12', 100.25",
            "'100x0', 100",
            "'100x2147483648', 100",
            "'1.2.3', '1.2.3'",
            "'words only', ''"
    })
    void priceCleaningKeepsThePriceSeparateFromGroupingAndMultiplier(String input, String expected) {
        assertEquals(expected, UtilMethods.priceToken(input));
    }

    /** Verifies explicit multipliers at integer boundaries and the fallback for an overflowing value. */
    @ParameterizedTest
    @CsvSource({
            "x0, 0",
            "100x12, 12",
            "100.5x2, 2",
            "x2147483647, 2147483647",
            "100x2147483648, 1"
    })
    void explicitMultipliersRespectIntegerBounds(String input, int expected) {
        assertEquals(expected, UtilMethods.getMultiplyValue(input));
    }

    /** Verifies that missing or malformed multiplier markers leave the effective multiplier at one. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"100", "100 / 250", "$1,234.50", "x", "x-2", "words"})
    void absentOrInvalidMultiplierDefaultsToOne(String input) {
        assertEquals(1, UtilMethods.getMultiplyValue(input));
    }

    /** Verifies that both cleaners map null to zero text while preserving an empty string. */
    @ParameterizedTest
    @NullAndEmptySource
    void cleanersHandleMissingInput(String input) {
        String expected = input == null ? "0" : "";
        assertEquals(expected, UtilMethods.cleanNumberText(input));
        assertEquals(expected, UtilMethods.priceToken(input));
    }
}

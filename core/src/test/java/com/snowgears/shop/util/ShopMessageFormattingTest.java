package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ShopMessageFormattingTest extends BaseMockBukkitTest {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().build();

    /**
     * Verifies that standalone legacy, hex and formatting codes are recognized as directives.
     *
     * @param code the standalone directive text
     */
    @ParameterizedTest
    @ValueSource(strings = {"#12AbEf", "#000000", "#FFFFFF", "&a", "§4", "&L", "&r"})
    void bareColorAndFormattingComponentsAreDirectives(String code) {
        assertTrue(ShopMessage.isColorDirective(Component.text(code)));
    }

    /**
     * Verifies that invalid codes and codes mixed with text remain ordinary content.
     *
     * @param text the content that must not be treated as a directive
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "#12345", "#1234567", "#GG0000", " #123456", "#123456 label", "&z"})
    void ordinaryOrMalformedTextIsNotAColorDirective(String text) {
        assertFalse(ShopMessage.isColorDirective(Component.text(text)));
    }

    /** Verifies that child-bearing and translatable components are not standalone color directives. */
    @Test
    void componentsWithChildrenAndTranslationsRemainContent() {
        assertFalse(ShopMessage.isColorDirective(Component.text("#123456").append(Component.text(" label"))));
        assertFalse(ShopMessage.isColorDirective(Component.translatable("#123456")));
    }

    /**
     * Verifies that stock selects a hex color which styles the following text without appearing literally.
     *
     * @param stock the shop's reported stock
     * @param admin whether the shop is an admin shop
     * @param expectedHex the expected color of the visible text
     */
    @ParameterizedTest
    @CsvSource({"1, false, #12AbEf", "0, false, #654321", "-1, false, #654321", "0, true, #12AbEf"})
    void stockHexColorStylesFollowingTextWithoutPrintingTheCode(int stock, boolean admin, String expectedHex) {
        String previousInStock = ShopMessage.getStockColorInStock();
        String previousOutOfStock = ShopMessage.getStockColorOutOfStock();
        try {
            ShopMessage.setStockColorInStock("#12AbEf");
            ShopMessage.setStockColorOutOfStock("#654321");
            AbstractShop shop = mock(AbstractShop.class);
            when(shop.getStock()).thenReturn(stock);
            when(shop.isAdmin()).thenReturn(admin);
            PlaceholderContext context = new PlaceholderContext();
            context.setShop(shop);

            Component actual = ShopMessage.format("[stock color]Available", context);

            assertEquals("Available", ShopMessage.toPlain(actual));
            assertStyledEquals(Component.text("Available", TextColor.fromHexString(expectedHex)), actual);
        } finally {
            ShopMessage.setStockColorInStock(previousInStock);
            ShopMessage.setStockColorOutOfStock(previousOutOfStock);
        }
    }

    /** Verifies that a hex placeholder preserves bold formatting until a reset restores plain white text. */
    @Test
    void hexPlaceholderPreservesFormattingUntilAReset() {
        String key = "[test color directive]";
        var previous = ShopMessage.getPlaceholders().get(key);
        ShopMessage.registerPlaceholder(key, context -> Component.text("#123456"));
        try {
            Component actual = ShopMessage.format("&l" + key + "Bold&rPlain", new PlaceholderContext());
            Component expected = Component.text("Bold", TextColor.fromHexString("#123456"))
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text("Plain", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));

            assertEquals("BoldPlain", ShopMessage.toPlain(actual));
            assertStyledEquals(expected, actual);
        } finally {
            if (previous == null) ShopMessage.getPlaceholders().remove(key);
            else ShopMessage.registerPlaceholder(key, previous);
        }
    }

    /**
     * Verifies that creation prompts resolve each shop type through the configured chat vocabulary.
     *
     * @param type the shop type requested by the creation process
     */
    @ParameterizedTest
    @EnumSource(ShopType.class)
    void creationPromptUsesTheConfiguredChatWord(ShopType type) {
        String key = type.getConfigKey().toUpperCase();
        String previous = ShopMessage.getCreationWords().put(key, "Localized " + type.name());
        try {
            ShopCreationProcess process = mock(ShopCreationProcess.class);
            when(process.getShopType()).thenReturn(type);
            PlaceholderContext context = new PlaceholderContext();
            context.setProcess(process);

            assertEquals("Create Localized " + type.name(),
                    ShopMessage.toPlain(ShopMessage.format("Create [shop type]", context)));
        } finally {
            if (previous == null) ShopMessage.getCreationWords().remove(key);
            else ShopMessage.getCreationWords().put(key, previous);
        }
    }

    /**
     * Compares visible text and styling through legacy serialization, independent of component layout.
     *
     * @param expected the expected styled component
     * @param actual the component produced by formatting
     */
    private static void assertStyledEquals(Component expected, Component actual) {
        // Serialization compares visible styling without depending on the component tree layout.
        assertEquals(LEGACY.serialize(expected), LEGACY.serialize(actual));
    }
}

package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ShopMessageSignWordTest extends BaseMockBukkitTest {
    /**
     * Verifies localized sign words, literal chat branding, and preservation of the cached sign template.
     *
     * @param type a shop type with a configured sign template
     */
    @ParameterizedTest
    // GAMBLE has no sign template in the test configuration.
    @EnumSource(value = ShopType.class, names = "GAMBLE", mode = EnumSource.Mode.EXCLUDE)
    void namedSignLinesUseSignWordsWhileChatKeepsTheBrandTag(ShopType type) {
        var config = ShopMessage.getSignConfig();
        Object previousShop = config.get("sign_creation.SHOP");
        String typePath = "sign_creation." + type.name();
        Object previousType = config.get(typePath);
        String[] template = ShopMessage.getSignLines(type.name());
        String[] previousTemplate = template.clone();
        try {
            config.set("sign_creation.SHOP", "Market");
            config.set(typePath, "Localized " + type.name());
            java.util.Arrays.fill(template, "");
            template[0] = "[shop] | [" + type.name().toLowerCase() + " shop]";
            AbstractShop shop = mock(AbstractShop.class);
            when(shop.getType()).thenReturn(type);

            String[] formatted = ShopMessage.getSignLines(type.name(), shop);

            assertEquals("Market | Localized " + type.name(), ChatColor.stripColor(formatted[0]));
            assertEquals(template[0], ShopMessage.getSignLines(type.name())[0],
                    "Formatting must not replace the cached raw template");
            assertArrayEquals(template, ShopMessage.getSignLines(type.name(), null));
            PlaceholderContext chatContext = new PlaceholderContext();
            chatContext.setShop(shop);
            assertEquals("[Shop]", ShopMessage.toPlain(ShopMessage.format("[shop]", chatContext)));
        } finally {
            config.set("sign_creation.SHOP", previousShop);
            config.set(typePath, previousType);
            System.arraycopy(previousTemplate, 0, template, 0, template.length);
        }
    }

    /**
     * Verifies that missing or empty sign words fall back to the lowercase lookup key.
     *
     * @param configuredWord the absent or empty configuration value
     */
    @ParameterizedTest
    @NullAndEmptySource
    void absentOrEmptySignWordFallsBackToLowercaseKey(String configuredWord) {
        var config = ShopMessage.getSignConfig();
        Object previous = config.get("sign_creation.SHOP");
        try {
            config.set("sign_creation.SHOP", configuredWord);
            assertEquals("shop", ShopMessage.getSignCreationWord("sHoP"));
        } finally {
            config.set("sign_creation.SHOP", previous);
        }
    }
}

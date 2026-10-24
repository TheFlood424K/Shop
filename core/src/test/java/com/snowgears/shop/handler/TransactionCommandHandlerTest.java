package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class TransactionCommandHandlerTest extends BaseMockBukkitTest {

    private TransactionCommandHandler handler;
    private Shop plugin;

    @BeforeEach
    void setup() {
        plugin = getPlugin();
        handler = new TransactionCommandHandler(plugin);
    }

    @Test
    void testParseSortOption_PriceAsc() {
        // Use reflection to test private method
        TransactionCommandHandler.SortOption option = invokeParseSortOption("price_asc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.PRICE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.ASC, option.direction);
    }

    @Test
    void testParseSortOption_PriceDesc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("price_desc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.PRICE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.DESC, option.direction);
    }

    @Test
    void testParseSortOption_AmountAsc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("amount_asc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.AMOUNT, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.ASC, option.direction);
    }

    @Test
    void testParseSortOption_DateDesc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("date_desc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.DATE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.DESC, option.direction);
    }

    @Test
    void testParseSortOption_ItemAsc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("item_asc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.ITEM, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.ASC, option.direction);
    }

    @Test
    void testParseSortOption_TypeDesc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("type_desc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.TYPE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.DESC, option.direction);
    }

    @Test
    void testParseSortOption_PlayerAsc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("player_asc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.PLAYER, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.ASC, option.direction);
    }

    @Test
    void testParseSortOption_ShopDesc() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("shop_desc");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.SHOP, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.DESC, option.direction);
    }

    @Test
    void testParseSortOption_DefaultDirection_Date() {
        // Date should default to DESC
        TransactionCommandHandler.SortOption option = invokeParseSortOption("date");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.DATE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.DESC, option.direction);
    }

    @Test
    void testParseSortOption_DefaultDirection_Price() {
        // Price should default to ASC
        TransactionCommandHandler.SortOption option = invokeParseSortOption("price");
        assertNotNull(option);
        assertEquals(TransactionCommandHandler.SortField.PRICE, option.field);
        assertEquals(TransactionCommandHandler.SortDirection.ASC, option.direction);
    }

    @Test
    void testParseSortOption_InvalidField() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("invalid_asc");
        assertNull(option);
    }

    @Test
    void testParseSortOption_InvalidDirection() {
        TransactionCommandHandler.SortOption option = invokeParseSortOption("price_invalid");
        assertNull(option);
    }

    @Test
    void testTabComplete_FirstArgument() {
        List<String> completions = handler.tabComplete(null, "tx", new String[]{""});
        assertTrue(completions.contains("page"));
        assertTrue(completions.contains("top"));
        assertTrue(completions.contains("sort:price_asc"));
        assertTrue(completions.contains("sort:price_desc"));
        assertTrue(completions.contains("sort:date_asc"));
        assertTrue(completions.contains("sort:date_desc"));
        assertTrue(completions.contains("limit:10"));
        assertTrue(completions.contains("limit:20"));
    }

    @Test
    void testTabComplete_PageArgument() {
        List<String> completions = handler.tabComplete(null, "tx", new String[]{"page", ""});
        // Should return empty or just handle gracefully
        assertNotNull(completions);
    }

    @Test
    void testTabComplete_TopArgument() {
        List<String> completions = handler.tabComplete(null, "tx", new String[]{"top", ""});
        assertTrue(completions.contains("sort:price_asc"));
        assertTrue(completions.contains("sort:price_desc"));
        assertTrue(completions.contains("limit:10"));
    }

    @Test
    void testSortOptionToString() {
        TransactionCommandHandler.SortOption option = new TransactionCommandHandler.SortOption(
                TransactionCommandHandler.SortField.PRICE,
                TransactionCommandHandler.SortDirection.ASC
        );
        assertEquals("price_asc", option.toString());

        TransactionCommandHandler.SortOption option2 = new TransactionCommandHandler.SortOption(
                TransactionCommandHandler.SortField.DATE,
                TransactionCommandHandler.SortDirection.DESC
        );
        assertEquals("date_desc", option2.toString());
    }

    @Test
    void testSortFieldFromString() {
        assertEquals(TransactionCommandHandler.SortField.PRICE, TransactionCommandHandler.SortField.fromString("price"));
        assertEquals(TransactionCommandHandler.SortField.AMOUNT, TransactionCommandHandler.SortField.fromString("amount"));
        assertEquals(TransactionCommandHandler.SortField.DATE, TransactionCommandHandler.SortField.fromString("date"));
        assertEquals(TransactionCommandHandler.SortField.ITEM, TransactionCommandHandler.SortField.fromString("item"));
        assertEquals(TransactionCommandHandler.SortField.TYPE, TransactionCommandHandler.SortField.fromString("type"));
        assertEquals(TransactionCommandHandler.SortField.PLAYER, TransactionCommandHandler.SortField.fromString("player"));
        assertEquals(TransactionCommandHandler.SortField.SHOP, TransactionCommandHandler.SortField.fromString("shop"));
        assertNull(TransactionCommandHandler.SortField.fromString("invalid"));
    }

    @Test
    void testSortDirectionFromString() {
        assertEquals(TransactionCommandHandler.SortDirection.ASC, TransactionCommandHandler.SortDirection.fromString("asc"));
        assertEquals(TransactionCommandHandler.SortDirection.DESC, TransactionCommandHandler.SortDirection.fromString("desc"));
        assertNull(TransactionCommandHandler.SortDirection.fromString("invalid"));
    }

    // Helper method to invoke private parseSortOption method via reflection
    private TransactionCommandHandler.SortOption invokeParseSortOption(String arg) {
        try {
            java.lang.reflect.Method method = TransactionCommandHandler.class.getDeclaredMethod("parseSortOption", String.class);
            method.setAccessible(true);
            return (TransactionCommandHandler.SortOption) method.invoke(handler, arg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke parseSortOption", e);
        }
    }
}
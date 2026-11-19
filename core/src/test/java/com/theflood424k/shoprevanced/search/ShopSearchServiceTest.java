package com.theflood424k.shoprevanced.search;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedHandler;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopSearchServiceTest extends BaseMockBukkitTest {

    private AbstractShop mockShop(String ownerName, double price, int stock, boolean admin,
                                  Material material, String displayName) {
        AbstractShop shop = Mockito.mock(AbstractShop.class);
        when(shop.getOwnerName()).thenReturn(ownerName);
        when(shop.getPrice()).thenReturn(price);
        when(shop.getStock()).thenReturn(stock);
        when(shop.isAdmin()).thenReturn(admin);

        ItemStack item = new ItemStack(material);
        if (displayName != null) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(displayName));
            item.setItemMeta(meta);
        }
        when(shop.getItemStack()).thenReturn(item);
        return shop;
    }

    private void handlerWithShops(AbstractShop... shops) {
        ShopRevancedHandler handler = Mockito.mock(ShopRevancedHandler.class);
        when(handler.getAllShops()).thenReturn(List.of(shops));
        setPluginField("ShopRevancedHandler", handler);
    }

    @Test
    void searchWithNoFilterReturnsAllShops() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, null),
                mockShop("Bob", 20.0, 0, false, Material.STONE, null)
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter(null, null, null, null, null));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.relevanceScore() == 1.0));
    }

    @Test
    void searchByItemNameExactMatch() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, "Dirt Block"),
                mockShop("Bob", 20.0, 0, false, Material.STONE, "Stone Block")
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter("dirt", null, null, null, null));

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).shop().getOwnerName());
        assertEquals(11.0, results.get(0).relevanceScore(), 0.001); // base 1.0 + exact 10.0
    }

    @Test
    void searchByItemNamePartialMatch() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, "Dirt Block"),
                mockShop("Bob", 20.0, 0, false, Material.STONE, "Stone Block")
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter("block", null, null, null, null));

        assertEquals(2, results.size());
        // Both have "block" in display name -> partial match bonus
        assertTrue(results.stream().allMatch(r -> r.relevanceScore() == 6.0)); // base 1.0 + partial 5.0
    }

    @Test
    void searchByItemNameNoMatch() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, null),
                mockShop("Bob", 20.0, 0, false, Material.STONE, null)
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter("diamond", null, null, null, null));

        assertEquals(0, results.size());
    }

    @Test
    void searchByPriceRange() {
        handlerWithShops(
                mockShop("Alice", 5.0, 5, false, Material.DIRT, null),
                mockShop("Bob", 15.0, 0, false, Material.STONE, null),
                mockShop("Carol", 25.0, 3, false, Material.GRASS_BLOCK, null)
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter(null, 10.0, 20.0, null, null));

        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).shop().getOwnerName());
        assertEquals(2.0, results.get(0).relevanceScore(), 0.001); // base 1.0 + price range 1.0
    }

    @Test
    void searchByOwner() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, null),
                mockShop("Bob", 20.0, 0, false, Material.STONE, null)
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter(null, null, null, "bob", null));

        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).shop().getOwnerName());
    }

    @Test
    void searchAvailableOnlyExcludesOutOfStock() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, null),
                mockShop("Bob", 20.0, 0, false, Material.STONE, null),
                mockShop("Carol", 30.0, 0, true, Material.GRASS_BLOCK, null) // admin shop, infinite stock
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter(null, null, null, null, true));

        assertEquals(2, results.size());
        // Alice (in stock) and Carol (admin, always available)
        assertTrue(results.stream().anyMatch(r -> "Alice".equals(r.shop().getOwnerName())));
        assertTrue(results.stream().anyMatch(r -> "Carol".equals(r.shop().getOwnerName())));
    }

    @Test
    void searchCombinedFilters() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, "Dirt Block"),
                mockShop("Bob", 15.0, 0, false, Material.DIRT, "Dirt Block"),
                mockShop("Carol", 50.0, 3, false, Material.DIAMOND, "Diamond")
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(
                new SearchFilter("dirt", 5.0, 20.0, null, null)
        );

        assertEquals(2, results.size());
        // Both dirt shops within price range; Bob is out of stock but availableOnly is null
        assertTrue(results.stream().allMatch(r -> r.shop().getItemStack().getType() == Material.DIRT));
    }

    @Test
    void searchIsRankedByRelevanceDescending() {
        handlerWithShops(
                mockShop("Alice", 10.0, 5, false, Material.DIRT, "Dirt Block"),
                mockShop("Bob", 5.0, 5, false, Material.DIRT, "Dirt")
        );

        ShopSearchService service = new ShopSearchService(getPlugin());
        List<SearchResult> results = service.search(new SearchFilter("dirt", null, null, null, null));

        assertEquals(2, results.size());
        // Bob has exact match "dirt" -> score 11.0; Alice has partial match "dirt block" contains "dirt" -> score 6.0
        assertEquals("Bob", results.get(0).shop().getOwnerName());
        assertEquals("Alice", results.get(1).shop().getOwnerName());
    }

    @Test
    void paginateReturnsCorrectPage() {
        ShopSearchService service = new ShopSearchService(getPlugin());

        // Build 25 results
        var results = new java.util.ArrayList<SearchResult>();
        for (int i = 0; i < 25; i++) {
            results.add(new SearchResult(mockShop("Owner" + i, i * 1.0, i, false, Material.DIRT, null), 1.0));
        }

        List<SearchResult> page1 = service.paginate(results, 1, 10);
        assertEquals(10, page1.size());

        List<SearchResult> page2 = service.paginate(results, 2, 10);
        assertEquals(10, page2.size());

        List<SearchResult> page3 = service.paginate(results, 3, 10);
        assertEquals(5, page3.size());

        List<SearchResult> page4 = service.paginate(results, 4, 10);
        assertEquals(0, page4.size()); // out of range
    }

    @Test
    void paginateDefaultsPageSize() {
        ShopSearchService service = new ShopSearchService(getPlugin());

        var results = new java.util.ArrayList<SearchResult>();
        for (int i = 0; i < 15; i++) {
            results.add(new SearchResult(mockShop("Owner" + i, i * 1.0, i, false, Material.DIRT, null), 1.0));
        }

        List<SearchResult> page = service.paginate(results, 1, 0); // pageSize < 1 -> default
        assertEquals(10, page.size());
    }

    @Test
    void searchFilterIsEmpty() {
        assertTrue(new SearchFilter(null, null, null, null, null).isEmpty());
        assertFalse(new SearchFilter("dirt", null, null, null, null).isEmpty());
        assertFalse(new SearchFilter(null, 1.0, null, null, null).isEmpty());
        assertFalse(new SearchFilter(null, null, 10.0, null, null).isEmpty());
        assertFalse(new SearchFilter(null, null, null, "Alice", null).isEmpty());
        assertFalse(new SearchFilter(null, null, null, null, true).isEmpty());
    }

    @Test
    void searchFilterTrimsValues() {
        SearchFilter filter = new SearchFilter("  dirt  ", null, null, "  bob  ", null);
        assertEquals("dirt", filter.itemName());
        assertEquals("bob", filter.owner());
    }

    @Test
    void searchResultRecord() {
        AbstractShop shop = mockShop("Alice", 10.0, 5, false, Material.DIRT, null);
        SearchResult result = new SearchResult(shop, 42.5);
        assertSame(shop, result.shop());
        assertEquals(42.5, result.relevanceScore(), 0.001);
    }
}

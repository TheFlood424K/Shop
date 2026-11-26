package com.theflood424k.shoprevanced.search;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service that searches all shop catalogs from a single entry point.
 * <p>
 * It applies filters (item name, price range, owner, availability), ranks the
 * matching shops by relevance, and supports pagination for large result sets.
 * <p>
 * All operations are read-only and thread-safe: they operate on a snapshot of
 * the current shops returned by {@link ShopRevanced#getShopRevancedHandler()}.
 * No world/block access is performed, so no region-owned scheduling is required.
 */
public class ShopSearchService {

    private static final double BASE_SCORE = 1.0;
    private static final double EXACT_NAME_MATCH_BONUS = 10.0;
    private static final double PARTIAL_NAME_MATCH_BONUS = 5.0;
    private static final double PRICE_RANGE_BONUS = 1.0;
    private static final double IN_STOCK_BONUS = 1.0;

    private final ShopRevanced plugin;

    public ShopSearchService(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    /**
     * Searches all shops matching the given filter, ranked by relevance.
     *
     * @param filter the search filter criteria (may be empty to return all shops)
     * @return a list of search results sorted by relevance descending, then price
     *         ascending for ties; never {@code null}
     */
    public List<SearchResult> search(SearchFilter filter) {
        List<AbstractShop> shops = plugin.getShopRevancedHandler().getAllShops();
        List<SearchResult> results = new ArrayList<>(shops.size());

        String itemName = filter.itemName() == null ? null : filter.itemName().toLowerCase();
        String owner = filter.owner() == null ? null : filter.owner().toLowerCase();
        Double minPrice = filter.minPrice();
        Double maxPrice = filter.maxPrice();
        Boolean availableOnly = filter.availableOnly();

        for (AbstractShop shop : shops) {
            if (shop == null || shop.getItemStack() == null) {
                continue;
            }

            double score = BASE_SCORE;

            // ---- Item name filter ----
            if (itemName != null && !itemName.isEmpty()) {
                String typeName = shop.getItemStack().getType().name().toLowerCase();
                String displayName = "";
                if (shop.getItemStack().hasItemMeta()
                        && shop.getItemStack().getItemMeta().hasDisplayName()) {
                    displayName = PlainTextComponentSerializer.plainText()
                            .serialize(shop.getItemStack().getItemMeta().displayName()).toLowerCase();
                }

                boolean exactMatch = typeName.equals(itemName) || displayName.equals(itemName);
                boolean partialMatch = typeName.contains(itemName) || displayName.contains(itemName);

                if (!exactMatch && !partialMatch) {
                    continue; // item does not match the search term
                }
                score += exactMatch ? EXACT_NAME_MATCH_BONUS : PARTIAL_NAME_MATCH_BONUS;
            }

            // ---- Price range filter ----
            double price = shop.getPrice();
            if (minPrice != null && price < minPrice) {
                continue;
            }
            if (maxPrice != null && price > maxPrice) {
                continue;
            }
            if (minPrice != null || maxPrice != null) {
                score += PRICE_RANGE_BONUS;
            }

            // ---- Owner filter ----
            if (owner != null && !owner.isEmpty()) {
                String shopOwner = shop.getOwnerName();
                if (shopOwner == null || !shopOwner.toLowerCase().equals(owner)) {
                    continue;
                }
            }

            // ---- Availability filter ----
            if (Boolean.TRUE.equals(availableOnly)) {
                int stock = shop.getStock();
                if (shop.isAdmin()) {
                    // Admin shops have infinite stock; always considered available
                    score += IN_STOCK_BONUS;
                } else if (stock <= 0) {
                    continue; // out of stock
                } else {
                    score += IN_STOCK_BONUS;
                }
            }

            results.add(new SearchResult(shop, score));
        }

        // Rank by relevance descending, then by price ascending for ties
        results.sort(Comparator
                .comparingDouble((SearchResult r) -> -r.relevanceScore())
                .thenComparingDouble(r -> r.shop().getPrice()));

        return results;
    }

    /**
     * Returns a paginated sub-list of already-ranked search results.
     *
     * @param results  the full list of search results (already ranked)
     * @param page     the 1-based page number
     * @param pageSize the number of results per page
     * @return the sub-list for the requested page, or an empty list if out of range
     */
    public List<SearchResult> paginate(List<SearchResult> results, int page, int pageSize) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = defaultPageSize();
        }
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= results.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + pageSize, results.size());
        return results.subList(fromIndex, toIndex);
    }

    /**
     * Default number of results per page.
     *
     * @return the default page size ({@code 10})
     */
    public static int defaultPageSize() {
        return 10;
    }
}

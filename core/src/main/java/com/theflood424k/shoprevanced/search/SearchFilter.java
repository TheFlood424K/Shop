package com.theflood424k.shoprevanced.search;

import java.util.Objects;

/**
 * Filter criteria for searching shops across all catalogs.
 * <p>
 * All fields are optional; a {@code null} or empty field is not applied as a filter.
 * An empty filter matches every shop.
 */
public record SearchFilter(

        /**
         * Item name substring to match (case-insensitive). Matches against the
         * shop's item type name and display name. {@code null} means no filter.
         */
        String itemName,

        /**
         * Minimum price (inclusive). {@code null} means no lower bound.
         */
        Double minPrice,

        /**
         * Maximum price (inclusive). {@code null} means no upper bound.
         */
        Double maxPrice,

        /**
         * Shop owner name (case-insensitive exact match). {@code null} means no filter.
         */
        String owner,

        /**
         * If {@code true}, only shops with stock greater than zero are returned.
         * {@code null} means no availability filter.
         */
        Boolean availableOnly
) {

    public SearchFilter {
        itemName = itemName == null ? null : itemName.trim();
        owner = owner == null ? null : owner.trim();
    }

    /**
     * @return {@code true} if no filter criteria are set (matches all shops).
     */
    public boolean isEmpty() {
        return itemName == null && minPrice == null && maxPrice == null
                && owner == null && availableOnly == null;
    }
}

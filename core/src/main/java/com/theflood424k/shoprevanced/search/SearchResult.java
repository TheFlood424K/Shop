package com.theflood424k.shoprevanced.search;

import com.theflood424k.shoprevanced.shop.AbstractShop;

/**
 * A single search result: a shop together with its computed relevance score.
 * <p>
 * Higher relevance scores indicate a more relevant match. Results are typically
 * sorted by score descending before being presented to a player.
 */
public record SearchResult(AbstractShop shop, double relevanceScore) {
}

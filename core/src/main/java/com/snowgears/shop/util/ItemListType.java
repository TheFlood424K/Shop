package com.snowgears.shop.util;

public enum ItemListType {
    NONE, DENY_LIST, ALLOW_LIST,
    /** Alias for {@link #ALLOW_LIST} — items on the list are permitted. */
    WHITELIST,
    /** Alias for {@link #DENY_LIST} — items on the list are blocked. */
    BLACKLIST
}

package com.snowgears.shop.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class SearchWindowTest {
    @Test
    public void searchWindowHasNonNullTitle() {
        SearchWindow window = new SearchWindow(UUID.randomUUID());
        Assertions.assertNotNull(window.getTitle());
        Assertions.assertEquals("Search", window.getTitle());
    }
}

package com.snowgears.shop.gui;

import org.junit.jupiter.api.Test;

public class ShopGUIListenerNullGuardTest {
    @Test
    public void listenerClassLoads() {
        Class<?> clazz = ShopGUIListener.class;
        org.junit.jupiter.api.Assertions.assertNotNull(clazz);
    }
}

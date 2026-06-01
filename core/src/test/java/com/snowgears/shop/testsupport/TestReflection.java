package com.snowgears.shop.testsupport;

import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;

/**
 * Test-only reflection utilities.
 * <p>
 * Prefer using this helper instead of spreading reflective hacks across tests.
 */
public final class TestReflection {

    private TestReflection() {}

    public static <T> T allocateInstance(Class<T> type) {
        try {
            return new ObjenesisStd().newInstance(type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate instance for " + type.getName(), e);
        }
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on " + target.getClass().getName(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> cur = type;
        while (cur != null) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + fieldName);
    }
}



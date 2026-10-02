package com.snowgears.shop.testsupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection utilities for test code.
 */
public final class TestReflection {

    private TestReflection() {}

    /**
     * Sets a private field on an object.
     */
    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on " + target.getClass().getSimpleName(), e);
        }
    }

    /**
     * Gets a private field from an object.
     */
    public static <T> T getField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field '" + fieldName + "' from " + target.getClass().getSimpleName(), e);
        }
    }

    /**
     * Finds a field in the class hierarchy.
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field '" + fieldName + "' not found in class hierarchy of " + clazz.getSimpleName());
    }

    /**
     * Invokes a private method on an object.
     */
    public static <T> T invokeMethod(Object target, String methodName, Object... args) {
        try {
            Method method = findMethod(target.getClass(), methodName, args);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method '" + methodName + "' on " + target.getClass().getSimpleName(), e);
        }
    }

    /**
     * Finds a method in the class hierarchy.
     */
    private static Method findMethod(Class<?> clazz, String methodName, Object... args) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException("Method '" + methodName + "' with " + args.length + " params not found in " + clazz.getSimpleName());
    }
}
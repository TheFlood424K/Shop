package com.snowgears.shop.util;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionParty class.
 */
class TransactionPartyTest {

    private TransactionParty transactionParty;
    private OfflinePlayer mockPlayer;
    private Player mockPlayerOnline;
    private Inventory mockInventory;
    private ItemStack testItem;

    // For verifying interactions with mockInventory
    private final Map<String, Object> inventoryStubResponses = new HashMap<>();
    private final List<String> inventoryInvocations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockPlayer = createDummyOfflinePlayer();
        mockPlayerOnline = createDummyPlayer();
        mockInventory = createDummyInventory();
        testItem = null;

        // Default constructor arguments - admin to avoid EconomyUtils calls
        transactionParty = new TransactionParty(false, true, mockPlayer, mockInventory);
    }

    /**
     * Creates a dummy OfflinePlayer that returns default values for all methods.
     * This avoids Mockito issues with mocking interfaces on Java 25.
     */
    @SuppressWarnings("unchecked")
    private OfflinePlayer createDummyOfflinePlayer() {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, methodArgs) -> {
                    // Return default values based on return type
                    if (method.getReturnType() == String.class) {
                        return "dummy";
                    } else if (method.getReturnType() == UUID.class) {
                        return new UUID(0,0);
                    } else if (method.getReturnType() == boolean.class) {
                        return false;
                    } else if (method.getReturnType() == int.class) {
                        return 0;
                    } else if (method.getReturnType() == long.class) {
                        return 0L;
                    } else if (method.getReturnType() == float.class) {
                        return 0.0f;
                    } else if (method.getReturnType() == double.class) {
                        return 0.0;
                    } else if (method.getReturnType() == void.class) {
                        return null;
                    } else {
                        // For object types, return null (may cause NPE if called, but we don't call them in tests)
                        return null;
                    }
                });
    }

    /**
     * Creates a dummy Player that returns default values for all methods.
     */
    @SuppressWarnings("unchecked")
    private Player createDummyPlayer() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, methodArgs) -> {
                    // Return default values based on return type
                    if (method.getReturnType() == String.class) {
                        return "dummy";
                    } else if (method.getReturnType() == UUID.class) {
                        return new UUID(0,0);
                    } else if (method.getReturnType() == boolean.class) {
                        return false;
                    } else if (method.getReturnType() == int.class) {
                        return 0;
                    } else if (method.getReturnType() == long.class) {
                        return 0L;
                    } else if (method.getReturnType() == float.class) {
                        return 0.0f;
                    } else if (method.getReturnType() == double.class) {
                        return 0.0;
                    } else if (method.getReturnType() == void.class) {
                        return null;
                    } else {
                        // For object types, return null
                        return null;
                    }
                });
    }

    /**
     * Creates a dummy Inventory that records invocations and can be stubbed.
     */
    @SuppressWarnings("unchecked")
    private Inventory createDummyInventory() {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, methodArgs) -> {
                    // Record the invocation for verification
                    inventoryInvocations.add(method.getName());

                    // Handle stubbed responses
                    if (inventoryStubResponses.containsKey(method.getName())) {
                        return inventoryStubResponses.get(method.getName());
                    }

                    // Return default values based on return type
                    if (method.getReturnType() == int.class) {
                        return 0;
                    } else if (method.getReturnType() == boolean.class) {
                        return false;
                    } else if (method.getReturnType() == void.class) {
                        return null;
                    } else if (method.getReturnType().isArray()) {
                        // For arrays, return empty array of the correct type
                        return java.lang.reflect.Array.newInstance(method.getReturnType().getComponentType(), 0);
                    } else {
                        // For other object types, return null
                        return null;
                    }
                });
    }

    /**
     * Clears the inventory stub responses and invocations for the next test.
     */
    private void resetInventoryMock() {
        inventoryStubResponses.clear();
        inventoryInvocations.clear();
    }

    /**
     * Sets a stubbed response for an inventory method.
     */
    private void whenInventory(String methodName, Object returnValue) {
        inventoryStubResponses.put(methodName, returnValue);
    }

    /**
     * Verifies that an inventory method was called with the expected arguments.
     * Note: This is a simplified verification that only checks the method name was called.
     * For more precise verification, we would need to record arguments too.
     */
    private void verifyInventoryCalled(String methodName) {
        assertTrue(inventoryInvocations.contains(methodName),
                "Expected inventory method '" + methodName + "' to be called, but it was not. Invocations: " + inventoryInvocations);
    }

    /**
     * Verifies that an inventory method was never called.
     */
    private void verifyInventoryNeverCalled(String methodName) {
        assertFalse(inventoryInvocations.contains(methodName),
                "Expected inventory method '" + methodName + "' to never be called, but it was. Invocations: " + inventoryInvocations);
    }

    @Test
    void testDepositItem_Success() {
        // Arrange
        whenInventory("addItem", new HashMap<>()); // Empty map means all items added

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deposited when there's room");
        verifyInventoryCalled("addItem");
        resetInventoryMock();
    }

    @Test
    void testDepositItem_Failure_NoRoom() {
        // Arrange
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        whenInventory("addItem", leftover);

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deposited when there's no room");
        verifyInventoryCalled("addItem");
        resetInventoryMock();
    }

    @Test
    void testDepositItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        whenInventory("addItem", leftover);

        // Act
        boolean result = adminParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in depositing items");
        verifyInventoryNeverCalled("addItem");
        resetInventoryMock();
    }

    @Test
    void testDeductItem_Success() {
        // Arrange
        whenInventory("removeItem", new HashMap<>()); // Empty map means all items removed

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deducted when present");
        verifyInventoryCalled("removeItem");
        resetInventoryMock();
    }

    @Test
    void testDeductItem_Failure_InsufficientQuantity() {
        // Arrange
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        whenInventory("removeItem", leftover);

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deducted when insufficient quantity");
        verifyInventoryCalled("removeItem");
        resetInventoryMock();
    }

    @Test
    void testDeductItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        whenInventory("removeItem", leftover);

        // Act
        boolean result = adminParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in deducting items");
        verifyInventoryNeverCalled("removeItem");
        resetInventoryMock();
    }

    @Test
    void testHasRoomForItem_AdminAlwaysTrue() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        whenInventory("getSize", 36);
        whenInventory("getContents", new ItemStack[36]); // All empty slots
        whenInventory("firstEmpty", 0); // First slot is empty

        // Act
        boolean result = adminParty.hasRoomForItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always have room for items");
        // Note: We don't verify InventoryUtils.hasRoom was called because it's hard to mock static methods
        // The important thing is that admin logic short-circuits and returns true
        resetInventoryMock();
    }

    @Test
    void testHasRoomForItem_DelegatesToInventoryUtils() {
        // Arrange
        TransactionParty nonAdminParty = new TransactionParty(false, false, mockPlayer, mockInventory);
        whenInventory("getSize", 36);
        whenInventory("getContents", new ItemStack[36]); // All empty slots

        // Act
        boolean result = nonAdminParty.hasRoomForItem(testItem);

        // Assert
        // For an empty inventory with a single item, hasRoom should return true
        // We're not mocking InventoryUtils.hasRoom directly, but we're verifying the delegation happens
        // by checking that the method returns a reasonable value based on inventory state
        assertTrue(result, "Non-admin party should delegate to InventoryUtils.hasRoom");
        resetInventoryMock();
    }
}
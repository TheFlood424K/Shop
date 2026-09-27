package com.snowgears.shop.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionParty class.
 */
class TransactionPartyTest {

    private TransactionParty transactionParty;
    private OfflinePlayer mockPlayer;
    private Inventory mockInventory;
    private ItemStack testItem;

    @BeforeEach
    void setUp() {
        mockPlayer = mock(OfflinePlayer.class);
        mockInventory = mock(PlayerInventory.class);
        testItem = new ItemStack(org.bukkit.Material.DIAMOND, 1);

        // Default constructor arguments
        transactionParty = new TransactionParty(false, false, mockPlayer, mockInventory);
    }

    @Test
    void testDepositItem_Success() {
        // Arrange
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>()); // Empty map means all items added

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deposited when there's room");
        verify(mockInventory).addItem(testItem);
    }

    @Test
    void testDepositItem_Failure_NoRoom() {
        // Arrange
        // Return a map with the test item indicating it couldn't be added
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deposited when there's no room");
        verify(mockInventory).addItem(testItem);
    }

    @Test
    void testDepositItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Return a map indicating failure (though admin should short-circuit)
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = adminParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in depositing items");
        // Verify that addItem was NOT called for admin parties
        verify(mockInventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void testDeductItem_Success() {
        // Arrange
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(new HashMap<>()); // Empty map means all items removed

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deducted when present");
        verify(mockInventory).removeItem(testItem);
    }

    @Test
    void testDeductItem_Failure_InsufficientQuantity() {
        // Arrange
        // Return a map with the test item indicating it couldn't be removed
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deducted when insufficient quantity");
        verify(mockInventory).removeItem(testItem);
    }

    @Test
    void testDeductItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Return a map indicating failure (though admin should short-circuit)
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = adminParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in deducting items");
        // Verify that removeItem was NOT called for admin parties
        verify(mockInventory, never()).removeItem(any(ItemStack.class));
    }

    @Test
    void testHasRoomForItem_AdminAlwaysTrue() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Set up inventory to return false (no room) - admin should still return true
        when(mockInventory.getSize()).thenReturn(36);
        when(mockInventory.getContents()).thenReturn(new ItemStack[36]); // All empty slots
        when(mockInventory.firstEmpty()).thenReturn(0); // First slot is empty

        // Act
        boolean result = adminParty.hasRoomForItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always have room for items");
        // Note: We don't verify InventoryUtils.hasRoom was called because it's hard to mock static methods
        // The important thing is that admin logic short-circuits and returns true
    }

    @Test
    void testHasRoomForItem_DelegatesToInventoryUtils() {
        // Arrange
        TransactionParty nonAdminParty = new TransactionParty(false, false, mockPlayer, mockInventory);
        // Set up basic inventory state
        when(mockInventory.getSize()).thenReturn(36);
        when(mockInventory.getContents()).thenReturn(new ItemStack[36]); // All empty slots

        // Act
        boolean result = nonAdminParty.hasRoomForItem(testItem);

        // Assert
        // For an empty inventory with a single item, hasRoom should return true
        // We're not mocking InventoryUtils.hasRoom directly, but we're verifying the delegation happens
        // by checking that the method returns a reasonable value based on inventory state
        assertTrue(result, "Non-admin party should delegate to InventoryUtils.hasRoom");
    }
}
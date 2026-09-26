package com.snowgears.shop.util;

import org.bukkit.OfflinePlayer;
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
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(0); // 0 leftover means all items added

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deposited when there's room");
        verify(mockInventory).addItem(testItem);
    }

    @Test
    void testDepositItem_Failure_NoRoom() {
        // Arrange
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(1); // 1 leftover means not all items added

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
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(100); // Would normally fail

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
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(0); // 0 leftover means all items removed

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deducted when present");
        verify(mockInventory).removeItem(testItem);
    }

    @Test
    void testDeductItem_Failure_InsufficientQuantity() {
        // Arrange
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(1); // 1 leftover means not all items removed

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
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(100); // Would normally fail

        // Act
        boolean result = adminParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in deducting items");
        // Verify that removeItem was NOT called for admin parties
        verify(mockInventory, never()).removeItem(any(ItemStack.class));
    }

    @Test
    void testHasRoomForItem_Success() {
        // Arrange
        when(mockInventory.contains(Mockito.atLeast(testItem, 1))).thenReturn(true);

        // Act
        boolean result = transactionParty.hasRoomForItem(testItem);

        // Assert
        assertTrue(result, "Should return true when item can fit in inventory");
    }

    @Test
    void testHasRoomForItem_Failure() {
        // Arrange
        when(mockInventory.contains(Mockito.atLeast(testItem, 1))).thenReturn(false);

        // Act
        boolean result = transactionParty.hasRoomForItem(testItem);

        // Assert
        assertFalse(result, "Should return false when item cannot fit in inventory");
    }

    @Test
    void testHasRoomForItem_AdminAlwaysTrue() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        when(mockInventory.contains(Mockito.atLeast(testItem, 1))).thenReturn(false); // Would normally be false

        // Act
        boolean result = adminParty.hasRoomForItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always have room for items");
        // Verify that contains was NOT called for admin parties
        verify(mockInventory, never()).contains(any(ItemStack.class));
    }
}
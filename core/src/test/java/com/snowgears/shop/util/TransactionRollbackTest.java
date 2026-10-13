package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.TestReflection;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Inventory rollback must preserve stock and payment even when recovery deposits would fail (#215). */
class TransactionRollbackTest {
    private Shop plugin;
    private PlayerMock player;
    private Inventory chest;
    private AbstractShop shop;
    private Transaction transaction;
    private TransactionParty buyer;
    private TransactionParty seller;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.loadSimple(Shop.class);
        player = server.addPlayer();
        chest = Bukkit.createInventory(null, 9);
        chest.setItem(0, new ItemStack(Material.DIAMOND, 4));
        chest.setItem(1, new ItemStack(Material.EMERALD, 63));
        player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 10));
        // Barter must use inventory rollback even when the server's ordinary currency is external.
        TestReflection.setField(plugin, "currencyType", CurrencyType.VAULT);
        shop = mock(AbstractShop.class);
        when(shop.getType()).thenReturn(ShopType.BARTER);
        when(shop.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND));
        when(shop.getSecondaryItemStack()).thenReturn(new ItemStack(Material.EMERALD));
        when(shop.getAmount()).thenReturn(4);
        when(shop.getPrice()).thenReturn(5.0);
        when(shop.getInventory()).thenReturn(chest);
        when(shop.getOwner()).thenReturn(player);
        createTransaction(ShopType.BARTER);
    }

    private void createTransaction(ShopType type) {
        transaction = new Transaction(player, shop, type);
        buyer = spy((TransactionParty) TestReflection.getField(transaction, "buyer"));
        seller = spy((TransactionParty) TestReflection.getField(transaction, "seller"));
        TestReflection.setField(transaction, "buyer", buyer);
        TestReflection.setField(transaction, "seller", seller);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    enum FailedTransfer { PAYMENT_WITHDRAWAL, STOCK_REMOVAL, PAYMENT_DEPOSIT, ITEM_DELIVERY }

    @ParameterizedTest
    @EnumSource(FailedTransfer.class)
    void partialTransferFailureRestoresBothInventories(FailedTransfer failure) {
        ItemStack[] buyerBefore = copy(player.getInventory());
        ItemStack[] sellerBefore = copy(chest);
        TransactionError expected;
        switch (failure) {
            case PAYMENT_WITHDRAWAL -> {
                doAnswer(call -> {
                    InventoryUtils.removeItem(player.getInventory(), new ItemStack(Material.EMERALD, 2));
                    return false;
                }).when(buyer).deductFunds(anyDouble());
                expected = TransactionError.INSUFFICIENT_FUNDS_PLAYER;
            }
            case STOCK_REMOVAL -> {
                doAnswer(call -> {
                    InventoryUtils.removeItem(chest, new ItemStack(Material.DIAMOND, 1));
                    return false;
                }).when(seller).deductItem(any(ItemStack.class));
                expected = TransactionError.INSUFFICIENT_FUNDS_SHOP;
            }
            case PAYMENT_DEPOSIT -> {
                doAnswer(call -> {
                    InventoryUtils.addItem(chest, new ItemStack(Material.EMERALD, 2));
                    return false;
                }).when(seller).depositFunds(anyDouble());
                expected = TransactionError.INVENTORY_FULL_SHOP;
            }
            case ITEM_DELIVERY -> {
                doAnswer(call -> {
                    InventoryUtils.addItem(player.getInventory(), new ItemStack(Material.DIAMOND, 1));
                    return false;
                }).when(buyer).depositItem(any(ItemStack.class));
                expected = TransactionError.INVENTORY_FULL_PLAYER;
            }
            default -> throw new AssertionError(failure);
        }
        // These are the recovery operations the old rollback used. Both refuse the refund.
        doReturn(false).when(seller).depositItem(any(ItemStack.class));
        doReturn(false).when(buyer).depositFunds(anyDouble());

        assertEquals(TransactionError.NONE, transaction.verify());
        assertEquals(expected, transaction.execute());
        assertArrayEquals(sellerBefore, chest.getContents(), "Original seller stock and payment must survive");
        assertArrayEquals(buyerBefore, player.getInventory().getContents(), "Buyer must retain all payment");
        verify(seller, never()).depositItem(any(ItemStack.class));
        verify(buyer, never()).depositFunds(anyDouble());
        verify(shop, never()).updateStock();
    }

    @Test
    void exceptionDuringPaymentDepositAlsoRestoresBothInventories() {
        ItemStack[] buyerBefore = copy(player.getInventory());
        ItemStack[] sellerBefore = copy(chest);
        doThrow(new IllegalStateException("Deposit unavailable")).when(seller).depositFunds(anyDouble());

        assertThrows(IllegalStateException.class, transaction::execute);
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
    }

    @Test
    void compressedCurrencyPartialDepositRestoresOriginalStacks() {
        TestReflection.setField(plugin, "currencyType", CurrencyType.ITEM);
        TestReflection.setField(plugin, "itemCurrency", new ItemStack(Material.WHEAT));
        when(shop.getType()).thenReturn(ShopType.SELL);
        when(shop.getPrice()).thenReturn(10.0);
        when(shop.getAmount()).thenReturn(1);
        chest.setItem(0, new ItemStack(Material.DIAMOND, 64));
        for (int slot = 1; slot < 8; slot++) {
            chest.setItem(slot, new ItemStack(Material.STONE, 64));
        }
        player.getInventory().setItem(0, new ItemStack(Material.WHEAT, 10));
        createTransaction(ShopType.SELL);
        ItemStack[] buyerBefore = copy(player.getInventory());
        ItemStack[] sellerBefore = copy(chest);

        // The capacity check lets both the block and remainder use the same empty slot.
        // The real deposit inserts the hay block but cannot then fit the loose wheat.
        assertEquals(TransactionError.NONE, transaction.verify());
        assertEquals(TransactionError.INVENTORY_FULL_SHOP, transaction.execute());
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
    }

    @Test
    void fullChestBarterCommitsUsingSlotFreedBySale() {
        for (int slot = 1; slot < chest.getSize(); slot++) {
            chest.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // MockBukkit's addItem treats an AIR stack as occupied, whereas Bukkit treats it as empty.
        // Model Bukkit's removal here so this test covers committing into the freed slot.
        doReturn(true).when(seller).canAcceptPaymentAfterRemoval(anyDouble(), any(ItemStack.class));
        doAnswer(call -> chest.removeItem((ItemStack) call.getArgument(0)).isEmpty())
                .when(seller).deductItem(any(ItemStack.class));
        assertEquals(TransactionError.NONE, transaction.verify(), "Full chest must pass verification");
        assertEquals(TransactionError.NONE, transaction.execute());
        assertEquals(0, InventoryUtils.getAmount(chest, new ItemStack(Material.DIAMOND)));
        assertEquals(5, InventoryUtils.getAmount(chest, new ItemStack(Material.EMERALD)));
        assertEquals(4, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.DIAMOND)));
        assertEquals(5, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.EMERALD)));
        verify(shop).updateStock();
    }

    @Test
    void adminBarterConsumesPaymentAndProvidesItemsWithoutChangingChest() {
        when(shop.isAdmin()).thenReturn(true);
        createTransaction(ShopType.BARTER);
        ItemStack[] sellerBefore = copy(chest);

        assertEquals(TransactionError.NONE, transaction.execute());
        assertArrayEquals(sellerBefore, chest.getContents());
        assertEquals(4, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.DIAMOND)));
        assertEquals(5, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.EMERALD)));
    }

    @Test
    void buyShopPaymentFailurePreservesPlayerItemsAndChestPayment() {
        TestReflection.setField(plugin, "currencyType", CurrencyType.ITEM);
        TestReflection.setField(plugin, "itemCurrency", new ItemStack(Material.EMERALD));
        when(shop.getType()).thenReturn(ShopType.BUY);
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 4));
        createTransaction(ShopType.BUY);
        ItemStack[] playerBefore = copy(player.getInventory());
        ItemStack[] chestBefore = copy(chest);
        doAnswer(call -> {
            InventoryUtils.addItem(player.getInventory(), new ItemStack(Material.EMERALD, 2));
            return false;
        }).when(seller).depositFunds(anyDouble());
        doReturn(false).when(buyer).depositFunds(anyDouble());

        assertEquals(TransactionError.INSUFFICIENT_FUNDS_SHOP, transaction.execute());
        assertArrayEquals(playerBefore, player.getInventory().getContents());
        assertArrayEquals(chestBefore, chest.getContents());
        verify(buyer, never()).depositFunds(anyDouble());
    }

    @Test
    void buyShopCommitsPaymentAndItems() {
        TestReflection.setField(plugin, "currencyType", CurrencyType.ITEM);
        TestReflection.setField(plugin, "itemCurrency", new ItemStack(Material.EMERALD));
        when(shop.getType()).thenReturn(ShopType.BUY);
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 4));
        createTransaction(ShopType.BUY);

        assertEquals(TransactionError.NONE, transaction.execute());
        assertEquals(8, InventoryUtils.getAmount(chest, new ItemStack(Material.DIAMOND)));
        assertEquals(58, InventoryUtils.getAmount(chest, new ItemStack(Material.EMERALD)));
        assertEquals(0, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.DIAMOND)));
        assertEquals(15, InventoryUtils.getAmount(player.getInventory(), new ItemStack(Material.EMERALD)));
    }

    @Test
    void rejectedVaultDepositRefundsBuyerBeforeAnyStockIsRemoved() {
        when(shop.getType()).thenReturn(ShopType.SELL);
        Economy economy = mock(Economy.class);
        TestReflection.setField(plugin, "econ", economy);
        when(economy.getBalance(any(OfflinePlayer.class))).thenReturn(10.0);
        when(economy.withdrawPlayer(any(OfflinePlayer.class), eq(5.0)))
                .thenReturn(new EconomyResponse(5.0, 5.0, EconomyResponse.ResponseType.SUCCESS, null));
        when(economy.depositPlayer(any(OfflinePlayer.class), eq(5.0)))
                .thenReturn(new EconomyResponse(0.0, 10.0, EconomyResponse.ResponseType.FAILURE, "Rejected"))
                .thenReturn(new EconomyResponse(5.0, 10.0, EconomyResponse.ResponseType.SUCCESS, null));
        createTransaction(ShopType.SELL);
        ItemStack[] sellerBefore = copy(chest);
        ItemStack[] buyerBefore = copy(player.getInventory());

        assertEquals(TransactionError.INVENTORY_FULL_SHOP, transaction.execute());
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
        verify(seller, never()).deductItem(any(ItemStack.class));
        verify(seller, never()).depositItem(any(ItemStack.class));
        verify(buyer).depositFunds(5.0);
        verify(economy, times(2)).depositPlayer(any(OfflinePlayer.class), eq(5.0));
    }

    @ParameterizedTest
    @CsvSource({
            "SELL, STOCK_REMOVAL, INSUFFICIENT_FUNDS_SHOP",
            "BUY, STOCK_REMOVAL, INSUFFICIENT_FUNDS_PLAYER",
            "SELL, ITEM_DELIVERY, INVENTORY_FULL_PLAYER",
            "BUY, ITEM_DELIVERY, INVENTORY_FULL_SHOP"
    })
    void paidItemTransferFailureRestoresInventoriesAndBalances(
            ShopType type, FailedTransfer failure, TransactionError expected) {
        Map<OfflinePlayer, Double> balances = createVaultTransaction(type);
        Map<OfflinePlayer, Double> balancesBefore = new HashMap<>(balances);
        ItemStack[] sellerBefore = copy(chest);
        ItemStack[] buyerBefore = copy(player.getInventory());
        Inventory source = type == ShopType.SELL ? chest : player.getInventory();
        Inventory destination = type == ShopType.SELL ? player.getInventory() : chest;
        if (failure == FailedTransfer.STOCK_REMOVAL) {
            doAnswer(call -> {
                InventoryUtils.removeItem(source, new ItemStack(Material.DIAMOND, 1));
                return false;
            }).when(seller).deductItem(any(ItemStack.class));
        } else {
            doAnswer(call -> {
                InventoryUtils.addItem(destination, new ItemStack(Material.DIAMOND, 1));
                return false;
            }).when(buyer).depositItem(any(ItemStack.class));
        }

        assertEquals(expected, transaction.execute());
        assertEquals(expected, transaction.getError());
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
        assertEquals(balancesBefore, balances);
        verify(seller).deductFunds(5.0);
        verify(buyer).depositFunds(5.0);
        if (failure == FailedTransfer.STOCK_REMOVAL) {
            verify(buyer, never()).depositItem(any(ItemStack.class));
        }
        verify(shop, never()).updateStock();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedPaymentRecoveryStepsAreIndependentAndLogged(boolean throwsException) {
        createVaultTransaction(ShopType.SELL);
        ItemStack[] sellerBefore = copy(chest);
        ItemStack[] buyerBefore = copy(player.getInventory());
        doAnswer(call -> {
            InventoryUtils.addItem(player.getInventory(), new ItemStack(Material.DIAMOND, 1));
            return false;
        }).when(buyer).depositItem(any(ItemStack.class));
        if (throwsException) {
            doThrow(new IllegalStateException("Withdrawal unavailable")).when(seller).deductFunds(5.0);
            doThrow(new IllegalStateException("Refund unavailable")).when(buyer).depositFunds(5.0);
        } else {
            doReturn(false).when(seller).deductFunds(5.0);
            doReturn(false).when(buyer).depositFunds(5.0);
        }
        Handler handler = mock(Handler.class);
        plugin.getLogger().addHandler(handler);
        try {
            assertEquals(TransactionError.INVENTORY_FULL_PLAYER, transaction.execute());
            assertArrayEquals(sellerBefore, chest.getContents());
            assertArrayEquals(buyerBefore, player.getInventory().getContents());
            verify(seller).deductFunds(5.0);
            verify(buyer).depositFunds(5.0);
            verify(handler).publish(argThat(record -> record.getLevel() == Level.SEVERE
                    && record.getMessage().contains("seller payment withdrawal")
                    && (record.getThrown() != null) == throwsException));
            verify(handler).publish(argThat(record -> record.getLevel() == Level.SEVERE
                    && record.getMessage().contains("buyer payment refund")
                    && (record.getThrown() != null) == throwsException));
            verify(shop, never()).updateStock();
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
    }

    @Test
    void exceptionDuringPaidItemDeliveryAlsoReversesPayment() {
        Map<OfflinePlayer, Double> balances = createVaultTransaction(ShopType.SELL);
        Map<OfflinePlayer, Double> balancesBefore = new HashMap<>(balances);
        ItemStack[] sellerBefore = copy(chest);
        ItemStack[] buyerBefore = copy(player.getInventory());
        doThrow(new IllegalStateException("Delivery unavailable")).when(buyer).depositItem(any(ItemStack.class));

        assertThrows(IllegalStateException.class, transaction::execute);
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
        assertEquals(balancesBefore, balances);
        verify(shop, never()).updateStock();
    }

    @Test
    void failedInventoryRestoreDoesNotPreventRemainingRecoverySteps() {
        Map<OfflinePlayer, Double> balances = createVaultTransaction(ShopType.SELL);
        Map<OfflinePlayer, Double> balancesBefore = new HashMap<>(balances);
        ItemStack[] buyerBefore = copy(player.getInventory());
        doAnswer(call -> {
            InventoryUtils.addItem(player.getInventory(), new ItemStack(Material.DIAMOND, 1));
            return false;
        }).when(buyer).depositItem(any(ItemStack.class));
        doThrow(new IllegalStateException("Inventory unavailable")).when(seller).restoreInventory(any());
        Handler handler = mock(Handler.class);
        plugin.getLogger().addHandler(handler);
        try {
            assertEquals(TransactionError.INVENTORY_FULL_PLAYER, transaction.execute());
            assertArrayEquals(buyerBefore, player.getInventory().getContents());
            assertEquals(balancesBefore, balances);
            verify(handler).publish(argThat(record -> record.getLevel() == Level.SEVERE
                    && record.getMessage().contains("seller inventory restore")
                    && record.getThrown() instanceof IllegalStateException));
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
    }

    private Map<OfflinePlayer, Double> createVaultTransaction(ShopType type) {
        when(shop.getType()).thenReturn(type);
        OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.randomUUID());
        when(shop.getOwner()).thenReturn(owner);
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 4));
        Map<OfflinePlayer, Double> balances = new HashMap<>();
        balances.put(player, 10.0);
        balances.put(owner, 10.0);
        Economy economy = mock(Economy.class);
        TestReflection.setField(plugin, "econ", economy);
        when(economy.getBalance(any(OfflinePlayer.class))).thenAnswer(call -> balances.get(call.getArgument(0)));
        when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(call -> {
            OfflinePlayer party = call.getArgument(0);
            double amount = call.getArgument(1);
            double remaining = balances.get(party) - amount;
            balances.put(party, remaining);
            return new EconomyResponse(amount, remaining, EconomyResponse.ResponseType.SUCCESS, null);
        });
        when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(call -> {
            OfflinePlayer party = call.getArgument(0);
            double amount = call.getArgument(1);
            double remaining = balances.get(party) + amount;
            balances.put(party, remaining);
            return new EconomyResponse(amount, remaining, EconomyResponse.ResponseType.SUCCESS, null);
        });
        createTransaction(type);
        return balances;
    }

    private ItemStack[] copy(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) { contents[i] = contents[i].clone(); }
        }
        return contents;
    }
}

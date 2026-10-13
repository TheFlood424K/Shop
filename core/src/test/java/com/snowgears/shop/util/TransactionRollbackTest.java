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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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

    /** Creates a barter fixture with chest stock, player payment, and an external default currency. */
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

    /**
     * Builds a transaction with spies on both parties so tests can inject transfer failures.
     *
     * @param type the transaction direction or shop type to exercise
     */
    private void createTransaction(ShopType type) {
        transaction = new Transaction(player, shop, type);
        buyer = spy((TransactionParty) TestReflection.getField(transaction, "buyer"));
        seller = spy((TransactionParty) TestReflection.getField(transaction, "seller"));
        TestReflection.setField(transaction, "buyer", buyer);
        TestReflection.setField(transaction, "seller", seller);
    }

    /** Releases the mocked Bukkit server after each test. */
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    enum FailedTransfer { PAYMENT_WITHDRAWAL, STOCK_REMOVAL, PAYMENT_DEPOSIT, ITEM_DELIVERY }

    /**
     * Verifies that each partially applied transfer restores both inventories without refund deposits.
     *
     * @param failure the transfer step that mutates inventory before reporting failure
     */
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

    /** Verifies that an exception during payment deposit restores both inventories before propagating. */
    @Test
    void exceptionDuringPaymentDepositAlsoRestoresBothInventories() {
        ItemStack[] buyerBefore = copy(player.getInventory());
        ItemStack[] sellerBefore = copy(chest);
        doThrow(new IllegalStateException("Deposit unavailable")).when(seller).depositFunds(anyDouble());

        assertThrows(IllegalStateException.class, transaction::execute);
        assertArrayEquals(sellerBefore, chest.getContents());
        assertArrayEquals(buyerBefore, player.getInventory().getContents());
    }

    /** Verifies that a compressed currency deposit restores the original stacks when its remainder cannot fit. */
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

    /** Verifies that a full barter chest accepts payment in the slot freed by removing sold stock. */
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

    /** Verifies that admin barter consumes player payment and delivers items without changing the chest. */
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

    /** Verifies that a failed BUY shop payment restores player stock and chest currency without a refund deposit. */
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

    /** Verifies that a successful BUY shop purchase transfers player items and chest payment in full. */
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

    /** Verifies that a refused Vault payment refunds the buyer before stock or items are transferred. */
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

    /**
     * Deep copies inventory stacks so subsequent transfers cannot mutate the expected contents.
     *
     * @param inventory the inventory whose slots are captured
     * @return an independent copy of its contents, preserving empty slots
     */
    private ItemStack[] copy(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) { contents[i] = contents[i].clone(); }
        }
        return contents;
    }
}

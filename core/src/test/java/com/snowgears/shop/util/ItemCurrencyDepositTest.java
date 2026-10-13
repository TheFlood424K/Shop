package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the item-currency payout path (issues #86, #91, #92).
 *
 * <p>Three defects compounded on one code path, and the order mattered:
 *
 * <ol>
 *   <li><b>#91</b> — the block count was built as a single {@code ItemStack} above the material's max
 *       stack size, which is why {@code addItem} could fail partway.</li>
 *   <li><b>#92</b> — when it did fail, the recovery called {@code removeItem} on the <em>seller's</em>
 *       inventory, destroying stock they already held.</li>
 *   <li><b>#86</b> — {@code depositFunds} discarded the failure result and reported success regardless.</li>
 * </ol>
 *
 * <p>Fixed together because fixing any one alone still loses a player their property.
 *
 * <p>{@code addItemCurrency} is private and is reached through {@link EconomyUtils#addFunds}, which
 * dispatches on the plugin's configured currency type rather than taking a material. It is invoked
 * reflectively here so the test exercises the real private method without widening its visibility for
 * a test's convenience — the same pattern the handler tests in this codebase already use.
 */
@ExtendWith(MockBukkitExtension.class)
class ItemCurrencyDepositTest {

    /** WHEAT -> HAY_BLOCK at 9:1, the conversion the registry ships. */
    private static final Material SINGULAR = Material.WHEAT;
    private static final Material BLOCK = Material.HAY_BLOCK;

    private Shop plugin;
    private Method addItemCurrency;

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private PlayerMock seller;

    /** Loads the plugin and resolves the private item-currency deposit method. */
    @BeforeEach
    void setUp() throws Exception {
        plugin = MockBukkit.loadSimple(Shop.class);
        addItemCurrency = EconomyUtils.class.getDeclaredMethod(
                "addItemCurrency", org.bukkit.inventory.Inventory.class, ItemStack.class, int.class);
        addItemCurrency.setAccessible(true);
    }

    /** Releases the mocked Bukkit server after each test. */
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Deposits wheat currency through the production helper into the player's inventory. */
    private boolean deposit(Player player, int amount) throws Exception {
        return (boolean) addItemCurrency.invoke(null, player.getInventory(),
                new ItemStack(SINGULAR, 1), amount);
    }

    /** Counts all items of the given material in the player's inventory. */
    private int countOf(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Verifies that payouts exceeding one stack still deposit their full value. */
    @Test
    @DisplayName("A large payout is split across stacks rather than built as one oversized stack")
    void largePayoutIsSplitAcrossStacks() throws Exception {
        // 45 wheat is exactly 5 hay blocks.
        assertTrue(deposit(seller, 45));
        assertEquals(5, countOf(seller, BLOCK));

        // 5,760 wheat is 640 blocks — ten stacks. Before #91 this was one ItemStack of 640, a quantity
        // no inventory can hold, so the server's handling of it was undefined.
        assertTrue(deposit(seller, 5760),
                "640 blocks across ten stacks should still succeed");
        assertEquals(645, countOf(seller, BLOCK));
    }

    /** Verifies that a large payout produces only valid stack sizes. */
    @Test
    @DisplayName("No stack handed to the inventory exceeds the material's max stack size")
    void noStackExceedsMaxStackSize() throws Exception {
        assertTrue(deposit(seller, 5760));

        int max = BLOCK.getMaxStackSize();
        for (ItemStack stack : seller.getInventory().getContents()) {
            if (stack != null && stack.getType() == BLOCK) {
                assertTrue(stack.getAmount() <= max,
                        "Found a stack of " + stack.getAmount() + " " + BLOCK + "; max is " + max);
            }
        }
    }

    /** Verifies that units left after block compression are deposited as wheat. */
    @Test
    @DisplayName("The remainder stays as singular units")
    void remainderStaysSingular() throws Exception {
        assertTrue(deposit(seller, 46));
        assertEquals(5, countOf(seller, BLOCK), "46 wheat is 5 blocks");
        assertEquals(1, countOf(seller, SINGULAR), "with one loose wheat left over");
    }

    /** Verifies that a failed large payout restores blocks and preserves existing wheat. */
    @Test
    @DisplayName("A failed payout does not remove the seller's existing stock (#92)")
    void failedPayoutDoesNotDestroySellerStock() throws Exception {
        // The seller already holds wheat. Before #92, a failed payout called removeItem on this
        // inventory — the destination — which deleted exactly this stock.
        seller.getInventory().addItem(new ItemStack(SINGULAR, 64));

        // Far more than any inventory holds, so the deposit cannot fully succeed.
        assertFalse(deposit(seller, 64 * 64 * 20), "This payout cannot succeed");

        assertEquals(64, countOf(seller, SINGULAR),
                "The seller's existing wheat must survive a failed payout untouched");
        assertEquals(0, countOf(seller, BLOCK),
                "All deposited blocks must be rolled back even above the removal helper's limit");
    }

    /** Verifies that a failed singular remainder restores the original block balance. */
    @Test
    void ordinaryRollbackPreservesExistingBlocks() throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(BLOCK, 3));
        for (int slot = 1; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // The five blocks fit, but the singular remainder does not.
        assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                new ItemStack(SINGULAR), 46));
        assertEquals(3, InventoryUtils.getAmount(inventory, new ItemStack(BLOCK)),
                "An ordinary rollback must leave the pre-existing blocks untouched");
        assertEquals(8 * 64, InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies that partial singular insertion restores both currency forms. */
    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void partialSingularInsertionIsRolledBack(int blocksToDeposit) throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(BLOCK, 3));
        inventory.setItem(1, new ItemStack(SINGULAR, 63));
        for (int slot = 2; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // Any requested blocks fit, but only one of the two singular units fits.
        assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                new ItemStack(SINGULAR), blocksToDeposit * 9 + 2));
        assertEquals(3, InventoryUtils.getAmount(inventory, new ItemStack(BLOCK)));
        assertEquals(63, InventoryUtils.getAmount(inventory, new ItemStack(SINGULAR)),
                "A failed deposit must remove its partially inserted singular units");
        assertEquals(7 * 64, InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies that singular units are rolled back even when block insertion failed first. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void singularInsertionAfterBlockFailureIsRolledBack(int singularsToDeposit) throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(BLOCK, 63));
        inventory.setItem(1, new ItemStack(SINGULAR, 63));
        for (int slot = 2; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // Only one block fits. Singular insertion then fits either fully or partially.
        assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                new ItemStack(SINGULAR), 65 * 9 + singularsToDeposit));
        assertEquals(63, InventoryUtils.getAmount(inventory, new ItemStack(BLOCK)));
        assertEquals(63, InventoryUtils.getAmount(inventory, new ItemStack(SINGULAR)),
                "Block failure must also roll back singular units inserted afterward");
        assertEquals(7 * 64, InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies rollback for currency without a block form, preserving its item metadata. */
    @Test
    void partialUncompressedCurrencyInsertionIsRolledBack() throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        ItemStack currency = new ItemStack(Material.STRING);
        var meta = currency.getItemMeta();
        meta.setDisplayName("Shop currency");
        currency.setItemMeta(meta);
        ItemStack existing = currency.clone();
        existing.setAmount(63);
        inventory.setItem(0, existing);
        for (int slot = 1; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        assertFalse((boolean) addItemCurrency.invoke(null, inventory, currency, 2));
        assertEquals(63, InventoryUtils.getAmount(inventory, currency));
        assertTrue(currency.isSimilar(inventory.getItem(0)));
        assertEquals(8 * 64, InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies rollback when the first or a later block chunk fits only partially. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void partialBlockInsertionIsRolledBack(int emptySlots) throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(BLOCK, 3));
        for (int slot = 1 + emptySlots; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // With no empty slots the first chunk partially fits; with one, the second does.
        // The deposit also requests later chunks that will never be attempted.
        assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                new ItemStack(SINGULAR), (emptySlots + 2) * 64 * 9));
        assertEquals(3, InventoryUtils.getAmount(inventory, new ItemStack(BLOCK)),
                "Rollback must remove both complete chunks and the partially inserted chunk");
        assertEquals((8 - emptySlots) * 64,
                InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies rollback of a partial block stack, including any earlier full chunks. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void partialBlockInsertionRollsBackAndPreservesExistingStock(int emptySlots) throws Exception {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(BLOCK, 63));
        for (int slot = 1 + emptySlots; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }

        // Capacity is one block, plus a full stack when an empty slot is available.
        // The next chunk inserts one block before failing, with another chunk still pending.
        // There is also no space for the singular remainder.
        int blocksToDeposit = (emptySlots + 1) * 64 + 5;
        assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                new ItemStack(SINGULAR), blocksToDeposit * 9 + 1));
        assertEquals(63, InventoryUtils.getAmount(inventory, new ItemStack(BLOCK)),
                "Rollback must remove partial and complete chunks and preserve existing blocks");
        assertEquals(0, InventoryUtils.getAmount(inventory, new ItemStack(SINGULAR)));
        assertEquals((8 - emptySlots) * 64,
                InventoryUtils.getAmount(inventory, new ItemStack(Material.STONE)));
    }

    /** Verifies that rollback attempts every chunk and logs the accumulated removal shortfall. */
    @Test
    void rollbackContinuesAfterRemovalShortfallAndReportsTotal() throws Exception {
        Inventory inventory = mock(Inventory.class);
        when(inventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack stack = invocation.getArgument(0);
            HashMap<Integer, ItemStack> leftovers = new HashMap<>();
            if (stack.getType() == SINGULAR) {
                leftovers.put(0, stack);
            }
            return leftovers;
        });
        // The first removal can find only 60 of its 64 blocks. Later attempts still succeed.
        when(inventory.getContents()).thenReturn(
                new ItemStack[]{new ItemStack(BLOCK, 60)},
                new ItemStack[]{new ItemStack(BLOCK, 64)},
                new ItemStack[]{new ItemStack(BLOCK, 5)});

        Handler handler = mock(Handler.class);
        plugin.getLogger().addHandler(handler);
        try {
            assertFalse((boolean) addItemCurrency.invoke(null, inventory,
                    new ItemStack(SINGULAR), 133 * 9 + 1));
            verify(inventory, times(3)).getContents();

            ArgumentCaptor<LogRecord> record = ArgumentCaptor.forClass(LogRecord.class);
            verify(handler).publish(record.capture());
            assertEquals(Level.WARNING, record.getValue().getLevel());
            assertEquals("Item currency deposit rollback could not remove 4 HAY_BLOCK blocks.",
                    record.getValue().getMessage());
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
    }

    /** Verifies that an unplaceable payout returns a failure result. */
    @Test
    @DisplayName("A failed payout reports false rather than throwing")
    void failedPayoutReportsFalse() throws Exception {
        assertFalse(deposit(seller, 64 * 64 * 20),
                "An unplaceable payout must be reported as a failure so the caller can reverse");
    }

    /** Verifies that transaction parties propagate the deposit helper's failure result. */
    @Test
    @DisplayName("depositFunds propagates the failure instead of reporting success (#86)")
    void depositFundsPropagatesFailure() {
        TransactionParty party = new TransactionParty(
                true, false, server.getOfflinePlayer(seller.getUniqueId()), seller.getInventory());

        // A payout no inventory can hold must not be reported as success: the seller would have
        // received nothing while the buyer was charged.
        assertFalse(party.depositFunds(64 * 64 * 50),
                "A payout that could not be placed must not report success");
    }
}

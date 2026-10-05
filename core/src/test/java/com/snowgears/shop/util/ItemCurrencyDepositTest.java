package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @BeforeEach
    void setUp() throws Exception {
        plugin = MockBukkit.loadSimple(Shop.class);
        addItemCurrency = EconomyUtils.class.getDeclaredMethod(
                "addItemCurrency", org.bukkit.inventory.Inventory.class, ItemStack.class, int.class);
        addItemCurrency.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private boolean deposit(Player player, int amount) throws Exception {
        return (boolean) addItemCurrency.invoke(null, player.getInventory(),
                new ItemStack(SINGULAR, 1), amount);
    }

    private int countOf(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

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

    @Test
    @DisplayName("The remainder stays as singular units")
    void remainderStaysSingular() throws Exception {
        assertTrue(deposit(seller, 46));
        assertEquals(5, countOf(seller, BLOCK), "46 wheat is 5 blocks");
        assertEquals(1, countOf(seller, SINGULAR), "with one loose wheat left over");
    }

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
    }

    @Test
    @DisplayName("A failed payout reports false rather than throwing")
    void failedPayoutReportsFalse() throws Exception {
        assertFalse(deposit(seller, 64 * 64 * 20),
                "An unplaceable payout must be reported as a failure so the caller can reverse");
    }

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
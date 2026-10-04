package com.snowgears.shop.util;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the currency affordability comparison in {@link EconomyUtils#hasSufficientFunds}.
 *
 * <p>Issue #44: the EXPERIENCE branch compared with {@code exp > amount} while VAULT and ITEM both
 * compared with {@code >=}, so a player holding exactly the required amount was told they could not
 * afford it.
 *
 * <p>Scope: the only production callers of {@code hasSufficientFunds} are the creation, destruction
 * and teleport costs. A shop purchase never reaches this method — {@link TransactionParty#deductFunds}
 * gates on {@code getAvailableFunds() < paymentAmount}, which already has correct {@code >=}
 * semantics — so the defect hit cost-charging paths only, not the buy/sell flow itself.
 */
class EconomyUtilsTest extends BaseMockBukkitTest {

    /**
     * Drives the check off total experience rather than exp-to-level, since {@code getTotalExperience}
     * sums the two and {@code level} alone is not the balance the code compares against.
     */
    private int giveExperience(PlayerMock player, int level) {
        player.setLevel(level);
        player.setExp(0f);
        return EconomyUtils.getTotalExperience(player);
    }

    @Test
    void experienceCurrencyAcceptsExactBalance() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock player = addStubbedPlayer("ExactBalance");
        int balance = giveExperience(player, 10);

        assertTrue(
                EconomyUtils.hasSufficientFunds(player, player.getInventory(), balance),
                "A player holding exactly the required experience should be able to pay it"
        );
    }

    @Test
    void experienceCurrencyAcceptsBalanceAboveAmount() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock player = addStubbedPlayer("AmpleBalance");
        int balance = giveExperience(player, 20);

        assertTrue(
                EconomyUtils.hasSufficientFunds(player, player.getInventory(), balance - 1),
                "A player holding more than the required experience should be able to pay"
        );
    }

    @Test
    void experienceCurrencyRejectsShortBalance() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock player = addStubbedPlayer("ShortBalance");
        int balance = giveExperience(player, 10);

        assertFalse(
                EconomyUtils.hasSufficientFunds(player, player.getInventory(), balance + 1),
                "A player one experience short should not be able to pay"
        );
    }

    @Test
    void experienceCurrencyRejectsEmptyBalance() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock player = addStubbedPlayer("NoBalance");

        assertFalse(
                EconomyUtils.hasSufficientFunds(player, player.getInventory(), 1),
                "A player with no experience should not be able to pay any amount"
        );
    }

    /**
     * The creation cost is paid through {@code hasSufficientFunds} and then withdrawn through
     * {@code removeFunds}. A player who passes the check must be left with zero, not a negative
     * balance — this pins the two halves of that pair together.
     */
    @Test
    void exactBalancePurchaseLeavesZeroNotNegative() {
        setConfig("currencyType", CurrencyType.EXPERIENCE);
        PlayerMock player = addStubbedPlayer("ZeroRemainder");
        int balance = giveExperience(player, 12);

        assertTrue(EconomyUtils.hasSufficientFunds(player, player.getInventory(), balance));
        assertTrue(EconomyUtils.removeFunds(player, player.getInventory(), balance));

        assertTrue(
                EconomyUtils.getFunds(player, player.getInventory()) == 0,
                "Paying the exact balance should leave zero, never a negative remainder"
        );
    }
}
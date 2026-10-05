package com.snowgears.shop.handler;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.PlayerTransactionRecord;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the transaction-log fixture round-trips (issue #96).
 *
 * <p>If this passes, the fixture can seed rows and read them back through the production query. That
 * unblocks two things:
 *
 * <ul>
 *   <li>{@link #68} — an end-to-end test pinning stock units, which needs a populated log.</li>
 *   <li>The coverage gap recorded in {@code TransactionCommandSelfOnlyTest}: the {@code owner_uuid}
 *       predicate was asserted by reading the SQL, never by running it. It is running here.</li>
 * </ul>
 *
 * <p>The last test is the one that matters most: it asserts a row written for one owner is
 * <em>invisible</em> to another. That is the property the SQL text was supposed to guarantee.
 */
class TransactionLogFixtureTest extends TransactionLogFixture {

    @Test
    @DisplayName("A seeded sale is readable through the production query")
    void seededSaleRoundTrips() throws Exception {
        PlayerMock seller = addStubbedPlayer("Seller");
        AbstractShop shop = shopFor(seller, new ItemStack(Material.DIRT, 5));

        UUID owner = seedSales(seller, shop, ShopType.SELL, 25.0, 3, 3);
        List<PlayerTransactionRecord> read = readSales(owner);

        assertFalse(read.isEmpty(),
                "Three sales were written through LogHandler.logTransaction; reading them back through "
                        + "getShopTransactions returned nothing. Either the write did not land or the "
                        + "async hops were not drained — and an undrained query fails silently.");
        assertEquals(3, read.size(), "Every seeded row should come back");
    }

    @Test
    @DisplayName("The owner filter is enforced by the running query, not merely by the SQL text")
    void ownerFilterIsActuallyApplied() throws Exception {
        PlayerMock seller = addStubbedPlayer("Seller");
        PlayerMock other = addStubbedPlayer("Other");
        AbstractShop shop = shopFor(seller, new ItemStack(Material.DIRT, 1));

        seedSales(seller, shop, ShopType.SELL, 10.0, 1, 2);

        List<PlayerTransactionRecord> forSeller = readSales(seller.getUniqueId());
        List<PlayerTransactionRecord> forStranger = readSales(other.getUniqueId());

        assertEquals(2, forSeller.size(), "The seller sees their own rows");
        assertTrue(forStranger.isEmpty(),
                "Another player must see none of them. This is the assertion the SQL text could never "
                        + "make — until now the owner_uuid predicate was only ever read, never run.");
    }

    @Test
    @DisplayName("Amounts and prices survive the round trip")
    void valuesSurviveTheRoundTrip() throws Exception {
        PlayerMock seller = addStubbedPlayer("Seller");
        AbstractShop shop = shopFor(seller, new ItemStack(Material.STONE, 64));

        seedSales(seller, shop, ShopType.SELL, 42.5, 7, 1);
        List<PlayerTransactionRecord> read = readSales(seller.getUniqueId());

        assertEquals(1, read.size());
        PlayerTransactionRecord row = read.get(0);
        assertEquals(7, row.getAmount(), "The amount must survive the write and read");
        assertEquals(42.5, row.getPrice(), 0.001, "The price must survive the write and read");
        assertEquals(ShopType.SELL, row.getTransactionType(), "The shop type must survive the write and read");
    }

    @Test
    @DisplayName("An item-only fixture needs no hand-written SQL")
    void fixtureNeedsNoHandWrittenSql() throws Exception {
        // A guard on the fixture's own premise. If someone later "simplifies" it by inserting rows
        // directly, the drift this fixture exists to prevent comes back — so assert the write path
        // is the production one.
        assertTrue(getPlugin().getLogHandler().isEnabled(),
                "The fixture writes through LogHandler, which needs database logging. The default test "
                        + "config uses logging.type FILE (H2); if this fails, that changed.");
    }
}
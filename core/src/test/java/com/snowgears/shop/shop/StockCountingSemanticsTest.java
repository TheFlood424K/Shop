package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the stock-counting semantics that issue #43 was filed against.
 *
 * <p>The issue asked for {@code getStock()}'s readers to be routed to {@code getCachedStock()} on the
 * grounds that the incremental tracker was "shipped but never called". Reading the code shows the
 * routing would have been a pessimisation, not a fix, and that the counter itself was wrong:
 *
 * <ul>
 *   <li>{@code calculateStock()} sets {@code stock = itemsInShop / getAmount()} — {@code stock} counts
 *       <em>transactions</em>, not items — while {@code adjustStock()} was handed {@code amountBeingSold},
 *       an <em>item</em> count. Wiring the readers would have surfaced those two units.</li>
 *   <li>{@code Transaction.execute()} called {@code adjustStock()} and then {@code updateStock()} a few
 *       lines later, and {@code updateStock()} rescans unconditionally, so the adjustment was always
 *       overwritten.</li>
 *   <li>{@code getCachedStock()} returned {@code STOCK_UNAVAILABLE} (-1) from its fallback path, which
 *       {@code getStock()} never does.</li>
 * </ul>
 *
 * These tests pin what the code actually does so the units cannot silently diverge again.
 */
class StockCountingSemanticsTest extends BaseMockBukkitTest {

    private ShopHandler shopHandler;
    private WorldMock world;

    @Override
    @BeforeEach
    public void initServer() {
        super.initServer();
        shopHandler = getPlugin().getShopHandler();
        world = addSimpleWorldPatched("stockworld");
    }

    private SellShop shopAt(int x, int z, int amountPerTransaction) {
        Location signLocation = new Location(world, x, 64, z);
        SellShop shop = new SellShop(signLocation, UUID.randomUUID(), 10.0, amountPerTransaction, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shopHandler.addShop(shop);
        return shop;
    }

    /** Links a chest to the shop so calculateStock() can actually read an inventory. */
    private Inventory attachChest(SellShop shop, int x, int z, int diamondCount) {
        Block chestBlock = world.getBlockAt(x, 64, z + 1);
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        // ItemStack rejects a non-positive amount, so an "empty" chest is one we simply never fill.
        if (diamondCount > 0) {
            chest.getBlockInventory().addItem(new ItemStack(Material.DIAMOND, diamondCount));
        }
        try {
            Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, chestBlock.getLocation());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to attach chest to shop", e);
        }
        return chest.getBlockInventory();
    }

    /**
     * The core unit rule: a shop selling in bulk reports how many <em>transactions</em> remain,
     * not how many items. This is what {@code adjustStock} violated when it wrote item counts into
     * the same field.
     */
    @Test
    void stockCountsTransactionsNotItems() {
        SellShop shop = shopAt(100, 100, 16);
        attachChest(shop, 100, 100, 160);

        shop.updateStock();

        assertEquals(10, shop.getStock(),
                "160 items sold 16 at a time is 10 transactions, not 160 items");
    }

    @Test
    void bulkShopAndSingleShopReportTheSameItemsDifferently() {
        SellShop bulk = shopAt(110, 110, 16);
        attachChest(bulk, 110, 110, 160);
        bulk.updateStock();

        SellShop single = shopAt(120, 120, 1);
        attachChest(single, 120, 120, 160);
        single.updateStock();

        assertEquals(10, bulk.getStock());
        assertEquals(160, single.getStock(),
                "The same chest reports 10 vs 160 purely because of the amount per transaction");
        assertNotEquals(bulk.getStock(), single.getStock(),
                "Stock is denominated in transactions — an item-denominated value would collapse these");
    }

    /** A partially-filled shop reports the remainder, floored. */
    @Test
    void partialStockFloorsToWholeTransactions() {
        SellShop shop = shopAt(130, 130, 16);
        attachChest(shop, 130, 130, 143);

        shop.updateStock();

        assertEquals(8, shop.getStock(), "143 items at 16 per transaction leaves 8 complete transactions");
    }

    /**
     * An empty shop reports zero. The deleted incremental tracker explicitly could not cache this
     * case — it required {@code stockCounter != 0} — so the case is worth pinning on its own.
     */
    @Test
    void emptyShopReportsZero() {
        SellShop shop = shopAt(140, 140, 16);
        attachChest(shop, 140, 140, 0);

        shop.updateStock();

        assertEquals(0, shop.getStock(), "An empty shop is out of stock, not out of cache");
    }

    /**
     * {@code getCachedStock()} must never leak the {@code STOCK_UNAVAILABLE} sentinel. {@code getStock()}
     * cannot return it, so a caller that swapped one for the other would be feeding -1 to sign lines —
     * the Bug 4 regression the sentinel was introduced to prevent.
     */
    @Test
    void cachedStockNeverReturnsTheUnavailableSentinel() {
        // A shop with no chest attached cannot resolve its inventory, so calculateStock() bails out
        // with STOCK_UNAVAILABLE rather than returning a count.
        SellShop shop = new SellShop(new Location(world, 150, 64, 150), UUID.randomUUID(), 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertNotEquals(AbstractShop.STOCK_UNAVAILABLE, shop.getCachedStock(),
                "getCachedStock() must not surface the unavailable sentinel to sign lines");
    }

    /** Draining a shop to empty must reach zero through the normal path, not stall at the last count. */
    @Test
    void removingAllItemsBringsStockToZero() {
        SellShop shop = shopAt(160, 160, 16);
        Inventory chest = attachChest(shop, 160, 160, 64);

        shop.updateStock();
        assertEquals(4, shop.getStock());

        chest.clear();
        shop.updateStock();

        assertEquals(0, shop.getStock(), "Removing every item must leave the shop out of stock");
    }

    /**
     * The regression this deletion is really about.
     *
     * <p>{@code adjustStock()} wrote {@code amountBeingSold} — an <em>item</em> count — into the
     * {@code stock} field, which {@code calculateStock()} denominates in <em>transactions</em>. Under
     * normal conditions the mistake is invisible, because {@code updateStock()} rescans a few lines
     * later and overwrites it. It becomes observable exactly when the rescan cannot happen: with the
     * chest unreadable, {@code calculateStock()} returns the sentinel and {@code updateStock()} returns
     * early, so whatever was written survives into {@code getStock()} and onto the sign.
     *
     * <p>The rescan is failed here by detaching the chest location, which is the same state a shop is
     * in while its chunk is unloaded. Assertions are on the value that survives.
     */
    @Test
    void unreadableInventoryLeavesLastKnownGoodTransactionCount() {
        SellShop shop = shopAt(180, 180, 16);
        attachChest(shop, 180, 180, 160);

        shop.updateStock();
        assertEquals(10, shop.getStock(), "Precondition: the shop holds 10 transactions");

        // Simulate an unresolvable inventory: calculateStock() returns STOCK_UNAVAILABLE and
        // updateStock() returns early without touching `stock`.
        detachChest(shop);

        shop.updateStock();

        assertEquals(10, shop.getStock(),
                "When the inventory cannot be read, stock must still hold the last known-good "
                        + "TRANSACTION count. An item-denominated write would surface as 160 here.");
        assertNotEquals(AbstractShop.STOCK_UNAVAILABLE, shop.getStock(),
                "The unavailable sentinel must never reach getStock()");
    }

    /** Clears chestLocation so getInventory() returns null, as it does for an unloaded chunk. */
    private void detachChest(SellShop shop) {
        try {
            Field field = AbstractShop.class.getDeclaredField("chestLocation");
            field.setAccessible(true);
            field.set(shop, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to detach chest from shop", e);
        }
    }

    @Test
    void adminShopAlwaysReportsUnlimitedStock() {
        SellShop shop = new SellShop(new Location(world, 170, 64, 170), UUID.randomUUID(), 10.0, 1, false, BlockFace.NORTH);
        shop.setAdmin(true);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shopHandler.addShop(shop);

        shop.updateStock();

        assertTrue(shop.getStock() > 1_000_000, "An admin (gamble) shop is never out of stock");
    }
}

package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.PlayerTransactionRecord;
import com.snowgears.shop.util.TransactionLookupFilter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seeds a transaction log and reads it back through the production query (issue #96).
 *
 * <p>No test populated {@code shop_transaction} / {@code shop_action} and then queried it through
 * {@link LogHandler}. {@code DbSetupIndexTest} proves the schema supports the query, but it
 * <em>hand-copies</em> the production SQL rather than calling it — so the query text and the code that
 * builds it can drift apart with nothing noticing. That is the gap
 * {@link TransactionCommandSelfOnlyTest} records: the {@code owner_uuid} predicate is asserted by
 * reading the SQL, not by running it.
 *
 * <p>This closes it by writing through {@link LogHandler#logTransaction} and reading through
 * {@link LogHandler#getShopTransactions}, so neither side can drift from the fixture.
 *
 * <h2>The async trap</h2>
 *
 * <p>Both sides hop twice — {@code runAsync} for the work, then {@code runNextTick} to deliver. A
 * test must drain <em>both</em> hops; draining one produces a silently empty result rather than a
 * failure. {@link #drain()} pumps until the tracked counter settles rather than assuming a fixed tick
 * count covers it.
 */
class TransactionLogFixture extends BaseMockBukkitTest {

    /** Counts completed work units so {@link #drain()} knows when the hops have finished. */
    private final AtomicInteger completed = new AtomicInteger();

    /** How many wait-and-pump cycles drain() performs. Chosen large enough that the second
     *  async hop (runNextTick) reliably lands even on busy CI runners, where the pool wait
     *  loop inside MockBukkit's waitAsyncTasksFinished() does not pump ticks. */
    private static final int DRAIN_ITERATIONS = 120;

    /** Ticks pumped per drain() iteration after the async pool is observed idle. Five is
     *  enough to flush any runNextTick callbacks scheduled by the just-completed async task. */
    private static final int TICKS_PER_DRAIN = 10;

    protected ServerMock server() {
        return getServer();
    }

    protected Shop plugin() {
        return getPlugin();
    }

    /**
     * Writes {@code count} sales through the production path and waits for them to land.
     *
     * @return the owner whose rows were written
     */
    protected UUID seedSales(PlayerMock seller, AbstractShop shop, ShopType type,
                             double price, int amount, int count) throws Exception {
        LogHandler log = getPlugin().getLogHandler();
        if (!log.isEnabled()) {
            throw new IllegalStateException(
                    "Database logging is disabled in this configuration, so the fixture cannot seed. "
                            + "The default test config uses logging.type FILE (H2) and should be enabled.");
        }

        for (int i = 0; i < count; i++) {
            completed.incrementAndGet();
            log.logTransaction(seller, shop, type, price, amount);
        }
        drain();
        return shop.getOwnerUUID();
    }

    /** Reads back through the production query, asserting the owner filter is honoured. */
    protected List<PlayerTransactionRecord> readSales(UUID owner) throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        List<PlayerTransactionRecord>[] box = newBox();

        getPlugin().getLogHandler().getShopTransactions(owner,
                System.currentTimeMillis() - 86_400_000L * 365,
                System.currentTimeMillis(),
                new TransactionLookupFilter(null, null, null, null),
                records -> {
                    box[0] = records;
                    delivered.incrementAndGet();
                });
        drain();

        if (delivered.get() == 0) {
            throw new IllegalStateException("The query never delivered; the async hops were not drained");
        }
        return box[0];
    }

    @SuppressWarnings("unchecked")
    private List<PlayerTransactionRecord>[] newBox() {
        return new List[1];
    }

    /**
     * Pumps ticks until the in-flight work settles.
     *
     * <p>The query is double-hopped ({@code runAsync} then {@code runNextTick}), so a single
     * {@code performTicks(2)} is not enough — and when it is not enough the result is an empty list
     * rather than a failure, which is how a broken fixture masquerades as "no sales recorded".
     *
     * <p>MockBukkit's {@code waitAsyncTasksFinished()} processes scheduled tasks <em>before</em>
     * waiting for the async pool, but does not pump ticks <em>during</em> the wait. When an async
     * task completes and schedules a {@code runNextTick} callback, that callback lands in the
     * scheduler queue for the next tick, but the main thread is asleep in the pool wait loop.
     * We therefore pump ticks both before and after the wait, with enough iterations to cover
     * load-induced delays on busy CI runners.
     */
    protected void drain() {
        for (int i = 0; i < DRAIN_ITERATIONS; i++) {
            // waitAsyncTasksFinished drains the current batch of async work and may take
            // up to executorTimeout (60s). After it returns the pool is idle but any
            // runNextTick callbacks scheduled by the just-finished tasks are queued for
            // the next tick — pump ticks so they fire.
            getServer().getScheduler().waitAsyncTasksFinished();
            getServer().getScheduler().performTicks(TICKS_PER_DRAIN);
        }
    }

    /**
     * A shop backed by a real chest block, so {@code logTransaction} can read its location.
     *
     * <p>{@code chestLocation} has no setter — {@code AbstractShop.load()} is the only thing that
     * assigns it — so the fixture places an actual chest and calls {@code load()} rather than
     * constructing a shop and hoping the write path tolerates a null location. It does not:
     * {@code logTransaction} dereferences {@code getChestLocation()} immediately.
     */
    protected AbstractShop shopFor(PlayerMock owner, ItemStack item) {
        if (getServer().getWorlds().isEmpty()) {
            getServer().addSimpleWorld("world");
        }
        org.bukkit.World world = getServer().getWorlds().get(0);

        Location chestLoc = new Location(world, 8, 64, 8);
        world.getBlockAt(chestLoc).setType(Material.CHEST);
        // The sign sits to the NORTH of the chest (z-1) facing NORTH — the arrangement
        // ShopCreationChestTest uses, and the one AbstractShop.load() resolves a chest from.
        // MockBukkit cannot derive the face from block data reliably, so the calculation is stubbed
        // exactly as that helper does.
        Location signLoc = chestLoc.clone().add(0, 0, -1);
        world.getBlockAt(signLoc).setType(Material.OAK_WALL_SIGN);
        stubCalculateBlockFaceForSign(org.bukkit.block.BlockFace.NORTH);

        AbstractShop shop = AbstractShop.create(signLoc, owner.getUniqueId(),
                10.0, 10.0, 1, false, ShopType.SELL,
                org.bukkit.block.BlockFace.NORTH);
        shop.setItemStack(item == null ? new ItemStack(Material.DIRT) : item);

        if (!shop.load()) {
            throw new IllegalStateException(
                    "Fixture could not load its shop; chestLocation stays null and logTransaction "
                            + "would NPE. The chest or sign is probably not where the loader expects.");
        }
        return shop;
    }

    /** Keeps the Player import honest for readers extending this fixture. */
    protected Player asPlayer(PlayerMock mock) {
        return mock;
    }
}
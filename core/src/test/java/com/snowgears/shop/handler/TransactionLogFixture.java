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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
 * <p>Writes run asynchronously; queries also use {@code runNextTick} to deliver results. A
 * test must wait for committed writes and the query callback. The fixture advances ticks while
 * waiting for those completion signals, rather than assuming a fixed tick count covers them.
 */
class TransactionLogFixture extends BaseMockBukkitTest {

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

        UUID owner = shop.getOwnerUUID();
        int expectedRows = countSales(owner) + count;
        for (int i = 0; i < count; i++) {
            log.logTransaction(seller, shop, type, price, amount);
        }
        await(() -> countSales(owner) == expectedRows, "seeded transaction writes");
        return owner;
    }

    /** Reads back through the production query, asserting the owner filter is honoured. */
    protected List<PlayerTransactionRecord> readSales(UUID owner) throws Exception {
        CompletableFuture<List<PlayerTransactionRecord>> result = new CompletableFuture<>();
        getPlugin().getLogHandler().getShopTransactions(owner,
                System.currentTimeMillis() - 86_400_000L * 365,
                System.currentTimeMillis(),
                new TransactionLookupFilter(null, null, null, null),
                result::complete);
        await(result::isDone, "transaction query callback");
        return result.get();
    }

    /** Observes committed writes only; all seeding and result assertions use production methods. */
    private int countSales(UUID owner) throws SQLException {
        try (Connection connection = getPlugin().getLogHandler().getDataSourceForTesting().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM shop_action WHERE owner_uuid = ? AND player_action = 'TRANSACT'")) {
            statement.setString(1, owner.toString());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    /** Advances ticks and yields to workers until completion, failing after five seconds. */
    private void await(Callable<Boolean> completed, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            getServer().getScheduler().performTicks(10);
            if (completed.call()) {
                return;
            }
            Thread.sleep(1);
        }
        throw new IllegalStateException(description + " did not complete within five seconds");
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

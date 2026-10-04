package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the head-icon cache policy added for issue #45.
 *
 * <p>Previously {@code playerHeads} was a plain {@code HashMap} that was written once per shop load
 * and never invalidated. Two defects followed: an owner who was offline the first time their shop
 * rendered kept a placeholder skull for the life of the server, and a player who changed their skin
 * kept the old head. The map also grew by one entry per distinct owner and was never evicted.
 *
 * <p>The fix caches only the <em>skull</em> — name and lore carry per-shop placeholders and are
 * rebuilt per render — adds a read-time TTL, invalidates on player join, and evicts when an owner's
 * last shop is removed.
 */
class PlayerHeadIconCacheTest extends BaseMockBukkitTest {

    private WorldMock world;

    @Override
    @BeforeEach
    public void initServer() {
        super.initServer();
        world = addSimpleWorldPatched("headworld");
    }

    private AbstractShop shopOwnedBy(UUID owner) {
        return shopOwnedBy(owner, 100);
    }

    /** addShop() keys on sign location, so distinct shops need distinct coordinates. */
    private AbstractShop shopOwnedBy(UUID owner, int x) {
        AbstractShop shop = new SellShop(new Location(world, x, 64, 100), owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        return shop;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ?> headCache() {
        return (Map<UUID, ?>) TestReflectionAccessor.getField(getPlugin().getGuiHandler(), "playerHeads");
    }

    /** Minimal reflective read, kept local so this test does not depend on testsupport internals. */
    static final class TestReflectionAccessor {
        static Object getField(Object target, String name) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read field " + name, e);
            }
        }
    }

    @Test
    void cacheIsEmptyBeforeAnyShopLoads() {
        assertEquals(0, headCache().size(), "Nothing should be cached until a shop is loaded");
    }

    @Test
    void loadingAShopCachesItsOwnerHead() {
        UUID owner = UUID.randomUUID();
        getPlugin().getShopHandler().addShop(shopOwnedBy(owner));

        assertTrue(headCache().containsKey(owner), "A loaded shop should cache its owner's head");
    }

    @Test
    void invalidationDropsTheCachedEntry() {
        UUID owner = UUID.randomUUID();
        getPlugin().getShopHandler().addShop(shopOwnedBy(owner));
        assertTrue(headCache().containsKey(owner), "Precondition: the head is cached");

        getPlugin().getGuiHandler().invalidatePlayerHead(owner);

        assertTrue(headCache().isEmpty(), "Invalidation must remove the entry, not merely hide it");
    }

    @Test
    void invalidatingAnUncachedOwnerIsHarmless() {
        getPlugin().getGuiHandler().invalidatePlayerHead(UUID.randomUUID());
        assertEquals(0, headCache().size());
    }

    @Test
    void invalidationToleratesNull() {
        getPlugin().getGuiHandler().invalidatePlayerHead(null);
        assertEquals(0, headCache().size(), "Null owner UUIDs occur on admin shops and must not throw");
    }

    /**
     * The reported symptom. A head resolved while the owner was offline was cached permanently; a
     * join must clear it so the next render re-resolves against a player who is actually online.
     *
     * <p>MockBukkit has no join simulator on {@code PlayerMock}, so the event is fired directly.
     * The listener defers invalidation by 5 ticks through FoliaLib, matching how
     * {@code PlayerNameCache} is refreshed, so the scheduler is advanced past that.
     */
    @Test
    void ownerJoiningClearsTheirCachedHead() {
        // The cache is keyed by owner UUID, so the shop must be owned by the player who joins —
        // MockBukkit assigns the UUID on addPlayer, so the player is created first.
        PlayerMock ownerJoins = addStubbedPlayer("ReturningOwner");
        UUID owner = ownerJoins.getUniqueId();

        getPlugin().getShopHandler().addShop(shopOwnedBy(owner));
        assertTrue(headCache().containsKey(owner), "Precondition: head cached for this owner");

        getServer().getPluginManager().callEvent(new PlayerJoinEvent(ownerJoins, "joined"));

        // The join handler defers invalidation by 5 ticks via FoliaLib, which dispatches onto the
        // async pool — so both the tick advance and the async drain are needed.
        getServer().getScheduler().performTicks(20);
        getServer().getScheduler().waitAsyncTasksFinished();

        assertTrue(headCache().isEmpty(),
                "A joining player's cached head must be dropped so a changed skin is picked up");
    }

    @Test
    void removingTheOwnersLastShopEvictsTheirHead() {
        UUID owner = UUID.randomUUID();
        AbstractShop shop = shopOwnedBy(owner);
        getPlugin().getShopHandler().addShop(shop);
        assertTrue(headCache().containsKey(owner), "Precondition: cached");

        getPlugin().getShopHandler().removeShop(shop, false);

        assertTrue(headCache().isEmpty(),
                "With no shops left, the cached head is dead weight and must be evicted");
    }

    @Test
    void removingOneOfManyShopsKeepsTheOwnersHead() {
        UUID owner = UUID.randomUUID();
        AbstractShop first = shopOwnedBy(owner);
        AbstractShop second = shopOwnedBy(owner, 120);
        getPlugin().getShopHandler().addShop(first);
        getPlugin().getShopHandler().addShop(second);

        getPlugin().getShopHandler().removeShop(first, false);

        assertTrue(headCache().containsKey(owner),
                "The owner still has a shop, so their head stays cached and the GUI keeps working");
    }

    /**
     * Callers mutate the returned stack (they set lore, attach click handlers), so handing out the
     * cached instance directly would let one render corrupt the cache for every later one.
     */
    @Test
    void getPlayerHeadIconHandsOutACopy() {
        UUID owner = UUID.randomUUID();
        getPlugin().getShopHandler().addShop(shopOwnedBy(owner));

        ItemStack first = getPlugin().getGuiHandler().getPlayerHeadIcon(owner);
        ItemStack second = getPlugin().getGuiHandler().getPlayerHeadIcon(owner);

        assertNotNull(first);
        assertNotSame(first, second, "Each caller must get its own copy of the cached skull");
    }

    @Test
    void getPlayerHeadIconForAnUnknownOwnerReturnsAir() {
        assertEquals(Material.AIR, getPlugin().getGuiHandler().getPlayerHeadIcon(UUID.randomUUID()).getType(),
                "An unknown owner yields AIR, which GUI callers already guard against");
    }

    /** Two owners are cached independently — the cache is keyed by owner, not shop. */
    @Test
    void separateOwnersGetSeparateEntries() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        getPlugin().getShopHandler().addShop(shopOwnedBy(first, 100));
        getPlugin().getShopHandler().addShop(shopOwnedBy(second, 120));

        assertEquals(2, headCache().size());
        getPlugin().getGuiHandler().invalidatePlayerHead(first);
        assertEquals(1, headCache().size(), "Invalidating one owner must not evict the other");
    }
}
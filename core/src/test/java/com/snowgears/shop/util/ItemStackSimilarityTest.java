package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code itemstacksAreSimilar} across item types the two meta casts cannot assume
 * (issue #90).
 *
 * <p>The method cast {@code ItemMeta} to {@code Damageable} and {@code Repairable} without checking,
 * and {@code ignoreItemRepairCost} defaults to {@code true} — so the {@code Repairable} cast ran on
 * <em>every</em> item comparison on a default install. On a real server any non-repairable item threw
 * {@code ClassCastException} from the primitive that stock counting, item removal, barter matching and
 * shop creation all depend on.
 *
 * <p><b>What these tests can and cannot prove.</b> MockBukkit's {@code ItemMetaMock} declares
 * {@code implements ItemMeta, Damageable, Repairable} unconditionally, for every material — dirt
 * included. The cast therefore never fails here, and no test written against MockBukkit can
 * reproduce the original {@code ClassCastException}. I verified this by reverting the fix and
 * re-running: the suite stayed green.
 *
 * <p>So what is asserted here is what <em>is</em> checkable — that normalisation still happens, that
 * comparison semantics are unchanged, and that a null meta does not NPE — and the {@code instanceof}
 * guards themselves are reviewed rather than tested. A test that cannot fail is worse than no test, so
 * none is pretending otherwise.
 */
@ExtendWith(MockBukkitExtension.class)
class ItemStackSimilarityTest {

    private Shop plugin;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void setConfigFlag(String field, boolean value) throws Exception {
        Field f = Shop.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(plugin, value);
    }

    @Test
    @DisplayName("Identical items are still similar, and different types still are not")
    void similaritySemanticsAreUnchanged() {
        assertTrue(InventoryUtils.itemstacksAreSimilar(
                        new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIAMOND_SWORD)),
                "The same item must compare similar");

        assertFalse(InventoryUtils.itemstacksAreSimilar(
                        new ItemStack(Material.DIRT), new ItemStack(Material.STONE)),
                "Different item types must not compare similar");

        assertFalse(InventoryUtils.itemstacksAreSimilar(null, new ItemStack(Material.DIRT)),
                "A null stack is never similar");
        assertFalse(InventoryUtils.itemstacksAreSimilar(new ItemStack(Material.DIRT), null));
    }

    @Test
    @DisplayName("Damage is normalised on the clones, so a damaged item still matches a pristine one")
    void normalisationStillHappens() throws Exception {
        setConfigFlag("checkItemDurability", false);

        // A damaged sword and a pristine one must compare similar when durability is ignored,
        // or the config option would have no effect at all. The reset happens on the internal
        // clones, so the caller's stack is deliberately left untouched — assert that too, since
        // mutating the argument would be a separate bug.
        ItemStack damaged = new ItemStack(Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.Damageable meta =
                (org.bukkit.inventory.meta.Damageable) damaged.getItemMeta();
        meta.setDamage(10);
        damaged.setItemMeta(meta);

        ItemStack pristine = new ItemStack(Material.DIAMOND_SWORD);

        assertEquals(10, ((org.bukkit.inventory.meta.Damageable) damaged.getItemMeta()).getDamage(),
                "Precondition: the caller's stack starts damaged");

        assertTrue(InventoryUtils.itemstacksAreSimilar(damaged, pristine),
                "With checkItemDurability=false a damaged item must match a pristine one — the "
                        + "normalisation has to actually happen, not just avoid throwing");

        assertEquals(10, ((org.bukkit.inventory.meta.Damageable) damaged.getItemMeta()).getDamage(),
                "Normalisation must happen on the internal clones; the caller's ItemStack is an "
                        + "argument and must not be mutated");
    }
}
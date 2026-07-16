package com.snowgears.shop.util;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps condensable currency items to their block form and the number of
 * singular units contained in one block (the "compression ratio").
 *
 * For example: DIAMOND -> DIAMOND_BLOCK at ratio 9
 * meaning 1 diamond block == 9 diamonds.
 *
 * Implements feature request snowgears#44: allow players to use ore blocks
 * interchangeably with singular ores when the currency type is ITEM.
 */
public final class BlockConversionRegistry {

    // singular material -> block material
    private static final Map<Material, Material> SINGULAR_TO_BLOCK;
    // singular material -> how many singulars fit in one block
    private static final Map<Material, Integer> COMPRESSION_RATIO;

    static {
        Map<Material, Material> toBlock = new HashMap<>();
        Map<Material, Integer> ratio   = new HashMap<>();

        // Standard 9-to-1 conversions
        reg(toBlock, ratio, Material.DIAMOND,            Material.DIAMOND_BLOCK,          9);
        reg(toBlock, ratio, Material.EMERALD,            Material.EMERALD_BLOCK,          9);
        reg(toBlock, ratio, Material.GOLD_INGOT,         Material.GOLD_BLOCK,             9);
        reg(toBlock, ratio, Material.IRON_INGOT,         Material.IRON_BLOCK,             9);
        reg(toBlock, ratio, Material.COAL,               Material.COAL_BLOCK,             9);
        reg(toBlock, ratio, Material.LAPIS_LAZULI,       Material.LAPIS_BLOCK,            9);
        reg(toBlock, ratio, Material.REDSTONE,           Material.REDSTONE_BLOCK,         9);
        reg(toBlock, ratio, Material.COPPER_INGOT,       Material.COPPER_BLOCK,           9);
        reg(toBlock, ratio, Material.AMETHYST_SHARD,     Material.AMETHYST_BLOCK,         4);
        reg(toBlock, ratio, Material.QUARTZ,             Material.QUARTZ_BLOCK,           4);
        reg(toBlock, ratio, Material.BONE_MEAL,          Material.BONE_BLOCK,             9);
        reg(toBlock, ratio, Material.SLIME_BALL,         Material.SLIME_BLOCK,            9);
        reg(toBlock, ratio, Material.HONEY_BOTTLE,       Material.HONEY_BLOCK,            4);
        reg(toBlock, ratio, Material.PRISMARINE_SHARD,   Material.PRISMARINE,             4);
        reg(toBlock, ratio, Material.DRIED_KELP,         Material.DRIED_KELP_BLOCK,       9);
        reg(toBlock, ratio, Material.WHEAT,              Material.HAY_BLOCK,              9);
        reg(toBlock, ratio, Material.NETHER_BRICK,       Material.NETHER_BRICKS,          4);

        // Netherite: nugget (scrap) -> ingot at 9-to-1, ingot -> block at 9-to-1
        reg(toBlock, ratio, Material.NETHERITE_SCRAP,    Material.NETHERITE_INGOT,        4);
        reg(toBlock, ratio, Material.NETHERITE_INGOT,    Material.NETHERITE_BLOCK,        9);

        SINGULAR_TO_BLOCK  = Collections.unmodifiableMap(toBlock);
        COMPRESSION_RATIO  = Collections.unmodifiableMap(ratio);
    }

    private static void reg(Map<Material, Material> toBlock,
                            Map<Material, Integer>  ratio,
                            Material singular, Material block, int units) {
        toBlock.put(singular, block);
        ratio.put(singular,   units);
    }

    private BlockConversionRegistry() {}

    /** Returns the block form of {@code singular}, or {@code null} if none registered. */
    public static Material getBlockForm(Material singular) {
        return SINGULAR_TO_BLOCK.get(singular);
    }

    /** Returns how many singular units fit in one block, or 0 if not registered. */
    public static int getCompressionRatio(Material singular) {
        return COMPRESSION_RATIO.getOrDefault(singular, 0);
    }

    /** Returns {@code true} if this singular material has a registered block conversion. */
    public static boolean hasBlockForm(Material singular) {
        return SINGULAR_TO_BLOCK.containsKey(singular);
    }
}

package com.snowgears.shop.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SafeTeleport {

    // Names instead of Material constants so older servers missing some of them still load this class
    private static final Set<String> HAZARDOUS_BODY = new HashSet<>(Arrays.asList(
            "LAVA", "FIRE", "SOUL_FIRE", "COBWEB", "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW",
            "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY"));

    private static final Set<String> HAZARDOUS_GROUND = new HashSet<>(Arrays.asList(
            "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS", "POINTED_DRIPSTONE"));

    private static final int[] NEARBY_FEET_OFFSETS = {0, -1, -2, 1, 2};

    private SafeTeleport() {
    }

    /**
     * Finds a spot in front of a shop sign where a player can stand without suffocating or taking damage,
     * looking at the sign. Returns null if there is none.
     */
    public static Location findStandingSpot(Block sign, BlockFace facing, Block chest) {
        if (sign == null || facing == null) {
            return null;
        }
        World world = sign.getWorld();
        if (world == null) {
            return null;
        }

        BlockFace opposite = facing.getOppositeFace();
        BlockFace left = rotateLeft(facing);
        BlockFace right = rotateRight(facing);

        List<Candidate> candidates = new ArrayList<>();

        // Candidate columns (X,Z positions to check)
        List<int[]> columns = new ArrayList<>();

        // Directly in front of the sign
        columns.add(new int[]{sign.getX() + facing.getModX(), sign.getZ() + facing.getModZ()});
        // Directly in front of chest
        if (chest != null) {
            columns.add(new int[]{chest.getX() + facing.getModX(), chest.getZ() + facing.getModZ()});
        }
        // On top of chest
        if (chest != null) {
            columns.add(new int[]{chest.getX(), chest.getZ()});
        }
        // Columns beside front column
        int frontX = sign.getX() + facing.getModX();
        int frontZ = sign.getZ() + facing.getModZ();
        columns.add(new int[]{frontX + left.getModX(), frontZ + left.getModZ()});
        columns.add(new int[]{frontX + right.getModX(), frontZ + right.getModZ()});
        if (chest != null) {
            int cx = chest.getX();
            int cz = chest.getZ();
            columns.add(new int[]{cx + left.getModX(), cz + left.getModZ()});
            columns.add(new int[]{cx + right.getModX(), cz + right.getModZ()});
        }

        // For each column, scan at different Y levels (prioritizing near sign/chest Y)
        int signY = sign.getY();
        int chestY = chest != null ? chest.getY() : signY;
        int[] baseYs = {signY, chestY};

        for (int[] col : columns) {
            int x = col[0];
            int z = col[1];
            for (int baseY : baseYs) {
                // Check at baseY and nearby Y offsets
                for (int dy : NEARBY_FEET_OFFSETS) {
                    int y = baseY + dy;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                        continue;
                    }
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    Block ground = world.getBlockAt(x, y - 1, z);
                    if (isSafeFeet(feet) && isSafeHead(head) && isSafeGround(ground)) {
                        candidates.add(new Candidate(new Location(world, x, y, z)));
                    }
                }
            }
            // Also scan a reasonable vertical range as fallback
            addCandidatesInColumn(world, x, z, candidates);
        }

        for (Candidate candidate : candidates) {
            Location loc = candidate.location;
            loc.setYaw(UtilMethods.faceToYaw(opposite));
            loc.setPitch(25.0f);
            loc.add(0.5, 0, 0.5);
            return loc;
        }
        return null;
    }

    private static void addCandidatesInColumn(World world, int x, int z, List<Candidate> candidates) {
        // Scan a reasonable vertical range around sea level
        for (int y = Math.min(world.getMaxHeight() - 2, 120); y >= Math.max(world.getMinHeight(), 0); y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block ground = world.getBlockAt(x, y - 1, z);
            if (isAir(feet) && isAir(head) && isSolid(ground) && !isHazardousGround(ground)) {
                candidates.add(new Candidate(new Location(world, x, y, z)));
            }
            // Limit candidates to avoid scanning entire world
            if (candidates.size() > 20) {
                break;
            }
        }
    }

    private static List<Candidate> getCandidatesForColumn(World world, int x, int z) {
        List<Candidate> list = new ArrayList<>();
        if (world == null) {
            return list;
        }
        addCandidatesInColumn(world, x, z, list);
        return list;
    }

    private static boolean isAir(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return true;
        }
        return block.isEmpty();
    }

    private static boolean isSolid(Block block) {
        if (block == null) {
            return false;
        }
        if (isAir(block)) {
            return false;
        }
        String name = block.getType().name();
        // Avoid non-solid special blocks as ground
        if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("CARPET")) {
            // still can be solid enough in practice, but be conservative
        }
        return block.getType().isSolid();
    }

    private static boolean isSafeFeet(Block block) {
        if (!isAir(block)) {
            return false;
        }
        return !isHazardousBody(block);
    }

    private static boolean isSafeHead(Block block) {
        if (!isAir(block)) {
            return false;
        }
        return !isHazardousBody(block);
    }

    private static boolean isSafeGround(Block block) {
        if (block == null) {
            return false;
        }
        if (!block.getType().isSolid()) {
            return false;
        }
        if (isHazardousGround(block)) {
            return false;
        }
        return true;
    }

    private static boolean isHazardousBody(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        if (HAZARDOUS_BODY.contains(name)) {
            return true;
        }
        // Check block data for liquids
        try {
            BlockData data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.Levelled) {
                // water/lava levelled
                String lower = name.toLowerCase();
                if (lower.contains("lava")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return name.contains("LAVA") || name.equals("WATER");
    }

    private static boolean isHazardousGround(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        if (HAZARDOUS_GROUND.contains(name)) {
            return true;
        }
        return false;
    }

    private static BlockFace rotateLeft(BlockFace face) {
        switch (face) {
            case NORTH:
                return WEST;
            case WEST:
                return SOUTH;
            case SOUTH:
                return EAST;
            case EAST:
                return NORTH;
            default:
                return face;
        }
    }

    private static BlockFace rotateRight(BlockFace face) {
        switch (face) {
            case NORTH:
                return EAST;
            case EAST:
                return SOUTH;
            case SOUTH:
                return WEST;
            case WEST:
                return NORTH;
            default:
                return face;
        }
    }

    private static class Candidate {
        Location location;

        Candidate(Location location) {
            this.location = location;
        }
    }
}

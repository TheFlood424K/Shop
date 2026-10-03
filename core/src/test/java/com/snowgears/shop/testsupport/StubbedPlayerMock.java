package com.snowgears.shop.testsupport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;
import java.util.UUID;

/**
 * A {@link PlayerMock} that implements the entity APIs MockBukkit v26.2 (4.116.1) leaves as
 * {@code UnimplementedOperationException} stubs.
 * <p>
 * MockBukkit's {@code UnimplementedOperationException} extends JUnit's {@code TestAbortedException},
 * so any test whose code path touches one of these methods is silently reported as <em>skipped</em>
 * rather than failed. That hides real coverage: 61 tests across this module were skipping for this
 * reason alone.
 * <p>
 * The overrides below supply the real semantics for the subset of APIs the Shop plugin actually
 * calls:
 * <ul>
 *   <li>{@link #setVisibleByDefault} / {@link #isVisibleByDefault} — display entities, see
 *       {@code Display.java}</li>
 *   <li>{@link #getTargetBlockExact} / {@link #getTargetBlock} / {@link #getTargetBlockFace} —
 *       ray targeting, see {@code MiscListener.java} and {@code DisplayListener.java}</li>
 * </ul>
 * Targeting is implemented by walking a straight ray from the player's eye along the direction
 * they are facing, which is what vanilla does and is sufficient for the sign/chest interactions
 * these tests exercise.
 */
public class StubbedPlayerMock extends PlayerMock {

    /** Maximum ray length, in blocks, for {@link #getTargetBlockExact}. */
    private static final double REACH = 64.0;

    public StubbedPlayerMock(ServerMock server, String name) {
        super(server, name);
    }

    public StubbedPlayerMock(ServerMock server, String name, UUID uuid) {
        super(server, name, uuid);
    }

    // ---------------------------------------------------------------------
    // Entity visibility — MockBukkit throws, Shop's Display handler calls it
    // ---------------------------------------------------------------------

    private boolean visibleByDefault = true;

    @Override
    public void setVisibleByDefault(boolean visible) {
        this.visibleByDefault = visible;
    }

    @Override
    public boolean isVisibleByDefault() {
        return visibleByDefault;
    }

    // ---------------------------------------------------------------------
    // Ray targeting — Mirrors LivingEntity#rayTraceEntities semantics closely
    // enough for block-targeting: step along the look vector, return the
    // first non-passable block within the requested distance.
    // ---------------------------------------------------------------------

    @Override
    public Block getTargetBlockExact(int maxDistance) {
        return getTargetBlock(Set.of(), maxDistance);
    }

    @Override
    public Block getTargetBlockExact(int maxDistance, org.bukkit.FluidCollisionMode fluidCollisionMode) {
        return getTargetBlock(Set.of(), maxDistance);
    }

    @Override
    public Block getTargetBlock(Set<Material> transparent, int maxDistance) {
        Location eye = getEyeLocation();
        if (eye.getWorld() == null || maxDistance <= 0) {
            return null;
        }

        // Bukkit yaw/pitch: 0 = +Z (south), 90 = -X (west), -90 = +X (east).
        // Convert to the look vector the entity actually travels along.
        double yaw = Math.toRadians(eye.getYaw());
        double pitch = Math.toRadians(eye.getPitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);

        int limit = Math.min(maxDistance, (int) Math.ceil(REACH));
        Location probe = eye.clone();
        for (int step = 0; step <= limit; step++) {
            Block block = probe.getBlock();
            if (isPassable(block, transparent)) {
                probe.add(dx, dy, dz);
                continue;
            }
            return block;
        }
        return null;
    }

    @Override
    public Block getTargetBlock(int maxDistance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode fluidMode) {
        return getTargetBlock(Set.of(), maxDistance);
    }

    @Override
    public org.bukkit.block.BlockFace getTargetBlockFace(int maxDistance) {
        Block target = getTargetBlockExact(maxDistance);
        return target == null ? null : faceTowards(target.getLocation());
    }

    /**
     * The face of {@code target} that points back at the player's eye — i.e. the face the player
     * is looking at. Computed from the dominant axis of the offset rather than
     * {@code Block#getFace}, which is not part of the Bukkit API.
     */
    private org.bukkit.block.BlockFace faceTowards(Location target) {
        Location eye = getEyeLocation();
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ax >= ay && ax >= az) {
            return dx > 0 ? org.bukkit.block.BlockFace.WEST : org.bukkit.block.BlockFace.EAST;
        }
        if (ay >= az) {
            return dy > 0 ? org.bukkit.block.BlockFace.DOWN : org.bukkit.block.BlockFace.UP;
        }
        return dz > 0 ? org.bukkit.block.BlockFace.NORTH : org.bukkit.block.BlockFace.SOUTH;
    }

    @Override
    public org.bukkit.block.BlockFace getTargetBlockFace(int maxDistance, org.bukkit.FluidCollisionMode fluidCollisionMode) {
        return getTargetBlockFace(maxDistance);
    }

    @Override
    public org.bukkit.block.BlockFace getTargetBlockFace(int maxDistance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode fluidMode) {
        return getTargetBlockFace(maxDistance);
    }

    /**
     * A block is passable when it is air, or when its material is one the caller explicitly
     * listed as transparent. Sign/chest interactions need solid blocks to register as targets,
     * so everything else counts as a hit.
     */
    private boolean isPassable(Block block, Set<Material> transparent) {
        if (block == null) {
            return true;
        }
        Material type = block.getType();
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }
        return transparent != null && transparent.contains(type);
    }

    // ---------------------------------------------------------------------
    // Misc no-throw overrides — these get called incidentally on some paths
    // and would otherwise abort otherwise-passing tests.
    // ---------------------------------------------------------------------

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void showDemoScreen() {
        // no-op
    }

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public String toString() {
        return "StubbedPlayerMock{" + getName() + "}";
    }
}
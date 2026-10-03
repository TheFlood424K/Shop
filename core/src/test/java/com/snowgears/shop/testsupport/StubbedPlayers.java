package com.snowgears.shop.testsupport;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Factory for {@link StubbedPlayerMock} instances.
 *
 * <p>MockBukkit v26.2 (4.116.1) implements several entity APIs the Shop plugin depends on —
 * {@code setVisibleByDefault}, {@code getTargetBlockExact}, {@code getTargetBlockFace}, and
 * friends — as {@code UnimplementedOperationException} stubs. That exception extends JUnit's
 * {@code TestAbortedException}, so any test whose code path touches one is reported as
 * <em>skipped</em> rather than failed. That silently removed 61 tests from CI reporting without
 * ever surfacing as a failure.
 *
 * <p>{@link StubbedPlayerMock} supplies real implementations for those APIs. Tests should create
 * players through this factory rather than {@code server.addPlayer(...)}, because MockBukkit's
 * {@code PlayerMockFactory} is {@code final} with no setter and its {@code addPlayer} methods
 * construct {@code PlayerMock} directly.
 */
public final class StubbedPlayers {

    private StubbedPlayers() {
    }

    /** Creates a {@link StubbedPlayerMock} with the given name and registers it with the server. */
    public static PlayerMock add(ServerMock server, String name) {
        PlayerMock player = new StubbedPlayerMock(server, name);
        server.addPlayer(player);
        return player;
    }

    /** As {@link #add(ServerMock, String)}, but with operator permissions. */
    public static PlayerMock addOp(ServerMock server, String name) {
        PlayerMock player = add(server, name);
        player.setOp(true);
        return player;
    }
}
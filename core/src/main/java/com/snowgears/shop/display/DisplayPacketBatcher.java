package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Batches display packets to reduce network overhead.
 * Packets for the same player within a short window are combined into a single tick.
 */
public class DisplayPacketBatcher {

    private static final int BATCH_WINDOW_TICKS = 1; // 1 tick = 50ms
    private static final int MAX_PACKETS_PER_BATCH = 50;

    private final Map<UUID, List<Packet>> pendingPackets = new ConcurrentHashMap<>();
    private final AtomicLong lastFlush = new AtomicLong(0);

    public void queuePacket(Player player, Runnable packetSender) {
        UUID playerId = player.getUniqueId();
        pendingPackets.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(new Packet(playerId, packetSender));

        // Flush if batch is full
        if (pendingPackets.get(playerId).size() >= MAX_PACKETS_PER_BATCH) {
            flushPlayer(playerId);
        }
    }

    public void flushAll() {
        long now = System.currentTimeMillis();
        if (now - lastFlush.get() < BATCH_WINDOW_TICKS * 50) {
            return; // Not time yet
        }
        if (lastFlush.compareAndSet(lastFlush.get(), now)) {
            for (UUID playerId : new ArrayList<>(pendingPackets.keySet())) {
                flushPlayer(playerId);
            }
        }
    }

    private void flushPlayer(UUID playerId) {
        List<Packet> packets = pendingPackets.remove(playerId);
        if (packets == null || packets.isEmpty()) return;

        Player player = Shop.getPlugin().getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Execute all packets for this player in one go
        for (Packet packet : packets) {
            try {
                packet.sender.run();
            } catch (Exception e) {
                Shop.getPlugin().getLogger().warning("Failed to send display packet: " + e.getMessage());
            }
        }
    }

    private static class Packet {
        final UUID playerId;
        final Runnable sender;

        Packet(UUID playerId, Runnable sender) {
            this.playerId = playerId;
            this.sender = sender;
        }
    }

    // Singleton instance
    private static DisplayPacketBatcher instance;

    public static DisplayPacketBatcher getInstance() {
        if (instance == null) {
            synchronized (DisplayPacketBatcher.class) {
                if (instance == null) {
                    instance = new DisplayPacketBatcher();
                }
            }
        }
        return instance;
    }
}
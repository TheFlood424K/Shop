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
        // The map is concurrent; the list it hands back is not. computeIfAbsent gives every caller for
        // the same player the *same* ArrayList, so two threads then .add() to it unguarded and one
        // write can be lost when the backing array is swapped during growth. Synchronising on the list
        // makes the add and the size check one operation, so the MAX_PACKETS_PER_BATCH backstop is
        // measured against a list that is not being mutated underneath it.
        List<Packet> queue = pendingPackets.computeIfAbsent(playerId, k -> new ArrayList<>());
        synchronized (queue) {
            queue.add(new Packet(playerId, packetSender));

            // Flush if batch is full. flushPlayer removes the entry from the map and drains it, so it
            // is safe to call while holding the lock: only one caller wins the remove.
            if (queue.size() >= MAX_PACKETS_PER_BATCH) {
                flushPlayer(playerId);
            }
        }
    }

    public void flushAll() {
        long now = System.currentTimeMillis();
        // Read once and compare against that same value. Reading lastFlush.get() separately for the
        // guard and again inside the CAS is not a lost-update — the CAS still fails if another thread
        // won — but it means the window check and the claim are decided on two different reads, so
        // two threads can both pass the guard and both flush.
        long previous = lastFlush.get();
        if (now - previous < BATCH_WINDOW_TICKS * 50L) {
            return; // Not time yet
        }
        if (lastFlush.compareAndSet(previous, now)) {
            for (UUID playerId : new ArrayList<>(pendingPackets.keySet())) {
                flushPlayer(playerId);
            }
        }
    }

    private void flushPlayer(UUID playerId) {
        List<Packet> packets = pendingPackets.remove(playerId);
        if (packets == null || packets.isEmpty()) return;

        Player player = Shop.getPlugin().getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            // The queue has already been removed from the map at this point, so returning here
            // discards every packet in it. A player who logs out between queueing and flushing — or
            // simply is not online when the batch drains — silently loses their display updates.
            // Nothing is done with them, but they must not vanish without a trace either.
            Shop.getPlugin().getLogger().fine("Dropping " + packets.size()
                    + " display packet(s) for " + playerId + ": player not online.");
            return;
        }

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
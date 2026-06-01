package com.snowgears.shop.testsupport;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for asserting and debugging PlayerMock message queues deterministically.
 */
public final class PlayerMessageTestUtil {

    private PlayerMessageTestUtil() {}

    public static List<String> drainMessages(ServerMock server, PlayerMock player, int maxTicks, int maxMessages) {
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < maxTicks && messages.size() < maxMessages; i++) {
            String msg = player.nextMessage();
            if (msg != null) {
                messages.add(msg);
                continue;
            }
            server.getScheduler().performTicks(1);
        }
        // Drain immediate backlog without ticking (if any)
        while (messages.size() < maxMessages) {
            String msg = player.nextMessage();
            if (msg == null) break;
            messages.add(msg);
        }
        return messages;
    }
}



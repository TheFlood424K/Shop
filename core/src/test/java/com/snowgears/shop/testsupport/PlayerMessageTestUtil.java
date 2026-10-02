package com.snowgears.shop.testsupport;

import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Utility for verifying player messages in tests.
 */
public final class PlayerMessageTestUtil {

    private PlayerMessageTestUtil() {}

    /**
     * Waits for and returns the next message sent to the player, or null if none within timeout.
     */
    public static String waitForMessage(PlayerMock player) {
        return BaseMockBukkitTest.waitForNextMessage(player);
    }

    /**
     * Asserts that the player received a message containing the expected text.
     */
    public static void assertReceivedMessage(PlayerMock player, String expectedSubstring) {
        String message = waitForMessage(player);
        if (message == null) {
            throw new AssertionError("Expected message containing '" + expectedSubstring + "' but received none");
        }
        if (!message.contains(expectedSubstring)) {
            throw new AssertionError("Expected message containing '" + expectedSubstring + "' but got: " + message);
        }
    }

    /**
     * Asserts that the player received a message exactly matching the expected text.
     */
    public static void assertReceivedExactMessage(PlayerMock player, String expected) {
        String message = waitForMessage(player);
        if (message == null) {
            throw new AssertionError("Expected exact message '" + expected + "' but received none");
        }
        if (!message.equals(expected)) {
            throw new AssertionError("Expected exact message '" + expected + "' but got: " + message);
        }
    }

    /**
     * Asserts that the player has no more messages.
     */
    public static void assertNoMoreMessages(PlayerMock player) {
        String message = player.nextMessage();
        if (message != null) {
            throw new AssertionError("Expected no more messages but got: " + message);
        }
    }
}
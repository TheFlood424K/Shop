package com.snowgears.shop.integration.features;

import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class TransactionCommandHandlerTest extends BaseMockBukkitTest {

    @Test
    void commandShouldRequirePermission() {
        PlayerMock player = getServer().addPlayer();
        // Not op, no shop.operator permission

        var command = getServer().getCommandMap().getCommand("shoplookup");
        assertNotNull(command);
        boolean result = command.execute(player, "shoplookup", new String[]{"Notch"});
        assertTrue(result);

        String message = waitForNextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("do not have permission"), "Should deny permission: " + message);
    }

    @Test
    void commandShouldShowUsageWhenNoArgs() {
        PlayerMock player = getServer().addPlayer();
        player.setOp(true);

        var command = getServer().getCommandMap().getCommand("shoplookup");
        assertNotNull(command);
        boolean result = command.execute(player, "shoplookup", new String[]{});
        assertTrue(result);

        String message = waitForNextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("Usage"), "Should show usage: " + message);
    }

    @Test
    void commandShouldLookupTransactions() {
        PlayerMock player = getServer().addPlayer();
        player.setOp(true);

        // Look up transactions for an owner with no data - should say no transactions found
        var command = getServer().getCommandMap().getCommand("shoplookup");
        assertNotNull(command);
        boolean result = command.execute(player, "shoplookup", new String[]{"Notch"});
        assertTrue(result);

        getServer().getScheduler().waitAsyncTasksFinished();

        String message = waitForNextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("No transactions found"), "Should say no transactions found: " + message);
    }

    @Test
    void commandShouldBeRegistered() {
        assertNotNull(getServer().getCommandMap().getCommand("shoplookup"),
                "shoplookup command should be registered in command map");
    }
}

package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for issue #142: Owner notification error messages (no-stock, no-space)
 * must contain the literal "[Shop]" brand tag from chatConfig.yml, not "sell"/"buy"/etc
 * from signConfig.yml.
 * <p>
 * The two config tables look identical but serve different purposes:
 * - chatConfig.yml → creation_words.SHOP = "[Shop]" (brand tag for chat messages)
 * - signConfig.yml → sign_creation.SHOP = "shop" (word to print on signs)
 * <p>
 * The "[Shop]" brand tag appears in the transaction_issue section of chatConfig.yml
 * (ownerNoStock, ownerNoSpace messages), NOT in the regular transaction owner messages.
 * <p>
 * If code incorrectly used the sign_creation table for these messages, owner error
 * notifications would show "sell"/"buy"/"barter" instead of the literal "[Shop]".
 */
@Tag("integration")
class OwnerNotificationBrandTagTest extends BaseMockBukkitTest {

    /** Verifies that a SELL shop stock warning preserves the literal {@code [Shop]} brand tag. */
    @Test
    void ownerNoStockMessageContainsLiteralShopBrandTag() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        // Create owner and buyer
        PlayerMock owner = addStubbedPlayer("Owner");
        owner.setOp(true);
        PlayerMock buyer = addStubbedPlayer("Buyer");

        // Enable owner notifications
        setConfig("usePerms", false);
        BaseMockBukkitTest.setConfig("allowCreateMethodChest", true);

        // Create a SELL shop via chest flow
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server, plugin, owner, world,
                new Location(world, 100, 65, 100),
                new ItemStack(Material.DIRT),
                "sell", 8, "1",
                true
        );

        assertNotNull(shop, "Shop should be created");
        assertTrue(shop.isInitialized(), "Shop should be initialized");

        // Drain creation messages
        while (owner.nextMessage() != null) {}
        while (buyer.nextMessage() != null) {}

        // Give buyer currency but shop has NO stock
        buyer.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        // Trigger purchase - buyer right-clicks sign (shop out of stock)
        Block signBlock = shop.getSignLocation().getBlock();
        world.loadChunk(signBlock.getChunk());
        PlayerInteractEvent event = new PlayerInteractEvent(
                buyer, Action.RIGHT_CLICK_BLOCK,
                buyer.getInventory().getItemInMainHand(),
                signBlock, BlockFace.NORTH, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        // Wait for error messages
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();

        // Check owner's out-of-stock notification
        String ownerMsg = BaseMockBukkitTest.waitForNextMessage(owner);
        assertNotNull(ownerMsg, "Owner should receive out-of-stock notification");

        // THE KEY ASSERTION: Owner no-stock notification must contain the literal "[Shop]" brand tag
        // from chatConfig.yml (creation_words.SHOP), NOT "sell" from signConfig.yml (sign_creation.SHOP)
        assertTrue(ownerMsg.contains("[Shop]"),
                "Owner no-stock notification must contain literal '[Shop]' brand tag from chatConfig.yml, not 'sell' from signConfig.yml. Got: " + ownerMsg);

        // Verify it matches the expected format from chatConfig.yml
        assertTrue(ownerMsg.contains("Your selling shop at"),
                "Owner no-stock notification should follow correct format. Got: " + ownerMsg);

        // Verify it does NOT start with the sign creation word as the brand tag
        // (the brand tag "[Shop]" is at the start of the message)
        assertFalse(ownerMsg.startsWith("sell "),
                "Owner no-stock notification must not use sign creation word 'sell' as brand tag prefix. Got: " + ownerMsg);
    }

    /** Verifies that a BUY shop space warning preserves the literal {@code [Shop]} brand tag. */
    @Test
    void ownerNoSpaceMessageContainsLiteralShopBrandTag() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        // Create owner and buyer
        PlayerMock owner = addStubbedPlayer("Owner");
        owner.setOp(true);
        PlayerMock buyer = addStubbedPlayer("Buyer");

        // Enable owner notifications
        setConfig("usePerms", false);
        BaseMockBukkitTest.setConfig("allowCreateMethodChest", true);

        // Create a BUY shop via chest flow (BUY shops can run out of space)
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server, plugin, owner, world,
                new Location(world, 200, 65, 100),
                new ItemStack(Material.DIRT),
                "buy", 8, "1",
                true
        );

        assertNotNull(shop, "Shop should be created");
        assertTrue(shop.isInitialized(), "Shop should be initialized");

        // Drain creation messages
        while (owner.nextMessage() != null) {}
        while (buyer.nextMessage() != null) {}

        // Fill buyer's inventory completely so they have no space for payment
        for (int i = 0; i < 36; i++) {
            buyer.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        }
        // Give buyer the item to sell
        buyer.getInventory().setItem(35, new ItemStack(Material.DIRT, 8));

        // Trigger sale - buyer right-clicks sign (buyer has no space for payment)
        Block signBlock = shop.getSignLocation().getBlock();
        world.loadChunk(signBlock.getChunk());
        PlayerInteractEvent event = new PlayerInteractEvent(
                buyer, Action.RIGHT_CLICK_BLOCK,
                buyer.getInventory().getItemInMainHand(),
                signBlock, BlockFace.NORTH, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        // Wait for error messages
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();

        // Check owner's no-space notification
        String ownerMsg = BaseMockBukkitTest.waitForNextMessage(owner);
        assertNotNull(ownerMsg, "Owner should receive no-space notification");

        // THE KEY ASSERTION: Owner no-space notification must contain the literal "[Shop]" brand tag
        assertTrue(ownerMsg.contains("[Shop]"),
                "Owner no-space notification must contain literal '[Shop]' brand tag from chatConfig.yml, not 'buy' from signConfig.yml. Got: " + ownerMsg);

        // Verify it matches the expected format from chatConfig.yml
        assertTrue(ownerMsg.contains("Your buying shop at"),
                "Owner no-space notification should follow correct format. Got: " + ownerMsg);

        // Verify it does NOT start with the sign creation word as the brand tag
        assertFalse(ownerMsg.startsWith("buy "),
                "Owner no-space notification must not use sign creation word 'buy' as brand tag prefix. Got: " + ownerMsg);
    }

    /** Verifies that a BARTER shop stock warning preserves the literal {@code [Shop]} brand tag. */
    @Test
    void barterShopNoStockMessageContainsLiteralShopBrandTag() {
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        // Create owner and trader
        PlayerMock owner = addStubbedPlayer("Owner");
        owner.setOp(true);
        PlayerMock trader = addStubbedPlayer("Trader");

        // Enable owner notifications
        setConfig("usePerms", false);
        BaseMockBukkitTest.setConfig("allowCreateMethodChest", true);

        // Create a BARTER shop via chest flow
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server, plugin, owner, world,
                new Location(world, 300, 65, 100),
                new ItemStack(Material.DIRT),
                "barter", 8, "1",
                true,
                "EMERALD", "1"
        );

        assertNotNull(shop, "Shop should be created");
        assertTrue(shop.isInitialized(), "Shop should be initialized");

        // Drain creation messages
        while (owner.nextMessage() != null) {}
        while (trader.nextMessage() != null) {}

        // Give trader the barter item (emerald) but shop has NO stock
        trader.getInventory().addItem(new ItemStack(Material.EMERALD, 8));

        // Trigger barter - trader right-clicks sign (shop out of stock)
        Block signBlock = shop.getSignLocation().getBlock();
        world.loadChunk(signBlock.getChunk());
        PlayerInteractEvent event = new PlayerInteractEvent(
                trader, Action.RIGHT_CLICK_BLOCK,
                trader.getInventory().getItemInMainHand(),
                signBlock, BlockFace.NORTH, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        // Wait for error messages
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();

        // Check owner's out-of-stock notification
        String ownerMsg = BaseMockBukkitTest.waitForNextMessage(owner);
        assertNotNull(ownerMsg, "Owner should receive out-of-stock notification");

        // Debug: print the actual message for debugging
        System.out.println("BARTER owner message: [" + ownerMsg + "]");

        // THE KEY ASSERTION: Owner no-stock notification must contain the literal "[Shop]" brand tag
        assertTrue(ownerMsg.contains("[Shop]"),
                "Owner barter no-stock notification must contain literal '[Shop]' brand tag from chatConfig.yml, not 'barter' from signConfig.yml. Got: " + ownerMsg);

        // Verify it matches the expected format from chatConfig.yml
        assertTrue(ownerMsg.contains("Your barter shop at"),
                "Owner barter no-stock notification should follow correct format. Got: " + ownerMsg);

        // Verify it does NOT start with the sign creation word as the brand tag
        // The message should start with color code + "[Shop]" not "barter "
        String prefix = ownerMsg.substring(0, Math.min(20, ownerMsg.length()));
        assertFalse(ownerMsg.startsWith("barter "),
                "Owner barter no-stock notification must not use sign creation word 'barter' as brand tag prefix. Message prefix: '" + prefix + "'. Full message: " + ownerMsg);
    }

    /** Verifies that the shop sign displays the configured sign word without the chat brand tag. */
    @Test
    void signPlaceholderUsesSignCreationWordNotBrandTag() {
        // This test verifies that the [shop] placeholder on SIGNS resolves to "shop"
        // from signConfig.yml's sign_creation table, NOT "[Shop]" from chatConfig.yml's
        // creation_words table. This is the core of issue #142.
        ServerMock server = getServer();
        Shop plugin = getPlugin();
        World world = server.addSimpleWorld("world");

        PlayerMock owner = addStubbedPlayer("Owner");
        owner.setOp(true);

        setConfig("usePerms", false);
        BaseMockBukkitTest.setConfig("allowCreateMethodChest", true);

        // Create a SELL shop via chest flow
        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                server, plugin, owner, world,
                new Location(world, 400, 65, 100),
                new ItemStack(Material.DIRT),
                "sell", 8, "1",
                true
        );

        assertNotNull(shop, "Shop should be created");
        assertTrue(shop.isInitialized(), "Shop should be initialized");

        // Drain creation messages
        while (owner.nextMessage() != null) {}

        // Restock shop so it's not out of stock
        world.loadChunk(shop.getChestLocation().getChunk());
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();
        if (shop.getInventory() != null) {
            shop.getInventory().addItem(new ItemStack(Material.DIRT, 8));
            shop.updateStock();
        }
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();

        // Load sign chunk and update sign
        world.loadChunk(shop.getSignLocation().getChunk());
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();
        shop.updateSign(true);
        server.getScheduler().performTicks(5);
        server.getScheduler().waitAsyncTasksFinished();

        // Read the sign's first line - it should use "shop" (from sign_creation.SHOP)
        // NOT "[Shop]" (from creation_words.SHOP)
        Block signBlock = shop.getSignLocation().getBlock();
        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) signBlock.getState();
        String line0 = sign.getLine(0);

        // The sign should show "shop" (or colored/bold version), NOT "[Shop]"
        // Note: the sign template uses "[shop]" which resolves to "shop" from sign_creation table
        assertTrue(line0.toLowerCase().contains("shop"),
                "Sign line 0 should contain 'shop' from sign_creation table. Got: " + line0);
        assertFalse(line0.contains("[Shop]"),
                "Sign line 0 must NOT contain '[Shop]' brand tag from creation_words table. Got: " + line0);
    }
}
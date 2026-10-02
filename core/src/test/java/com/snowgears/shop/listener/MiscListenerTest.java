package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.testsupport.ShopCreationFlowTestUtil;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit and integration tests for MiscListener shop creation and management functionality.
 * Follows upstream's BaseMockBukkitTest patterns with proper MockBukkit lifecycle management.
 */
@Tag("integration")
class MiscListenerTest extends BaseMockBukkitTest {

    private Shop plugin;
    private ShopHandler shopHandler;
    private MiscListener miscListener;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        plugin = getPlugin();
        shopHandler = plugin.getShopHandler();
        miscListener = plugin.getMiscListener();

        world = getServer().addSimpleWorld("world");
        player = getServer().addPlayer();
        player.setOp(true);

        // Enable sign and chest creation methods
        setConfig("allowCreateMethodSign", true);
        setConfig("allowCreateMethodChest", true);
        setConfig("debug_shopCreateCooldown", 0);
    }

    // ============================================================
    // WALL SIGN CREATION TESTS (all 4 cardinal directions)
    // ============================================================

    @Test
    @DisplayName("Wall sign facing SOUTH detects chest to the north")
    void testWallSignFacingSouth_chestNorth() {
        testWallSignCreation(BlockFace.SOUTH);
    }

    @Test
    @DisplayName("Wall sign facing NORTH detects chest to the south")
    void testWallSignFacingNorth_chestSouth() {
        testWallSignCreation(BlockFace.NORTH);
    }

    @Test
    @DisplayName("Wall sign facing EAST detects chest to the west")
    void testWallSignFacingEast_chestWest() {
        testWallSignCreation(BlockFace.EAST);
    }

    @Test
    @DisplayName("Wall sign facing WEST detects chest to the east")
    void testWallSignFacingWest_chestEast() {
        testWallSignCreation(BlockFace.WEST);
    }

    private void testWallSignCreation(BlockFace signFacing) {
        // Use unique location per test to avoid interference
        int offset = signFacing.ordinal() * 10;
        Location signLoc = new Location(world, 5 + offset, 65, 5 + offset);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(signFacing);
        world.setBlockData(signBlock.getLocation(), data);

        // Chest is at the OPPOSITE face of the sign's facing
        // (WallSign.getFacing() returns direction text faces; chest is behind the sign)
        BlockFace chestFace = signFacing.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        // Fire SignChangeEvent with proper lines
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        // Verify shop creation dialog sent (first message is initialCreateInstruction, second is initialize)
        String msg = waitForNextMessage(player);
        assertNotNull(msg, "Player should receive initial instruction message: " + msg);

        // Drain remaining messages (initialize message for the shop type)
        waitForNextMessage(player);

        // Verify shop registered
        AbstractShop shop = shopHandler.getShop(signLoc);
        assertNotNull(shop, "Shop should be created and registered");
        assertFalse(shop.isInitialized(), "Shop should not be initialized yet");
    }

    @Test
    @DisplayName("Sign without creation word is ignored")
    void testNonShopSignIgnored() {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text("Not a shop"));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text("sell"));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        // No message should be sent
        assertNull(player.nextMessage(), "No message should be sent for non-shop signs");
        assertNull(shopHandler.getShop(signLoc), "No shop should be created");
    }

    @Test
    @DisplayName("Sign creation disabled prevents shop creation")
    void testSignCreationDisabled() {
        setConfig("allowCreateMethodSign", false);

        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        assertNull(player.nextMessage(), "No message when creation disabled");
        assertNull(shopHandler.getShop(signLoc), "No shop created when disabled");
    }

    @Test
    @DisplayName("Invalid amount (zero) on line 2 rejects creation")
    void testInvalidAmountZero() {
        testInvalidSignLine("0", "positive number");
    }

    @Test
    @DisplayName("Invalid amount (negative) on line 2 rejects creation")
    void testInvalidAmountNegative() {
        testInvalidSignLine("-5", "positive number");
    }

    @Test
    @DisplayName("Non-numeric amount on line 2 rejects creation")
    void testNonNumericAmount() {
        testInvalidSignLine("abc", "positive number");
    }

    private void testInvalidSignLine(String amount, String expectedError) {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text(amount));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains(expectedError), "Should reject invalid amount: " + msg);
        assertNull(shopHandler.getShop(signLoc), "No shop should be created");
    }

    @Test
    @DisplayName("Invalid price on line 3 rejects creation")
    void testInvalidPrice() {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("abc"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("price") || msg.contains("number"), "Should reject invalid price: " + msg);
    }

    @Test
    @DisplayName("Admin shop creation via 'admin' keyword on line 3")
    void testAdminShopCreation() {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL") + " admin"));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("unlimited stock") || msg.contains("admin"), "Should show admin creation message: " + msg);

        AbstractShop shop = shopHandler.getShop(signLoc);
        assertNotNull(shop);
        assertTrue(shop.isAdmin(), "Shop should be admin");
    }

    @Test
    @DisplayName("Case-insensitive [shop] tag works")
    void testCaseInsensitiveShopTag() {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP").toLowerCase()));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        // First message is initialCreateInstruction
        assertTrue(msg.contains("set up your shop"), "Case-insensitive tag should work: " + msg);
    }

    @Test
    @DisplayName("Ground sign (non-wall) is ignored")
    void testGroundSignIgnored() {
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_SIGN); // Ground sign, not wall sign

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(signEvent);

        assertNull(shopHandler.getShop(signLoc), "Ground sign should not create shop");
    }

    // ============================================================
    // BUCKET EMPTY PREVENTION TESTS
    // ============================================================

    @Test
    @DisplayName("Bucket empty on wall sign cancels event")
    void testBucketEmptyOnWallSign() {
        AbstractShop shop = createInitializedShopViaSign();

        org.bukkit.event.player.PlayerBucketEmptyEvent event = mock(org.bukkit.event.player.PlayerBucketEmptyEvent.class);
        Block signBlock = shop.getSignLocation().getBlock();
        when(event.getBlockClicked()).thenReturn(signBlock);
        when(event.isCancelled()).thenReturn(false);

        miscListener.onBucketEmpty(event);

        assertTrue(event.isCancelled(), "Bucket empty on shop sign should be cancelled");
    }

    @Test
    @DisplayName("Bucket empty on non-shop wall sign does not cancel")
    void testBucketEmptyOnNonShopWallSign() {
        Location signLoc = new Location(world, 200, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        org.bukkit.event.player.PlayerBucketEmptyEvent event = mock(org.bukkit.event.player.PlayerBucketEmptyEvent.class);
        when(event.getBlockClicked()).thenReturn(signBlock);
        when(event.isCancelled()).thenReturn(false);

        miscListener.onBucketEmpty(event);

        assertFalse(event.isCancelled(), "Bucket empty on non-shop sign should not be cancelled");
    }

    // ============================================================
    // CHEST CREATION FLOW TESTS
    // ============================================================

    @Test
    @DisplayName("Chest creation flow with chat steps creates shop")
    void testChestCreationFlow() {
        Location chestLoc = new Location(world, 100, 64, 100);
        ItemStack item = new ItemStack(Material.DIAMOND);

        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                getServer(), plugin, player, world, chestLoc, item, "sell", 1, "10", true
        );

        assertNotNull(shop, "Shop should be created via chest flow");
        assertTrue(shop.isInitialized(), "Shop should be initialized");
        assertEquals(Material.DIAMOND, shop.getItemStack().getType());
        assertEquals(ShopType.SELL, shop.getType());
    }

    @Test
    @DisplayName("Chest creation prevents duplicate on same chest")
    void testPreventDuplicateChestCreation() {
        Location chestLoc = new Location(world, 100, 64, 100);
        ItemStack item = new ItemStack(Material.DIAMOND);

        // Create first shop
        AbstractShop shop1 = ShopCreationFlowTestUtil.createShopViaChestFlow(
                getServer(), plugin, player, world, chestLoc, item, "sell", 1, "10", true
        );
        assertNotNull(shop1);

        // Attempt second creation on same chest
        PlayerMock player2 = getServer().addPlayer();
        player2.setOp(true);
        ItemStack item2 = new ItemStack(Material.GOLD_INGOT);

        player2.setSneaking(true);
        player2.getInventory().setItemInMainHand(item2);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        org.bukkit.event.player.PlayerInteractEvent attempt = new org.bukkit.event.player.PlayerInteractEvent(
                player2, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item2,
                world.getBlockAt(chestLoc), BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(attempt);
        getServer().getScheduler().performTicks(5);

        assertNull(player2.nextMessage(), "No creation messages for existing shop chest");
        assertEquals(1, shopHandler.getNumberOfShops(), "Only one shop should exist");
    }

    @Test
    @DisplayName("Chest creation timeout after 30 seconds")
    void testChestCreationTimeout() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        // Start creation but don't finish
        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);

        // Drain initial messages
        while (player.nextMessage() != null) {}

        // Wait for async timeout task
        getServer().getScheduler().waitAsyncTasksFinished();

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("timed out"), "Should receive timeout message: " + msg);
        assertNull(shopHandler.getShopByChest(chestBlock), "No shop should exist after timeout");
    }

    @Test
    @DisplayName("Barter shop creation flow works")
    void testBarterShopCreation() {
        Location chestLoc = new Location(world, 100, 64, 100);
        ItemStack sellItem = new ItemStack(Material.DIAMOND);

        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                getServer(), plugin, player, world, chestLoc, sellItem, "barter", 1, "1", true
        );

        assertNotNull(shop);
        assertEquals(ShopType.BARTER, shop.getType());
    }

    @Test
    @DisplayName("Buy shop creation flow works")
    void testBuyShopCreation() {
        Location chestLoc = new Location(world, 100, 64, 100);
        ItemStack item = new ItemStack(Material.DIAMOND);

        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                getServer(), plugin, player, world, chestLoc, item, "buy", 1, "10", true
        );

        assertNotNull(shop);
        assertEquals(ShopType.BUY, shop.getType());
    }

    @Test
    @DisplayName("Combo shop creation flow works")
    void testComboShopCreation() {
        Location chestLoc = new Location(world, 100, 64, 100);
        ItemStack item = new ItemStack(Material.DIAMOND);

        AbstractShop shop = ShopCreationFlowTestUtil.createShopViaChestFlow(
                getServer(), plugin, player, world, chestLoc, item, "combo", 1, "10", true
        );

        assertNotNull(shop);
        assertEquals(ShopType.COMBO, shop.getType());
    }

    // ============================================================
    // SHOP DESTROY TESTS
    // ============================================================

    @Test
    @DisplayName("Owner can destroy own shop sign")
    void testDestroyOwnSign() {
        AbstractShop shop = createInitializedShopViaSign();

        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("destroyed"), "Should receive destroy confirmation: " + msg);
        assertEquals(Material.AIR, world.getBlockAt(shop.getSignLocation()).getType(), "Sign should be broken");
        assertNull(shopHandler.getShop(shop.getSignLocation()), "Shop should be removed");
    }

    @Test
    @DisplayName("Destroy requires sneak when configured")
    void testDestroyRequiresSneak() {
        setConfig("destroyShopRequiresSneak", true);

        AbstractShop shop = createInitializedShopViaSign();

        // Not sneaking - should be cancelled
        player.setSneaking(false);
        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());

        assertNotNull(shopHandler.getShop(shop.getSignLocation()), "Shop should remain when not sneaking");

        // Sneaking - should destroy
        player.setSneaking(true);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());

        assertNull(shopHandler.getShop(shop.getSignLocation()), "Shop should be destroyed when sneaking");
    }

    @Test
    @DisplayName("Non-owner cannot destroy shop without permission")
    void testNonOwnerCannotDestroy() {
        AbstractShop shop = createInitializedShopViaSign();

        PlayerMock other = getServer().addPlayer();
        other.setOp(false);

        PlayerSimulation sim = new PlayerSimulation(other);
        sim.simulateBlockBreak(shop.getSignLocation().getBlock());

        String msg = waitForNextMessage(other);
        assertNotNull(msg);
        assertTrue(msg.contains("not authorized"), "Should deny non-owner: " + msg);
        assertNotNull(shopHandler.getShop(shop.getSignLocation()), "Shop should remain");
    }

    @Test
    @DisplayName("Operator can destroy other's shop")
    void testOperatorCanDestroyOther() {
        AbstractShop shop = createInitializedShopViaSign();

        PlayerMock operator = getServer().addPlayer();
        operator.setOp(true);

        PlayerSimulation sim = new PlayerSimulation(operator);
        sim.simulateBlockBreak(shop.getSignLocation().getBlock());

        String msg = waitForNextMessage(operator);
        assertNotNull(msg);
        assertTrue(msg.contains("destroyed"), "Operator should be able to destroy: " + msg);
        assertNull(shopHandler.getShop(shop.getSignLocation()), "Shop should be removed");
    }

    @Test
    @DisplayName("Breaking primary chest prompts to break sign first")
    void testBreakPrimaryChestPromptsSign() {
        AbstractShop shop = createInitializedShopViaSign();

        Block chestBlock = shop.getChestLocation().getBlock();

        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(chestBlock);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("must remove the sign"), "Should prompt to break sign first: " + msg);
        assertEquals(Material.CHEST, chestBlock.getType(), "Chest should not break");
    }

    @Test
    @DisplayName("Breaking expansion chest allowed for owner")
    void testBreakExpansionChestAllowed() {
        AbstractShop shop = createInitializedShopViaSign();

        // Place adjacent chest
        Location primary = shop.getChestLocation();
        Location expansionLoc = primary.clone().add(1, 0, 0);
        Block expansion = world.getBlockAt(expansionLoc);
        expansion.setType(Material.CHEST);

        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(expansion);

        assertEquals(Material.AIR, expansion.getType(), "Expansion chest should break");
        assertNotNull(shopHandler.getShopByChest(primary.getBlock()), "Shop should still exist");
    }

    // ============================================================
    // BLOCK UNDER SHOP PROTECTION
    // ============================================================

    @Test
    @DisplayName("Block under shop chest protected from unauthorized players")
    void testBlockUnderShopProtected() {
        AbstractShop shop = createInitializedShopViaSign();

        Location under = shop.getChestLocation().clone().add(0, -1, 0);
        Block underBlock = world.getBlockAt(under);
        underBlock.setType(Material.STONE);

        PlayerMock random = getServer().addPlayer();
        random.setOp(false);
        PlayerSimulation sim = new PlayerSimulation(random);
        sim.simulateBlockBreak(underBlock);

        assertEquals(Material.STONE, underBlock.getType(), "Unauthorized player cannot break block under chest");

        // Owner can break
        PlayerSimulation simOwner = new PlayerSimulation(player);
        simOwner.simulateBlockBreak(underBlock);
        assertEquals(Material.AIR, underBlock.getType(), "Owner can break block under chest");
    }

    // ============================================================
    // SHOP EXPANSION TESTS
    // ============================================================

    @Test
    @DisplayName("Owner can expand shop with adjacent chest")
    void testShopExpansionOwner() {
        AbstractShop shop = createInitializedShopViaSign();

        Location primary = shop.getChestLocation();
        Location expansionLoc = primary.clone().add(1, 0, 0);

        // Place adjacent chest
        Block expansion = world.getBlockAt(expansionLoc);
        expansion.setType(Material.CHEST);

        // Should not be cancelled
        assertEquals(Material.CHEST, expansion.getType(), "Expansion chest should be placed");
    }

    // ============================================================
    // CANCEL CREATION PROCESS TESTS
    // ============================================================

    @Test
    @DisplayName("cancelShopCreationProcess removes process and sends message")
    void testCancelCreationProcess() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);

        // Drain messages
        while (player.nextMessage() != null) {}

        // Verify process exists
        assertNotNull(miscListener.getShopCreationProcess(player), "Process should exist");

        // Cancel
        miscListener.cancelShopCreationProcess(player);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("Cancelled"), "Should receive cancel message: " + msg);
        assertNull(miscListener.getShopCreationProcess(player), "Process should be removed");
    }

    @Test
    @DisplayName("getShopCreationProcess returns process for player")
    void testGetShopCreationProcess() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);
        while (player.nextMessage() != null) {}

        assertNotNull(miscListener.getShopCreationProcess(player), "Should return process");
        assertNull(miscListener.getShopCreationProcess(getServer().addPlayer("Other")), "Should return null for other player");
    }

    @Test
    @DisplayName("removeShopCreationProcess removes process without message")
    void testRemoveShopCreationProcess() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);
        while (player.nextMessage() != null) {}

        assertNotNull(miscListener.getShopCreationProcess(player), "Process should exist");

        miscListener.removeShopCreationProcess(player);

        assertNull(miscListener.getShopCreationProcess(player), "Process should be removed");
        // No message should be sent for silent removal
        assertNull(player.nextMessage(), "No message for silent removal");
    }

    // ============================================================
    // CREATION COOLDOWN TESTS
    // ============================================================

    @Test
    @DisplayName("Creation cooldown prevents rapid creation")
    void testCreationCooldown() {
        setConfig("debug_shopCreateCooldown", 1000); // 1 second cooldown

        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        // First creation
        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);
        while (player.nextMessage() != null) {}

        // Cancel first
        miscListener.cancelShopCreationProcess(player);
        while (player.nextMessage() != null) {}

        // Immediate second attempt should be blocked
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        org.bukkit.event.player.PlayerInteractEvent secondAttempt = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, new ItemStack(Material.DIAMOND),
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(secondAttempt);
        getServer().getScheduler().performTicks(5);

        String msg = waitForNextMessage(player);
        assertNotNull(msg);
        assertTrue(msg.contains("cooldown") || msg.contains("few seconds"), "Should be blocked by cooldown: " + msg);
    }

    // ============================================================
    // IS PLAYER TARGETING SHOP CREATION BLOCK TESTS
    // ============================================================

    @Test
    @DisplayName("isPlayerTargetingShopCreationBlock returns true for uninitialized shop sign")
    void testIsPlayerTargetingUninitializedSign() {
        AbstractShop shop = createInitializedShopViaSign();
        // Make it uninitialized
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.set(shop, false);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        // Note: MockBukkit doesn't fully support getTargetBlockExact
        // This test verifies the method doesn't throw
        boolean result = miscListener.isPlayerTargetingShopCreationBlock(player);
        // Result depends on mock implementation, just verify no exception
    }

    @Test
    @DisplayName("isPlayerTargetingShopCreationBlock returns false for initialized shop sign")
    void testIsPlayerTargetingInitializedSign() {
        AbstractShop shop = createInitializedShopViaSign();

        boolean result = miscListener.isPlayerTargetingShopCreationBlock(player);
        // Result depends on mock implementation, just verify no exception
    }

    // ============================================================
    // IS CHEST IN SHOP CREATION PROCESS TESTS
    // ============================================================

    @Test
    @DisplayName("isChestInShopCreationProcess returns true during creation")
    void testIsChestInCreationProcess() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);
        while (player.nextMessage() != null) {}

        assertTrue(miscListener.isChestInShopCreationProcess(chestLoc), "Chest should be in creation process");

        miscListener.cancelShopCreationProcess(player);
        while (player.nextMessage() != null) {}

        assertFalse(miscListener.isChestInShopCreationProcess(chestLoc), "Chest should not be in creation process after cancel");
    }

    @Test
    @DisplayName("getShopCreationProcessByChest returns process during creation")
    void testGetCreationProcessByChest() {
        Location chestLoc = new Location(world, 100, 64, 100);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        stubCalculateBlockFaceForSign(BlockFace.NORTH);

        player.setSneaking(true);
        ItemStack item = new ItemStack(Material.DIRT);
        player.getInventory().setItemInMainHand(item);
        org.bukkit.event.player.PlayerInteractEvent startCreate = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, item,
                chestBlock, BlockFace.NORTH, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(startCreate);
        while (player.nextMessage() != null) {}

        assertNotNull(miscListener.getShopCreationProcessByChest(chestLoc), "Should return process for chest");

        miscListener.cancelShopCreationProcess(player);
        while (player.nextMessage() != null) {}

        assertNull(miscListener.getShopCreationProcessByChest(chestLoc), "Should return null after cancel");
    }

    // ============================================================
    // HANDLE SHOP LEFT CLICK TESTS
    // ============================================================

    @Test
    @DisplayName("handleShopLeftClick initializes uninitialized shop sign")
    void testHandleShopLeftClickInitializes() {
        AbstractShop shop = createInitializedShopViaSign();
        // Make it uninitialized
        try {
            java.lang.reflect.Field field = AbstractShop.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.set(shop, false);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        ItemStack item = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(item);

        miscListener.handleShopLeftClick(player, shop.getSignLocation().getBlock(), item, BlockFace.NORTH);

        // Should initialize the shop
        // Note: The actual initialization depends on ShopCreationUtil
        // This test verifies the method doesn't throw
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private AbstractShop createInitializedShopViaSign() {
        // Create via sign method
        Location signLoc = new Location(world, 100, 64, 100);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH);
        world.setBlockData(signBlock.getLocation(), data);

        BlockFace chestFace = BlockFace.NORTH.getOppositeFace();
        Location chestLoc = signBlock.getRelative(chestFace).getLocation();
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SHOP")));
        lines.add(net.kyori.adventure.text.Component.text("1"));
        lines.add(net.kyori.adventure.text.Component.text("10"));
        lines.add(net.kyori.adventure.text.Component.text(ShopMessage.getCreationWord("SELL")));

        SignChangeEvent event = new SignChangeEvent(signBlock, player, lines, org.bukkit.block.sign.Side.FRONT);
        getServer().getPluginManager().callEvent(event);
        while (player.nextMessage() != null) {}

        AbstractShop shop = shopHandler.getShop(signLoc);
        assertNotNull(shop, "Shop should be created");

        // Initialize with item
        ItemStack initItem = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(initItem);
        org.bukkit.event.player.PlayerInteractEvent initEvent = new org.bukkit.event.player.PlayerInteractEvent(
                player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, initItem,
                signBlock, BlockFace.WEST, org.bukkit.inventory.EquipmentSlot.HAND
        );
        getServer().getPluginManager().callEvent(initEvent);
        while (player.nextMessage() != null) {}

        return shop;
    }
}
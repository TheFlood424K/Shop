package com.snowgears.shop.testsupport;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Test helper for driving the real chest-based shop creation flow (PlayerInteractEvent + chat steps).
 * <p>
 * This intentionally mirrors {@code ShopCreationChestTest} but is configurable (e.g., creator can be non-op).
 */
public final class ShopCreationFlowTestUtil {

    private ShopCreationFlowTestUtil() {}

    public static AbstractShop createShopViaChestFlow(
            ServerMock server,
            Shop plugin,
            PlayerMock player,
            World world,
            Location chestLoc,
            ItemStack itemInHand,
            String shopTypeChat,
            int amountChat,
            String priceChat,
            boolean creatorIsOp
    ) {
        return createShopViaChestFlow(server, plugin, player, world, chestLoc, itemInHand,
                shopTypeChat, amountChat, priceChat, creatorIsOp, "COBBLESTONE", priceChat);
    }

    /**
     * Drives the chat-creation flow to completion for any shop type.
     *
     * @param barterItemChat material name of the item to barter for (BARTER shops only)
     * @param comboPriceChat what to type when a COMBO shop asks for the second (buy) price
     */
    public static AbstractShop createShopViaChestFlow(
            ServerMock server,
            Shop plugin,
            PlayerMock player,
            World world,
            Location chestLoc,
            ItemStack itemInHand,
            String shopTypeChat,
            int amountChat,
            String priceChat,
            boolean creatorIsOp,
            String barterItemChat,
            String comboPriceChat
    ) {
        player.setOp(creatorIsOp);

        // Allow everyone to create by default (tests can override this before calling)
        BaseMockBukkitTest.setConfig("allowCreateMethodChest", true);

        // Place chest with free space to the NORTH for sign placement
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        world.getBlockAt(chestLoc.clone().add(0, 0, -1)).setType(Material.AIR);
        BaseMockBukkitTest.stubCalculateBlockFaceForSign(BlockFace.NORTH);

        // Start creation by sneaking and left-clicking the chest with an item in hand
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(itemInHand);
        PlayerInteractEvent startCreate = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                itemInHand,
                chestBlock,
                BlockFace.NORTH,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(startCreate);
        server.getScheduler().performTicks(20);
        // Drain initial prompts (these calls also tick as needed inside waitForNextMessage)
        BaseMockBukkitTest.waitForNextMessage(player);
        BaseMockBukkitTest.waitForNextMessage(player);

        // Step 1: Type (chat handler runs via executeAsyncEvent().get(), so it's safe to send sequentially)
        BaseMockBukkitTest.sendChatMessage(player, shopTypeChat);
        BaseMockBukkitTest.waitForNextMessage(player);

        ShopType type = ShopType.valueOf(shopTypeChat.toUpperCase());

        // Step 2: Amount. For BARTER this is still the sell-item amount, and answering it is
        // what advances the process to ChatCreationStep.BARTER_ITEM.
        BaseMockBukkitTest.sendChatMessage(player, String.valueOf(amountChat));
        BaseMockBukkitTest.waitForNextMessage(player);

        if (type == ShopType.BARTER) {
            // BARTER picks the item to trade for by left-clicking the chest again while holding
            // it ("Now hit the chest again with the item you want to barter for"). This MUST
            // happen while the step is BARTER_ITEM: MiscListener#handleShopLeftClick only takes
            // the barter item on that step, and otherwise falls through and cancels the process.
            // Sneaking is irrelevant on this step — the branch returns before the sneaking check.
            ItemStack barterItem = new ItemStack(Material.valueOf(barterItemChat.toUpperCase()));
            player.setSneaking(true);
            player.getInventory().setItemInMainHand(barterItem);
            PlayerInteractEvent pickBarterItem = new PlayerInteractEvent(
                    player,
                    Action.LEFT_CLICK_BLOCK,
                    barterItem,
                    chestBlock,
                    BlockFace.NORTH,
                    EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(pickBarterItem);
            server.getScheduler().performTicks(5);
            BaseMockBukkitTest.waitForNextMessage(player);

            // Step 3: how many of the barter item to receive. BARTER asks no price question.
            BaseMockBukkitTest.sendChatMessage(player, priceChat);
            BaseMockBukkitTest.waitForNextMessage(player);
        }
        else {
            // Step 3: Price
            BaseMockBukkitTest.sendChatMessage(player, priceChat);
            BaseMockBukkitTest.waitForNextMessage(player);
        }

        // COMBO then asks what the shop should buy for, before the shop is created.
        if (type == ShopType.COMBO) {
            BaseMockBukkitTest.sendChatMessage(player, comboPriceChat);
            BaseMockBukkitTest.waitForNextMessage(player);
        }

        // Success/failure message often arrives after at least one tick due to scheduled creation task
        server.getScheduler().performTicks(2);

        // Wait for the Folia/Bukkit scheduled create+init pipeline to complete.
        // Important: the chat creation process is removed immediately after enqueueing the create task,
        // so we cannot use "process removed" as a completion signal.
        AbstractShop created = null;
        for (int i = 0; i < 200; i++) {
            server.getScheduler().performTicks(1);
            server.getScheduler().waitAsyncTasksFinished();
            created = plugin.getShopHandler().getShopByChest(chestBlock);
            if (created != null) {
                return created;
            }
        }
        return created;
    }
}
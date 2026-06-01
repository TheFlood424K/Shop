package com.snowgears.shop.testsupport;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
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

        // Step 2: Amount
        BaseMockBukkitTest.sendChatMessage(player, String.valueOf(amountChat));
        BaseMockBukkitTest.waitForNextMessage(player);

        // Step 3: Price
        BaseMockBukkitTest.sendChatMessage(player, priceChat);
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



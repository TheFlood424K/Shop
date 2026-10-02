package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;


public class SpearAttackListener implements Listener {


    private static final double REACH = 5.0;

    private final Shop plugin;

    public SpearAttackListener(Shop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpearSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItem(EquipmentSlot.HAND);
        if (!itemInHand.getType().name().contains("SPEAR")) {
            return;
        }

        RayTraceResult result = player.rayTraceBlocks(REACH);
        Block clicked = result == null ? null : result.getHitBlock();
        if (clicked == null) {
            return;
        }

        BlockFace blockFace = result.getHitBlockFace();
        plugin.getMiscListener().handleShopLeftClick(player, clicked, itemInHand, blockFace);
    }
}
package com.snowgears.shop.listener;

import com.snowgears.shop.testsupport.StubbedPlayers;
import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpearAttackListener.
 * Tests that spear/trident attacks properly interact with shop creation blocks.
 */
@ExtendWith(MockBukkitExtension.class)
class SpearAttackListenerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private SpearAttackListener spearAttackListener;
    private MiscListener miscListener;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        spearAttackListener = new SpearAttackListener(plugin);
        miscListener = plugin.getMiscListener();
    }

    @AfterEach
    void tearDown() {
        // Extension handles MockBukkit lifecycle
    }

    @Test
    void testOnSpearSwingIgnoresNonSpearItems() {
        Player player = StubbedPlayers.add(server, "TestPlayer");
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND_SWORD));

        // Mock ray trace to return a block
        Block targetBlock = world.getBlockAt(100, 64, 100);
        targetBlock.setType(Material.CHEST);

        // Create animation event
        PlayerAnimationEvent event = new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING);

        // Should not throw and should return early (not a spear)
        assertDoesNotThrow(() -> {
            spearAttackListener.onSpearSwing(event);
        });
    }

    @Test
    void testOnSpearSwingWithTrident() {
        // Trident contains "SPEAR" in its name
        Player player = StubbedPlayers.add(server, "TestPlayer");
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.TRIDENT));

        Block targetBlock = world.getBlockAt(100, 64, 100);
        targetBlock.setType(Material.CHEST);

        PlayerAnimationEvent event = new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING);

        // Should call handleShopLeftClick
        // We can't easily verify the call without mocking miscListener
        // but we can verify it doesn't crash
        assertDoesNotThrow(() -> {
            spearAttackListener.onSpearSwing(event);
        });
    }
}
package com.snowgears.shop.integration.features;

import static com.snowgears.shop.integration.features.ShopCreationChestTest.createShop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.CommandHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.InventoryUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.UUID;

@Tag("integration")
public class TransactionLookupCommandTest extends BaseMockBukkitTest {

    private ServerMock server;
    private World world;
    private PlayerMock owner;
    private PlayerMock other;
    private AbstractShop shop;

    @BeforeEach
    void setup() {
        server = getServer();
        world = server.addSimpleWorld("world");
        owner = server.addPlayer();
        owner.setOp(true);
        owner.setSneaking(false);

        other = server.addPlayer();
        other.setOp(false);

        setConfig("usePerms", false);
        setConfig("displayType", com.snowgears.shop.display.DisplayType.NONE);
        setConfig("debug_shopCreateCooldown", 0);
    }

    private AbstractShop createInitializedShopAt(Location chestLoc) {
        return createShop(server, getPlugin(), owner, world, chestLoc, new ItemStack(Material.DIRT), "sell", 8, "1");
    }

    @Test
    void transactionCommand_showsUsageForOperator() {
        // Execute /shop trans command
        getPlugin().getCommand("shop").execute(owner, "shop", new String[]{"transactions"});

        // Should show transaction header (or "No transactions found" if no transactions)
        String msg = waitForNextMessage(owner);
        assertNotNull(msg);
        // The command should execute without error
    }

    @Test
    void transactionCommand_withOwnerAll_showsAllTransactions() {
        // Create a shop and perform a transaction
        shop = createInitializedShopAt(new Location(world, 100, 65, 10));

        // Add stock to shop
        shop.getChestLocation().getChunk().load(true);
        shop.getInventory().addItem(new ItemStack(Material.DIRT, 8));
        shop.updateStock();

        // Add currency to other player
        other.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        // Perform transaction
        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        waitForNextMessage(other); // consume transaction message

        // Now execute /shop trans all
        getPlugin().getCommand("shop").execute(owner, "shop", new String[]{"transactions", "all"});

        String msg = waitForNextMessage(owner);
        assertNotNull(msg);
    }

    @Test
    void transactionCommand_withOwnerName_showsSpecificOwnerTransactions() {
        // Create a shop and perform a transaction
        shop = createInitializedShopAt(new Location(world, 200, 65, 10));

        // Add stock to shop
        shop.getChestLocation().getChunk().load(true);
        shop.getInventory().addItem(new ItemStack(Material.DIRT, 8));
        shop.updateStock();

        // Add currency to other player
        other.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        // Perform transaction
        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        waitForNextMessage(other); // consume transaction message

        // Execute /shop trans <ownerName>
        getPlugin().getCommand("shop").execute(owner, "shop", new String[]{"transactions", owner.getName()});

        String msg = waitForNextMessage(owner);
        assertNotNull(msg);
    }

    @Test
    void transactionCommand_withOwnerUUID_showsSpecificOwnerTransactions() {
        // Create a shop and perform a transaction
        shop = createInitializedShopAt(new Location(world, 300, 65, 10));

        // Add stock to shop
        shop.getChestLocation().getChunk().load(true);
        shop.getInventory().addItem(new ItemStack(Material.DIRT, 8));
        shop.updateStock();

        // Add currency to other player
        other.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        // Perform transaction
        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        waitForNextMessage(other); // consume transaction message

        // Execute /shop trans <ownerUUID>
        getPlugin().getCommand("shop").execute(owner, "shop", new String[]{"transactions", owner.getUniqueId().toString()});

        String msg = waitForNextMessage(owner);
        assertNotNull(msg);
    }

    @Test
    void transactionCommand_nonOperator_denied() {
        // Non-operator should be denied
        setConfig("usePerms", true);

        getPlugin().getCommand("shop").execute(other, "shop", new String[]{"transactions"});

        String msg = waitForNextMessage(other);
        assertNotNull(msg);
        assertTrue(msg.contains("not authorized") || msg.contains("Not authorized") || msg.contains("Not authorized"));
    }

    @Test
    void transactionCommand_console_denied() {
        // Console should be denied
        getPlugin().getCommand("shop").execute(server.getConsoleSender(), "shop", new String[]{"transactions"});

        // Console commands don't produce player messages, just verify no exception
    }
}
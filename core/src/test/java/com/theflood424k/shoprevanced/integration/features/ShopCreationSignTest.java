package com.theflood424k.shoprevanced.integration.features;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.util.List;
import java.util.ArrayList;
import org.bukkit.entity.Player;
import org.bukkit.block.sign.Side;
import org.bukkit.block.BlockFace;
import net.kyori.adventure.text.Component;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class ShopCreationSignTest extends BaseMockBukkitTest {

    @Test
    void createUsingSign() {
        ServerMock server = getServer();
        ShopRevanced plugin = getPlugin();

        // Arrange: world & player
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        // Ensure sign creation enabled; display is disabled and economy mocked by base
        setConfig("allowCreateMethodSign", true);

        // Place the wall sign and chest behind it
        Location signLoc = new Location(world, 5, 65, 5);
        Block signBlock = world.getBlockAt(signLoc);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.NORTH); // chest on WEST
        world.setBlockData(signBlock.getLocation(), data); // Setting the sign direction does not seem to be working with MockBukkit, the default is NORTH though.
        Location chestLoc = signBlock.getRelative(BlockFace.NORTH.getOppositeFace()).getLocation();
        
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);

        // Fire SignChangeEvent with proper lines
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(ShopRevancedMessage.getCreationWord("ShopRevanced")));
        lines.add(Component.text("1"));      // amount
        lines.add(Component.text("10"));     // price
        lines.add(Component.text(ShopRevancedMessage.getCreationWord("SELL")));
        SignChangeEvent signEvent = new SignChangeEvent(signBlock, (Player) player, lines, Side.FRONT);
        server.getPluginManager().callEvent(signEvent);

        // Assert player is sent dialog to set up ShopRevanced
        assertEquals("§6Now just hit the sign with the item you want to sell to other players!", waitForNextMessage(player));
        assertNull(player.nextMessage());

        // Assert ShopRevanced created and registered
        AbstractShop shop = plugin.getShopRevancedHandler().getShop(signLoc);
        assertNotNull(shop, "ShopRevanced should be created via sign event");
        assertFalse(shop.isInitialized(), "ShopRevanced should not be initialized yet");

        // Initialize via left-click on sign with an item in hand
        ItemStack toInit = new ItemStack(Material.DIAMOND);
        player.getInventory().setItemInMainHand(toInit);
        PlayerInteractEvent initEvent = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                toInit,
                signBlock,
                BlockFace.WEST,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(initEvent);

        assertTrue(waitForNextMessage(player).contains("§eYou have created a shop that sells §6"));
        assertNull(player.nextMessage());

        assertTrue(shop.isInitialized(), "ShopRevanced should be initialized after left-click with item");
    }
}








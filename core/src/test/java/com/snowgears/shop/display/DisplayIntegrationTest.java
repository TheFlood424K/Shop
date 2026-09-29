package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for display functionality.
 */
@ExtendWith(MockBukkitExtension.class)
class DisplayIntegrationTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject
    private World world;

    private Shop plugin;
    private ShopHandler shopHandler;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.loadSimple(Shop.class);
        shopHandler = plugin.getShopHandler();
    }

    @AfterEach
    void tearDown() {
        // Extension handles cleanup
    }

    @Test
    void testDisplayCreation() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        // Display should be created automatically
        assertNotNull(shop.getDisplay());
    }

    @Test
    void testDisplayTypeEnum() {
        // Test DisplayType enum values
        for (DisplayType type : DisplayType.values()) {
            assertNotNull(type.name());
        }
    }

    @Test
    void testDisplayTagOptionEnum() {
        // Test DisplayTagOption enum values
        for (DisplayTagOption option : DisplayTagOption.values()) {
            assertNotNull(option.name());
        }
    }

    @Test
    void testPluginDisplayTagOption() {
        // Test plugin display tag option getter
        DisplayTagOption option = plugin.getDisplayTagOption();
        assertNotNull(option);
    }

    @Test
    void testUpdateSign() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();
        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        // Should not throw even without chest
        assertDoesNotThrow(() -> shop.updateSign());
    }
}
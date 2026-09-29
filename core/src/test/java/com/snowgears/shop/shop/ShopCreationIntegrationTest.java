package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.util.ShopLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
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
 * Integration tests for shop creation, types, and basic functionality.
 */
@ExtendWith(MockBukkitExtension.class)
class ShopCreationIntegrationTest {

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
    void testCreateSellShop() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertEquals(ShopType.SELL, shop.getType());
        assertEquals(owner, shop.getOwnerUUID());
        assertEquals(10.0, shop.getPrice());
        assertEquals(1, shop.getAmount());
        assertEquals(Material.DIAMOND, shop.getItemStack().getType());
    }

    @Test
    void testCreateBuyShop() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        BuyShop shop = new BuyShop(signLoc, owner, 5.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.GOLD_INGOT));

        assertEquals(ShopType.BUY, shop.getType());
        assertEquals(owner, shop.getOwnerUUID());
        assertEquals(5.0, shop.getPrice());
        assertEquals(Material.GOLD_INGOT, shop.getItemStack().getType());
    }

    @Test
    void testCreateComboShop() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        ComboShop shop = new ComboShop(signLoc, owner, 10.0, 5.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.IRON_INGOT));

        assertEquals(ShopType.COMBO, shop.getType());
        assertEquals(10.0, shop.getPriceBuy());
        assertEquals(5.0, shop.getPriceSell());
    }

    @Test
    void testCreateBarterShop() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        BarterShop shop = new BarterShop(signLoc, owner, 1.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        assertEquals(ShopType.BARTER, shop.getType());
        assertEquals(Material.DIAMOND, shop.getItemStack().getType());
    }

    @Test
    void testShopRegistrationAndLookup() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);

        assertEquals(1, shopHandler.getNumberOfShops());
        assertSame(shop, shopHandler.getShop(signLoc));
    }

    @Test
    void testMultipleShopsSameOwner() {
        UUID owner = UUID.randomUUID();

        SellShop shop1 = new SellShop(new Location(world, 100, 64, 100), owner, 10.0, 1, false, BlockFace.NORTH);
        shop1.setItemStack(new ItemStack(Material.DIAMOND));

        SellShop shop2 = new SellShop(new Location(world, 200, 64, 200), owner, 20.0, 1, false, BlockFace.NORTH);
        shop2.setItemStack(new ItemStack(Material.GOLD_INGOT));

        shopHandler.addShop(shop1);
        shopHandler.addShop(shop2);

        assertEquals(2, shopHandler.getNumberOfShops());
        assertEquals(2, shopHandler.getShops(owner).size());
    }

    @Test
    void testShopRemoval() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        SellShop shop = new SellShop(signLoc, owner, 10.0, 1, false, BlockFace.NORTH);
        shop.setItemStack(new ItemStack(Material.DIAMOND));

        shopHandler.addShop(shop);
        assertEquals(1, shopHandler.getNumberOfShops());

        shopHandler.removeShop(shop, false);
        assertEquals(0, shopHandler.getNumberOfShops());
        assertNull(shopHandler.getShop(signLoc));
    }

    @Test
    void testAbstractShopCreateFactory() {
        Location signLoc = new Location(world, 100, 64, 100);
        UUID owner = UUID.randomUUID();

        AbstractShop sellShop = AbstractShop.create(signLoc, owner, 10.0, 0.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        assertEquals(ShopType.SELL, sellShop.getType());

        AbstractShop buyShop = AbstractShop.create(signLoc, owner, 10.0, 5.0, 1, false, ShopType.BUY, BlockFace.NORTH);
        assertEquals(ShopType.BUY, buyShop.getType());

        AbstractShop comboShop = AbstractShop.create(signLoc, owner, 10.0, 5.0, 1, false, ShopType.COMBO, BlockFace.NORTH);
        assertEquals(ShopType.COMBO, comboShop.getType());

        AbstractShop barterShop = AbstractShop.create(signLoc, owner, 10.0, 0.0, 1, false, ShopType.BARTER, BlockFace.NORTH);
        assertEquals(ShopType.BARTER, barterShop.getType());
    }
}
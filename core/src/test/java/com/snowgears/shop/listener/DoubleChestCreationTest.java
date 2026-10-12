package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ShopCreationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.*;

@Tag("unit")
class DoubleChestCreationTest {
    private ShopHandler handler;
    private ShopCreationUtil creation;
    private MiscListener listener;
    private Player player;
    private Block first;
    private Block second;

    /** Builds a listener fixture whose two chest blocks share one double-chest inventory. */
    @BeforeEach
    void setUp() {
        Shop plugin = mock(Shop.class);
        handler = mock(ShopHandler.class);
        creation = mock(ShopCreationUtil.class);
        player = mock(Player.class);
        listener = new MiscListener(plugin);
        when(plugin.getShopHandler()).thenReturn(handler);
        when(plugin.getShopCreationUtil()).thenReturn(creation);
        when(plugin.getAllowCreationMethodChest()).thenReturn(true);
        first = mock(Block.class);
        second = mock(Block.class);
        Chest left = mock(Chest.class);
        Chest right = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        DoubleChest combined = mock(DoubleChest.class);
        when(first.getState()).thenReturn(left);
        when(second.getState()).thenReturn(right);
        when(left.getInventory()).thenReturn(inventory);
        when(right.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(combined);
        when(combined.getLeftSide()).thenReturn(left);
        when(combined.getRightSide()).thenReturn(right);
        when(left.getBlock()).thenReturn(first);
        when(right.getBlock()).thenReturn(second);
        when(handler.isChest(first)).thenReturn(true);
        when(handler.isChest(second)).thenReturn(true);
    }

    /**
     * Verifies that clicking either inventory half reaches validation with the same primary chest.
     *
     * @param firstX the first chest's X coordinate
     * @param firstZ the first chest's Z coordinate
     * @param secondX the second chest's X coordinate
     * @param secondZ the second chest's Z coordinate
     * @param clickSecond whether to click the second chest
     */
    @ParameterizedTest
    @CsvSource({"10, 10, 11, 10, false", "10, 10, 11, 10, true",
            "10, 10, 10, 11, false", "10, 10, 10, 11, true",
            "-2, -1, -1, -1, true", "-1, -2, -1, -1, true"})
    void eitherHalfUsesTheSamePrimaryChest(int firstX, int firstZ, int secondX, int secondZ,
                                          boolean clickSecond) {
        when(first.getLocation()).thenReturn(new Location(null, firstX, 65, firstZ));
        when(second.getLocation()).thenReturn(new Location(null, secondX, 65, secondZ));

        // Stop at the public validation boundary; no chat process or display is needed.
        listener.handleShopLeftClick(player, clickSecond ? second : first, null, BlockFace.NORTH);

        verify(creation).shopCanBeCreated(player, first);
        verifyNoMoreInteractions(creation);
        verify(handler).getShopByChest(first);
        verify(handler).getShopByChest(second);
    }

    /**
     * Verifies that an existing shop on the mate blocks creation before container validation.
     *
     * @param clickSecond whether to click the second chest
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void occupiedMatePreventsCreationFromEitherHalf(boolean clickSecond) {
        Block clicked = clickSecond ? second : first;
        Block mate = clickSecond ? first : second;
        when(handler.getShopByChest(mate)).thenReturn(mock(AbstractShop.class));

        listener.handleShopLeftClick(player, clicked, null, BlockFace.NORTH);

        verify(handler).getShopByChest(clicked);
        verify(handler).getShopByChest(mate);
        verifyNoInteractions(creation);
    }
}

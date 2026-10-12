package com.snowgears.shop.shop;

import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@Tag("unit")
class DisplayCyclePermissionTest extends BaseMockBukkitTest {
    /**
     * Verifies that click results and display mutations agree with ownership and permission rules.
     *
     * @param usePerms whether permission checks are enabled
     * @param owner whether the player has the shop owner's UUID
     * @param op whether the player is a server operator
     * @param operatorPermission whether the player has {@code shop.operator}
     * @param displayPermission whether the player has {@code shop.setdisplay}
     * @param allowed whether the display should change
     */
    @ParameterizedTest
    @CsvSource({
            "false, false, false, false, false, false",
            "false, false, false, true, true, false",
            "false, false, true, false, false, true",
            "true, false, true, false, true, false",
            "true, false, false, true, false, true",
            "true, false, false, false, true, false",
            "false, true, false, false, false, true",
            "true, true, false, false, false, false",
            "true, true, true, true, false, false",
            "true, true, false, false, true, true"
    })
    void displayChangesOnlyWithTheRequiredAccess(boolean usePerms, boolean owner, boolean op,
                                                boolean operatorPermission, boolean displayPermission,
                                                boolean allowed) {
        setConfig("usePerms", usePerms);
        UUID ownerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner ? ownerId : UUID.randomUUID());
        when(player.isOp()).thenReturn(op);
        when(player.hasPermission("shop.operator")).thenReturn(operatorPermission);
        when(player.hasPermission("shop.setdisplay")).thenReturn(displayPermission);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        SellShop shop = spy(new SellShop(new Location(getServer().addSimpleWorld("world"), 10, 65, 10),
                ownerId, 10, 1, false, BlockFace.NORTH));
        AbstractDisplay display = mock(AbstractDisplay.class);
        doReturn(display).when(shop).getDisplay();

        assertEquals(allowed, shop.executeClickAction(event, ShopClickType.SHIFT_RIGHT_CLICK_CHEST));
        if (allowed) {
            verify(display).cycleType(player);
            verifyNoMoreInteractions(display);
        } else {
            verifyNoInteractions(display);
        }
    }
}

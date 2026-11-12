package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** No-op display implementation used when Paper displays are disabled. */
public class DisplayDisabled extends AbstractDisplay {

    public DisplayDisabled(Location shopSignLocation) {
        super(shopSignLocation);
        if (!Shop.loggedDisplayDisabledWarning) {
            Shop.getPlugin().getLogger().warning("[DisplayDisabled] Display is disabled! No display features will be used.");
            Shop.getPlugin().getLogger().warning("[DisplayDisabled] Shop will attempt to function without display features.");
            Shop.loggedDisplayDisabledWarning = true;
        }
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        // Intentionally disabled.
    }
}

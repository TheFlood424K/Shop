package com.theflood424k.shoprevanced.display;

import com.theflood424k.shoprevanced.ShopRevanced;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** No-op display implementation used when Paper displays are disabled. */
public class DisplayDisabled extends AbstractDisplay {

    public DisplayDisabled(Location shopSignLocation) {
        super(shopSignLocation);
        if (!ShopRevanced.loggedDisplayDisabledWarning) {
            ShopRevanced.getPlugin().getLogger().warning("[DisplayDisabled] Display is disabled! No display features will be used.");
            ShopRevanced.getPlugin().getLogger().warning("[DisplayDisabled] ShopRevanced will attempt to function without display features.");
            ShopRevanced.loggedDisplayDisabledWarning = true;
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



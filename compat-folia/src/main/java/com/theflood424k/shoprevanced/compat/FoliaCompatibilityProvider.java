package com.theflood424k.shoprevanced.compat;

import org.bukkit.Server;

/**
 * Detects Folia's regionized server API without requiring Folia at compile time.
 */
public final class FoliaCompatibilityProvider implements CompatibilityProvider {

    @Override
    public String id() {
        return "folia";
    }

    @Override
    public boolean isAvailable(Server server) {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer",
                    false, server.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    @Override
    public String describe(Server server) {
        return "regionized scheduler API detected";
    }
}

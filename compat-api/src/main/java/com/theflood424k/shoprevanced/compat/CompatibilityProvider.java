package com.theflood424k.shoprevanced.compat;

import org.bukkit.Server;

/**
 * Optional server-platform compatibility provider.
 */
public interface CompatibilityProvider {

    /**
     * @return stable provider identifier
     */
    String id();

    /**
     * @param server server implementation
     * @return whether this provider applies to the server
     */
    boolean isAvailable(Server server);

    /**
     * @param server server implementation
     * @return concise runtime capability description
     */
    String describe(Server server);
}

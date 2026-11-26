package com.theflood424k.shoprevanced.compat;

import org.bukkit.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Loads optional platform compatibility providers without coupling the base
 * Paper plugin to a server-specific implementation.
 */
public final class CompatibilityManager {

    private CompatibilityManager() {
    }

    /**
     * Finds providers that apply to the current server.
     *
     * @param server server implementation
     * @return detected providers
     */
    public static List<CompatibilityProvider> detect(Server server) {
        List<CompatibilityProvider> detected = new ArrayList<>();
        ServiceLoader<CompatibilityProvider> providers = ServiceLoader.load(CompatibilityProvider.class);
        try {
            for (CompatibilityProvider provider : providers) {
                if (provider.isAvailable(server)) {
                    detected.add(provider);
                }
            }
        } catch (ServiceConfigurationError error) {
            throw new IllegalStateException("Unable to load Shop compatibility providers", error);
        }
        return List.copyOf(detected);
    }
}

package com.theflood424k.shoprevanced.compat;

import org.bukkit.Server;

import java.lang.reflect.Method;

/**
 * Detects DivineMC's optional parallel-ticking API.
 */
public final class DivineMcCompatibilityProvider implements CompatibilityProvider {

    private static final String[] API_METHODS = {
            "isParallelWorldTickingEnabled",
            "isRegionizedChunkTickingEnabled",
            "isPluginListenerSerializationEnabled"
    };

    @Override
    public String id() {
        return "divinemc";
    }

    @Override
    public boolean isAvailable(Server server) {
        for (String methodName : API_METHODS) {
            if (findMethod(server, methodName) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe(Server server) {
        return "parallelWorldTicking=" + invokeBoolean(server, "isParallelWorldTickingEnabled")
                + ", regionizedChunkTicking=" + invokeBoolean(server, "isRegionizedChunkTickingEnabled")
                + ", listenerSerialization=" + invokeBoolean(server, "isPluginListenerSerializationEnabled");
    }

    private static Method findMethod(Server server, String name) {
        try {
            return server.getClass().getMethod(name);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static boolean invokeBoolean(Server server, String name) {
        Method method = findMethod(server, name);
        if (method == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(server);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return false;
        }
    }
}

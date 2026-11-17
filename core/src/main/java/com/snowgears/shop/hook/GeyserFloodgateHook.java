package com.snowgears.shop.hook;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import com.snowgears.shop.Shop;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

public class GeyserFloodgateHook implements Listener {

    private Shop plugin;

    public GeyserFloodgateHook(Shop instance) {
        plugin = instance;
    }

    /**
     * Checks whether a player is a Bedrock player connecting via GeyserMC.
     * Returns false if Geyser API is not available or the player is not a Bedrock player.
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        var api = GeyserApi.api();
        return api != null && api.isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Checks whether a player is a Floodgate-verified Bedrock player.
     * Floodgate allows Bedrock players to join Java servers without a Java edition account.
     */
    public boolean isFloodgatePlayer(Player player) {
        if (player == null) {
            return false;
        }
        var api = FloodgateApi.getInstance();
        return api != null && api.isFloodgatePlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isBedrockPlayer(player)) {
            if (isFloodgatePlayer(player)) {
                plugin.getLogger().notice("Bedrock player " + player.getName() + " joined via Floodgate");
            } else {
                plugin.getLogger().notice("Bedrock player " + player.getName() + " joined via GeyserMC");
            }
        }
    }
}

package com.snowgears.shop.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Production {@link DiscordDmSender} that talks to DiscordSRV via reflection.
 * This keeps shop-core free of a hard compile dependency on DiscordSRV: if the
 * plugin is not installed (or its API surface differs), all methods are safe
 * no-ops and a warning is logged once.
 */
public class DiscordSRVDmSender implements DiscordDmSender {

    private static final String PLUGIN_NAME = "DiscordSRV";

    private final Object api;
    private final boolean available;

    public DiscordSRVDmSender() {
        Object resolvedApi = null;
        boolean resolvedAvailable = false;
        try {
            var plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin != null) {
                var getApi = plugin.getClass().getMethod("getApi");
                resolvedApi = getApi.invoke(plugin);
                resolvedAvailable = resolvedApi != null;
            }
        } catch (Exception e) {
            // DiscordSRV not present or unexpected API surface; DMs simply won't send.
            Bukkit.getLogger().warning("[Shop] DiscordSRV DM integration unavailable: " + e.getMessage());
        }
        this.api = resolvedApi;
        this.available = resolvedAvailable;
    }

    @Override
    public void sendDirectMessageIfLinked(Player player, String message) {
        if (!available || player == null || api == null || message == null) {
            return;
        }
        try {
            // DiscordSRVApi#getDiscordUser(Player) -> DiscordUser (null if not linked)
            var getDiscordUser = api.getClass().getMethod("getDiscordUser", Player.class);
            var discordUser = getDiscordUser.invoke(api, player);
            if (discordUser == null) {
                return;
            }

            // DiscordUser#getUser() -> net.dv8tion.jda.api.entities.User
            var getUser = discordUser.getClass().getMethod("getUser");
            var jdaUser = getUser.invoke(discordUser);
            if (jdaUser == null) {
                return;
            }

            // User#openPrivateChannel() -> CompletableFuture<PrivateChannel>
            var openPm = jdaUser.getClass().getMethod("openPrivateChannel");
            var future = openPm.invoke(jdaUser);
            var privateChannel = ((java.util.concurrent.CompletableFuture<?>) future).join();

            // PrivateChannel#sendMessage(CharSequence) -> RestAction<Message>, then complete
            var sendMessage = privateChannel.getClass().getMethod("sendMessage", CharSequence.class);
            var restAction = sendMessage.invoke(privateChannel, message);
            var complete = restAction.getClass().getMethod("complete");
            complete.invoke(restAction);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Shop] Failed to send Discord DM to " + player.getName() + ": " + e.getMessage());
        }
    }
}

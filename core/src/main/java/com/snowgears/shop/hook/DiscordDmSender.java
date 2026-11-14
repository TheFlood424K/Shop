package com.snowgears.shop.hook;

import org.bukkit.entity.Player;

/**
 * Sends a direct message to a player on Discord if their Minecraft account is
 * linked through DiscordSRV. Implementations are expected to be no-ops when
 * DiscordSRV is not installed or the player is not linked, so production can
 * safely wire one up unconditionally.
 */
public interface DiscordDmSender {

    /**
     * Sends {@code message} as a Discord DM to {@code player} if their account
     * is linked through DiscordSRV. Does nothing otherwise.
     */
    void sendDirectMessageIfLinked(Player player, String message);
}

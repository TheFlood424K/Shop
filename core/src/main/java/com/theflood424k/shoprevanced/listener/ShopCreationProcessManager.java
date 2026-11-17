package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.AbstractDisplay;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.ShopRevancedCreationProcess;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of in-progress shop creation processes for players.
 * <p>
 * Extracts the creation-process tracking logic from {@code MiscListener} into a
 * dedicated, single-responsibility class. This makes the creation workflow easier
 * to understand, test, and maintain independently of block-event handling.
 */
public class ShopCreationProcessManager {

    private final ShopRevanced plugin;
    private final ConcurrentHashMap<UUID, ShopRevancedCreationProcess> playerChatCreationSteps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastChatCreation = new ConcurrentHashMap<>();

    public ShopCreationProcessManager(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the active creation process for the given player, or {@code null} if none exists.
     */
    public ShopRevancedCreationProcess getShopRevancedCreationProcess(Player player) {
        return playerChatCreationSteps.get(player.getUniqueId());
    }

    /**
     * Stores a creation process for the given player.
     */
    public void putShopRevancedCreationProcess(Player player, ShopRevancedCreationProcess process) {
        playerChatCreationSteps.put(player.getUniqueId(), process);
    }

    /**
     * Removes the creation process for the given player.
     */
    public void removeShopRevancedCreationProcess(Player player) {
        playerChatCreationSteps.remove(player.getUniqueId());
    }

    /**
     * Cancels the active creation process for the given player, cleaning up any
     * display entities and removing the player from creative selection.
     */
    public void cancelShopRevancedCreationProcess(Player player) {
        ShopRevancedCreationProcess process = getShopRevancedCreationProcess(player);
        if (process != null) {
            process.display.removeDisplayEntities(player, true);
            playerChatCreationSteps.remove(player.getUniqueId());
            // Send message that the creation was cancelled
            com.theflood424k.shoprevanced.util.ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
        }

        // Remove player from creative selection if they are in it!
        // The Bukkit API can only change player game modes in a sync task, not an async task
        plugin.getFoliaLib().getScheduler().runLater(() -> {
            plugin.getCreativeSelectionListener().removePlayerFromCreativeSelection(player);
        }, 1);
    }

    /**
     * Returns {@code true} if a chest at the given location is part of an active creation process.
     */
    public boolean isChestInShopRevancedCreationProcess(Location location) {
        return getShopRevancedCreationProcessByChest(location) != null;
    }

    /**
     * Returns the creation process associated with the given chest location, or {@code null}.
     */
    public ShopRevancedCreationProcess getShopRevancedCreationProcessByChest(Location location) {
        for (ShopRevancedCreationProcess process : playerChatCreationSteps.values()) {
            if (process.getClickedChest().getLocation().equals(location)) {
                return process;
            }
        }
        return null;
    }

    /**
     * Records that a creation process was last created for the given player at the current time.
     */
    public void recordLastChatCreation(Player player) {
        lastChatCreation.put(player.getUniqueId(), new Date().getTime());
    }

    /**
     * Returns the timestamp of the last chat creation for the given player, or {@code null}.
     */
    public Long getLastChatCreation(Player player) {
        return lastChatCreation.get(player.getUniqueId());
    }
}

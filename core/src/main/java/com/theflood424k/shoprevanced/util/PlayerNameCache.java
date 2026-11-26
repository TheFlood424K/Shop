package com.theflood424k.shoprevanced.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import com.theflood424k.shoprevanced.ShopRevanced;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight cache for player names to avoid expensive OfflinePlayer.getName() calls.
 * Thread-safe with lazy loading during initial cache build phase.
 */
public class PlayerNameCache {
    
    private static final ConcurrentHashMap<UUID, String> cache = new ConcurrentHashMap<>();
    private static final String CACHE_FILENAME = "Data/playerNameCache.yml";
    
    /**
     * Initialize cache on startup - checks if cache file exists
     */
    public static void initialize() {
        File cacheFile = new File(ShopRevanced.getPlugin().getDataFolder(), CACHE_FILENAME);
        
        if (cacheFile.exists()) {
            loadFromFile(cacheFile);
        }
    }
    
    /**
     * Gets a player name from cache, with lazy loading during initial build.
     * Never blocks on OfflinePlayer.getName() (which can hit disk/Mojang and stall
     * the calling thread). Returns a placeholder on a cache miss; the real name is
     * filled in later via {@link #cacheName(UUID, String)} (e.g. on player join).
     *
     * @param uuid Player UUID
     * @return Cached player name, or a placeholder if not yet cached
     */
    public static String getName(UUID uuid) {
        String cachedName = cache.get(uuid);
        if (cachedName != null) {
            return cachedName;
        }

            // Cache miss: seed the cache with a placeholder first so a recursive
                    // OfflinePlayer.getName() loop (observed on 1.21.5) cannot re-enter this
                    // method indefinitely, then attempt a one-time lazy load from the
                    // OfflinePlayer. The real name overwrites the placeholder on success.
        String shortId = uuid.toString();
                    String unknownPlayerString = "Unknown Player (" + shortId.substring(0, 3) + "..." + shortId.substring(shortId.length() - 3) + ")";
                    cache.put(uuid, unknownPlayerString);

                    try {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        if (offlinePlayer.hasPlayedBefore()) {
                            String name = offlinePlayer.getName();
                            if (name != null && !name.trim().isEmpty()) {
                                cache.put(uuid, name);
                                return name;
                }
                        } else {
                            ShopRevanced.getPlugin().getLogger().warning(
                                    "Player " + uuid + " has not played on this server and/or their player data file does not exist! Unable to load the player name from OfflinePlayer!");
            }
                    } catch (Exception e) {
                        ShopRevanced.getPlugin().getLogger().warning("Error while loading player name for " + uuid + " from OfflinePlayer! " + e.getMessage());
                    }

                    return unknownPlayerString;
    }
    
    /**
     * Caches a player name and switches off initial build mode
     * @param uuid Player UUID  
     * @param name Player name
     */
    public static void cacheName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.trim().isEmpty()) {
            cache.put(uuid, name);
        }
    }
    
    /**
     * Removes a player from cache
     * @param uuid Player UUID
     */
    public static void removeName(UUID uuid) {
        cache.remove(uuid);
    }
    
    /**
     * Saves cache to file
     * @param dataFolder Plugin data folder
     */
    public static void saveToFile() {
        File cacheFile = new File(ShopRevanced.getPlugin().getDataFolder(), CACHE_FILENAME);
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (ConcurrentHashMap.Entry<UUID, String> entry : cache.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
            config.save(cacheFile);
        } catch (IOException e) {
            ShopRevanced.getPlugin().getLogger().warning("Error while saving player name cache to file! " + e.getMessage());
        }
    }
    
    /**
     * Loads cache from file
     */
    private static void loadFromFile(File cacheFile) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(cacheFile);
            for (String key : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String name = config.getString(key);
                    if (name != null) {
                        cache.put(uuid, name);
                    }
                } catch (IllegalArgumentException e) {
                    // Skip invalid UUID entries
                }
            }
        } catch (Exception e) {
            ShopRevanced.getPlugin().getLogger().warning("Error while loading player name cache from file! " + e.getMessage());
        }
    }
    
    /**
     * Gets current cache size for monitoring
     * @return Number of cached entries
     */
    public static int getCacheSize() {
        return cache.size();
    }
} 


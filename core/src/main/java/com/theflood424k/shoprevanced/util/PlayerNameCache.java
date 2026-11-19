package com.theflood424k.shoprevanced.util;

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

        // Cache miss: return a placeholder immediately instead of blocking on
        // OfflinePlayer.getName(). The name will be populated on next cacheName() call.
        String shortId = uuid.toString();
        return "Unknown Player (" + shortId.substring(0, 3) + "..." + shortId.substring(shortId.length() - 3) + ")";
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


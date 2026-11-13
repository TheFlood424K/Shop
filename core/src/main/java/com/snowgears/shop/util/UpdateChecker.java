package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitRunnable;
import com.tcoded.folialib.wrapper.task.WrappedTask;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

//author: Benz56
//https://github.com/Benz56/Async-Update-Checker/blob/master/UpdateChecker.java
//Migrated from SpigotMC API to GitHub Releases API (stable) + GitHub Actions (nightly)

public class UpdateChecker {

    private final Shop plugin;
    private final String localPluginVersion;
    private String githubReleaseVersion;

    //Constants. Customize to your liking.
    private static final String REPO_OWNER = "TheFlood424K";
    private static final String REPO_NAME = "Shop";
    private static final String ERR_MSG = "&cShop update checker failed!";
    private static final String UPDATE_MSG = "&fNew Shop update &av{latestReleasedVersion} &fis available &f(your running: &ev{runningVersion}&f)\n&bhttps://github.com/TheFlood424K/Shop/releases";
    private static final String DEV_VERSION_MSG = "&l&e[Shop] &r&6Running development version &l&c{runningVersion}&r&6. If you encounter bugs, please roll back to the latest stable version.";
    //PermissionDefault.FALSE == OPs need the permission to be notified.
    //PermissionDefault.TRUE == all OPs are notified regardless of having the permission.
    private static final Permission UPDATE_PERM = new Permission("shop.update", PermissionDefault.FALSE);
    private static final long CHECK_INTERVAL = 12_000; //In ticks.
    private WrappedTask task;

    public UpdateChecker(final Shop plugin) {
        this.plugin = plugin;
        this.localPluginVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdate() {
        task = plugin.getFoliaLib().getScheduler().runTimer(new BukkitRunnable() {
            @Override
            public void run() {
                //The request is executed asynchronously as to not block the main thread.
                plugin.getFoliaLib().getScheduler().runAsync(task -> {
                    //Request the latest stable release from GitHub Releases API.
                    try {
                        final URL url = new URL("https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest");
                        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("User-Agent", "Shop-UpdateChecker");
                        githubReleaseVersion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                    } catch (final IOException e) {
                        Bukkit.getServer().getConsoleSender().sendMessage(LegacyComponentSerializer.legacy('&').deserialize(ERR_MSG));
                        e.printStackTrace();
                        cancelTask();
                        return;
                    }

                    // Parse the tag_name from the GitHub release JSON response.
                    String tagName = parseTagName(githubReleaseVersion);
                    if (tagName == null) {
                        Bukkit.getServer().getConsoleSender().sendMessage(LegacyComponentSerializer.legacy('&').deserialize(ERR_MSG));
                        cancelTask();
                        return;
                    }

                    // Strip leading 'v' from tag (e.g. "v1.12.0" -> "1.12.0").
                    String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

                    // Are we running the current version?
                    if (compareVersions(localPluginVersion, latestVersion) == 0) return;
                    // Are we running an older version?
                    if (compareVersions(localPluginVersion, latestVersion) < 0) {
                        Bukkit.getServer().getConsoleSender().sendMessage(LegacyComponentSerializer.legacy('&').deserialize(embedVersions(UPDATE_MSG, localPluginVersion, latestVersion)));

                        //Register the PlayerJoinEvent
                        plugin.getFoliaLib().getScheduler().runNextTick(nextTask -> Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler(priority = EventPriority.MONITOR)
                            public void onPlayerJoin(final PlayerJoinEvent event) {
                                final Player player = event.getPlayer();
                                if (!player.isOp()) return;
                                Component updateMsg = LegacyComponentSerializer.legacy('&').deserialize(embedVersions(UPDATE_MSG, localPluginVersion, latestVersion));
                                updateMsg = updateMsg.hoverEvent(HoverEvent.showText(Component.text("Click to open Shop plugin page")));
                                updateMsg = updateMsg.clickEvent(ClickEvent.openUrl("https://github.com/TheFlood424K/Shop/releases"));
                                player.sendMessage(updateMsg);
                            }
                        }, plugin));
                    }
                    /* RUNNING SHOP DEV VERSION */
                    if (compareVersions(localPluginVersion, latestVersion) > 0) {
                        Bukkit.getServer().getConsoleSender().sendMessage(LegacyComponentSerializer.legacy('&').deserialize(embedVersions(DEV_VERSION_MSG, localPluginVersion, latestVersion)));

                        //Register the PlayerJoinEvent
                        plugin.getFoliaLib().getScheduler().runNextTick(nextTask -> Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler(priority = EventPriority.MONITOR)
                            public void onPlayerJoin(final PlayerJoinEvent event) {
                                final Player player = event.getPlayer();
                                if (!player.isOp()) return;
                                player.sendMessage(LegacyComponentSerializer.legacy('&').deserialize(embedVersions(DEV_VERSION_MSG, localPluginVersion, latestVersion)));
                            }
                        }, plugin));
                    }

                    cancelTask(); //Cancel the runnable as an update has been found.
                });
            }
        }, 1, CHECK_INTERVAL);
    }

    public void cancelTask() {
        plugin.getFoliaLib().getScheduler().cancelTask(task);
    }

    private String embedVersions(String msg, String runningVersion, String latestReleasedVersion) {
        return msg.replace("{runningVersion}", runningVersion).replace("{latestReleasedVersion}", latestReleasedVersion);
    }

    /**
     * Extracts the value of the "tag_name" field from a GitHub release JSON response
     * using simple string scanning (no JSON library dependency).
     */
    private String parseTagName(String jsonResponse) {
        try {
            int tagIdx = jsonResponse.indexOf("\"tag_name\"");
            if (tagIdx == -1) return null;
            int colonIdx = jsonResponse.indexOf(":", tagIdx);
            if (colonIdx == -1) return null;
            int quoteIdx = jsonResponse.indexOf("\"", colonIdx + 1);
            if (quoteIdx == -1) return null;
            int endQuoteIdx = jsonResponse.indexOf("\"", quoteIdx + 1);
            if (endQuoteIdx == -1) return null;
            return jsonResponse.substring(quoteIdx + 1, endQuoteIdx);
        } catch (Exception e) {
            return null;
        }
    }

    public int compareVersions(String localVersion, String latestVersion) {
        // Split versions into base version and tag
        String[] v1Parts = localVersion.split("-", 2);
        String[] v2Parts = latestVersion.split("-", 2);

        String v1Base = v1Parts[0];
        String v2Base = v2Parts[0];

        String v1Tag = v1Parts.length > 1 ? v1Parts[1] : "";
        String v2Tag = v2Parts.length > 1 ? v2Parts[1] : "";

        // Split base versions into numeric parts
        String[] v1Nums = v1Base.split("\\.");
        String[] v2Nums = v2Base.split("\\.");

        int maxLength = Math.max(v1Nums.length, v2Nums.length);

        for (int i = 0; i < maxLength; i++) {
            int n1 = i < v1Nums.length ? Integer.parseInt(v1Nums[i]) : 0;
            int n2 = i < v2Nums.length ? Integer.parseInt(v2Nums[i]) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        // If we get here, all numeric parts are equal
        // For now, ignore the tags, since it is likely the "commit" tag on the local version
        // but on the github release we do not include the commit hash in the version string
        return 0;
    }
}
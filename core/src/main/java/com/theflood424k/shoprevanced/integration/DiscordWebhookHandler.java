package com.theflood424k.shoprevanced.integration;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerExchangeShopEvent;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Sends Discord notifications for shop transactions.
 * Listens for PlayerExchangeShopEvent and posts a rich embed to a configured
 * Discord webhook URL, and optionally DMs the shop owner when their account is
 * linked through DiscordSRV.
 */
public class DiscordWebhookHandler implements Listener {

    private final ShopRevanced plugin;
    private final WebhookSender sender;
    private final DiscordDmSender discordDmSender;

    private boolean enabled;
    private String webhookUrl;
    private boolean notifyOnSale;
    private boolean notifyOnBuy;
    private boolean notifyOnBarter;
    private boolean notifyOnGamble;
    private boolean notifyOnAdminShop;
    private boolean notifyShopOwnerDm;
    private String username;
    private String avatarUrl;

    public DiscordWebhookHandler(ShopRevanced instance) {
        this(instance, new HttpWebhookSender(), null);
    }

    DiscordWebhookHandler(ShopRevanced instance, WebhookSender sender) {
        this(instance, sender, null);
    }

    public DiscordWebhookHandler(ShopRevanced instance, WebhookSender sender, DiscordDmSender discordDmSender) {
        plugin = instance;
        this.sender = sender;
        this.discordDmSender = discordDmSender;
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        webhookUrl = plugin.getConfig().getString("discord.webhookUrl", "");
        notifyOnSale = plugin.getConfig().getBoolean("discord.notifyOnSale", true);
        notifyOnBuy = plugin.getConfig().getBoolean("discord.notifyOnBuy", true);
        notifyOnBarter = plugin.getConfig().getBoolean("discord.notifyOnBarter", true);
        notifyOnGamble = plugin.getConfig().getBoolean("discord.notifyOnGamble", true);
        notifyOnAdminShop = plugin.getConfig().getBoolean("discord.notifyOnAdminShop", false);
        notifyShopOwnerDm = plugin.getConfig().getBoolean("discord.notifyShopOwnerDm", false);
        username = plugin.getConfig().getString("discord.username", "Shop");
        avatarUrl = plugin.getConfig().getString("discord.avatarUrl", "");

        // SSRF guard: only Discord webhook endpoints may be used as the webhook URL.
        // Anything else is rejected with a startup warning and treated as unset, so no
        // outbound request is ever made to an untrusted target.
        if (!webhookUrl.isEmpty() && !HttpWebhookSender.isAllowedWebhookUrl(webhookUrl)) {
            plugin.getLogger().warning("Configured Discord webhook URL is not an allowlisted Discord webhook URL "
                    + "(https://discord.com/api/webhooks/ or https://discordapp.com/api/webhooks/); "
                    + "Discord webhook notifications are disabled.");
            webhookUrl = "";
        }
    }

    @EventHandler
    public void onPlayerExchangeShop(PlayerExchangeShopEvent event) {
        if (!enabled || event.isCancelled() || webhookUrl.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        AbstractShop shop = event.getShop();
        ShopType type = event.getType();

        boolean shouldNotify = switch (type) {
            case SELL -> notifyOnSale;
            case BUY -> notifyOnBuy;
            case BARTER -> notifyOnBarter;
            case GAMBLE -> notifyOnGamble;
            case COMBO -> false;
        };

        if (shop.isAdmin() && !notifyOnAdminShop) {
            shouldNotify = false;
        }

        if (!shouldNotify) {
            return;
        }

        // Resolve the DM recipient on the event thread so the async sender never
        // calls main-thread Bukkit APIs (Bukkit.getPlayer) on a Folia server.
        Player owner = null;
        if (notifyShopOwnerDm && discordDmSender != null && !shop.isAdmin()) {
            owner = Bukkit.getPlayer(shop.getOwnerName());
        }

        // Send asynchronously to avoid blocking the main thread
        runAsync(() -> sendWebhook(player, shop, type));

        // Optionally DM the shop owner if their account is linked via DiscordSRV
        if (notifyShopOwnerDm && discordDmSender != null && !shop.isAdmin()) {
            final Player dmOwner = owner;
            runAsync(() -> sendDmToOwner(player, shop, type, dmOwner));
        }
    }

    /**
     * Runs a task off the main thread. When FoliaLib is available the task is routed
     * through FoliaLib's scheduler instead of raw Bukkit; otherwise it falls back to
     * the Bukkit async scheduler.
     * <p>
     * Package-private seam: tests can override this to run synchronously without
     * relying on the Bukkit/Folia scheduler threading.
     */
    void runAsync(Runnable runnable) {
        FoliaLib foliaLib = plugin.getFoliaLib();
        if (foliaLib != null) {
            foliaLib.getScheduler().runAsync(ignored -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    private void sendWebhook(Player player, AbstractShop shop, ShopType type) {
        String itemName = shop.getItemStack().getType().name();
        int amount = shop.getAmount();
        double price = shop.getPrice();
        String ownerName = shop.getOwnerName();

        String json = buildJsonPayload(player, shop, type, itemName, amount, price, ownerName);
        sender.send(webhookUrl, json);
    }

    private void sendDmToOwner(Player buyer, AbstractShop shop, ShopType type, Player owner) {
        if (owner == null) {
            return;
        }

        String itemName = shop.getItemStack().getType().name();
        int amount = shop.getAmount();
        double price = shop.getPrice();
        String message = buildDmMessage(buyer, shop, type, itemName, amount, price);

        discordDmSender.sendDirectMessageIfLinked(owner, message);
    }

    private String buildDmMessage(Player buyer, AbstractShop shop, ShopType type, String itemName, int amount, double price) {
        StringBuilder sb = new StringBuilder();
        sb.append("Shop Transaction Notification\n");
        sb.append("Type: ").append(type.name()).append("\n");
        sb.append("Player: ").append(escapeJson(buyer.getName())).append("\n");
        sb.append("Shop: ").append(escapeJson(shop.getOwnerName())).append("\n");
        sb.append("Item: ").append(escapeJson(itemName)).append("\n");
        sb.append("Amount: ").append(amount).append("\n");
        sb.append("Price: ").append(price).append("\n");
        return sb.toString();
    }

    private String buildJsonPayload(Player player, AbstractShop shop, ShopType type, String itemName, int amount, double price, String ownerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\": \"").append(escapeJson(username)).append("\",");
        if (!avatarUrl.isEmpty()) {
            sb.append("\"avatar_url\": \"").append(escapeJson(avatarUrl)).append("\",");
        }
        sb.append("\"embeds\": [{");
        sb.append("\"title\": \"Shop Transaction - ").append(escapeJson(type.name())).append("\",");
        sb.append("\"color\": 5763719,");
        sb.append("\"fields\": [");
        sb.append("{\"name\": \"Player\", \"value\": \"").append(escapeJson(player.getName())).append("\", \"inline\": true},");
        sb.append("{\"name\": \"Shop Owner\", \"value\": \"").append(escapeJson(ownerName)).append("\", \"inline\": true},");
        sb.append("{\"name\": \"Type\", \"value\": \"").append(type.name()).append("\", \"inline\": true},");
        sb.append("{\"name\": \"Item\", \"value\": \"").append(escapeJson(itemName)).append("\", \"inline\": true},");
        sb.append("{\"name\": \"Amount\", \"value\": \"").append(amount).append("\", \"inline\": true},");
        sb.append("{\"name\": \"Price\", \"value\": \"").append(price).append("\", \"inline\": true}");
        sb.append("]");
        sb.append("}]}");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // JSON requires control characters (U+0000 through U+001F and U+007F) to be escaped.
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}

package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
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

    private final Shop plugin;
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

    public DiscordWebhookHandler(Shop instance) {
        this(instance, new HttpWebhookSender(), null);
    }

    DiscordWebhookHandler(Shop instance, WebhookSender sender) {
        this(instance, sender, null);
    }

    public DiscordWebhookHandler(Shop instance, WebhookSender sender, DiscordDmSender discordDmSender) {
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

        // Send asynchronously to avoid blocking the main thread
        runAsync(() -> sendWebhook(player, shop, type));

        // Optionally DM the shop owner if their account is linked via DiscordSRV
        if (notifyShopOwnerDm && discordDmSender != null && !shop.isAdmin()) {
            runAsync(() -> sendDmToOwner(player, shop, type));
        }
    }

    /**
     * Runs a task off the main thread. Package-private so tests can override it to run
     * synchronously without relying on the Bukkit scheduler threading.
     */
    void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void sendWebhook(Player player, AbstractShop shop, ShopType type) {
        String itemName = shop.getItemStack().getType().name();
        int amount = shop.getAmount();
        double price = shop.getPrice();
        String ownerName = shop.getOwnerName();

        String json = buildJsonPayload(player, shop, type, itemName, amount, price, ownerName);
        sender.send(webhookUrl, json);
    }

    private void sendDmToOwner(Player buyer, AbstractShop shop, ShopType type) {
        String ownerName = shop.getOwnerName();
        Player owner = Bukkit.getPlayer(ownerName);
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
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

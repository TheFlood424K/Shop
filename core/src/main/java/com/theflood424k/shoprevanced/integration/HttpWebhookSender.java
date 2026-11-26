package com.theflood424k.shoprevanced.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production {@link WebhookSender} using the built-in {@link HttpClient}.
 */
public class HttpWebhookSender implements WebhookSender {

    private static final Logger LOGGER = Logger.getLogger(HttpWebhookSender.class.getName());

    private final HttpClient httpClient;

    public HttpWebhookSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Allowlist for outbound webhook targets, mitigating SSRF via a configured
     * webhook URL. Only HTTPS Discord webhook endpoints are permitted:
     * https://discord.com/api/webhooks/... and https://discordapp.com/api/webhooks/...
     */
    public static boolean isAllowedWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith("/api/webhooks/")) {
            return false;
        }
        return "discord.com".equalsIgnoreCase(host) || "discordapp.com".equalsIgnoreCase(host);
    }

    @Override
    public void send(String url, String jsonPayload) {
        if (!isAllowedWebhookUrl(url)) {
            LOGGER.warning("Refusing to send webhook notification: target URL is not on the Discord webhook allowlist.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.fine("Discord webhook notification sent successfully");
            } else {
                LOGGER.warning("Discord webhook notification failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send Discord webhook notification", e);
        }
    }
}

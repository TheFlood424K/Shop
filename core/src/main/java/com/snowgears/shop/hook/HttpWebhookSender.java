package com.snowgears.shop.hook;

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

    @Override
    public void send(String url, String jsonPayload) {
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

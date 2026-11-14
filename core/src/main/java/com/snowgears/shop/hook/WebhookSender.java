package com.snowgears.shop.hook;

/**
 * Sends a JSON payload to a webhook URL.
 * Separated from {@link DiscordWebhookHandler} so the HTTP call can be mocked in tests.
 */
public interface WebhookSender {
    void send(String url, String jsonPayload);
}

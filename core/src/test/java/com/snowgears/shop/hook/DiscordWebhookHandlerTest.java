package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordWebhookHandlerTest extends BaseMockBukkitTest {

    private DiscordWebhookHandler createHandler(Shop plugin, WebhookSender sender) {
        return new DiscordWebhookHandler(plugin, sender);
    }

    private DiscordWebhookHandler createSyncHandler(Shop plugin, WebhookSender sender) {
        return new DiscordWebhookHandler(plugin, sender) {
            @Override
            void runAsync(Runnable r) {
                r.run();
            }
        };
    }

    private DiscordWebhookHandler createSyncHandler(Shop plugin, WebhookSender sender, DiscordDmSender dmSender) {
        return new DiscordWebhookHandler(plugin, sender, dmSender) {
            @Override
            void runAsync(Runnable r) {
                r.run();
            }
        };
    }

    @Test
    void configDefaults_discordDisabled() {
        // discord.enabled defaults to false
        assertFalse(getPlugin().getConfig().getBoolean("discord.enabled", false));
    }

    @Test
    void eventCancelled_noSend() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyOnSale", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);
        event.setCancelled(true);

        handler.onPlayerExchangeShop(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void discordDisabled_noSend() {
        // discord.enabled is false by default
        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void emptyWebhookUrl_noSend() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "");
        getPlugin().getConfig().set("discord.notifyOnSale", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void notifyOnSale_false_sellEventNoSend() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyOnSale", false);
        getPlugin().getConfig().set("discord.notifyOnBuy", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void notifyOnBuy_true_buyEventSends() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyOnBuy", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createSyncHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.BUY);
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.STONE);
        when(shop.getItemStack()).thenReturn(itemStack);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, times(1)).send(eq("https://discord.com/api/webhooks/test"), anyString());
    }

    @Test
    void adminShop_notifyOnAdminShopFalse_noSend() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyOnSale", true);
        getPlugin().getConfig().set("discord.notifyOnAdminShop", false);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(true);
        when(shop.getType()).thenReturn(ShopType.SELL);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void adminShop_notifyOnAdminShopTrue_sends() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyOnSale", true);
        getPlugin().getConfig().set("discord.notifyOnAdminShop", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordWebhookHandler handler = createSyncHandler(getPlugin(), sender);
        PlayerMock player = getServer().addPlayer();
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(true);
        when(shop.getType()).thenReturn(ShopType.SELL);
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.STONE);
        when(shop.getItemStack()).thenReturn(itemStack);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(player, shop);

        handler.onPlayerExchangeShop(event);

        verify(sender, times(1)).send(eq("https://discord.com/api/webhooks/test"), anyString());
    }

    @Test
    void notifyShopOwnerDm_true_ownerLinked_dmSent() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyShopOwnerDm", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordDmSender dmSender = mock(DiscordDmSender.class);
        DiscordWebhookHandler handler = createSyncHandler(getPlugin(), sender, dmSender);
        PlayerMock buyer = getServer().addPlayer("Buyer");
        PlayerMock owner = getServer().addPlayer("ShopOwner");
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);
        when(shop.getOwnerName()).thenReturn("ShopOwner");
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.STONE);
        when(shop.getItemStack()).thenReturn(itemStack);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(buyer, shop);

        handler.onPlayerExchangeShop(event);

        verify(dmSender, times(1)).sendDirectMessageIfLinked(eq(owner), anyString());
    }

    @Test
    void notifyShopOwnerDm_false_noDm() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        // notifyShopOwnerDm defaults to false

        WebhookSender sender = mock(WebhookSender.class);
        DiscordDmSender dmSender = mock(DiscordDmSender.class);
        DiscordWebhookHandler handler = createSyncHandler(getPlugin(), sender, dmSender);
        PlayerMock buyer = getServer().addPlayer("Buyer");
        PlayerMock owner = getServer().addPlayer("ShopOwner");
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(false);
        when(shop.getType()).thenReturn(ShopType.SELL);
        when(shop.getOwnerName()).thenReturn("ShopOwner");
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.STONE);
        when(shop.getItemStack()).thenReturn(itemStack);

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(buyer, shop);

        handler.onPlayerExchangeShop(event);

        verify(dmSender, never()).sendDirectMessageIfLinked(any(), anyString());
    }

    @Test
    void adminShop_notifyShopOwnerDm_true_noDm() {
        getPlugin().getConfig().set("discord.enabled", true);
        setRawConfig("discord.webhookUrl", "https://discord.com/api/webhooks/test");
        getPlugin().getConfig().set("discord.notifyShopOwnerDm", true);

        WebhookSender sender = mock(WebhookSender.class);
        DiscordDmSender dmSender = mock(DiscordDmSender.class);
        DiscordWebhookHandler handler = createSyncHandler(getPlugin(), sender, dmSender);
        PlayerMock buyer = getServer().addPlayer("Buyer");
        AbstractShop shop = mock(AbstractShop.class);
        when(shop.isAdmin()).thenReturn(true);
        when(shop.getType()).thenReturn(ShopType.SELL);
        when(shop.getOwnerName()).thenReturn("Admin");

        PlayerExchangeShopEvent event = new PlayerExchangeShopEvent(buyer, shop);

        handler.onPlayerExchangeShop(event);

        verify(dmSender, never()).sendDirectMessageIfLinked(any(), anyString());
    }

    @Test
    void escapeJson_specialCharactersEscaped() throws Exception {
        Method escapeMethod = DiscordWebhookHandler.class.getDeclaredMethod("escapeJson", String.class);
        escapeMethod.setAccessible(true);

        String result = (String) escapeMethod.invoke(new DiscordWebhookHandler(getPlugin()), "hello\"world\\test\nnewline");
        assertEquals("hello\\\"world\\\\test\\nnewline", result);
    }

    @Test
    void escapeJson_nullReturnsEmpty() throws Exception {
        Method escapeMethod = DiscordWebhookHandler.class.getDeclaredMethod("escapeJson", String.class);
        escapeMethod.setAccessible(true);

        String result = (String) escapeMethod.invoke(new DiscordWebhookHandler(getPlugin()), (String) null);
        assertEquals("", result);
    }

    @Test
    void escapeJson_emptyStringReturnsEmpty() throws Exception {
        Method escapeMethod = DiscordWebhookHandler.class.getDeclaredMethod("escapeJson", String.class);
        escapeMethod.setAccessible(true);

        String result = (String) escapeMethod.invoke(new DiscordWebhookHandler(getPlugin()), "");
        assertEquals("", result);
    }
}

package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.gui.*;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ConfigUpdater;
import com.snowgears.shop.util.PlayerSettings;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.UtilMethods;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ShopGuiHandler {

    public enum GuiIcon {
        MENUBAR_BACK, HOME_SEARCH, MENUBAR_LAST_PAGE, MENUBAR_NEXT_PAGE,
        MENUBAR_SORT_PRICE_LOW, MENUBAR_SORT_PRICE_HIGH, MENUBAR_SORT_NAME_LOW, MENUBAR_SORT_NAME_HIGH,
        MENUBAR_FILTER_TYPE_ALL, MENUBAR_FILTER_TYPE_SELL, MENUBAR_FILTER_TYPE_BUY, MENUBAR_FILTER_TYPE_BARTER, MENUBAR_FILTER_TYPE_GAMBLE,
        MENUBAR_FILTER_STOCK_ALL, MENUBAR_FILTER_STOCK_IN, MENUBAR_FILTER_STOCK_OUT,
        HOME_LIST_ALL_SHOPS, HOME_LIST_PLAYERS, HOME_SETTINGS, HOME_COMMANDS,
        LIST_SHOP, LIST_PLAYER, LIST_PLAYER_ADMIN,
        SETTINGS_NOTIFY_OWNER_ON, SETTINGS_NOTIFY_OWNER_OFF, SETTINGS_NOTIFY_USER_ON, SETTINGS_NOTIFY_USER_OFF, SETTINGS_NOTIFY_STOCK_ON, SETTINGS_NOTIFY_STOCK_OFF,
        COMMANDS_CURRENCY, COMMANDS_SET_CURRENCY, COMMANDS_SET_GAMBLE, COMMANDS_REFRESH_DISPLAYS, COMMANDS_RELOAD, COMMANDS_ITEMLIST_ALLOW_ADD, COMMANDS_ITEMLIST_ALLOW_REMOVE, COMMANDS_ITEMLIST_DENY_ADD, COMMANDS_ITEMLIST_DENY_REMOVE,
        ALL_SHOP_ICON, ALL_PLAYER_ICON, ALL_ADMIN_ICON
    }

    public enum GuiTitle {
        HOME, LIST_PLAYERS, LIST_SHOPS, SETTINGS, COMMANDS, LIST_SEARCH_RESULTS

    }

    public Shop plugin;
    private HashMap<UUID, ShopGuiWindow> playerGuiWindows = new HashMap<>();
    private HashMap<UUID, PlayerSettings> playerSettings = new HashMap<>();

    private HashMap<GuiIcon, ItemStack> guiIcons = new HashMap<>();
    private HashMap<GuiTitle, String> guiWindowTitles = new HashMap<>();

    /**
     * Cached owner head icons, keyed by owner UUID.
     *
     * <p>Only the <em>skull</em> is cached — never the formatted display name or lore. Those embed
     * shop-specific placeholders such as the shop count, so caching them would freeze a value that
     * changes as the owner creates and destroys shops. Formatting happens per render instead.
     *
     * <p>Entries expire so a head resolved while the owner was offline (yielding a placeholder skull)
     * is retried later, and so a player who changes their skin is eventually picked up. Explicit
     * invalidation on join handles the same problem immediately rather than after the TTL.
     */
    private final ConcurrentHashMap<UUID, CachedHead> playerHeads = new ConcurrentHashMap<>();

    private static final long HEAD_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    /** A cached skull and the time it stops being considered fresh. */
    private record CachedHead(ItemStack skull, long expiresAt) {}

    public ShopGuiHandler(Shop instance){
        plugin = instance;
    }

    public ShopGuiWindow getWindow(Player player){
        if(playerGuiWindows.get(player.getUniqueId()) != null){
            return playerGuiWindows.get(player.getUniqueId());
        }
        HomeWindow window = new HomeWindow(player.getUniqueId());
        playerGuiWindows.put(player.getUniqueId(), window);
        return window;
    }

    public void setWindow(Player player, ShopGuiWindow window){
        playerGuiWindows.put(player.getUniqueId(), window);

        window.open();
    }

    public void closeWindow(Player player){
        if(playerGuiWindows.get(player.getUniqueId()) != null){
            playerGuiWindows.remove(player.getUniqueId());
        }
        player.closeInventory();
    }

    public void reloadPlayerHeadIcon(AbstractShop shop){
        if(shop == null || shop.getOwnerUUID() == null)
            return;

        UUID playerUUID = shop.getOwnerUUID();
        boolean isAdminShop = playerUUID.equals(plugin.getShopHandler().getAdminUUID());

        // The skull is the only part worth caching; name and lore are rebuilt below on every call
        // because they embed per-shop placeholders.
        ItemStack playerHead = resolveHead(playerUUID, isAdminShop);

        ItemStack placeHolderIcon = Shop.getPlugin().getGuiHandler()
                .getIcon(isAdminShop ? GuiIcon.ALL_ADMIN_ICON : GuiIcon.ALL_PLAYER_ICON, playerUUID, null);

        ItemStack rendered = playerHead.clone();
        ItemMeta itemMeta = rendered.getItemMeta();

        String name = ShopMessage.formatMessage(placeHolderIcon.getItemMeta().getDisplayName(), shop);
        if (name == null || name.isEmpty()) {
            String shortId = playerUUID.toString();
            shortId = shortId.substring(0,3) + "..." + shortId.substring(shortId.length()-3);
            name = "Unknown Player (" + shortId + ")";
        }
        List<String> lore = new ArrayList<>();
        for(String loreLine : placeHolderIcon.getItemMeta().getLore()){
            lore.add(ShopMessage.formatMessage(loreLine, shop));
        }

        itemMeta.setDisplayName(name);
        itemMeta.setLore(lore);

        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        container.set(Shop.getPlugin().getPlayerUUIDNameSpacedKey(), PersistentDataType.STRING, playerUUID.toString());

        rendered.setItemMeta(itemMeta);
        playerHeads.put(playerUUID, new CachedHead(rendered, System.currentTimeMillis() + HEAD_CACHE_TTL_MILLIS));
    }

    /**
     * Returns a fresh clone of the cached skull, rebuilding it when absent or expired.
     *
     * <p>Resolution prefers the online player, because {@code Bukkit.getOfflinePlayer} can touch the
     * profile cache and block. Offline lookups therefore only happen once per TTL window.
     */
    private ItemStack resolveHead(UUID playerUUID, boolean isAdminShop) {
        CachedHead cached = playerHeads.get(playerUUID);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.skull().clone();
        }

        ItemStack skull;
        if (isAdminShop) {
            skull = plugin.getGuiHandler().getIcon(GuiIcon.ALL_ADMIN_ICON, playerUUID, null).clone();
        } else {
            skull = new ItemStack(Material.PLAYER_HEAD);
            OfflinePlayer owner = Bukkit.getPlayer(playerUUID) != null
                    ? Bukkit.getPlayer(playerUUID)
                    : Bukkit.getOfflinePlayer(playerUUID);
            if (owner != null && owner.getName() != null) {
                ((SkullMeta) skull.getItemMeta()).setOwningPlayer(owner);
            }
        }
        return skull;
    }

    /**
     * Drops a cached head so the next render rebuilds it.
     *
     * <p>Called when an owner joins — their head is resolvable and their skin may have changed — and
     * when their last shop is removed, which bounds the cache to the set of current owners.
     */
    public void invalidatePlayerHead(UUID playerUUID) {
        if (playerUUID != null) {
            playerHeads.remove(playerUUID);
        }
    }

    public ItemStack getPlayerHeadIcon(UUID playerUUID){
        CachedHead cached = playerHeads.get(playerUUID);
        if(cached != null && cached.expiresAt() > System.currentTimeMillis())
            return cached.skull().clone();
        return new ItemStack(Material.AIR);
    }

    public ArrayList<ItemStack> getShopOwnerHeads(){
        List<ItemStack> heads = new ArrayList<>();
        for (CachedHead cached : playerHeads.values()) {
            if (cached.expiresAt() > System.currentTimeMillis()) {
                heads.add(cached.skull().clone());
            }
        }
        return new ArrayList<>(heads);
    }

    public GuiIcon getIconFromOption(Player player, PlayerSettings.Option option){
        return getIconFromOption(player.getUniqueId(), option);
    }

    public GuiIcon getIconFromOption(UUID playerUUID, PlayerSettings.Option option){
        if(playerSettings.get(playerUUID) != null){
            PlayerSettings settings = playerSettings.get(playerUUID);
            return settings.getGuiIcon(option);
        }

        PlayerSettings settings = PlayerSettings.loadFromFile(playerUUID);
        if(settings == null)
            settings = new PlayerSettings(playerUUID);

        playerSettings.put(playerUUID, settings);
        return settings.getGuiIcon(option);
    }

    public void setIconForOption(Player player, PlayerSettings.Option option, GuiIcon guiIcon){
        PlayerSettings settings;

        if(playerSettings.get(player.getUniqueId()) != null){
            settings = playerSettings.get(player.getUniqueId());
        }
        else {
            settings = PlayerSettings.loadFromFile(player);
            if (settings == null)
                settings = new PlayerSettings(player);
        }

        settings.setOption(option, guiIcon);
        playerSettings.put(player.getUniqueId(), settings);
    }

    public void toggleNotificationSetting(Player player, PlayerSettings.Option option){
        PlayerSettings settings;

        if(playerSettings.get(player.getUniqueId()) != null){
            settings = playerSettings.get(player.getUniqueId());
        }
        else {
            settings = PlayerSettings.loadFromFile(player);
            if (settings == null)
                settings = new PlayerSettings(player);
        }

        settings.toggleNotification(option);
        playerSettings.put(player.getUniqueId(), settings);
    }

    public ItemStack getIcon(GuiIcon iconEnum, UUID playerUUID, AbstractShop shop){
        if(iconEnum == GuiIcon.LIST_SHOP){
            return shop.getGuiIcon();
        }
        else if(iconEnum == GuiIcon.LIST_PLAYER || iconEnum == GuiIcon.LIST_PLAYER_ADMIN){
            return getPlayerHeadIcon(playerUUID);
        }

        if(guiIcons.containsKey(iconEnum))
            return guiIcons.get(iconEnum);
        return null;
    }

    public String getTitle(GuiTitle title){
        return guiWindowTitles.get(title);
    }

    public String getTitle(ShopGuiWindow window){
        if(window instanceof HomeWindow){
            return guiWindowTitles.get(GuiTitle.HOME);
        }
        else if(window instanceof ListPlayersWindow){
            return guiWindowTitles.get(GuiTitle.LIST_PLAYERS);
        }
        else if(window instanceof PlayerSettingsWindow){
            return guiWindowTitles.get(GuiTitle.SETTINGS);
        }
        else if(window instanceof CommandsWindow){
            return guiWindowTitles.get(GuiTitle.COMMANDS);
        }
        return "Window";
    }

    public void loadIconsAndTitles(){
        File configFile = new File(plugin.getDataFolder(), "guiConfig.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("guiConfig.yml"), configFile);
        }

        try {
            ConfigUpdater.update(plugin, "guiConfig.yml", configFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        //load all titles first
        Set<String> titles = config.getConfigurationSection("titles").getKeys(false);

        for(GuiTitle titleEnum : GuiTitle.values()) {
            String titleString = config.getString("titles."+titleEnum.toString().toLowerCase());
            if (titleString == null) {
                titleString = titleEnum.toString(); // fallback to enum name
            }
            guiWindowTitles.put(titleEnum, ChatColor.translateAlternateColorCodes('&', titleString));
        }

        Set<String> icons = config.getConfigurationSection("icons").getKeys(false);

        //load all icons next
        for(GuiIcon iconEnum : GuiIcon.values()) {
            // If creative selection is disabled then disable the search icon
            if (!plugin.allowCreativeSelection() && iconEnum == GuiIcon.HOME_SEARCH) { continue; }

            boolean translateColors = true;
            if(iconEnum == GuiIcon.ALL_SHOP_ICON || iconEnum == GuiIcon.ALL_PLAYER_ICON)
                translateColors = false;

            String iconString = iconEnum.toString().toLowerCase();
            String parentKey = iconString.substring(0, iconString.indexOf('_'));
            String childKey = iconString.substring(iconString.indexOf('_')+1);

            String type = config.getString("icons."+parentKey+"."+childKey+".type");
            String name = config.getString("icons."+parentKey+"."+childKey+".name");

            if(name != null && translateColors)
                name = ChatColor.translateAlternateColorCodes('&', name);

            List<String> loreLines = config.getStringList("icons."+parentKey+"."+childKey+".lore");
            List<String> lore = new ArrayList<>();
            if(loreLines != null) {
                for (String line : loreLines) {
                    if(translateColors)
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    else
                        lore.add(line);
                }
            }

            ItemStack icon = null;
            if(type != null) {
                icon = new ItemStack(Material.valueOf(type.toUpperCase()));
            }
            else if(childKey.equals("set_gamble")){
                icon = plugin.getGambleDisplayItem().clone();
            }
            else if(parentKey.equals("list")){
                if(childKey.equals("player")) {
                    icon = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
                }
            }
            else if(childKey.equals("shop_icon")){
                icon = new ItemStack(Material.DIRT); // just a placeholder. will replace with the item shop is selling later
            }
            else if(childKey.equals("player_icon")){
                icon = new ItemStack(Material.PLAYER_HEAD); // just a placeholder. will replace with the player head of shop later
            }

            if(icon != null) {
                // Check if are set to "AIR" and if we should be disabled.
                if (icon.getItemMeta() != null) {
                    ItemMeta iconMeta = icon.getItemMeta();

                    // iconMeta will be null if the icon is "Air", only update iconMeta if it actually exists
                    if (iconMeta != null) {
                        if (name != null)
                            iconMeta.setDisplayName(name);
                        if (lore != null && !lore.isEmpty())
                            iconMeta.setLore(lore);
                        icon.setItemMeta(iconMeta);
                        guiIcons.put(iconEnum, icon);
                    }
                }
            }
        }
    }
}

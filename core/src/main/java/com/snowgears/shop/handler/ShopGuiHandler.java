package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.gui.*;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ConfigUpdater;
import com.snowgears.shop.util.PlayerSettings;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.UtilMethods;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import net.md_5.bungee.api.ChatColor;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Handles Shop GUI windows, player settings, icons, titles, and player head icons.
 */
public class ShopGuiHandler {

    /**
     * Icon identifiers used for GUI elements.
     */
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

    /**
     * Window title identifiers for GUI screens.
     */
    public enum GuiTitle {
        HOME, LIST_PLAYERS, LIST_SHOPS, SETTINGS, COMMANDS, LIST_SEARCH_RESULTS

    }

    public Shop plugin;
    private HashMap<UUID, ShopGuiWindow> playerGuiWindows = new HashMap<>();
    private HashMap<UUID, PlayerSettings> playerSettings = new HashMap<>();

    private HashMap<GuiIcon, ItemStack> guiIcons = new HashMap<>();
    private HashMap<GuiTitle, String> guiWindowTitles = new HashMap<>();

    private HashMap<UUID, ItemStack> playerHeads = new HashMap<>();
    // Owners whose head needs (re)building, with their latest shop. Filled from async shop loading.
    private Map<UUID, AbstractShop> staleHeads = new ConcurrentHashMap<>();

    // Throttling queue for profile lookups to avoid Mojang session server rate limits
    private final Queue<AbstractShop> pendingHeadLoads = new LinkedBlockingQueue<>();
    private final Set<UUID> queuedHeadOwners = Collections.synchronizedSet(new HashSet<>());
    private WrappedTask headQueueTask;

    public ShopGuiHandler(Shop instance){
        plugin = instance;
    }

    /**
     * Gets the GUI window for the player, creating a home window if none exists.
     *
     * @param player the player
     * @return the player's GUI window
     */
    public ShopGuiWindow getWindow(Player player){
        if(playerGuiWindows.get(player.getUniqueId()) != null){
            return playerGuiWindows.get(player.getUniqueId());
        }
        HomeWindow window = new HomeWindow(player.getUniqueId());
        playerGuiWindows.put(player.getUniqueId(), window);
        return window;
    }

    /**
     * Sets the player's current GUI window and opens it.
     *
     * @param player the player
     * @param window the window to display
     */
    public void setWindow(Player player, ShopGuiWindow window){
        playerGuiWindows.put(player.getUniqueId(), window);

        window.open();
    }

    /**
     * Closes the player's GUI window and closes their inventory.
     *
     * @param player the player
     */
    public void closeWindow(Player player){
        if(playerGuiWindows.get(player.getUniqueId()) != null){
            playerGuiWindows.remove(player.getUniqueId());
        }
        player.closeInventory();
    }

    /**
     * Marks the owner's GUI head for rebuilding the next time it is shown.
     * Heads are built lazily: each setItemMeta on a head without textures makes Paper send an uncached
     * Mojang profile lookup, so building one per loaded shop floods the session server at startup.
     *
     * To avoid rate limits, head rebuilds are queued and processed one at a time with a small delay.
     */
    public void reloadPlayerHeadIcon(AbstractShop shop){
        if(shop == null || shop.getOwnerUUID() == null)
            return;

        UUID playerUUID = shop.getOwnerUUID();

        // If head is already cached or it's the admin UUID, build immediately
        if(playerHeads.containsKey(playerUUID) || playerUUID.equals(plugin.getShopHandler().getAdminUUID())){
            doReloadPlayerHeadIcon(shop);
            return;
        }

        // Queue the shop for throttled head loading
        synchronized (pendingHeadLoads) {
            if (queuedHeadOwners.add(playerUUID)) {
                pendingHeadLoads.add(shop);
            }
            if (headQueueTask == null) {
                startHeadQueueProcessor();
            }
        }
    }

    private void doReloadPlayerHeadIcon(AbstractShop shop){
        if(shop == null || shop.getOwnerUUID() == null)
            return;
        staleHeads.put(shop.getOwnerUUID(), shop);
    }

    private void startHeadQueueProcessor(){
        headQueueTask = plugin.getFoliaLib().getScheduler().runTimer(task -> {
            AbstractShop shop;
            synchronized (pendingHeadLoads) {
                shop = pendingHeadLoads.poll();
                if (shop != null) {
                    queuedHeadOwners.remove(shop.getOwnerUUID());
                }
            }
            if (shop != null) {
                doReloadPlayerHeadIcon(shop);
            } else {
                // Queue is empty, cancel the task
                headQueueTask.cancel();
                headQueueTask = null;
            }
        }, 20, 20); // Start after 1 second, then process one per second (20 ticks)
    }

    private void buildStaleHead(UUID playerUUID){
        AbstractShop shop = staleHeads.remove(playerUUID);
        if(shop != null)
            buildPlayerHeadIcon(shop);
    }

    private void buildPlayerHeadIcon(AbstractShop shop){
        UUID playerUUID = shop.getOwnerUUID();
        OfflinePlayer offlinePlayer = shop.getOwner();
        ItemStack playerHead = playerHeads.get(playerUUID);
        ItemMeta itemMeta;
        ItemStack placeHolderIcon;
        if(playerHead == null) {
            //playerUUID is the fake admin UUID
            if(playerUUID.equals(plugin.getShopHandler().getAdminUUID())) {
                playerHead = Shop.getPlugin().getGuiHandler().getIcon(GuiIcon.ALL_ADMIN_ICON, playerUUID, null).clone();
                itemMeta = playerHead.getItemMeta();
            }
            else{
                playerHead = new ItemStack(Material.PLAYER_HEAD);
                itemMeta = playerHead.getItemMeta();

                if (offlinePlayer != null && offlinePlayer.getName() != null)
                    ((SkullMeta)itemMeta).setOwningPlayer(offlinePlayer);
            }

        }
        else{
            itemMeta =  playerHead.getItemMeta();
        }

        if(playerUUID.equals(plugin.getShopHandler().getAdminUUID())) {
            //get the placeholder icon with all of the unformatted fields
            placeHolderIcon = Shop.getPlugin().getGuiHandler().getIcon(GuiIcon.ALL_ADMIN_ICON, playerUUID, null);
        }
        else {
            //get the placeholder icon with all of the unformatted fields
            placeHolderIcon = Shop.getPlugin().getGuiHandler().getIcon(GuiIcon.ALL_PLAYER_ICON, playerUUID, null);
        }

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

        playerHead.setItemMeta(itemMeta);

        playerHeads.put(playerUUID, playerHead);
    }

    /**
     * Gets the player's head icon, building stale heads if needed.
     *
     * @param playerUUID the player's UUID
     * @return the player head item stack, or AIR if not available
     */
    public ItemStack getPlayerHeadIcon(UUID playerUUID){
        buildStaleHead(playerUUID);
        if(playerHeads.containsKey(playerUUID))
            return playerHeads.get(playerUUID);
        return new ItemStack(Material.AIR);
    }

    /**
     * Gets all cached shop owner head icons.
     *
     * @return list of player head item stacks
     */
    public ArrayList<ItemStack> getShopOwnerHeads(){
        for(UUID playerUUID : new ArrayList<>(staleHeads.keySet()))
            buildStaleHead(playerUUID);
        return playerHeads.values().stream().collect(
                Collectors.toCollection(ArrayList::new)
        );
    }

    /**
     * Gets the GUI icon for the given player setting option.
     *
     * @param player the player
     * @param option the player setting option
     * @return the GUI icon
     */
    public GuiIcon getIconFromOption(Player player, PlayerSettings.Option option){
        return getIconFromOption(player.getUniqueId(), option);
    }

    /**
     * Gets the GUI icon for the given player setting option.
     *
     * @param playerUUID the player's UUID
     * @param option the player setting option
     * @return the GUI icon
     */
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

    /**
     * Sets the GUI icon for the given player setting option.
     *
     * @param player the player
     * @param option the player setting option
     * @param guiIcon the GUI icon to set
     */
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

    /**
     * Toggles the notification setting for the given player option.
     *
     * @param player the player
     * @param option the player setting option
     */
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

    /**
     * Gets the GUI icon item stack.
     *
     * @param iconEnum the icon identifier
     * @param playerUUID the player's UUID
     * @param shop the shop if applicable
     * @return the item stack for the icon
     */
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

    /**
     * Gets the title string for the given GUI title.
     *
     * @param title the GUI title
     * @return the formatted title string
     */
    public String getTitle(GuiTitle title){
        return guiWindowTitles.get(title);
    }

    /**
     * Gets the title string for the given GUI window.
     *
     * @param window the GUI window
     * @return the formatted title string
     */
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

    /**
     * Loads GUI icons and titles from guiConfig.yml.
     */
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

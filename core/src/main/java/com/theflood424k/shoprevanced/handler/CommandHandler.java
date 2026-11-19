package com.theflood424k.shoprevanced.handler;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.gui.ShopGuiWindow;
import com.theflood424k.shoprevanced.search.SearchFilter;
import com.theflood424k.shoprevanced.search.SearchResult;
import com.theflood424k.shoprevanced.search.ShopSearchService;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.ItemListType;
import com.theflood424k.shoprevanced.util.PlayerSettings;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandHandler extends BukkitCommand {

    private ShopRevanced plugin;

    public CommandHandler(ShopRevanced instance, String permission, String name, String description, String usageMessage, List<String> aliases) {
        super(name, description, usageMessage, aliases);
        this.setPermission(permission);
        plugin = instance;
        try {
            register();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void sendCommandMessage(String subType, Player player) {
        ShopRevancedMessage.sendMessage("command", subType, player, null);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if(plugin.useGUI()) {
                    ShopGuiWindow window = plugin.getGuiHandler().getWindow(player);
                    window.open();
                }
                else {
                    //these are commands all players have access to
                    sendCommandMessage("list", player);
                    sendCommandMessage("currency", player);

                    //these are commands only operators have access to
                    if (player.hasPermission("ShopRevanced.operator") || player.isOp()) {
                        sendCommandMessage("setcurrency", player);
                        sendCommandMessage("setgamble", player);
                        sendCommandMessage("itemrefresh", player);
                        if(plugin.getItemListType() != ItemListType.NONE){
                            sendCommandMessage("itemlist", player);
                        }
                        sendCommandMessage("reload", player);
                    }
                }
            }
            //these are commands that can be executed from the console
            else{
                sender.sendMessage("/"+this.getName()+" list - list all shops on server");
                sender.sendMessage("/"+this.getName()+" currency - information about currency being used on server");
                sender.sendMessage("/"+this.getName()+" item refresh - refresh display items on all shops");
                sender.sendMessage("/"+this.getName()+" reload - reload ShopRevanced plugin");
            }
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("search")) {
                handleSearch(sender, args);
            } else if (args[0].equalsIgnoreCase("list")) {
                if (sender instanceof Player) {
                    Player player = (Player)sender;
                    sendCommandMessage("list_output_total", player);
                    if(plugin.usePerms())
                        sendCommandMessage("list_output_perms", player);
                    else
                        sendCommandMessage("list_output_noperms", player);
                }
                else
                    sender.sendMessage("[ShopRevanced] There are " + plugin.getShopRevancedHandler().getNumberOfShops() + " shops registered on the server.");
            }
            else if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("ShopRevanced.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    plugin.reload();
                    sendCommandMessage("reload_output", player);
                } else {
                    plugin.reload();
                    sender.sendMessage("[ShopRevanced] Reloaded plugin.");
                }

                for(Player p : Bukkit.getOnlinePlayers()){
                    if(p != null){
                        p.closeInventory();
                    }
                }
                plugin.getShopRevancedHandler().removeLegacyDisplays();

            }
            else if (args[0].equalsIgnoreCase("currency")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && player.hasPermission("ShopRevanced.operator")) || player.isOp()) {

                        sendCommandMessage("currency_output", player);
                        sendCommandMessage("currency_output_tip", player);
                        return true;
                    }
                } else {
                    sender.sendMessage("The server is using "+plugin.getCurrencyName()+" as currency.");
                }
            }
            else if (args[0].equalsIgnoreCase("setcurrency")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && player.hasPermission("ShopRevanced.operator")) || player.isOp()) {
                        if(plugin.getCurrencyType() != CurrencyType.ITEM){
                            sendCommandMessage("error_novault", player);
                            return true;
                        }
                        else{
                            ItemStack handItem = player.getInventory().getItemInMainHand();
                            if(handItem == null || handItem.getType() == Material.AIR){
                                sendCommandMessage("error_nohand", player);
                                return true;
                            }
                            handItem.setAmount(1);
                            plugin.setItemCurrency(handItem);
                            sendCommandMessage("setcurrency_output", player);
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage("The server is using "+PlainTextComponentSerializer.plainText().serialize(plugin.getItemNameUtil().getName(plugin.getItemCurrency()))+" as currency.");
                }
            }
            else if(args[0].equalsIgnoreCase("setgamble")){
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("ShopRevanced.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    if(player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != Material.AIR)
                        plugin.setGambleDisplayItem(player.getInventory().getItemInMainHand());
                    else {
                        sendCommandMessage("error_nohand", player);
                        return true;
                    }
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("refresh")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("ShopRevanced.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    plugin.getShopRevancedHandler().removeLegacyDisplays();
                    sendCommandMessage("itemrefresh_output", player);
                } else {
                    plugin.getShopRevancedHandler().removeLegacyDisplays();
                    sender.sendMessage("[ShopRevanced] The display items on all of the shops have been refreshed.");
                }
            }
            else if (args[0].equalsIgnoreCase("itemlist")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("ShopRevanced.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    if(args[1].equalsIgnoreCase("add")){
                        plugin.getShopRevancedHandler().addInventoryToItemList(player.getInventory());
                        sendCommandMessage("itemlist_add", player);
                    }
                    else if(args[1].equalsIgnoreCase("remove")){
                        plugin.getShopRevancedHandler().removeInventoryFromItemList(player.getInventory());
                        sendCommandMessage("itemlist_remove", player);
                    }
                } else {
                    sender.sendMessage("[ShopRevanced] This command can only be run as a player.");
                }
            }
            else if (args[0].equalsIgnoreCase("notify")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if(args[1].equalsIgnoreCase("user")) {
                        toggleOptionAndNotifyPlayer(player, PlayerSettings.Option.NOTIFICATION_SALE_USER);
                    }
                    else if(args[1].equalsIgnoreCase("owner")) {
                        toggleOptionAndNotifyPlayer(player, PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
                    }
                    else if(args[1].equalsIgnoreCase("stock")) {
                        toggleOptionAndNotifyPlayer(player, PlayerSettings.Option.NOTIFICATION_STOCK);
                    }
                } else {
                    sender.sendMessage("[ShopRevanced] This command can only be run as a player.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        List<String> results = new ArrayList<>();
        if (args.length == 0) {
            results.add(this.getName());
        }
        else if (args.length == 1) {
            results.add("list");
            results.add("currency");
            results.add("search");

            boolean showOperatorCommands = false;
            if(sender instanceof Player){
                Player player = (Player)sender;

                if(player.hasPermission("ShopRevanced.operator") || player.isOp()) {
                    showOperatorCommands = true;
                }
            }
            else{
                showOperatorCommands = true;
            }

            if(showOperatorCommands){
                results.add("setcurrency");
                results.add("setgamble");
                results.add("item");
                results.add("notify");
                if(ShopRevanced.getPlugin().getItemListType() != ItemListType.NONE){
                    results.add("itemlist");
                }
                results.add("reload");
            }
            return sortedResults(args[0], results);
        }
        else if (args.length == 2) {
            if(args[0].equalsIgnoreCase("notify")){
                results.add("user");
                results.add("owner");
                results.add("stock");
            }

            boolean showOperatorCommands = false;
            if(sender instanceof Player){
                Player player = (Player)sender;

                if(player.hasPermission("ShopRevanced.operator") || player.isOp()) {
                    showOperatorCommands = true;
                }
            }
            else{
                showOperatorCommands = true;
            }

            if(showOperatorCommands){
                if(ShopRevanced.getPlugin().getItemListType() != ItemListType.NONE){
                    if(args[0].equalsIgnoreCase("itemlist")) {
                        results.add("add");
                        results.add("remove");
                    }
                }
                if(args[0].equalsIgnoreCase("item")) {
                    results.add("refresh");
                }
            }
            return sortedResults(args[1], results);
        }
        return results;
    }

    private void handleSearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[ShopRevanced] Search can only be used by players.");
            return;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sendCommandMessage("search_usage", player);
            return;
        }

        ShopSearchService searchService = plugin.getShopSearchService();
        if (searchService == null) {
            player.sendMessage("[ShopRevanced] Search service is not available.");
            return;
        }

        String itemName = args[1];
        Double minPrice = null;
        Double maxPrice = null;
        if (args.length >= 3) {
            try {
                minPrice = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sendCommandMessage("search_invalid_price", player);
                return;
            }
        }
        if (args.length >= 4) {
            try {
                maxPrice = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sendCommandMessage("search_invalid_price", player);
                return;
            }
        }

        SearchFilter filter = new SearchFilter(itemName, minPrice, maxPrice, null, null);
        List<SearchResult> results = searchService.search(filter);
        List<SearchResult> page = searchService.paginate(results, 1, ShopSearchService.defaultPageSize());

        if (results.isEmpty()) {
            if (plugin.getShopRevancedHandler().getNumberOfShops() == 0) {
                sendCommandMessage("search_no_shops", player);
            } else {
                sendCommandMessage("search_no_results", player);
            }
            return;
        }

        sendCommandMessage("search_results", player);
        // NOTE: {count} placeholder is not token-replaced by chatConfig; send a follow-up with the count.
        player.sendMessage(Component.text("§aTotal results: " + results.size()));

        for (SearchResult result : page) {
            AbstractShop shop = result.shop();
            ItemStack item = shop.getItemStack();
            String itemNameDisplay = plugin.getItemNameUtil().getName(item) == null
                    ? item.getType().name()
                    : PlainTextComponentSerializer.plainText()
                    .serialize(plugin.getItemNameUtil().getName(item));
            String owner = shop.getOwnerName() == null ? "unknown" : shop.getOwnerName();
            player.sendMessage(Component.text(
                    "§7• §e" + itemNameDisplay + " §7- §a" + shop.getPrice() + " §7- §b" + owner
                    + " §7(score §f" + String.format("%.1f", result.relevanceScore()) + "§7)"));
        }

        if (page.size() < results.size()) {
            player.sendMessage(Component.text(
                    "§7Showing page 1 of " + page.size() + "/" + results.size() + " results."));
        }
    }

    private void toggleOptionAndNotifyPlayer(Player player, PlayerSettings.Option option) {
        ShopRevanced.getPlugin().getGuiHandler().toggleNotificationSetting(player, option);

        switch (option) {
            case NOTIFICATION_SALE_USER:
                sendCommandMessage("notify_user", player);
                break;
            case NOTIFICATION_SALE_OWNER:
                sendCommandMessage("notify_owner", player);
                break;
            case NOTIFICATION_STOCK:
                sendCommandMessage("notify_stock", player);
                break;
        }
    }

    private void register() {
        Bukkit.getCommandMap().register(this.getName(), this);
    }

    // Sorts possible results to provide true tab auto complete based off of what is already typed.
    public List <String> sortedResults(String arg, List<String> results) {
        final List < String > completions = new ArrayList < > ();
        StringUtil.copyPartialMatches(arg, results, completions);
        Collections.sort(completions);
        results.clear();
        for (String s: completions) {
            results.add(s);
        }
        return results;
    }
}




package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.gui.ShopGuiWindow;
import com.snowgears.shop.util.CurrencyType;
import com.snowgears.shop.util.ItemListType;
import com.snowgears.shop.util.PlayerSettings;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandHandler extends BukkitCommand {

    private Shop plugin;

    public CommandHandler(Shop instance, String permission, String name, String description, String usageMessage, List<String> aliases) {
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
        ShopMessage.sendMessage("command", subType, player, null);
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
                    // FIX (Bug 16): /shop notify was fully implemented but never listed in help.
                    sendCommandMessage("notify", player);

                    //these are commands only operators have access to
                    if (player.hasPermission("shop.operator") || player.isOp()) {
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
                sender.sendMessage("/"+this.getName()+" reload - reload Shop plugin");
            }
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("list")) {
                if (sender instanceof Player) {
                    Player player = (Player)sender;
                    sendCommandMessage("list_output_total", player);
                    if(plugin.usePerms())
                        sendCommandMessage("list_output_perms", player);
                    else
                        sendCommandMessage("list_output_noperms", player);
                }
                else
                    sender.sendMessage("[Shop] There are " + plugin.getShopHandler().getNumberOfShops() + " shops registered on the server.");
            }
            else if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("shop.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    plugin.reload();
                    sendCommandMessage("reload_output", player);
                } else {
                    plugin.reload();
                    sender.sendMessage("[Shop] Reloaded plugin.");
                }

                for(Player p : Bukkit.getOnlinePlayers()){
                    if(p != null){
                        p.closeInventory();
                    }
                }
                plugin.getShopHandler().removeLegacyDisplays();

            }
            else if (args[0].equalsIgnoreCase("currency")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    // FIX (Bug 15): currency info should be visible to all players.
                    // Previously the entire response was gated behind shop.operator / isOp(),
                    // causing non-op players to see complete silence despite the command
                    // being listed in the public help text.
                    sendCommandMessage("currency_output", player);
                    // Only show the "how to change currency" tip to operators.
                    if ((plugin.usePerms() && player.hasPermission("shop.operator")) || player.isOp()) {
                        sendCommandMessage("currency_output_tip", player);
                    }
                } else {
                    sender.sendMessage("The server is using "+plugin.getCurrencyName()+" as currency.");
                }
            }
            else if (args[0].equalsIgnoreCase("setcurrency")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && player.hasPermission("shop.operator")) || player.isOp()) {
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
                    sender.sendMessage("The server is using "+ShopMessage.toPlain(plugin.getItemNameUtil().getName(plugin.getItemCurrency()))+" as currency.");
                }
            }
            else if(args[0].equalsIgnoreCase("setgamble")){
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("shop.operator")) || (!plugin.usePerms() && !player.isOp())) {
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
                    if ((plugin.usePerms() && !player.hasPermission("shop.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    plugin.getShopHandler().removeLegacyDisplays();
                    sendCommandMessage("itemrefresh_output", player);
                } else {
                    plugin.getShopHandler().removeLegacyDisplays();
                    sender.sendMessage("[Shop] The display items on all of the shops have been refreshed.");
                }
            }
            else if (args[0].equalsIgnoreCase("itemlist")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("shop.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        sendCommandMessage("not_authorized", player);
                        return true;
                    }
                    if(args[1].equalsIgnoreCase("add")){
                        plugin.getShopHandler().addInventoryToItemList(player.getInventory());
                        sendCommandMessage("itemlist_add", player);
                    }
                    else if(args[1].equalsIgnoreCase("remove")){
                        plugin.getShopHandler().removeInventoryFromItemList(player.getInventory());
                        sendCommandMessage("itemlist_remove", player);
                    }
                } else {
                    sender.sendMessage("[Shop] This command can only be run as a player.");
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
                    sender.sendMessage("[Shop] This command can only be run as a player.");
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

            boolean showOperatorCommands = false;
            if(sender instanceof Player){
                Player player = (Player)sender;

                if(player.hasPermission("shop.operator") || player.isOp()) {
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
                if(Shop.getPlugin().getItemListType() != ItemListType.NONE){
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

                if(player.hasPermission("shop.operator") || player.isOp()) {
                    showOperatorCommands = true;
                }
            }
            else{
                showOperatorCommands = true;
            }

            if(showOperatorCommands){
                if(Shop.getPlugin().getItemListType() != ItemListType.NONE){
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

    private void toggleOptionAndNotifyPlayer(Player player, PlayerSettings.Option option) {
        Shop.getPlugin().getGuiHandler().toggleNotificationSetting(player, option);

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

    private void register() throws ReflectiveOperationException {
        CommandMap commandMap = getCommandMap();
        commandMap.register(this.getName(), this);
    }

    /**
     * FIX (Bug 13): Re-register this handler under a (possibly new) alias without
     * creating a duplicate entry in Bukkit's CommandMap.
     *
     * Called by Shop.onEnable() on every reload after the first registration so
     * that the live handler instance always reflects the current plugin state.
     */
    public void reregister(String newAlias) {
        try {
            CommandMap commandMap = getCommandMap();
            Map<String, Command> knownCommands = commandMap.getKnownCommands();
            // Remove both the bare name and the plugin-prefixed variant.
            knownCommands.remove(this.getName());
            knownCommands.remove(plugin.getName().toLowerCase() + ":" + this.getName());
            if (newAlias != null && !newAlias.isBlank() && !newAlias.equals(this.getName())) {
                knownCommands.remove(newAlias);
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + newAlias);
                this.setName(newAlias);
                this.setAliases(new ArrayList<>(List.of(newAlias)));
            }
            commandMap.register(this.getName(), this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CommandMap getCommandMap() throws ReflectiveOperationException {
        final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        bukkitCommandMap.setAccessible(true);
        return (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
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

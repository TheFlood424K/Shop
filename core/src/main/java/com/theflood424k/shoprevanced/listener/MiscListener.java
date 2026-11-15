package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.AbstractDisplay;
import com.theflood424k.shoprevanced.event.PlayerDestroyShopEvent;
import com.theflood424k.shoprevanced.event.PlayerResizeShopEvent;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class MiscListener implements Listener {

    public ShopRevanced plugin;
    private ConcurrentHashMap<UUID, ShopRevancedCreationProcess> playerChatCreationSteps = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, Long> lastChatCreation = new ConcurrentHashMap<>();

    public MiscListener(ShopRevanced instance) {
        plugin = instance;
    }

    //prevent emptying of bucket when player clicks on ShopRevanced sign
    //also prevent when emptying on display item itself
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block b = event.getBlockClicked();

        if (b.getBlockData() instanceof WallSign) {
            AbstractShop shop = plugin.getShopRevancedHandler().getShop(b.getLocation());
            if (shop != null)
                event.setCancelled(true);
        }
        Block blockToFill = event.getBlockClicked().getRelative(event.getBlockFace());
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(blockToFill.getRelative(BlockFace.DOWN));
        if (shop != null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopCreation(SignChangeEvent event) {
        final Block b = event.getBlock();
        final Player player = event.getPlayer();

        if(!plugin.getAllowCreationMethodSign())
            return;

        if(!(b.getState() instanceof Sign))
            return;

        BlockFace signDirection = null;
        Block chest = null;
        if(b.getBlockData() instanceof WallSign) {
            signDirection = ((WallSign) b.getBlockData()).getFacing();
            chest = b.getRelative(signDirection.getOppositeFace());
        }
        else if(b.getBlockData() instanceof Rotatable){ //regular sign post
            signDirection = ((Rotatable) b.getBlockData()).getRotation();
            //adjust the sign direction to cordinal direction if its not already one
            if( signDirection.toString().indexOf('_') != -1) {
                String adjustedDirString = signDirection.toString().substring(0, signDirection.toString().indexOf('_'));
                signDirection = BlockFace.valueOf(adjustedDirString);
            }
            chest = b.getRelative(signDirection.getOppositeFace());
        }
        else
            return;

        int amount = 0 ;
        ShopType type = null;
        boolean isAdmin = false;
        if (plugin.getShopRevancedHandler().isChest(chest)) {
            final Sign signBlock = (Sign) b.getState();
            if (event.getLine(0).toLowerCase().contains(ShopRevancedMessage.getCreationWord("ShopRevanced").toLowerCase())) {

                if(!plugin.getShopRevancedCreationUtil().shopCanBeCreated(player, chest)){
                    cancelShopRevancedCreationProcess(player);
                    event.setCancelled(true);
                    return;
                }

                try {
                    String line2 = UtilMethods.cleanNumberText(event.getLine(1));
                    amount = Integer.parseInt(line2);
                    if (amount < 1) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                        cancelShopRevancedCreationProcess(player);
                        event.setCancelled(true);
                        return;
                    }
                } catch (NumberFormatException e) {
                    ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                    ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                    cancelShopRevancedCreationProcess(player);
                    event.setCancelled(true);
                    return;
                }

                type = plugin.getShopRevancedCreationUtil().getShopType(event.getLine(3));
                isAdmin = plugin.getShopRevancedCreationUtil().getShopIsAdmin(event.getLine(3));

                if(type == null)
                    type = ShopType.SELL;

                PricePair pricePair = plugin.getShopRevancedCreationUtil().getShopPricePair(player, event.getLine(2), type);
                if(pricePair == null){
                    event.setCancelled(true);
                    return;
                }

                AbstractShop shop = plugin.getShopRevancedCreationUtil().createShop(player, chest, signBlock.getBlock(), pricePair, amount, isAdmin, type, signDirection, false);
                if(shop == null) {
                    event.setCancelled(true);
                    return;
                }

                ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, chest, signDirection);
                process.setStep(ShopRevancedCreationProcess.ChatCreationStep.SIGN_ITEM);
                playerChatCreationSteps.put(player.getUniqueId(), process);

                process.displayFloatingText(type.toString(), "initialize");
                if (plugin.allowCreativeSelection() && (type == ShopType.BUY || type == ShopType.COMBO)) {
                    ShopRevancedMessage.sendMessage(type.toString(), "initializeAlt", player, shop);
                }

                //give player a limited amount of time to finish creating the ShopRevanced until it is deleted
                plugin.getFoliaLib().getScheduler().runLater(() -> {
                    //the ShopRevanced has still not been initialized with an item from a player
                    if (!shop.isInitialized()) {
                        shop.delete();
                        if (b.getBlockData() instanceof WallSign) {
                            String[] lines = ShopRevancedMessage.getSignLines("timeout", shop);
                            Sign sign = (Sign) b.getState();
                            sign.setLine(0, lines[0]);
                            sign.setLine(1, lines[1]);
                            sign.setLine(2, lines[2]);
                            sign.setLine(3, lines[3]);
                            sign.update(true);
                            cancelShopRevancedCreationProcess(player);
                        }
                    }
                }, plugin.getShopCreationTimeout() * 20L);
            }
        }
    }

    public ShopRevancedCreationProcess getShopRevancedCreationProcess(Player player){
        return playerChatCreationSteps.get(player.getUniqueId());
    }

    public void removeShopRevancedCreationProcess(Player player){
        playerChatCreationSteps.remove(player.getUniqueId());
    }

    public void cancelShopRevancedCreationProcess(Player player){
        ShopRevancedCreationProcess process = this.getShopRevancedCreationProcess(player);
        if (process != null) {
            process.display.removeDisplayEntities(player, true);
            playerChatCreationSteps.remove(player.getUniqueId());
            // Send message that the creation was cancelled
            ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
        }

        // Remove player from creative selection if they are in it!
        // The Bukkit API can only change player game modes in a sync task, not an async task
        plugin.getFoliaLib().getScheduler().runLater(() -> {
            plugin.getCreativeSelectionListener().removePlayerFromCreativeSelection(player);
        }, 1);
    }

    public boolean isChestInShopRevancedCreationProcess(Location location) {
        return getShopRevancedCreationProcessByChest(location) != null;
    }

    public ShopRevancedCreationProcess getShopRevancedCreationProcessByChest(Location location) {
        for (ShopRevancedCreationProcess process : playerChatCreationSteps.values()) {
            if (process.getClickedChest().getLocation().equals(location)) {
                return process;
            }
        }
        return null;
    }

    // Fired anytime a player interacts with a block, air, or entity.
    // This is used to handle ShopRevanced creation and initialization.
    // Generally, events shouldn't be cancelled here, as this is the first event in a chain, 
    // and we should allow other event handlers down the chain to handle more specific situations.
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) { return; }
        try {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                return; // off hand version, ignore.
            }
        } catch (NoSuchMethodError error) {}
        
        final Player player = event.getPlayer();



        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final Block clicked = event.getClickedBlock();

            if (clicked.getBlockData() instanceof WallSign) {

                if(!plugin.getAllowCreationMethodSign())
                    return;

                // We only want to handle shops that exist but are not initialized.
                AbstractShop shop = plugin.getShopRevancedHandler().getShop(clicked.getLocation());
                if (shop == null || shop.isInitialized()) {
                    return;
                }

                //creative selection listener will handle if item is null
                if(event.getItem() == null || event.getItem().getType() == Material.AIR)
                    return;
                
                boolean initializedShop;
                if(shop.getType() == ShopType.BARTER && shop.getItemStack() != null && shop.getSecondaryItemStack() == null)
                    initializedShop = plugin.getShopRevancedCreationUtil().initializeShop(shop, player, shop.getItemStack(), event.getItem());
                else
                    initializedShop = plugin.getShopRevancedCreationUtil().initializeShop(shop, player, event.getItem(), null);

                if(initializedShop){
                    plugin.getShopRevancedCreationUtil().sendCreationSuccess(player, shop);
                    plugin.getLogHandler().logAction(player, shop, ShopActionType.INIT);
                }

                return;
            }
            else if(plugin.getShopRevancedHandler().isChest(clicked)){

                if(!plugin.getAllowCreationMethodChest())
                    return;

                //dont let players create shops via chest on shops that already exist
                // This check is also required for chests to be destroyed properly without new shops getting created. This is because PlayerInteractEvent is called before BlockBreakEvent.
                AbstractShop existingShop = plugin.getShopRevancedHandler().getShopByChest(clicked);
                if (existingShop != null) {
                    return;
                }

                //TODO come back to this and allow players to create double chest shops via chest creation method

                // Make sure that the ShopRevanced can be created at all, prior to checking whats in the players hand.
                if(!plugin.getShopRevancedCreationUtil().shopCanBeCreated(player, clicked)){
                    return;
                }

                if(event.getItem() == null || event.getItem().getType() == Material.AIR){
                    if(plugin.allowCreativeSelection()) {
                        //TODO this section needs to check if the current step is to get the barter item
                        ShopRevancedCreationProcess currentProcess = playerChatCreationSteps.get(player.getUniqueId());
                        // Check if last created process is within 80ms, if so, cancel the event
                        Long lastCreatedProcess = lastChatCreation.get(player.getUniqueId());
                        if (lastCreatedProcess != null && (new Date().getTime() - lastCreatedProcess) < 80) {
                            return;
                        }
                        if (currentProcess != null && currentProcess.getStep() == ShopRevancedCreationProcess.ChatCreationStep.BARTER_ITEM) {   
                            plugin.getCreativeSelectionListener().putPlayerInCreativeSelection(player, clicked.getLocation(), false);
                            return;
                        }
                        else if (currentProcess == null && player.isSneaking()){
                            //if the player has created a new process in the last 5 seconds, block them from creating another
                            if(lastCreatedProcess != null && (new Date().getTime() - lastCreatedProcess) < plugin.getDebug_shopCreateCooldown()) {
                                ShopRevancedMessage.sendMessage("interactionIssue", "createCooldown", player, null);
                                return;
                            }

                            BlockFace signFacing = plugin.getShopRevancedCreationUtil().calculateBlockFaceForSign(player, clicked, event.getBlockFace());
                            if(signFacing == null) {
                                return;
                            }

                            ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, clicked, signFacing);
                            playerChatCreationSteps.put(player.getUniqueId(), process);
                            lastChatCreation.put(player.getUniqueId(), new Date().getTime());
                            process.markInteracted();
                            plugin.getCreativeSelectionListener().putPlayerInCreativeSelection(player, clicked.getLocation(), false);
                        }
                    }
                    return;
                }
                else {
                    ShopRevancedCreationProcess currentProcess = playerChatCreationSteps.get(player.getUniqueId());
                    plugin.getLogger().debug("Current ShopRevanced Creation Process: " + currentProcess);
                    if (currentProcess != null && currentProcess.getStep() == ShopRevancedCreationProcess.ChatCreationStep.BARTER_ITEM) {
                        if (!plugin.getShopRevancedCreationUtil().itemsCanBeInitialized(player, currentProcess.getItemStack(), event.getItem())) {
                            return;
                        }
                        currentProcess.setBarterItemStack(event.getItem());
                        currentProcess.markInteracted();
                        currentProcess.displayFloatingText(currentProcess.getShopType().toString(), "createHitChestBarterAmount");
                        return;
                    }
                }

                if(!player.isSneaking())
                    return;

                Long lastCreatedProcess = lastChatCreation.get(player.getUniqueId());
                if(lastCreatedProcess != null) {
                    //if the player has created a new process in the last 5 seconds, block them from creating another
                    long diff = (new Date().getTime() - lastCreatedProcess);
                    if (diff < plugin.getDebug_shopCreateCooldown()) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCooldown", player, null);
                        return;
                    }
                }
                // Cleanup the last process if needed and cancel the existing ShopRevanced creation process if it exists
                if (playerChatCreationSteps.get(player.getUniqueId()) != null) {
                    this.cancelShopRevancedCreationProcess(player);
                    return;
                }

                BlockFace signFacing = plugin.getShopRevancedCreationUtil().calculateBlockFaceForSign(player, clicked, event.getBlockFace());
                if(signFacing == null)
                    return;

                //since player is creating a ShopRevanced via clicking a chest with an item, create a new object to track the steps of that process
                ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, clicked, signFacing);
                process.setItemStack(event.getItem());
                playerChatCreationSteps.put(player.getUniqueId(), process);
                lastChatCreation.put(player.getUniqueId(), new Date().getTime());
                process.markInteracted();

                //send player text prompts after they have clicked the chest with the item they want to create a ShopRevanced with
                ShopRevancedMessage.sendMessage("initialCreateInstruction", null, process, player);
                process.displayFloatingText("createHitChest", null);
                List<String> autocomplete = new ArrayList<>();
                Arrays.asList(ShopType.values()).forEach((shopType -> autocomplete.add(shopType.toString().toLowerCase())));
                try {
                    player.setCustomChatCompletions(autocomplete);
                } catch (Error | Exception error) {} // Suppress error if autocomplete is not supported
                if((!plugin.usePerms() && player.isOp()) || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"))) {
                    ShopRevancedMessage.sendMessage("adminCreateHitChest", null, process, player);
                }

                //give player a limited amount of time to finish creating the ShopRevanced until it is deleted
                final UUID originalProcessUUID = process.getUniqueID();
                plugin.getFoliaLib().getScheduler().runLater(() -> {
                    //the ShopRevanced has still not been initialized with an item from a player
                    ShopRevancedCreationProcess currentProcess = playerChatCreationSteps.get(player.getUniqueId());
                    if (currentProcess != null && currentProcess.getUniqueID().equals(originalProcessUUID)) {
                        currentProcess.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                        plugin.getCreativeSelectionListener().removePlayerFromCreativeSelection(player);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createHitChestTimeout", currentProcess, player);
                    }
                }, plugin.getShopCreationTimeout() * 20L);
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        Player player = event.getPlayer();
        if(playerChatCreationSteps.containsKey(player.getUniqueId())){
            ShopRevancedCreationProcess process = playerChatCreationSteps.get(player.getUniqueId());
            String message = event.getMessage();
            plugin.getLogger().debug("ShopRevanced Creation Process: " + process.getStep() + " Player " + player.getName() + " input: " + message, true);

            // Allow player to cancel ShopRevanced creation at any step by typing "cancel"
            if (message != null && message.equalsIgnoreCase("cancel")) {
                process.cleanup();
                cancelShopRevancedCreationProcess(player);
                event.setCancelled(true);
                return;
            }

            switch (process.getStep()){
                case SHOP_TYPE:
                    ShopType type = plugin.getShopRevancedCreationUtil().getShopType(event.getMessage());
                    if(type == null){
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                        return;
                    }
                    boolean isAdmin = plugin.getShopRevancedCreationUtil().getShopIsAdmin(event.getMessage());
                    process.setShopType(type);
                    process.setAdmin(isAdmin);
                    event.setCancelled(true);

                    if(type == ShopType.GAMBLE){ ShopRevancedMessage.sendMessage(type.toString(), "createHitChestPrice", process, player); }
                    else {
                        process.displayFloatingText(type.toString(), "createHitChestAmount");
                    }
                    break;
                case ITEM_AMOUNT:
                    int amount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(event.getMessage());
                        amount = Integer.parseInt(textAmt);
                        if (amount < 1) {
                            ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                            ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                            event.setCancelled(true);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                        process.cleanup();
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        playerChatCreationSteps.remove(player.getUniqueId());
                        return;
                    }
                    process.setItemAmount(amount);
                    event.setCancelled(true);

                    if(process.getShopType() == ShopType.BARTER){
                        process.displayFloatingText(process.getShopType().toString(), "createHitChest");
                        if (plugin.allowCreativeSelection()) {
                            ShopRevancedMessage.sendMessage(process.getShopType().toString(), "initializeBarterAlt", player, null);
                        }
                    }
                    else {
                        process.displayFloatingText(process.getShopType().toString(), "createHitChestPrice");
                    }
                    break;
                case ITEM_PRICE:
                    double price = plugin.getShopRevancedCreationUtil().getShopPrice(player, event.getMessage(), process.getShopType());
                    if(price == -1){
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                        return;
                    }
                    process.setPrice(price);
                    event.setCancelled(true);

                    if(process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.ITEM_PRICE_COMBO){
                        process.displayFloatingText(process.getShopType().toString(), "createHitChestPriceCombo");
                        return;
                    }
                    if(process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED){
                        process.createShop(player);
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    break;
                case ITEM_PRICE_COMBO:
                    double priceCombo = plugin.getShopRevancedCreationUtil().getShopPriceCombo(player, event.getMessage(), process.getShopType());
                    if(priceCombo == -1){
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                        return;
                    }
                    process.setPriceCombo(priceCombo);
                    event.setCancelled(true);

                    if(process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED){
                        process.createShop(player);
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    break;
                case BARTER_ITEM_AMOUNT:
                    int barterAmount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(event.getMessage());
                        barterAmount = Integer.parseInt(textAmt);
                        if (barterAmount < 1) {
                            ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                            ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                            event.setCancelled(true);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                        return;
                    }
                    process.setPrice(barterAmount);
                    event.setCancelled(true);

                    if(process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED) {
                        process.createShop(player);
                        process.cleanup();
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    break;
                // ITEM, BARTER_ITEM, or FINISHED
                default:
                    // If the user chatted and we were not in one of the earlier steps, cancel the creation process
                    // This will happen if the user was meant to select an ITEM or BARTER_ITEM, and exited the window
                    // without selecting their item to buy.
                    // This prevents chat from being locked for the player
                    process.cleanup();
                    this.cancelShopRevancedCreationProcess(player);
                    break;
            }
        }
    }

    //player destroys ShopRevanced, call PlayerDestroyShopEvent or PlayerResizeShopEvent
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void shopDestroy(BlockBreakEvent event) {

        Block b = event.getBlock();
        Player player = event.getPlayer();

        if (b.getBlockData() instanceof WallSign) {
            AbstractShop shop = plugin.getShopRevancedHandler().getShop(b.getLocation());
            if (shop == null)
                return;
            // Disable dropping sign if its fake
            if(shop.isFakeSign()){
                event.setDropItems(false);
            }
            if (!shop.isInitialized()) {
                event.setCancelled(true);
                return;
            }

            if(plugin.getDestroyShopRequiresSneak()){
                if(!player.isSneaking()){
                    event.setCancelled(true);
                    plugin.getLogger().trace("[MiscListener.shopDestroy : getDestroyShopRequiresSneak] updateSign");
                    shop.updateSign();
                    return;
                }
            }
            //player trying to break their own ShopRevanced
            if (shop.getOwnerName().equals(player.getName())) {
                if (plugin.usePerms() && !(player.hasPermission("ShopRevanced.destroy") || player.hasPermission("ShopRevanced.operator"))) {
                    event.setCancelled(true);
                    ShopRevancedMessage.sendMessage("permission", "destroy", player, shop);
                    return;
                }

                //if players must pay to create shops, remove money first
                double cost = plugin.getDestructionCost();
                if(cost > 0){
                    // Check for funds
                    if (!EconomyUtils.hasSufficientFunds(player, player.getInventory(), cost)){
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyInsufficientFunds", player, shop);
                        event.setCancelled(true);
                        return;
                    }
                    // Remove funds
                    boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
                    if(!removed){
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyInsufficientFunds", player, shop);
                        event.setCancelled(true);
                        return;
                    }
                }

                PlayerDestroyShopEvent e = new PlayerDestroyShopEvent(player, shop);
                plugin.getServer().getPluginManager().callEvent(e);
                if (e.isCancelled()) {
                    event.setCancelled(true);
                    return;
                }

                plugin.getLogHandler().logAction(player, shop, ShopActionType.DESTROY);

                if(shop.isFakeSign()){
                    event.setDropItems(false);
                }

                if((!shop.isAdmin()) && plugin.returnCreationCost() && plugin.getCreationCost() > 0) {
                    if (plugin.getCurrencyType() != CurrencyType.ITEM) {
                        EconomyUtils.addFunds(shop.getOwner(),player.getInventory(), plugin.getCreationCost());
                    } else {
                        ItemStack currencyDrop = plugin.getItemCurrency().clone();
                        currencyDrop.setAmount((int) plugin.getCreationCost());
                        shop.getChestLocation().getWorld().dropItemNaturally(shop.getChestLocation(), currencyDrop);
                    }
                }


                ShopRevancedMessage.sendMessage(shop.getType().toString(), "destroy", player, shop);
                // We already log on ShopActionType.DESTROY in the Log Handler, so don't log the ShopRevanced destroy reason
                shop.delete();

                return;
            }
            //player trying to break other players ShopRevanced
            else {
                boolean isRegionOwner = false;
                //check if the player is a world guard region owner
                if (plugin.worldGuardExists()) {
                    isRegionOwner = WorldGuardHook.isRegionOwner(player, shop.getSignLocation());
                }
                if (isRegionOwner || player.isOp() || (plugin.usePerms() && (player.hasPermission("ShopRevanced.operator") || player.hasPermission("ShopRevanced.destroy.other")))) {
                    PlayerDestroyShopEvent e = new PlayerDestroyShopEvent(player, shop);
                    plugin.getServer().getPluginManager().callEvent(e);

                    if (e.isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }

                    plugin.getLogHandler().logAction(player, shop, ShopActionType.DESTROY);

                    if(shop.isFakeSign()){
                        event.setDropItems(false);
                    }

                    ShopRevancedMessage.sendMessage(shop.getType().toString(), "opDestroy", player, shop);
                    shop.delete();
                } else {
                    ShopRevancedMessage.sendMessage("permission", "destroyOther", player, shop);
                    event.setCancelled(true);
                }
            }
        } else if (plugin.getShopRevancedHandler().isChest(b)) {
            // ShopRevanced will not exist in ShopRevancedHandler if it is in the middle of a ShopRevanced creation process
            // protect shops that are in the middle of a ShopRevanced creation process from being destroyed
            ShopRevancedCreationProcess process = this.getShopRevancedCreationProcessByChest(b.getLocation());
            if (process != null) {
                // The owner of a chest/chat creation can cancel it by breaking the chest a second time.
                // Sign-based creation stays protected (a real ShopRevanced and sign already exist on the chest).
                if (process.getPlayerUUID().equals(player.getUniqueId()) && !process.isSignCreation()) {
                    // Ignore breaks that are part of the natural creation flow: while the player is still
                    // selecting an item, or when the break coincides with the click that started/advanced
                    // creation (in creative mode a single click both interacts and breaks the block).
                    if (!process.isAwaitingItemSelection() && !process.wasJustInteracted()) {
                        if (process.isDestroyArmed()) {
                            // second deliberate attempt: cancel creation and let the chest break in the same hit
                            this.cancelShopRevancedCreationProcess(player);
                            return;
                        }
                        process.setDestroyArmed(true);
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyUninitializedChestCancel", player, null);
                    }
                } else {
                    ShopRevancedMessage.sendMessage("interactionIssue", "destroyUninitializedChest", player, null);
                }
                event.setCancelled(true); // don't break chest
                return;
            }

            AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(b);
            if (shop == null) {
                return;
            }

            // since we are dealing with an existing ShopRevanced, cancel the event so that
            // we can explicitly "uncancel" it later if we want to allow the chest to be broken.
            event.setCancelled(true);

            InventoryHolder ih = ((InventoryHolder)b.getState()).getInventory().getHolder();

            if (ih instanceof DoubleChest) {
                if(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && (player.hasPermission("ShopRevanced.operator") || player.hasPermission("ShopRevanced.destroy.other")))){

                    // the broken block was the initial chest with the sign
                    if(shop.getChestLocation().equals(b.getLocation())){
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyChest", player, shop);
                        // event.setCancelled(true);
                        shop.sendEffects(false, player);
                        return;
                    }
                    else {
                        PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), false);
                        Bukkit.getPluginManager().callEvent(e);

                        if(e.isCancelled()){
                            // event.setCancelled(true);
                            return;
                        }
                        // Explicitly allow the chest to be broken since it is the "Expansion" chest
                        // we need to uncancel the event so that the chest can be broken.
                        event.setCancelled(false);
                        return;
                    }
                } else {
                    ShopRevancedMessage.sendMessage("permission", "destroyOther", player, shop);
                    // event.setCancelled(true);
                }
            }
            else{
                if(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && (player.hasPermission("ShopRevanced.operator") || player.hasPermission("ShopRevanced.destroy.other")))) {
                    ShopRevancedMessage.sendMessage("interactionIssue", "destroyChest", player, shop);
                    shop.sendEffects(false, player);
                } else {
                    ShopRevancedMessage.sendMessage("permission", "destroyOther", player, shop);
                }
                // event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBreakBlockUnderShop(BlockBreakEvent event){
       //if the block under a chest has been broken, check that its a ShopRevanced chest
        if(DisplayUtil.isChest(event.getBlock().getRelative(BlockFace.UP).getType())){
            AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getBlock().getRelative(BlockFace.UP));
                if(shop != null){
                //if it is a ShopRevanced chest, don't allow it to be broken unless its by the owner or someone with permission
                Player player = event.getPlayer();
                if(!(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator")))){
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onShopExpansion(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        Player player = event.getPlayer();

        if (plugin.getShopRevancedHandler().isChest(b)) {
            ArrayList<BlockFace> doubleChestFaces = new ArrayList<>();
            doubleChestFaces.add(BlockFace.NORTH);
            doubleChestFaces.add(BlockFace.EAST);
            doubleChestFaces.add(BlockFace.SOUTH);
            doubleChestFaces.add(BlockFace.WEST);

            //find out if the player placed a chest next to an already active ShopRevanced
            AbstractShop shop = plugin.getShopRevancedHandler().getShopTouchingBlock(b);
            if (shop == null || (b.getType() != shop.getChestLocation().getBlock().getType()))
                return;

            //owner is trying to
            if (shop.getOwnerUUID().equals(player.getUniqueId())) {
                PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), true);
                Bukkit.getPluginManager().callEvent(e);

                if(e.isCancelled()){
                    event.setCancelled(true);
                    return;
                }
                return;
            }
            //other player is trying to
            else {
                if (player.isOp() || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"))) {
                    PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), true);
                    Bukkit.getPluginManager().callEvent(e);

                    if(e.isCancelled()){
                        event.setCancelled(true);
                        return;
                    }

                } else
                    event.setCancelled(true);
            }
        }
    }
}








package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Handles all shop creation events: sign-based creation, chest+item creation,
 * and chat-driven creation-step progression.
 * <p>
 * Extracted from {@code MiscListener} to isolate creation logic into a
 * single-responsibility listener.
 */
public class ShopCreationListener implements Listener {

    private final ShopRevanced plugin;
    private final ShopCreationProcessManager creationProcessManager;

    public ShopCreationListener(ShopRevanced plugin, ShopCreationProcessManager creationProcessManager) {
        this.plugin = plugin;
        this.creationProcessManager = creationProcessManager;
    }

    //prevent emptying of bucket when player clicks on ShopRevanced sign
    //also prevent when emptying on display item itself
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
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

        if (!plugin.getAllowCreationMethodSign())
            return;

        if (!(b.getState() instanceof Sign))
            return;

        BlockFace signDirection = null;
        Block chest = null;
        BlockData blockData = b.getBlockData();
        if (blockData instanceof WallSign) {
            signDirection = ((WallSign) blockData).getFacing();
            chest = b.getRelative(signDirection.getOppositeFace());
        } else if (blockData instanceof Rotatable) { //regular sign post
            signDirection = ((Rotatable) blockData).getRotation();
            //adjust the sign direction to cordinal direction if its not already one
            if (signDirection.toString().indexOf('_') != -1) {
                String adjustedDirString = signDirection.toString().substring(0, signDirection.toString().indexOf('_'));
                signDirection = BlockFace.valueOf(adjustedDirString);
            }
            chest = b.getRelative(signDirection.getOppositeFace());
        } else
            return;

        int amount = 0;
        ShopType type = null;
        boolean isAdmin = false;
        if (plugin.getShopRevancedHandler().isChest(chest)) {
            final Sign signBlock = (Sign) b.getState();
            if (event.getLine(0).toLowerCase().contains(ShopRevancedMessage.getCreationWord("ShopRevanced").toLowerCase())) {

                if (!plugin.getShopRevancedCreationUtil().shopCanBeCreated(player, chest)) {
                    creationProcessManager.cancelShopRevancedCreationProcess(player);
                    event.setCancelled(true);
                    return;
                }

                try {
                    String line2 = UtilMethods.cleanNumberText(event.getLine(1));
                    amount = Integer.parseInt(line2);
                    if (amount < 1) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                        creationProcessManager.cancelShopRevancedCreationProcess(player);
                        event.setCancelled(true);
                        return;
                    }
                } catch (NumberFormatException e) {
                    ShopRevancedMessage.sendMessage("interactionIssue", "line2", player, null);
                    ShopRevancedMessage.sendMessage("interactionIssue", "createCancel", player, null);
                    creationProcessManager.cancelShopRevancedCreationProcess(player);
                    event.setCancelled(true);
                    return;
                }

                type = plugin.getShopRevancedCreationUtil().getShopType(event.getLine(3));
                isAdmin = plugin.getShopRevancedCreationUtil().getShopIsAdmin(event.getLine(3));

                if (type == null)
                    type = ShopType.SELL;

                PricePair pricePair = plugin.getShopRevancedCreationUtil().getShopPricePair(player, event.getLine(2), type);
                if (pricePair == null) {
                    event.setCancelled(true);
                    return;
                }

                AbstractShop shop = plugin.getShopRevancedCreationUtil().createShop(player, chest, signBlock.getBlock(), pricePair, amount, isAdmin, type, signDirection, false);
                if (shop == null) {
                    event.setCancelled(true);
                    return;
                }

                ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, chest, signDirection);
                process.setStep(ShopRevancedCreationProcess.ChatCreationStep.SIGN_ITEM);
                                creationProcessManager.putShopRevancedCreationProcess(player, process);

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
                            creationProcessManager.cancelShopRevancedCreationProcess(player);
                        }
                    }
                }, plugin.getShopCreationTimeout() * 20L);
            }
        }
    }

    // Fired anytime a player interacts with a block, air, or entity.
    // This is used to handle ShopRevanced creation and initialization.
    // Generally, events shouldn't be cancelled here, as this is the first event in a chain, 
    // and we should allow other event handlers down the chain to handle more specific situations.
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }
        try {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                return; // off hand version, ignore.
            }
        } catch (NoSuchMethodError error) {
        }

        final Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final Block clicked = event.getClickedBlock();

            if (clicked.getBlockData() instanceof WallSign) {

                if (!plugin.getAllowCreationMethodSign())
                    return;

                // We only want to handle shops that exist but are not initialized.
                AbstractShop shop = plugin.getShopRevancedHandler().getShop(clicked.getLocation());
                if (shop == null || shop.isInitialized()) {
                    return;
                }

                //creative selection listener will handle if item is null
                if (event.getItem() == null || event.getItem().getType() == Material.AIR)
                    return;

                boolean initializedShop;
                if (shop.getType() == ShopType.BARTER && shop.getItemStack() != null && shop.getSecondaryItemStack() == null)
                    initializedShop = plugin.getShopRevancedCreationUtil().initializeShop(shop, player, shop.getItemStack(), event.getItem());
                else
                    initializedShop = plugin.getShopRevancedCreationUtil().initializeShop(shop, player, event.getItem(), null);

                if (initializedShop) {
                    plugin.getShopRevancedCreationUtil().sendCreationSuccess(player, shop);
                    plugin.getLogHandler().logAction(player, shop, com.theflood424k.shoprevanced.util.ShopActionType.INIT);
                }

                return;
            } else if (plugin.getShopRevancedHandler().isChest(clicked)) {

                if (!plugin.getAllowCreationMethodChest())
                    return;

                //dont let players create shops via chest on shops that already exist
                // This check is also required for chests to be destroyed properly without new shops getting created. This is because PlayerInteractEvent is called before BlockBreakEvent.
                AbstractShop existingShop = plugin.getShopRevancedHandler().getShopByChest(clicked);
                if (existingShop != null) {
                    return;
                }

                //TODO come back to this and allow players to create double chest shops via chest creation method

                // Make sure that the ShopRevanced can be created at all, prior to checking whats in the players hand.
                if (!plugin.getShopRevancedCreationUtil().shopCanBeCreated(player, clicked)) {
                    return;
                }

                if (event.getItem() == null || event.getItem().getType() == Material.AIR) {
                    if (plugin.allowCreativeSelection()) {
                        //TODO this section needs to check if the current step is to get the barter item
                        ShopRevancedCreationProcess currentProcess = creationProcessManager.getShopRevancedCreationProcess(player);
                        // Check if last created process is within 80ms, if so, cancel the event
                        Long lastCreatedProcess = creationProcessManager.getLastChatCreation(player);
                        if (lastCreatedProcess != null && (new Date().getTime() - lastCreatedProcess) < 80) {
                            return;
                        }
                        if (currentProcess != null && currentProcess.getStep() == ShopRevancedCreationProcess.ChatCreationStep.BARTER_ITEM) {
                            plugin.getCreativeSelectionListener().putPlayerInCreativeSelection(player, clicked.getLocation(), false);
                            return;
                        } else if (currentProcess == null && player.isSneaking()) {
                            //if the player has created a new process in the last 5 seconds, block them from creating another
                            if (lastCreatedProcess != null && (new Date().getTime() - lastCreatedProcess) < plugin.getDebug_shopCreateCooldown()) {
                                ShopRevancedMessage.sendMessage("interactionIssue", "createCooldown", player, null);
                                return;
                            }

                            BlockFace signFacing = plugin.getShopRevancedCreationUtil().calculateBlockFaceForSign(player, clicked, event.getBlockFace());
                            if (signFacing == null) {
                                return;
                            }

                            ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, clicked, signFacing);
                                                        creationProcessManager.putShopRevancedCreationProcess(player, process);
                            creationProcessManager.recordLastChatCreation(player);
                            process.markInteracted();
                            plugin.getCreativeSelectionListener().putPlayerInCreativeSelection(player, clicked.getLocation(), false);
                        }
                    }
                    return;
                } else {
                    ShopRevancedCreationProcess currentProcess = creationProcessManager.getShopRevancedCreationProcess(player);
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

                if (!player.isSneaking())
                    return;

                Long lastCreatedProcess = creationProcessManager.getLastChatCreation(player);
                if (lastCreatedProcess != null) {
                    //if the player has created a new process in the last 5 seconds, block them from creating another
                    long diff = (new Date().getTime() - lastCreatedProcess);
                    if (diff < plugin.getDebug_shopCreateCooldown()) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "createCooldown", player, null);
                        return;
                    }
                }
                // Cleanup the last process if needed and cancel the existing ShopRevanced creation process if it exists
                if (creationProcessManager.getShopRevancedCreationProcess(player) != null) {
                    creationProcessManager.cancelShopRevancedCreationProcess(player);
                    return;
                }

                BlockFace signFacing = plugin.getShopRevancedCreationUtil().calculateBlockFaceForSign(player, clicked, event.getBlockFace());
                if (signFacing == null)
                    return;

                //since player is creating a ShopRevanced via clicking a chest with the item, create a new object to track the steps of that process
                ShopRevancedCreationProcess process = new ShopRevancedCreationProcess(player, clicked, signFacing);
                process.setItemStack(event.getItem());
                                creationProcessManager.putShopRevancedCreationProcess(player, process);
                creationProcessManager.recordLastChatCreation(player);
                process.markInteracted();

                //send player text prompts after they have clicked the chest with the item they want to create a ShopRevanced with
                ShopRevancedMessage.sendMessage("initialCreateInstruction", null, process, player);
                process.displayFloatingText("createHitChest", null);
                List<String> autocomplete = new ArrayList<>();
                Arrays.asList(ShopType.values()).forEach((shopType -> autocomplete.add(shopType.toString().toLowerCase())));
                try {
                    player.setCustomChatCompletions(autocomplete);
                } catch (Error | Exception error) { // Suppress error if autocomplete is not supported
                }
                if ((!plugin.usePerms() && player.isOp()) || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"))) {
                    ShopRevancedMessage.sendMessage("adminCreateHitChest", null, process, player);
                }

                //give player a limited amount of time to finish creating the ShopRevanced until it is deleted
                final UUID originalProcessUUID = process.getUniqueID();
                plugin.getFoliaLib().getScheduler().runLater(() -> {
                    //the ShopRevanced has still not been initialized with an item from a player
                    ShopRevancedCreationProcess currentProcess = creationProcessManager.getShopRevancedCreationProcess(player);
                    if (currentProcess != null && currentProcess.getUniqueID().equals(originalProcessUUID)) {
                        currentProcess.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        plugin.getCreativeSelectionListener().removePlayerFromCreativeSelection(player);
                        ShopRevancedMessage.sendMessage("interactionIssue", "createHitChestTimeout", currentProcess, player);
                    }
                }, plugin.getShopCreationTimeout() * 20L);
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (creationProcessManager.getShopRevancedCreationProcess(player) != null) {
            ShopRevancedCreationProcess process = creationProcessManager.getShopRevancedCreationProcess(player);
            String message = event.getMessage();
            plugin.getLogger().debug("ShopRevanced Creation Process: " + process.getStep() + " Player " + player.getName() + " input: " + message, true);

            // Allow player to cancel ShopRevanced creation at any step by typing "cancel"
            if (message != null && message.equalsIgnoreCase("cancel")) {
                process.cleanup();
                creationProcessManager.cancelShopRevancedCreationProcess(player);
                event.setCancelled(true);
                return;
            }

            switch (process.getStep()) {
                case SHOP_TYPE:
                    ShopType type = plugin.getShopRevancedCreationUtil().getShopType(message);
                    if (type == null) {
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        return;
                    }
                    boolean isAdmin = plugin.getShopRevancedCreationUtil().getShopIsAdmin(message);
                    process.setShopType(type);
                    process.setAdmin(isAdmin);
                    event.setCancelled(true);

                    if (type == ShopType.GAMBLE) {
                        ShopRevancedMessage.sendMessage(type.toString(), "createHitChestPrice", process, player);
                    } else {
                        process.displayFloatingText(type.toString(), "createHitChestAmount");
                    }
                    break;
                case ITEM_AMOUNT:
                    int amount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(message);
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
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        return;
                    }
                    process.setItemAmount(amount);
                    event.setCancelled(true);

                    if (process.getShopType() == ShopType.BARTER) {
                        process.displayFloatingText(process.getShopType().toString(), "createHitChest");
                        if (plugin.allowCreativeSelection()) {
                            ShopRevancedMessage.sendMessage(process.getShopType().toString(), "initializeBarterAlt", player, null);
                        }
                    } else {
                        process.displayFloatingText(process.getShopType().toString(), "createHitChestPrice");
                    }
                    break;
                case ITEM_PRICE:
                    double price = plugin.getShopRevancedCreationUtil().getShopPrice(player, message, process.getShopType());
                    if (price == -1) {
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        return;
                    }
                    process.setPrice(price);
                    event.setCancelled(true);

                    if (process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.ITEM_PRICE_COMBO) {
                        process.displayFloatingText(process.getShopType().toString(), "createHitChestPriceCombo");
                        return;
                    }
                    if (process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED) {
                        process.createShop(player);
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                    }
                    break;
                case ITEM_PRICE_COMBO:
                    double priceCombo = plugin.getShopRevancedCreationUtil().getShopPriceCombo(player, message, process.getShopType());
                    if (priceCombo == -1) {
                        //instead of cancelling the chat event, just let them know what they typed wasnt a number and break them out of the creation process so they aren't chat locked
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        return;
                    }
                    process.setPriceCombo(priceCombo);
                    event.setCancelled(true);

                    if (process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED) {
                        process.createShop(player);
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                    }
                    break;
                case BARTER_ITEM_AMOUNT:
                    int barterAmount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(message);
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
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                        return;
                    }
                    process.setPrice(barterAmount);
                    event.setCancelled(true);

                    if (process.getStep() == ShopRevancedCreationProcess.ChatCreationStep.FINISHED) {
                        process.createShop(player);
                        process.cleanup();
                        creationProcessManager.removeShopRevancedCreationProcess(player);
                    }
                    break;
                // ITEM, BARTER_ITEM, or FINISHED
                default:
                    // If the user chatted and we were not in one of the earlier steps, cancel the creation process
                    // This will happen if the user was meant to select an ITEM or BARTER_ITEM, and exited the window
                    // without selecting their item to buy.
                    // This prevents chat from being locked for the player
                    process.cleanup();
                    creationProcessManager.cancelShopRevancedCreationProcess(player);
                    break;
            }
        }
    }
}

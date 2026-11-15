package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.event.PlayerDestroyShopEvent;
import com.theflood424k.shoprevanced.event.PlayerResizeShopEvent;
import com.theflood424k.shoprevanced.integration.WorldGuardHook;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.util.CurrencyType;
import com.theflood424k.shoprevanced.util.DisplayUtil;
import com.theflood424k.shoprevanced.util.EconomyUtils;
import com.theflood424k.shoprevanced.util.ItemStackUtils;
import com.theflood424k.shoprevanced.util.ShopRevancedCreationProcess;
import com.theflood424k.shoprevanced.util.ShopRevancedMessage;
import com.theflood424k.shoprevanced.util.ShopActionType;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Handles shop destruction and protection events: breaking shop signs/chests,
 * protecting blocks beneath shop chests, and shop expansion via chest placement.
 * <p>
 * Extracted from {@code MiscListener} to isolate protection logic into a
 * single-responsibility listener.
 */
public class ShopProtectionListener implements Listener {

    private final ShopRevanced plugin;
    private final ShopCreationProcessManager creationProcessManager;

    public ShopProtectionListener(ShopRevanced plugin, ShopCreationProcessManager creationProcessManager) {
        this.plugin = plugin;
        this.creationProcessManager = creationProcessManager;
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
            if (shop.isFakeSign()) {
                event.setDropItems(false);
            }
            if (!shop.isInitialized()) {
                event.setCancelled(true);
                return;
            }

            if (plugin.getDestroyShopRequiresSneak()) {
                if (!player.isSneaking()) {
                    event.setCancelled(true);
                    plugin.getLogger().trace("[ShopProtectionListener.shopDestroy : getDestroyShopRequiresSneak] updateSign");
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
                if (cost > 0) {
                    // Check for funds
                    if (!EconomyUtils.hasSufficientFunds(player, player.getInventory(), cost)) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyInsufficientFunds", player, shop);
                        event.setCancelled(true);
                        return;
                    }
                    // Remove funds
                    boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
                    if (!removed) {
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

                if (shop.isFakeSign()) {
                    event.setDropItems(false);
                }

                if ((!shop.isAdmin()) && plugin.returnCreationCost() && plugin.getCreationCost() > 0) {
                    if (plugin.getCurrencyType() != CurrencyType.ITEM) {
                        EconomyUtils.addFunds(shop.getOwner(), player.getInventory(), plugin.getCreationCost());
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

                    if (shop.isFakeSign()) {
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
            com.theflood424k.shoprevanced.util.ShopRevancedCreationProcess process = creationProcessManager.getShopRevancedCreationProcessByChest(b.getLocation());
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
                            creationProcessManager.cancelShopRevancedCreationProcess(player);
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

            InventoryHolder ih = ((InventoryHolder) b.getState()).getInventory().getHolder();

            if (ih instanceof DoubleChest) {
                if (shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && (player.hasPermission("ShopRevanced.operator") || player.hasPermission("ShopRevanced.destroy.other")))) {

                    // the broken block was the initial chest with the sign
                    if (shop.getChestLocation().equals(b.getLocation())) {
                        ShopRevancedMessage.sendMessage("interactionIssue", "destroyChest", player, shop);
                        // event.setCancelled(true);
                        shop.sendEffects(false, player);
                        return;
                    } else {
                        PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), false);
                        Bukkit.getPluginManager().callEvent(e);

                        if (e.isCancelled()) {
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
            } else {
                if (shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && (player.hasPermission("ShopRevanced.operator") || player.hasPermission("ShopRevanced.destroy.other")))) {
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
    public void onBreakBlockUnderShop(BlockBreakEvent event) {
        //if the block under a chest has been broken, check that its a ShopRevanced chest
        if (com.theflood424k.shoprevanced.util.DisplayUtil.isChest(event.getBlock().getRelative(BlockFace.UP).getType())) {
            AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getBlock().getRelative(BlockFace.UP));
            if (shop != null) {
                //if it is a ShopRevanced chest, don't allow it to be broken unless its by the owner or someone with permission
                Player player = event.getPlayer();
                if (!(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator")))) {
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

                if (e.isCancelled()) {
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

                    if (e.isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }

                } else
                    event.setCancelled(true);
            }
        }
    }
}

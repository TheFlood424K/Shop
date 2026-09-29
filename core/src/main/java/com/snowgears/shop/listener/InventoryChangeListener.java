package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for inventory changes that could affect shop stock levels.
 * When external modifications are detected (hoppers, player manual edits, etc.),
 * marks the associated shop's stock cache as dirty to force recalculation.
 */
public class InventoryChangeListener implements Listener {

    private final Set<Location> recentlyProcessed = new HashSet<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;
        checkAndMarkDirty(inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) return;
        checkAndMarkDirty(inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Hopper transfers, etc.
        checkAndMarkDirty(event.getSource());
        checkAndMarkDirty(event.getDestination());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Check when chest is opened manually
        Inventory inventory = event.getInventory();
        if (inventory == null) return;
        checkAndMarkDirty(inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // If a chest/container is broken, invalidate any shop using it
        invalidateShopAt(block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // If a chest is placed where a shop was, invalidate that shop
        invalidateShopAt(event.getBlock().getLocation());
    }

    private void checkAndMarkDirty(Inventory inventory) {
        if (inventory == null) return;
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof Chest)) return;
        Chest chest = (Chest) holder;
        Location chestLoc = chest.getLocation();
        if (chestLoc == null) return;
        invalidateShopAt(chestLoc);

        // Also check double chest counterpart
        if (chest.getInventory() instanceof DoubleChestInventory) {
            DoubleChestInventory dci = (DoubleChestInventory) chest.getInventory();
            DoubleChest doubleChest = (DoubleChest) dci.getHolder();
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();
            if (left != null && left.getLocation() != null) {
                invalidateShopAt(left.getLocation());
            }
            if (right != null && right.getLocation() != null) {
                invalidateShopAt(right.getLocation());
            }
        }
    }

    private void invalidateShopAt(Location location) {
        if (location == null) return;

        // Simple debouncing to avoid duplicate processing
        Location key = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (recentlyProcessed.contains(key)) return;
        recentlyProcessed.add(key);

        // Clean up old entries periodically
        if (recentlyProcessed.size() > 1000) {
            recentlyProcessed.clear();
        }

        Shop plugin = Shop.getPlugin();
        if (plugin == null) return;

        AbstractShop shop = plugin.getShopHandler().getShopByChest(location.getBlock());
        if (shop != null) {
            shop.markStockDirty();
        }
    }
}
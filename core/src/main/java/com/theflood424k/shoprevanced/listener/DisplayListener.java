package com.theflood424k.shoprevanced.listener;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.DisplayTagOption;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.GambleShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import com.theflood424k.shoprevanced.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import com.tcoded.folialib.wrapper.task.WrappedTask;

public class DisplayListener implements Listener {

    public ShopRevanced plugin;
    private ArrayList<ItemStack> allServerRecipeResults = new ArrayList<>();
    private WrappedTask repeatingViewTask;
    private WrappedTask repeatingDisplayTask;

    public void startRepeatingDisplayViewTask() {
        if (plugin.getDisplayTagOption() == DisplayTagOption.VIEW_SIGN) {
            repeatingViewTask =
                    plugin.getFoliaLib().getScheduler().runTimer(() -> {

                        for (Player player : plugin.getServer().getOnlinePlayers()) {
                            if (player == null || !player.isOnline()) continue;

                            plugin.getFoliaLib().getScheduler().runAtEntity(
                                    player,
                                    task -> {
                                        try {
                                            Block block = player.getTargetBlockExact(8);
                                            if (block != null &&
                                                    block.getBlockData() instanceof WallSign) {

                                                AbstractShop shop =
                                                        plugin.getShopRevancedHandler()
                                                                .getShop(block.getLocation());

                                                if (shop != null) {
                                                    shop.getDisplay()
                                                            .showDisplayTags(player);
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                            );
                        }
                    }, 1, 15);
        }

        startRepeatingDisplayProcessingTask();
    }

    private void startRepeatingDisplayProcessingTask() {

        repeatingDisplayTask =
                plugin.getFoliaLib().getScheduler().runTimer(() -> {

                    List<Player> players =
                            new ArrayList<>(plugin.getServer().getOnlinePlayers());

                    for (int i = 0; i < players.size(); i++) {
                        Player player = players.get(i);
                        if (player == null || !player.isOnline()) continue;

                        int delay = i;

                        plugin.getFoliaLib().getScheduler().runAtEntityLater(
                                player,
                                task -> {
                                    if (player.isOnline()) {
                                        plugin.getShopRevancedHandler()
                                                .processShopDisplaysNearPlayer(player);
                                    }
                                },
                                delay
                        );
                    }
                }, 1, (long) (plugin.getDisplayProcessInterval() * 20));
    }

    public DisplayListener(ShopRevanced instance) {
        plugin = instance;

        // Load all recipes on server once all other plugins are loaded
        plugin.getFoliaLib().getScheduler().runLater(task -> {
            HashMap<ItemStack, Boolean> recipes = new HashMap();
            Iterator<Recipe> recipeIterator = plugin.getServer().recipeIterator();
            while(recipeIterator.hasNext()) {
                ItemStack result = recipeIterator.next().getResult();
                if(result.getAmount() != 0)
                    recipes.put(result, true);
            }
            allServerRecipeResults.addAll(recipes.keySet());
            Collections.shuffle(allServerRecipeResults);
        }, 1);
    }

    public ItemStack getRandomItem(AbstractShop shop){
        if (shop == null)
            return new ItemStack(Material.AIR);


        if(InventoryUtils.isEmpty(shop.getInventory())) {
            int index = new Random().nextInt(allServerRecipeResults.size());
            return allServerRecipeResults.get(index);
        } else {
            return InventoryUtils.getRandomItem(shop.getInventory());
        }
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getToBlock().getRelative(BlockFace.DOWN));
        if (shop != null)
            event.setCancelled(true);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getBlock().getRelative(event.getDirection()).getRelative(BlockFace.DOWN));
        if (shop != null && shop.getDisplay().getType() != DisplayType.NONE)
            event.setCancelled(true);

        shop = plugin.getShopRevancedHandler().getShopByChest(event.getBlock().getRelative(event.getDirection()).getRelative(BlockFace.UP));
        if (shop != null)
            event.setCancelled(true);

        for(Block pushedBlock : event.getBlocks()){
            shop = plugin.getShopRevancedHandler().getShopByChest(pushedBlock.getRelative(event.getDirection()).getRelative(BlockFace.DOWN));
            if (shop != null && shop.getDisplay().getType() != DisplayType.NONE) {
                event.setCancelled(true);
                return;
            }

            shop = plugin.getShopRevancedHandler().getShopByChest(pushedBlock.getRelative(event.getDirection()).getRelative(BlockFace.UP));
            if (shop != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block pulledBlock = event.getBlock().getRelative(event.getDirection().getOppositeFace()).getRelative(event.getDirection().getOppositeFace()).getRelative(BlockFace.UP);
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(pulledBlock);
        if (shop != null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(event.getBlock().getRelative(BlockFace.DOWN));
        if(shop != null){
            if(shop.getDisplay().getType() == null && plugin.getDisplayType() != DisplayType.NONE)
                event.setCancelled(true);
            else if (shop.getDisplay().getType() != null && shop.getDisplay().getType() != DisplayType.NONE)
                event.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onShopInventoryClose(InventoryCloseEvent event) {
        try {
            if(event.getInventory().getHolder() instanceof Container){
                Container container = ((Container)event.getInventory().getHolder());
                AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(container.getBlock());

                if(shop == null) {
                    return;
                }

                shop.updateStock();

                //make sure to set gamble item again if player set it to new custom items
                if(shop.getType() == ShopType.GAMBLE){
                    ((GambleShop)shop).setGambleItem();
                }
            }
            //for some reason, DoubleChest does not extend Container like Chest does
            else if(event.getInventory().getHolder() instanceof DoubleChest){
                DoubleChest doubleChest = ((DoubleChest)event.getInventory().getHolder());
                AbstractShop shop = plugin.getShopRevancedHandler().getShopByChest(doubleChest.getLocation().getBlock());

                if(shop == null) {
                    return;
                }

                shop.updateStock();

                //make sure to set gamble item again if player set it to new custom items
                if(shop.getType() == ShopType.GAMBLE){
                    ((GambleShop)shop).setGambleItem();
                }
            }
        } catch (NoClassDefFoundError e) {}
    }

    public void cancelRepeatingViewTask(){
        if (repeatingViewTask != null) {
            plugin.getFoliaLib().getScheduler().cancelTask(repeatingViewTask);
        }
        if (repeatingDisplayTask != null) {
            plugin.getFoliaLib().getScheduler().cancelTask(repeatingDisplayTask);
        }
    }
}







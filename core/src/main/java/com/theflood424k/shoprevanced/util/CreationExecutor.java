package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.display.DisplayType;
import com.theflood424k.shoprevanced.event.PlayerCreateShopEvent;
import com.theflood424k.shoprevanced.event.PlayerInitializeShopEvent;
import com.theflood424k.shoprevanced.shop.AbstractShop;
import com.theflood424k.shoprevanced.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Executes shop creation operations: creating shops, initializing them,
 * sending success messages, and cleaning up creation processes.
 */
public class CreationExecutor {

    private final ShopRevanced plugin;

    public CreationExecutor(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    public AbstractShop createShop(Player player, Block chestBlock, Block signBlock,
                                   PricePair pricePair, int amount, boolean isAdmin,
                                   ShopType type, BlockFace signDirection, boolean isFakeSign) {
        String playerMessage = null;
        if (type == null)
            type = ShopType.SELL;

        final AbstractShop shop = AbstractShop.create(
                signBlock.getLocation(), player.getUniqueId(),
                pricePair.getPrice(), pricePair.getPriceCombo(), amount, isAdmin, type, signDirection);
        shop.setFakeSign(isFakeSign);

        if (plugin.usePerms()) {
            if (!(player.hasPermission("ShopRevanced.create." + type.toString().toLowerCase())
                    || player.hasPermission("ShopRevanced.create"))) {
                playerMessage = ShopRevancedMessage.getUnformattedMessage("permission", "create");
            }
        }

        if (type == ShopType.GAMBLE) {
            isAdmin = true;
            shop.setAdmin(true);
            if ((plugin.usePerms() && !player.hasPermission("ShopRevanced.operator"))
                    || (!plugin.usePerms() && !player.isOp())) {
                playerMessage = ShopRevancedMessage.getUnformattedMessage("permission", "create");
            }
        }

        double cost = plugin.getCreationCost();
        if (cost > 0) {
            if (!EconomyUtils.hasSufficientFunds(player, player.getInventory(), cost)) {
                playerMessage = ShopRevancedMessage.getUnformattedMessage("interactionIssue", "createInsufficientFunds");
            }
        }

        if (player.isOp() || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"))) {
            playerMessage = null;
        }

        AbstractShop existingShop = plugin.getShopRevancedHandler().getShopByChest(chestBlock);
        if (existingShop != null && !existingShop.isAdmin()) {
            if (!existingShop.getOwnerUUID().equals(player.getUniqueId())) {
                playerMessage = ShopRevancedMessage.getUnformattedMessage("interactionIssue", "createOtherPlayer");
            }
        }

        if (playerMessage != null) {
            if (!playerMessage.isEmpty())
                ShopRevancedMessage.sendMessage(playerMessage, player, shop);
            return null;
        }

        if (chestBlock.getState() instanceof Container) {
            existingShop = plugin.getShopRevancedHandler().getShopByChest(chestBlock);
            if (existingShop != null && chestBlock.getLocation().equals(existingShop.getChestLocation())) {
                ShopRevancedMessage.sendMessage("interactionIssue", "createOtherPlayer", player, shop);
                return null;
            }

            if (!(signBlock.getBlockData() instanceof WallSign)) {
                if (!signBlock.getType().toString().contains("_SIGN")) {
                    return null;
                }
                String wallSignString = signBlock.getType().toString().replaceAll("_SIGN", "_WALL_SIGN");
                signBlock.setType(Material.valueOf(wallSignString));
                org.bukkit.block.data.Directional wallSignData =
                        (org.bukkit.block.data.Directional) signBlock.getBlockData();
                wallSignData.setFacing(signDirection);
                signBlock.setBlockData(wallSignData);
            }
            Sign signBlockState = (Sign) signBlock.getState();
            signBlockState.update();

            shop.setAdmin(isAdmin);
            boolean loaded = shop.load();
            if (!loaded) {
                plugin.getLogger().warning("ShopRevanced creation failed, unable to load the ShopRevanced. Aborting ShopRevanced creation.");
                return null;
            }

            PlayerCreateShopEvent e = new PlayerCreateShopEvent(player, shop);
            plugin.getServer().getPluginManager().callEvent(e);

            plugin.getLogHandler().logAction(player, shop, ShopActionType.CREATE);

            if (e.isCancelled())
                return null;

            if (plugin.getDisplayLightLevel() > 0) {
                Block displayBlock = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
                if (UtilMethods.materialIsNonIntrusive(displayBlock.getType())) {
                    displayBlock.setType(Material.LIGHT);
                    org.bukkit.block.data.type.Light data = (org.bukkit.block.data.type.Light) displayBlock.getBlockData();
                    data.setLevel(plugin.getDisplayLightLevel());
                    displayBlock.setBlockData(data);
                }
            }

            if (type == ShopType.GAMBLE) {
                shop.setItemStack(plugin.getGambleDisplayItem());
                shop.setAmount(1);
                plugin.getShopRevancedHandler().addShop(shop);
                shop.getDisplay().setType(DisplayType.LARGE_ITEM, false);
                sendCreationSuccess(player, shop);
                plugin.getLogHandler().logAction(player, shop, ShopActionType.INIT);
                return null;
            }

            plugin.getShopRevancedHandler().addShop(shop);
            plugin.getLogger().trace("[ShopRevancedCreationUtil.createShop] updateSign");
            shop.updateSign();
        }
        return shop;
    }

    public void cleanupShopRevancedCreationProcess(Player player) {
        ShopRevancedCreationProcess process = plugin.getShopCreationProcessManager().getShopRevancedCreationProcess(player);
        if (process != null) {
            process.cleanup();
            plugin.getShopCreationProcessManager().removeShopRevancedCreationProcess(player);
        }
    }

    public void sendCreationSuccess(Player player, AbstractShop shop) {
        if (shop.getDisplay() != null)
            shop.getDisplay().spawn(player);
        plugin.getLogger().trace("[ShopRevancedCreationUtil.sendCreationSuccess] updateSign");
        shop.updateSign(true);
        shop.setNeedsSave(true);
        ShopRevancedMessage.sendMessage(shop.getType().toString(), "create", player, shop);
        shop.sendEffects(true, player);
        plugin.getShopRevancedHandler().saveShops(shop.getOwnerUUID(), true);
        cleanupShopRevancedCreationProcess(player);
    }

    public boolean initializeShop(AbstractShop shop, Player player, ItemStack item, ItemStack barterItem) {
        if (!player.getUniqueId().equals(shop.getOwnerUUID())) {
            if ((!plugin.usePerms() && !player.isOp())
                    || (plugin.usePerms() && !player.hasPermission("ShopRevanced.operator"))) {
                ShopRevancedMessage.sendMessage("interactionIssue", "initialize", player, shop);
                shop.sendEffects(false, player);
                return false;
            }
        }

        if (item.getType() == Material.AIR) {
            return false;
        }

        if (plugin.getDisplayType() != DisplayType.NONE) {
            Block aboveShop = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
            if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                if (plugin.forceDisplayToNoneIfBlocked()) {
                    shop.getDisplay().setType(DisplayType.NONE, false);
                } else {
                    ShopRevancedMessage.sendMessage("interactionIssue", "displayRoom", player, shop);
                    shop.sendEffects(false, player);
                    return false;
                }
            }
        }

        double cost = plugin.getCreationCost();
        if (cost > 0 && !shop.isAdmin()
                && !(shop.getType() == ShopType.BARTER && barterItem == null)) {
            boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
            if (!removed) {
                ShopRevancedMessage.sendMessage("interactionIssue", "createInsufficientFunds", player, shop);
                shop.sendEffects(false, player);
                return false;
            }
        }

        try {
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                if (shop.getChestLocation().getBlock().getState() instanceof org.bukkit.block.ShulkerBox) {
                    return false;
                }
            }
        } catch (NoSuchFieldError e) {
        }

        if (!itemsCanBeInitialized(player, item, barterItem)) {
            shop.sendEffects(false, player);
            return false;
        }

        if (shop.getItemStack() == null && item != null) {
            PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
            Bukkit.getServer().getPluginManager().callEvent(e);
            if (e.isCancelled())
                return false;

            shop.setItemStack(item);

            ShopRevancedCreationProcess process = plugin.getShopCreationProcessManager().getShopRevancedCreationProcess(player);
            if (shop.getType() == ShopType.BARTER && barterItem == null) {
                ShopRevancedMessage.sendMessage(shop.getType().toString(), "initializeInfo", player, shop);
                process.setStep(ShopRevancedCreationProcess.ChatCreationStep.SIGN_BARTER_ITEM);
                process.displayFloatingText(shop.getType().toString(), "initializeBarter");
                if (plugin.allowCreativeSelection()) {
                    ShopRevancedMessage.sendMessage("BUY", "initializeAlt", player, shop);
                }
            } else if (shop.getType() != ShopType.BARTER) {
                return true;
            }
        }
        if (shop.getSecondaryItemStack() == null && barterItem != null) {
            PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
            Bukkit.getServer().getPluginManager().callEvent(e);
            if (e.isCancelled())
                return false;
            shop.setSecondaryItemStack(barterItem);
            return true;
        }
        return false;
    }

    private boolean itemsCanBeInitialized(Player player, ItemStack itemStack, ItemStack barterItemStack) {
        boolean isAdmin = (!plugin.usePerms() && player.isOp())
                || (plugin.usePerms() && player.hasPermission("ShopRevanced.operator"));

        if (!isAdmin) {
            boolean passesItemList = plugin.getShopRevancedHandler().passesItemListCheck(itemStack);
            if (!passesItemList) {
                ShopRevancedMessage.sendMessage("interactionIssue", "itemListDeny", player, null);
                return false;
            }
        }

        if (InventoryUtils.itemstacksAreSimilar(itemStack, barterItemStack)) {
            ShopRevancedMessage.sendMessage("interactionIssue", "sameItem", player, null);
            return false;
        }
        return true;
    }
}

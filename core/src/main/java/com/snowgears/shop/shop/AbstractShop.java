package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.util.*;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.Sound;
import org.bukkit.Effect;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static com.snowgears.shop.util.UtilMethods.isMCVersion17Plus;

/**
 * Abstract base class for all shop types.
 */
public abstract class AbstractShop {

    protected UUID id = UUID.randomUUID();
    protected boolean needsSave = false;
    protected boolean isLoaded = false;
    protected Location signLocation;
    protected Location chestLocation;
    protected BlockFace facing;
    protected UUID owner;
    protected ItemStack item;
    protected ItemStack secondaryItem;
    protected AbstractDisplay display;
    protected double price;
    protected int amount;
    protected boolean isAdmin;
    protected ShopType type;
    protected String[] signLines;
    protected boolean signLinesRequireRefresh;
    protected boolean isPerformingTransaction;
    protected ItemStack guiIcon;
    protected boolean fakeSign;

    protected int stock;
    protected Material cachedContainerType;

    public AbstractShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        this.signLocation = signLoc;
        this.owner = player;
        this.price = pri;
        this.amount = amt;
        this.isAdmin = admin;
        this.item = null;
        this.facing = facing;

        this.signLinesRequireRefresh = true; // Reload signs on load in case config changed!

        display = Shop.getPlugin().getShopHandler().createDisplay(this.signLocation);
        fakeSign = false;

        if(isAdmin)
            owner = Shop.getPlugin().getShopHandler().getAdminUUID();
    }

    /**
     * Creates a new shop instance based on the shop type.
     *
     * @param signLoc The location of the shop sign
     * @param player The player UUID
     * @param pri The price
     * @param priCombo The combo price
     * @param amt The amount
     * @param admin Whether this is an admin shop
     * @param shopType The shop type
     * @param facing The facing direction
     * @return a new shop instance, or null if type is invalid
     */
    public static AbstractShop create(Location signLoc, UUID player, double pri, double priCombo, int amt, Boolean admin, ShopType shopType, BlockFace facing) {

        switch(shopType){
            case SELL:
                return new SellShop(signLoc, player, pri, amt, admin, facing);
            case BUY:
                return new BuyShop(signLoc, player, pri, amt, admin, facing);
            case BARTER:
                return new BarterShop(signLoc, player, pri, amt, admin, facing);
            case GAMBLE:
                return new GambleShop(signLoc, player, pri, amt, admin, facing);
            case COMBO:
                return new ComboShop(signLoc, player, pri, priCombo, amt, admin, facing);
        }
        return null;
    }

    /**
     * Sets the shop ID.
     *
     * @param newId The new UUID
     */
    public void setId(UUID newId) {
        this.id = newId;
    }

    /**
     * Gets the shop ID.
     *
     * @return the shop UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Checks if the chunk containing the shop is loaded.
     *
     * @return true if the chunk is loaded
     */
    public boolean isChunkLoaded() {
        return UtilMethods.isChunkLoaded(this.getSignLocation());
    }

    //this calls BlockData which loads the chunk the shop is in by doing so
    /**
     * Loads this shop from its sign and chest locations.
     * <p>
     * Validates the sign and chest blocks, extracts facing direction,
     * and initializes the shop's container type.
     * </p>
     *
     * @return true if loaded successfully, false otherwise
     */
    public boolean load() {
        try {
            Block signBlock = signLocation.getBlock();
            if (signBlock.getType() == Material.AIR) {
                Shop.getPlugin().getLogger().warning("Error attempting to load shop! No sign found for Shop (detected: AIR), deleting shop: " + this);
                this.delete();
                return false;
            }
            if (!(signBlock.getBlockData() instanceof WallSign)) {
                Shop.getPlugin().getLogger().warning("Error attempting to load shop! Sign Block for Shop is not a WallSign (detected: " + signBlock.getType() + "), deleting shop: " + this);
                this.delete();
                return false;
            }
            facing = ((WallSign) signBlock.getBlockData()).getFacing();
            Block chestBlock = signBlock.getRelative(facing.getOppositeFace());
            chestLocation = chestBlock.getLocation();

            if (!Shop.getPlugin().getShopHandler().isChest(chestBlock)){
                Shop.getPlugin().getLogger().warning("Error attempting to load shop! Invalid block type detected when trying to load Shop Chest (detected: " + chestBlock.getType() + "), deleting shop: " + this);
                this.delete();
                return false;
            }
            // Now that we are loaded, we can update the stock
            // Force sign lines to refresh on load. This avoids stale sign text when the cached stock
            // matches the newly calculated stock (updateStock() only forces sign updates on change).
            this.signLinesRequireRefresh = true;
            this.updateStock();
            // Prime the container type on the owning region thread so off-thread readers
            // (e.g. the metrics task) can use the cached value without touching the block.
            this.getContainerType();
            Shop.getPlugin().getLogger().debug("Loaded shop successfully: " + this);
            isLoaded = true;
            return true;
        } catch (Error | Exception error) {
            //this shop has no sign on it. return false
            Shop.getPlugin().getLogger().warning("Unknown error while attempting to load Shop sign and/or chest! Deleting shop: " + this);
            this.delete();
            return false;
        }
    }

    /**
     * Checks if the shop needs to be saved.
     *
     * @return true if the shop needs saving
     */
    public boolean needsSave() {
        return needsSave;
    }

    /**
     * Sets whether the shop needs to be saved.
     *
     * @param shouldSave true if the shop should be saved
     */
    public void setNeedsSave(boolean shouldSave) {
        needsSave = shouldSave;
    }

    //abstract methods that must be implemented in each shop subclass

    /**
     * Calculates the current stock level for this shop.
     * <p>
     * For admin shops returns Integer.MAX_VALUE. Otherwise determines how
     * many items are available divided by the amount per transaction.
     * </p>
     *
     * @return the calculated stock level
     */
    protected int calculateStock() {
        if(this.isAdmin) {
            // There is always stock in the admin shop!
            stock = Integer.MAX_VALUE;
            return stock;
        }
        if(this.getInventory() == null || this.getItemStack() == null) {
            //if stock is already calculated but now inventory is null, use old stock value
            if(stock != -1)
                return stock;
            else
                stock = -1;
            return stock;
        }
        int itemsInShop = InventoryUtils.getAmount(this.getInventory(), this.getItemStack());
        stock = itemsInShop / this.getAmount();
        if(stock == 0 && Shop.getPlugin().getAllowPartialSales()){
            // Calculate the minimum items required to show as in stock
            int minItemAmountRequired = (int) Math.ceil(1 / this.getPricePerItem());

            if(itemsInShop >= minItemAmountRequired){
                stock = 1;
            }
        }
        return stock;
    }

    /**
     * Updates the stock level for this shop.
     * <p>
     * Recalculates stock and updates the sign if it has changed.
     * Also updates Bluemap markers if applicable.
     * </p>
     */
    public void updateStock() {
        int oldStock = stock;

        // Update the stock
        this.calculateStock();

        // Update sign if needed
        boolean hasStockChange = stock != oldStock;
        if(hasStockChange){
            Shop.getPlugin().getLogger().trace("[AbstractShop.updateStock] updateSign, new stock != oldStock! newStock: " + stock + " old stock: " + oldStock + "\n" + this);
            this.updateSign(true);

            //also set marker in here if using a marker integration
            if(Shop.getPlugin().getBluemapHookListener() != null) {
                Shop.getPlugin().getBluemapHookListener().updateMarker(this);
            }

            needsSave = true;
            return;
        }

        // Allow sign to update if there is a pending change (signLinesRequireRefresh)
        this.updateSign();
    }

    /**
     * Gets the current stock level for this shop.
     *
     * @return the current stock level (Integer.MAX_VALUE for admin shops)
     */
    public int getStock(){
        if(isAdmin){
            return Integer.MAX_VALUE;
        }
        return stock;
    }

    /**
     * Sets the stock level when loading from storage.
     *
     * @param stock the stock level to set
     */
    public void setStockOnLoad(int stock){
        this.stock = stock;
    }

    /**
     * Checks if this shop has been initialized with an item.
     *
     * @return true if the shop has an item set
     */
    public boolean isInitialized(){
        return (item != null);
    }

    //getter methods

    /**
     * Gets the sign location.
     *
     * @return the sign location
     */
    public Location getSignLocation() {
        return signLocation;
    }

    /**
     * Gets the wall sign block data.
     *
     * @return the wall sign, or null if not loaded or not a wall sign
     */
    public WallSign getSign(){
        if (!this.isChunkLoaded()) { return null; }
        BlockData signBlockData = this.getSignLocation().getBlock().getBlockData();
        if(signBlockData instanceof WallSign){
            return (WallSign)signBlockData;
        }
        return null;
    }

    /**
     * Gets the chest location.
     *
     * @return the chest location
     */
    public Location getChestLocation() {
        return chestLocation;
    }

    /**
     * Gets the inventory for this shop's chest.
     *
     * @return the inventory, or null if unavailable
     */
    public Inventory getInventory() {
        if(chestLocation == null || signLocation == null || !this.isChunkLoaded())
            return null;
        Block chestBlock = chestLocation.getBlock();
        if(chestBlock.getState() instanceof InventoryHolder){
            return ((InventoryHolder)(chestBlock.getState())).getInventory();
        }
        return null;
    }

    /**
     * Gets the cached container type.
     *
     * @return the container material, or null if unavailable
     */
    public Material getContainerType() {
        if(cachedContainerType != null)
            return cachedContainerType;
        if(chestLocation == null || !this.isChunkLoaded())
            return null;
        try {
            cachedContainerType = chestLocation.getBlock().getType();
            return cachedContainerType;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the owner UUID.
     *
     * @return the owner UUID
     */
    public UUID getOwnerUUID() {
        return owner;
    }

    /**
     * Gets the owner name.
     *
     * @return "admin" for admin shops, cached player name, or "CLOSED" if unavailable
     */
    public String getOwnerName() {
        if(this.isAdmin())
            return "admin";

        if (this.getOwnerUUID() != null) {
            // Use cache first - this avoids expensive disk I/O
            return PlayerNameCache.getName(this.getOwnerUUID());
        }

        return ChatColor.RED + "CLOSED";
    }

    /**
     * Gets the offline player owner.
     *
     * @return the offline player
     */
    public OfflinePlayer getOwner() {
        return Bukkit.getOfflinePlayer(this.owner);
    }

    /**
     * Gets a clone of the item stack with transaction amount set.
     *
     * @return the item stack, or null if not set
     */
    public ItemStack getItemStack() {
        if (item != null) {
            ItemStack is = item.clone();
            is.setAmount(this.getAmount());
            return is;
        }
        return null;
    }

    /**
     * Gets a clone of the secondary item stack for barter shops.
     *
     * @return the secondary item stack, or null if not set
     */
    public ItemStack getSecondaryItemStack() {
        if (secondaryItem != null) {
            ItemStack is = secondaryItem.clone();
            is.setAmount((int)this.getPrice());
            return is;
        }
        return null;
    }

    /**
     * Gets the display associated with this shop.
     *
     * @return the display instance
     */
    public AbstractDisplay getDisplay() {
        return display;
    }

    /**
     * Gets the price for this shop.
     *
     * @return the price
     */
    public double getPrice() {
        return price;
    }

    /**
     * Gets the price per individual item.
     *
     * @return the price per item
     */
    public double getPricePerItem() {
        // Calculate pricePerItem for partial sales, round up!
        double pricePer = this.getPrice() / this.getAmount();

        return pricePer;
    }

    /**
     * Gets the number of items per price unit.
     *
     * @return items per price unit
     */
    public double getItemsPerPriceUnit() {
        // Calculate items you can get for each price unit, round down!
        double pricePer = this.getAmount() / this.getPrice();

        return pricePer;
    }

    /**
     * Gets the formatted price string.
     *
     * @return the price string
     */
    public String getPriceString() {
        if(this.type == ShopType.BARTER && this.isInitialized()){
            return (int)this.getPrice() + " " + Shop.getPlugin().getItemNameUtil().getName(this.getSecondaryItemStack()).toPlainText();
        }
        return Shop.getPlugin().getPriceString(this.price, false);
    }

    /**
     * Gets the formatted price per item string.
     *
     * @return the price per item string
     */
    public String getPricePerItemString() {
        double pricePer = this.getPricePerItem();
        return Shop.getPlugin().getPriceString(pricePer, true);
    }

    /**
     * Checks if this is an admin shop.
     *
     * @return true if admin shop
     */
    public boolean isAdmin() {
        return isAdmin;
    }

    /**
     * Sets whether this is an admin shop.
     * <p>
     * Only use this method if the shop has not been added to the main handler maps yet.
     * </p>
     *
     * @param isAdmin true if admin shop
     */
    public void setAdmin(boolean isAdmin){
        this.isAdmin = isAdmin;
        if(isAdmin)
            this.owner = Shop.getPlugin().getShopHandler().getAdminUUID();
    }

    /**
     * Gets the shop type.
     *
     * @return the shop type
     */
    public ShopType getType() {
        return type;
    }

    /**
     * Gets the amount of items per transaction.
     *
     * @return the amount per transaction
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Gets the facing direction of the shop sign.
     *
     * @return the facing direction
     */
    public BlockFace getFacing(){
        return facing;
    }

    /**
     * Gets the GUI icon for this shop.
     *
     * @return the GUI icon
     */
    public ItemStack getGuiIcon(){
        // Load it when it is first called
        if (guiIcon == null) { this.refreshGuiIcon(); }
        return guiIcon;
    }

    //setter methods

    /**
     * Sets the item stack for this shop.
     *
     * @param is the item stack to set
     */
    public void setItemStack(ItemStack is) {
        // If the item stack passed is null, go ahead and just skip it.
        if (is == null) return;

        // Remove "0 Damage" from item meta (old config bug)
        this.item = this.removeZeroDamageMeta(is.clone());
        this.calculateStock();
        this.updateSign(true);
    }

    /**
     * Sets the secondary item stack for this shop.
     *
     * @param is the secondary item stack to set
     */
    public void setSecondaryItemStack(ItemStack is) {
        this.secondaryItem = this.removeZeroDamageMeta(is.clone());
        this.calculateStock();
        this.updateSign(true);
    }

    /**
     * Removes zero damage metadata from an item stack.
     * <p>
     * Removes legacy durability metadata where damage is 0.
     * </p>
     *
     * @param item the item stack to process
     * @return the processed item stack
     */
    public ItemStack removeZeroDamageMeta(ItemStack item) {
        try {
            // In the past we used to explicitly set the durability of an item to be 0, this caused blocks/items to be saved
            // with extra NBT data that we don't actually want. For example, dirt shouldn't have a damage of 0.
            // Detect if we set it to 0, and if so, remove it from the ItemMeta!
            if (item.getItemMeta() instanceof Damageable && ((Damageable) item.getItemMeta()).getDamage() == 0) {
                String components = item.getItemMeta().getAsComponentString(); // example: "[minecraft:damage=53]"

                // Remove it from the array
                components = components.replace(",minecraft:damage=0", ""); // Middle of an array
                components = components.replace("minecraft:damage=0,", ""); // Start of an array
                components = components.replace("minecraft:damage=0", ""); // Only object in array

                // Convert it back into an item
                String itemTypeKey = item.getType().getKey().toString(); // example: "minecraft:diamond_sword"
                String itemAsString = itemTypeKey + components; // results in: "minecraft:diamond_sword[minecraft:damage=53]"
                return Bukkit.getItemFactory().createItemStack(itemAsString);
            }

            // Default return original item
            return item;
        } catch (Exception e) {
            Shop.getPlugin().getLogger().debug("Error removing zero damage meta from item: " + item);
            Shop.getPlugin().getLogger().helpful("checkItemDurability feature may be unsupported on your version of Paper/Spigot!");
            return item;
        } catch (Error e) {
            Shop.getPlugin().getLogger().debug("Error removing zero damage meta from item: " + item);
            Shop.getPlugin().getLogger().helpful("checkItemDurability feature may be unsupported on your version of Paper/Spigot!");
            return item;
        }
    }

    /**
     * Sets the owner UUID.
     *
     * @param newOwner the new owner UUID
     */
    public void setOwner(UUID newOwner){
        this.owner = newOwner;
    }

    /**
     * Sets the price.
     *
     * @param price the price to set
     */
    public void setPrice(double price){
        this.price = price;
    }

    /**
     * Sets the amount per transaction.
     *
     * @param amount the amount to set
     */
    public void setAmount(int amount){
        this.amount = amount;
    }

    /**
     * Refreshes the GUI icon for this shop.
     */
    public void refreshGuiIcon() {
        if(this.type != ShopType.GAMBLE) {
            if (this.getItemStack() == null)
                return;
            guiIcon = this.getItemStack().clone();
            guiIcon.setAmount(1);
        }
        else{
            guiIcon = Shop.getPlugin().getGambleDisplayItem().clone();
            guiIcon.setAmount(1);
        }

        //get the placeholder icon with all of the unformatted fields
        ItemStack placeHolderIcon = Shop.getPlugin().getGuiHandler().getIcon(ShopGuiHandler.GuiIcon.ALL_SHOP_ICON, null, null);

        String name = ShopMessage.formatMessage(placeHolderIcon.getItemMeta().getDisplayName(), this, null, false);
        List<String> lore = new ArrayList<>();
        for(String loreLine : placeHolderIcon.getItemMeta().getLore()){
            // Don't add barter line to non barter shops
            if(loreLine.contains("[barter item]") && this.getType() != ShopType.BARTER) continue;
            // Add all lore lines
            PlaceholderContext context = new PlaceholderContext();
            context.setShop(this);
            lore.add(ShopMessage.format(loreLine, context).toLegacyText());
        }

        ItemMeta iconMeta = guiIcon.getItemMeta();
        iconMeta.setDisplayName(name);
        iconMeta.setLore(lore);

        PersistentDataContainer container = iconMeta.getPersistentDataContainer();
        container.set(Shop.getPlugin().getSignLocationNameSpacedKey(), PersistentDataType.STRING, UtilMethods.getCleanLocation(this.getSignLocation(), true));

        guiIcon.setItemMeta(iconMeta);
    }

    /**
     * Gets the durability percentage of the shop item.
     *
     * @return the durability percentage
     */
    public int getItemDurabilityPercent(){
        ItemStack item = this.getItemStack().clone();
        return UtilMethods.getDurabilityPercent(item);
    }

    /**
     * Gets the durability percentage of the secondary shop item.
     *
     * @return the durability percentage
     */
    public int getSecondaryItemDurabilityPercent(){
        ItemStack item = this.getSecondaryItemStack().clone();
        return UtilMethods.getDurabilityPercent(item);
    }

    /**
     * Checks if a transaction is currently being performed.
     *
     * @return true if performing a transaction
     */
    public boolean isPerformingTransaction(){
        return isPerformingTransaction;
    }

    /**
     * Gets the current sign lines.
     *
     * @return the sign lines
     */
    public String[] getSignLines() { return signLines; }
    /**
     * Updates the sign display.
     */
    public void updateSign() { this.updateSign(false); }
    /**
     * Updates the sign display.
     *
     * @param forceUpdate if true forces update
     */
    public void updateSign(boolean forceUpdate) {
        // If we don't need to update the lines, then don't update them!
        if (!signLinesRequireRefresh && !forceUpdate) { return; }
        // Do not trigger the sign update if the chunk has not been loaded yet
        if (!this.isChunkLoaded()) { if (forceUpdate) { signLinesRequireRefresh = true; } return; }
        // Immediately set to false to prevent multiple calls to updateSign overlapping
        signLinesRequireRefresh = false;
        signLines = ShopMessage.getSignLines(this, this.type);

        // Use the sign's location to ensure the update runs in the correct region in Folia
        Shop.getPlugin().getFoliaLib().getScheduler().runAtLocationLater(signLocation, task -> {
            // Update the GUI Icon since the sign needs an update.
            refreshGuiIcon();

            Sign signBlock;
            try {
                signBlock = (Sign) signLocation.getBlock().getState(); // this will load the sign
            } catch (ClassCastException e){
                Shop.getPlugin().getLogger().warning("Error attempting to update Shop sign! Sign Block for Shop is not a Sign (detected: " + signLocation.getBlock().getType() + "), deleting shop: " + this);
                this.delete();
                return;
            }

            String[] oldLines = signBlock.getLines();
            String[] newLines = signLines.clone();
            boolean hasSignUpdate = false;
            // If the sign lines are the same, don't update them!
            boolean linesMatch = newLines[0].equals(oldLines[0]) && newLines[1].equals(oldLines[1]) && newLines[2].equals(oldLines[2]) && newLines[3].equals(oldLines[3]);

            if (!isInitialized()) {
                hasSignUpdate = true; // force update the sign
                signBlock.setLine(0, ChatColor.RED + ChatColor.stripColor(newLines[0]));
                signBlock.setLine(1, ChatColor.RED + ChatColor.stripColor(newLines[1]));
                signBlock.setLine(2, ChatColor.RED + ChatColor.stripColor(newLines[2]));
                signBlock.setLine(3, ChatColor.RED + ChatColor.stripColor(newLines[3]));
            } else if (!linesMatch) {
                hasSignUpdate = true; // force update the sign
                signBlock.setLine(0, newLines[0]);
                signBlock.setLine(1, newLines[1]);
                signBlock.setLine(2, newLines[2]);
                signBlock.setLine(3, newLines[3]);
            }
            // If the sign is glowing, update it if the setting has changed
            if(isMCVersion17Plus()) {
                boolean shouldGlow = Shop.getPlugin().getGlowingSignText();
                if (shouldGlow != signBlock.isGlowingText()) { 
                    hasSignUpdate = true;
                    signBlock.setGlowingText(shouldGlow);
                }
            }
            // Update the sign if it has changed
            if (hasSignUpdate) { signBlock.update(true); }

            // Update the floating holograms for anybody who currently has them open
            if (display != null) display.updateDisplayTags();
        }, 2);
    }

    /**
     * Deletes this shop.
     */
    public void delete() { this.delete(true); }
    /**
     * Deletes this shop.
     *
     * @param forceSave if true forces save before deletion
     */
    public void delete(boolean forceSave) {
        try {
            // First, remove the shop from the shop handler in case of any errors with later methods.
            Shop.getPlugin().getShopHandler().removeShop(this, forceSave);

            if(UtilMethods.isMCVersion17Plus() && Shop.getPlugin().getDisplayLightLevel() > 0 && this.getChestLocation() != null) {
                Block chestBlock = this.getChestLocation().getBlock();
                if (chestBlock != null && Shop.getPlugin().getShopHandler().isChest(chestBlock)) {
                    Block displayBlock = chestBlock.getRelative(BlockFace.UP);
                    if(UtilMethods.materialIsNonIntrusive(displayBlock.getType())) {
                        displayBlock.setType(Material.AIR);
                    }
                }
            }

            Block b = this.getSignLocation().getBlock();
            if (b.getBlockData() instanceof WallSign) {
                Sign signBlock = (Sign) b.getState();
                String[] deletedLines = ShopMessage.getSignLines("deleted", this);
                signBlock.setLine(0, deletedLines[0]);
                signBlock.setLine(1, deletedLines[1]);
                signBlock.setLine(2, deletedLines[2]);
                signBlock.setLine(3, deletedLines[3]);
                signBlock.update(true);
            }

            // Finally, remove any active displays
            if (display != null) {
                display.remove(null);
            }
            Shop.getPlugin().getLogger().debug("Deleted Shop " + this);
        } catch (Error | Exception e) {
            Shop.getPlugin().getLogger().severe("Unknown error attempting to delete shop, deletion might not have fully completed successfully: " + e.getMessage());
            Shop.getPlugin().getLogger().debug("Full stack trace for shop deletion error: ", e);
        }
    }

    /**
     * Teleports a player to this shop.
     *
     * @param player the player to teleport
     */
    public void teleportPlayer(Player player){
        if(player == null)
            return;

        if(chestLocation == null) {
            this.load();
            Location loc = this.getSignLocation().getBlock().getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);
            player.teleport(loc);
        }
        else {
            Location loc = this.getSignLocation().getBlock().getRelative(facing).getLocation().add(0.5, 0, 0.5);
            loc.setYaw(UtilMethods.faceToYaw(facing.getOppositeFace()));
            loc.setPitch(25.0f);

            player.teleport(loc);
        }
        Shop.getPlugin().getShopListener().addTeleportCooldown(player);
    }

    /**
     * Prints sales information to a player.
     *
     * @param player the player to send information to
     */
    public void printSalesInfo(Player player) {
        for (String message : ShopMessage.getUnformattedMessageList(this.getType().toString(), "description")) {
            if (message != null && !message.isEmpty()) {
                Map<ItemStack, Integer> items = new HashMap<>();
                items.put(this.item, this.amount);
                if (this.getSecondaryItemStack() != null) { items.put(this.getSecondaryItemStack(), (int) this.price); }
                ShopMessage.sendMessage(message, player, this);
            }
        }
    }

    /**
     * Checks if this shop has a fake sign.
     *
     * @return true if fake sign
     */
    public boolean isFakeSign(){
        return fakeSign;
    }

    /**
     * Sets whether this shop has a fake sign.
     *
     * @param fakeSign true if fake sign
     */
    public void setFakeSign(boolean fakeSign){
        this.fakeSign = fakeSign;
    }

    /**
     * Executes a click action for this shop.
     *
     * @param event the player interact event
     * @param clickType the click type
     * @return true if action executed
     */
    public boolean executeClickAction(PlayerInteractEvent event, ShopClickType clickType){
        ShopAction action = Shop.getPlugin().getShopAction(clickType);
        if(action == null)
            return false; //there is no action mapped to this click type
        Player player = event.getPlayer();

        switch(action) {
            case TRANSACT:
                Shop.getPlugin().getTransactionHelper().executeTransactionFromEvent(event, this, false);
                break;
            case TRANSACT_FULLSTACK:
                Shop.getPlugin().getTransactionHelper().executeTransactionFromEvent(event, this, true);
                break;
            case VIEW_DETAILS:
                this.printSalesInfo(player);
                break;
            case CYCLE_DISPLAY:
                //player clicked another player's shop sign
                if (!this.getOwnerName().equals(player.getName())) {
                    //player has permission to change another player's shop display
                    if((!Shop.getPlugin().usePerms() && player.isOp()) || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
                        this.getDisplay().cycleType(player);
                    }
                //player clicked own shop sign
                } else {
                    if(Shop.getPlugin().usePerms() && !player.hasPermission("shop.setdisplay"))
                        return false;

                    this.getDisplay().cycleType(player);
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Sends effects to the player.
     *
     * @param success true if transaction was successful
     * @param player the player
     */
    public void sendEffects(boolean success, Player player){
        try {
            if (success) {
                if (Shop.getPlugin().playSounds()) player.playSound(this.getSignLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                if (Shop.getPlugin().playEffects()) player.getWorld().playEffect(this.getChestLocation(), Effect.STEP_SOUND, Material.EMERALD_BLOCK);
            } else {
                if (Shop.getPlugin().playSounds()) player.playSound(this.getSignLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.0F);
                if (Shop.getPlugin().playEffects()) player.getWorld().playEffect(this.getChestLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
            }
        } catch (Error e) {
            Shop.getPlugin().getLogger().debug("Error playing shop effects: " + e.getMessage());
        } catch (Exception e) {
            Shop.getPlugin().getLogger().debug("Error playing shop effects: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        String worldName = "unknown";
        try {
            if (signLocation != null && signLocation.getWorld() != null) {
                worldName = signLocation.getWorld().getName();
            }
        } catch (Exception e) {
            // Avoid accessing world asynchronously on Folia
            worldName = "unknown";
        }
        return "AbstractShop{" +
                "type=" + (type != null ? type.toString().toUpperCase() : "null") +
                (isAdmin ? ", isAdmin=" + isAdmin : "") +
                ", owner=" + this.getOwnerName() +
                ", loc=" + ((signLocation != null) ? worldName + ":" + signLocation.getBlockX() + "/" + signLocation.getBlockY() + "/" + signLocation.getBlockZ() : "null") +
                ", price=" + price +
                ", amount=" + amount +
                ", stock=" + stock +
                ", item=" + (item != null ? Shop.getPlugin().getItemNameUtil().getName(item).toPlainText() : "null") +
                (secondaryItem != null ? ", secondaryItem=" + Shop.getPlugin().getItemNameUtil().getName(secondaryItem).toPlainText() : "") +
                ", id=" + (this.getId() != null ? this.getId().toString().substring(0, Math.min(5, this.getId().toString().length())) : "null") +
                ", filename=" + this.getOwnerUUID() + ".yml" +
                ", needsSave=" + this.needsSave +
                ", isLoaded=" + this.isLoaded +
                '}';
    }
}

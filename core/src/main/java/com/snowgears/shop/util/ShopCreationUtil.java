package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.event.PlayerCreateShopEvent;
import com.snowgears.shop.event.PlayerInitializeShopEvent;
import com.snowgears.shop.hook.GriefPreventionTrustListener;
import com.snowgears.shop.hook.TownyHook;
import com.snowgears.shop.hook.WorldGuardHook;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopCreationUtil {

    private Shop plugin;
    private BlockFace[] wallFaces = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ShopCreationUtil(Shop plugin){
        this.plugin = plugin;
    }

    public BlockFace calculateBlockFaceForSign(Player player, Block chest, BlockFace facePreference){
        if(facePreference == BlockFace.UP || facePreference == BlockFace.DOWN)
            facePreference = BlockFace.NORTH;
        Block futureSign = chest.getRelative(facePreference);
        if(UtilMethods.materialIsNonIntrusive(futureSign.getType()))
            return facePreference;
        for(BlockFace face : wallFaces){
            futureSign = chest.getRelative(face);
            if(UtilMethods.materialIsNonIntrusive(futureSign.getType()))
                return face;
        }
        ShopMessage.sendMessage("interaction_issue", "createSignRoom", player, null);
        return null;
    }

    public boolean shopCanBeCreated(Player player, Block chest) {
        int numberOfShops = plugin.getShopHandler().getNumberOfShops(player);
        int buildPermissionNumber = plugin.getShopListener().getBuildLimit(player);

        if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
            if (numberOfShops >= buildPermissionNumber) {
                if (player.isSneaking()) {
                    if (!cooldowns.containsKey(player.getUniqueId()) || cooldowns.get(player.getUniqueId()) < System.currentTimeMillis()) {
                        ShopMessage.sendMessage("permission", "buildLimit", player, null);
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
                    }
                }
                return false;
            }
        }

        if (plugin.getWorldBlacklist().contains(chest.getWorld().getName())) {
            if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
                ShopMessage.sendMessage("interaction_issue", "worldBlacklist", player, null);
                return false;
            }
        }

        if (plugin.usePerms() && !player.hasPermission("shop.operator")) {
            boolean canCreate = false;
            if(!player.hasPermission("shop.create")){
                for(ShopType shopType : ShopType.values()){
                    if(player.hasPermission("shop.create."+shopType.toString().toLowerCase()))
                        canCreate = true;
                }
            }
            else {
                canCreate = true;
            }
            if(!canCreate){
                ShopMessage.sendMessage("permission", "create", player, null);
                return false;
            }
        }

        // All region-protection checks below use &= so that any single deny is permanent.
        // Previously each check overwrote canCreateShopInRegion, meaning a later check that
        // returned true (e.g. GriefPrevention failing-open because GP isn't installed) would
        // silently erase a deny that an earlier check (e.g. WorldGuard) had already set.
        //
        // A shop occupies two blocks - a sign and a container. The sign goes on whichever adjacent
        // wall face has room, so the candidate sign blocks are the chest's horizontal neighbours.
        // Regions are volumetric, so a chest and its sign can sit either side of a WorldGuard or
        // Towny boundary. Checking only the chest let a shop straddle that boundary, while every
        // other region check in the plugin (ShopListener, TransactionHandler, MiscListener) uses
        // the sign as the anchor.
        boolean canCreateShopInRegion = true;

        // WorldGuard region check (optional hook)
        try {
            if(plugin.worldGuardExists()) {
                canCreateShopInRegion &= WorldGuardHook.canCreateShop(player, chest.getLocation());
                // The sign is placed on whichever adjacent wall face has room, so the candidate
                // sign blocks are every horizontal neighbour of the chest. A shop whose chest is
                // allowed but every possible sign position is denied cannot be created anywhere.
                canCreateShopInRegion &= WorldGuardHook.canCreateShopOnAnyNeighbour(player, chest);
            }
        } catch (NoClassDefFoundError e) {
            //tried to hook world guard but it was not registered
            e.printStackTrace();
        }

        // Towny region check (optional hook)
        try {
            if(plugin.hookTowny()) {
                canCreateShopInRegion &= TownyHook.canCreateShop(player, chest.getLocation());
                canCreateShopInRegion &= TownyHook.canCreateShopOnAnyNeighbour(player, chest);
            }
        } catch (NoClassDefFoundError e) {
            //tried to hook towny but it was not registered
            e.printStackTrace();
        }

        // GriefPrevention build-rights check at the chest location.
        // Runs before any block mutations (sign type conversion) so GP never fires its own
        // "can't build here" denial message mid-creation.
        try {
            if (plugin.isGriefPreventionTrustIntegrationEnabled()) {
                canCreateShopInRegion &= GriefPreventionTrustListener.canPlayerBuildAt(player, chest.getLocation());
            }
        } catch (NoClassDefFoundError e) {
            // GriefPrevention was not registered — ignore.
            e.printStackTrace();
        }

        if (!canCreateShopInRegion) {
            ShopMessage.sendMessage("interaction_issue", "regionRestriction", player, null);
            return false;
        }

        return true;
    }

    public AbstractShop createShop(Player player, Block chestBlock, Block signBlock, PricePair pricePair, int amount, boolean isAdmin, ShopType type, BlockFace signDirection, boolean isFakeSign){
        String playerMessage = null;
        if(type == null)
            type = ShopType.SELL;

        // The admin flag arrives from the player's own creation input (see getShopIsAdmin), so it is a
        // request, not a fact. Honour it only for a player entitled to it, and coerce rather than
        // refuse: the rest of creation proceeds normally and the player keeps their shop.
        boolean mayBeOperator = (!plugin.usePerms() && player.isOp())
                || (plugin.usePerms() && player.hasPermission("shop.operator"));
        if (isAdmin && !mayBeOperator) {
            isAdmin = false;
            ShopMessage.sendMessage("permission", "adminShop", player, null);
        }

        final AbstractShop shop = AbstractShop.create(signBlock.getLocation(), player.getUniqueId(), pricePair.getPrice(), pricePair.getPriceCombo(), amount, isAdmin, type, signDirection);
        shop.setFakeSign(isFakeSign);

        if (plugin.usePerms()) {
            if (!(player.hasPermission("shop.create." + type.toString().toLowerCase()) || player.hasPermission("shop.create")))
                playerMessage = ShopMessage.getUnformattedMessage("permission", "create");
        }

        if (type == ShopType.GAMBLE) {
            isAdmin = true;
            shop.setAdmin(true);
            if (!mayBeOperator) {
                playerMessage = ShopMessage.getUnformattedMessage("permission", "create");
            }
        }

        //if players must pay to create shops, check that they have enough money first
        double cost = plugin.getCreationCost();
        if (cost > 0) {
            if (!EconomyUtils.hasSufficientFunds(player, player.getInventory(), cost)) {
                playerMessage = ShopMessage.getUnformattedMessage("interaction_issue", "createInsufficientFunds");
            }
        }

        if (mayBeOperator) {
            playerMessage = null;
        }

        // Prevent creating a shop on a double chest that already belongs to someone else. This runs
        // AFTER the operator clear above so it is the last writer — the comment on this check says it
        // applies "even if they are OP", and that only holds because of the ordering. The admin
        // exemption was removed deliberately: admin status is a property of a shop, not a licence to
        // take over another player's chest.
        AbstractShop existingShop = plugin.getShopHandler().getShopByChest(chestBlock);
        if (existingShop != null && !existingShop.getOwnerUUID().equals(player.getUniqueId())) {
            playerMessage = ShopMessage.getUnformattedMessage("interaction_issue", "createOtherPlayer");
        }

        if (playerMessage != null) {
            if(!playerMessage.isEmpty())
                ShopMessage.sendMessage(playerMessage, player, shop);
            return null;
        }

        //removed all the direction checking code. just make sure its a container
        //make sure that the sign is in front of the chest, unless it is a shulker box
        if (chestBlock.getState() instanceof Container) {
            existingShop = plugin.getShopHandler().getShopByChest(chestBlock);
            if (existingShop != null) {
                //if the block they are adding a sign to is already a shop, do not let them
                if (chestBlock.getLocation().equals(existingShop.getChestLocation())) {
                    ShopMessage.sendMessage("interaction_issue", "createOtherPlayer", player, shop);
                    return null;
                }
            }

            // Creation is wall-sign only, deliberately. A standing sign has to be reliably associated
            // with the container it belongs to, and "the block below" is ambiguous once another
            // shop is placed in between — the association is what identifies a shop, so getting it
            // wrong is worse than declining to create one. Interaction with existing sign-post
            // shops is supported and is not restricted by this guard; see the sign checks in
            // ShopListener, which accept WALL_SIGNS and STANDING_SIGNS alike.
            if (!(signBlock.getBlockData() instanceof WallSign)) {
                return null;
            }
            Sign signBlockState = (Sign) shop.getSignLocation().getBlock().getState();
            signBlockState.update();

            shop.setAdmin(isAdmin);
            boolean loaded = shop.load();
            if (!loaded) {
                plugin.getLogger().warning("Shop creation failed, unable to load the shop. Aborting shop creation."); // only seen this happen in tests
                return null;
            }

            PlayerCreateShopEvent e = new PlayerCreateShopEvent(player, shop);
            plugin.getServer().getPluginManager().callEvent(e);

            plugin.getLogHandler().logAction(player, shop, ShopActionType.CREATE);

            if (e.isCancelled())
                return null;

            if (UtilMethods.isMCVersion17Plus() && plugin.getDisplayLightLevel() > 0) {
                Block displayBlock = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
                if (UtilMethods.materialIsNonIntrusive(displayBlock.getType())) {
                    displayBlock.setType(Material.LIGHT);
                    Light data = (Light) displayBlock.getBlockData();
                    data.setLevel(plugin.getDisplayLightLevel());
                    displayBlock.setBlockData(data);
                }
            }

            if (type == ShopType.GAMBLE) {
                shop.setItemStack(plugin.getGambleDisplayItem());
                shop.setAmount(1);
                plugin.getShopHandler().addShop(shop);
                shop.getDisplay().setType(DisplayType.LARGE_ITEM, false);

                plugin.getShopCreationUtil().sendCreationSuccess(player, shop);
                plugin.getLogHandler().logAction(player, shop, ShopActionType.INIT);
                return null;
            }

            plugin.getShopHandler().addShop(shop);
            Shop.getPlugin().getLogger().trace("[ShopCreationUtil.createShop] updateSign");
            shop.updateSign();
        }
        return shop;
    }

    public void cleanupShopCreationProcess(Player player){
        ShopCreationProcess process = plugin.getMiscListener().getShopCreationProcess(player);
        if (process != null) {
            process.cleanup();
            plugin.getMiscListener().removeShopCreationProcess(player);
        }
    }

    public void sendCreationSuccess(Player player, AbstractShop shop){
        if (shop.getDisplay() != null) shop.getDisplay().spawn(player);
        Shop.getPlugin().getLogger().trace("[ShopCreationUtil.sendCreationSuccess] updateSign");
        shop.updateSign(true);
        shop.setNeedsSave(true);
        ShopMessage.sendMessage(shop.getType().name(), "create", player, shop);
        shop.sendEffects(true, player);
        // Save the shop to disk. This is called here to ensure the shop is persisted immediately after creation.
        // Note: This save trigger could be moved to a more appropriate location in the future.
        Shop.getPlugin().getShopHandler().saveShops(shop.getOwnerUUID(), true);
        // Cleanup the shop creation process
        cleanupShopCreationProcess(player);
    }

    public boolean itemsCanBeInitialized(Player player, ItemStack itemStack, ItemStack barterItemStack){
        boolean isAdmin = (!plugin.usePerms() && player.isOp()) || (plugin.usePerms() && player.hasPermission("shop.operator"));

        //if the item is on the DENY LIST or the item is not on the ALLOW LIST, don't let player initialize with it
        // Only perform this check for non admins
        if (!isAdmin) {
            boolean passesItemList = plugin.getShopHandler().passesItemListCheck(itemStack);
            if (!passesItemList) {
                ShopMessage.sendMessage("interaction_issue", "itemListDeny", player, null);
                return false;
            }
        }

        // Bug 6 fix: barterItemStack is null on the first barter initialization step.
        // Passing null to itemstacksAreSimilar() causes an NPE that silently swallows
        // the entire initialization, leaving the shop permanently un-initialized.
        // Guard the call so we only compare items when both are non-null.
        if (barterItemStack != null && InventoryUtils.itemstacksAreSimilar(itemStack, barterItemStack)) {
            ShopMessage.sendMessage("interaction_issue", "createSameItem", player, null);
            return false;
        }
        return true;
    }

    public boolean initializeShop(AbstractShop shop, Player player , ItemStack item, ItemStack barterItem){
        if (!player.getUniqueId().equals(shop.getOwnerUUID())) {
            //do not allow non operators to initialize other player's shops
            if((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
                ShopMessage.sendMessage("interaction_issue", "initializeOtherShop", player, shop);
                shop.sendEffects(false, player);
                return false;
            }
        }

        if (item.getType() == Material.AIR) {
                    ShopMessage.sendMessage("interaction_issue", "createNoItem", player, shop);
                    shop.sendEffects(false, player);
                    return false;
                }

        // Null-guard: chestLocation is set by load() inside createShop().  If load()
        // failed or has not yet committed the chest location (e.g. the sign block had
        // not yet propagated to the world on the creation tick), getChestLocation()
        // returns null and the subsequent getBlock() call throws an NPE that silently
        // swallows the initialization request, leaving the shop permanently un-initialized.
        if (shop.getChestLocation() == null) {
            plugin.getLogger().warning("initializeShop: chest location is null for shop " + shop + " — aborting initialization.");
            shop.sendEffects(false, player);
            return false;
        }

        if(plugin.getDisplayType() != DisplayType.NONE) {
            //make sure there is room above the shop for the display
            Block aboveShop = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
            if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                if(plugin.forceDisplayToNoneIfBlocked()){
                    shop.getDisplay().setType(DisplayType.NONE, false);
                }
                else {
                    ShopMessage.sendMessage("interaction_issue", "createDisplayRoom", player, shop);
                    shop.sendEffects(false, player);
                    return false;
                }
            }
        }

        //if players must pay to create shops, remove money first
        double cost = plugin.getCreationCost();
        // Check if the shop is not an admin shop and if the shop is not a barter shop or the barter item is not null
        // When creating a barter shop with a sign, initializeShop is called twice, we only want to charge them once both items are selected
        if(cost > 0 && !shop.isAdmin() && !(shop.getType() == ShopType.BARTER && barterItem == null)){
            boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
            if(!removed){
                ShopMessage.sendMessage("interaction_issue", "createInsufficientFunds", player, shop);
                shop.sendEffects(false, player);
                return false;
            }
        }

        try {
                    //stop the edge case of shulker boxes being able to be used in shulker chests
                    if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                        if (shop.getChestLocation().getBlock().getState() instanceof ShulkerBox) {
                            ShopMessage.sendMessage("interaction_issue", "shulkerBoxConflict", player, shop);
                            shop.sendEffects(false, player);
                            return false;
                        }
                    }
                } catch (NoSuchFieldError e) {}

        if(!itemsCanBeInitialized(player, item, barterItem)){
            shop.sendEffects(false, player);
            return false;
        }

        if (shop.getItemStack() == null && item != null) {

            PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
            Bukkit.getServer().getPluginManager().callEvent(e);

            if(e.isCancelled())
                return false;

            shop.setItemStack(item);

            ShopCreationProcess process = plugin.getMiscListener().getShopCreationProcess(player);
            if (shop.getType() == ShopType.BARTER && barterItem == null) {
                ShopMessage.sendMessage("interaction", shop.getType().name() + ".initializeInfo", player, shop);
                process.setStep(ShopCreationProcess.ChatCreationStep.SIGN_BARTER_ITEM);
                process.displayFloatingText("interaction", shop.getType().name() + ".initializeBarter");
                if(plugin.allowCreativeSelection()) {
                    ShopMessage.sendMessage("interaction", "BUY.initializeAlt", player, shop);
                }
            }
            else if(shop.getType() != ShopType.BARTER){
                return true;
            }
        }
        if (shop.getSecondaryItemStack() == null && barterItem != null) {

                PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
                Bukkit.getServer().getPluginManager().callEvent(e);

                if(e.isCancelled())
                    return false;

                shop.setSecondaryItemStack(barterItem);
                return true;
        }
                // If we reach here, the shop is already fully initialized
                plugin.getLogger().debug("initializeShop: shop already initialized for " + shop);
                return false;
    }

    public ShopType getShopType(String input){
        ShopType type = null;
        if (input.toLowerCase().contains(ShopMessage.getCreationWord("BUY")))
            type = ShopType.BUY;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("BARTER")))
            type = ShopType.BARTER;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("GAMBLE")))
            type = ShopType.GAMBLE;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("COMBO")))
            type = ShopType.COMBO;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("SELL")))
            type = ShopType.SELL;
        return type;
    }

    /**
     * Parses a cleaned price token as a double when it contains a decimal point, or as a
     * signed long otherwise, returning the result as a double.
     *
     * @throws NumberFormatException if the token is invalid for the selected parser,
     *         including a whole number outside the signed long range
     */
    private static double parsePriceToken(String token) {
        if (token.contains("."))
            return Double.parseDouble(token);
        return Long.parseLong(token);
    }

    /** A combo requires an explicit slash; whitespace alone groups a single price. */
    private static boolean isComboLine(String input) {
        return input.contains("/");
    }

    /**
     * Splits an explicitly slash-separated combo into two raw price tokens.
     * Whitespace around the separator is allowed, but neither price may contain whitespace.
     *
     * @param input the raw sign price line
     * @return the primary and secondary tokens, with multiplier markers preserved
     * @throws NumberFormatException if there is not exactly one slash and two nonempty tokens,
     *         or either price contains internal whitespace
     */
    private static String[] comboPriceTokens(String input) {
        String[] sides = input.trim().split("/", -1);
        if (sides.length != 2 || sides[0].isBlank() || sides[1].isBlank())
            throw new NumberFormatException("Expected two combo prices");
        String[] tokens = input.replace('/', ' ').trim().split("\\s+");
        if (tokens.length != 2)
            throw new NumberFormatException("Expected two combo prices");
        return tokens;
    }

    /**
     * Cleans a single sign price while rejecting a minus sign in any later whitespace token.
     * This prevents cleaning from hiding a negative secondary price without a slash separator.
     *
     * @param input the raw single-price line, optionally containing grouping spaces
     * @return the cleaned numeric token
     * @throws NumberFormatException if a token after the first contains a minus sign
     */
    private static String singlePriceToken(String input) {
        // Do not let priceToken erase a negative second value in an unseparated combo.
        String[] tokens = input.trim().split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            if (tokens[i].contains("-"))
                throw new NumberFormatException("Unexpected negative price");
        }
        return UtilMethods.priceToken(input);
    }

    /**
     * Parses the primary chat price using the configured currency's numeric format.
     * Negative prices and zero prices for non-gamble shops are rejected with a player message.
     *
     * @param player the player to notify about invalid input
     * @param input the raw chat price
     * @param shopType the shop type used to decide whether zero is allowed
     * @return the parsed price, or {@code -1} when parsing or price validation fails
     */
    public double getShopPrice(Player player, String input, ShopType shopType){
        double price = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                // Spaces are preserved here on purpose: this is the single-price prompt, and a
                // price typed with an internal space is invalid input that should be rejected.
                // Collapsing the space would let "10 000" parse as 10000. See issue #125.
                String line3 = UtilMethods.cleanNumberText(input);
                double multiplyValue = UtilMethods.getMultiplyValue(line3);

                price = parsePriceToken(line3);
                price *= multiplyValue;
            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "line3", player, null);
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                price = Long.parseLong(line3);

            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "line3", player, null);
                return -1;
            }
        }
        // Bug 3 fix: reject price == 0 for all transactional shop types (BUY, SELL, COMBO),
        // not just BARTER. A zero price on a BUY/SELL/COMBO shop silently creates a free-item
        // shop with no config opt-in. GAMBLE shops intentionally have no "price" in this sense
        // (their prize value is determined elsewhere), so they are excluded from the check.
        if (price < 0 || (price == 0 && shopType != ShopType.GAMBLE)) {
            ShopMessage.sendMessage("interaction_issue", "line3", player, null);
            return -1;
        }
        return price;
    }

    /**
     * Parses the secondary chat price using the configured currency's numeric format.
     * This step reports malformed numbers but does not validate the parsed price's sign.
     *
     * @param player the player to notify about malformed input
     * @param input the raw secondary chat price
     * @param shopType the requested shop type; currently unused by this parsing step
     * @return the parsed secondary price, or {@code -1} when numeric parsing fails
     */
    public double getShopPriceCombo(Player player, String input, ShopType shopType){
        double priceCombo = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                // Spaces are preserved here on purpose: this is the single-price prompt, and a
                // price typed with an internal space is invalid input that should be rejected.
                // Collapsing the space would let "10 000" parse as 10000. See issue #125.
                String line3 = UtilMethods.cleanNumberText(input);
                double multiplyValue = UtilMethods.getMultiplyValue(line3);

                priceCombo = parsePriceToken(line3);
                priceCombo *= multiplyValue;

            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "line3", player, null);
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                priceCombo = Long.parseLong(line3);
            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "line3", player, null);
                return -1;
            }
        }
        return priceCombo;
    }

    /**
     * Parses a sign line as one price or two prices separated by a slash.
     * Spaces group a single price. In Vault mode, a multiplier on the first token scales both
     * prices; item currency requires whole numbers. Negative prices and a zero primary barter
     * price are rejected, with a message sent to the player.
     * Whitespace is allowed around the slash, but not within either price of a pair.
     * Multiplier markers on the secondary price or in item currency mode are ignored.
     *
     * @param player the player to notify about invalid input
     * @param input the raw sign price line
     * @param shopType the shop type used to validate the primary price
     * @return the price pair, with a zero secondary price for single input, or {@code null}
     *         when parsing or price validation fails
     * @throws NullPointerException if {@code input} is null
     */
    public PricePair getShopPricePair(Player player, String input, ShopType shopType){
        double price = 0;
        double priceCombo = 0;
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            try {
                if (isComboLine(input)) {
                    String[] multiplePrices = comboPriceTokens(input);
                    // Read the multiplier from the raw first token, not the cleaned one: the
                    // marker is stripped by priceToken, so reading it afterwards always returns 1.
                    double multiplyValue = UtilMethods.getMultiplyValue(multiplePrices[0]);
                    String priceToken = UtilMethods.priceToken(multiplePrices[0]);
                    String comboToken = UtilMethods.priceToken(multiplePrices[1]);

                    price = parsePriceToken(priceToken);
                    priceCombo = parsePriceToken(comboToken);
                    price *= multiplyValue;
                    priceCombo *= multiplyValue;
                } else {
                    // Not a combo: a single price, possibly typed with a thousands-separator
                    // space ("10 000"). Collapse the spaces so it parses as one number rather
                    // than splitting into "10" and "000" and creating the shop at 10. See #125.
                    String line3 = singlePriceToken(input);
                    double multiplyValue = UtilMethods.getMultiplyValue(input);
                    price = parsePriceToken(line3);
                    price *= multiplyValue;
                }

            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "createLine3", player, null);
                return null;
            }
        } else {
            try {
                if (isComboLine(input)) {
                    String[] multiplePrices = comboPriceTokens(input);
                    String priceToken = UtilMethods.priceToken(multiplePrices[0]);
                    String comboToken = UtilMethods.priceToken(multiplePrices[1]);
                    price = Long.parseLong(priceToken);
                    priceCombo = Long.parseLong(comboToken);
                } else {
                    price = Long.parseLong(singlePriceToken(input));
                }
            } catch (NumberFormatException e) {
                ShopMessage.sendMessage("interaction_issue", "createLine3", player, null);
                return null;
            }
        }
        //only allow price to be zero if the type is selling
        if (price < 0 || (price == 0 && shopType == ShopType.BARTER)) {
            ShopMessage.sendMessage("interaction_issue", "line3", player, null);
            return null;
        }
        // priceCombo was never validated independently — a negative combo price would silently
        // pass through even though the primary price guard catches it (#93).
        if (priceCombo < 0) {
            ShopMessage.sendMessage("interaction_issue", "line3", player, null);
            return null;
        }
        return new PricePair(price, priceCombo);
    }

    /**
     * Whether the player's creation line asks for an admin shop.
     *
     * <p>Substring matching, consistent with {@link #getShopType(String)} and every other
     * creation-word lookup in this class. A stricter rule here would reject a line the type parser
     * happily accepts, which is a worse failure than the one it prevents.
     *
     * <p>This only decides whether the request is <em>made</em>. {@link #createShop} checks whether it
     * is <em>allowed</em>, so a true return here is not by itself a bypass — and that separation is
     * what makes the substring rule tolerable.
     */
    public boolean getShopIsAdmin(String input){
        if (input == null) return false;
        return input.toLowerCase().contains(ShopMessage.getCreationWord("ADMIN"));
    }
}

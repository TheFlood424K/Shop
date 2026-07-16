package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class EconomyUtils {

    // -------------------------------------------------------------------------
    // Helpers for block-compressed item currency
    // -------------------------------------------------------------------------

    /**
     * Returns the total unit-equivalent amount of the currency item available
     * in {@code inventory}, counting both singular items AND their block form
     * (e.g. diamond + diamond_block * 9).
     */
    private static int getItemCurrencyAmount(Inventory inventory, ItemStack singularCurrency) {
        ItemStack singular = singularCurrency.clone();
        singular.setAmount(1);
        int singularCount = InventoryUtils.getAmount(inventory, singular);

        Material blockForm = BlockConversionRegistry.getBlockForm(singular.getType());
        int ratio = BlockConversionRegistry.getCompressionRatio(singular.getType());
        if (blockForm == null || ratio <= 0) {
            return singularCount;
        }
        ItemStack blockStack = new ItemStack(blockForm, 1);
        int blockCount = InventoryUtils.getAmount(inventory, blockStack);
        return singularCount + blockCount * ratio;
    }

    /**
     * Removes {@code amount} units of the currency from {@code inventory}.
     * Deducts singular items first; if more is still needed, breaks whole
     * blocks and returns any leftover singular change.
     * Returns {@code true} on success, {@code false} if insufficient stock.
     */
    private static boolean removeItemCurrency(Inventory inventory, ItemStack singularCurrency, int amount) {
        ItemStack singular = singularCurrency.clone();
        singular.setAmount(1);

        Material blockForm = BlockConversionRegistry.getBlockForm(singular.getType());
        int ratio = BlockConversionRegistry.getCompressionRatio(singular.getType());

        // Remove singular items first
        int singularInInv = InventoryUtils.getAmount(inventory, singular);
        int fromSingular = Math.min(singularInInv, amount);
        if (fromSingular > 0) {
            ItemStack toRemove = singular.clone();
            toRemove.setAmount(fromSingular);
            InventoryUtils.removeItem(inventory, toRemove);
            amount -= fromSingular;
        }

        if (amount <= 0) return true;

        // Not enough singulars — try blocks
        if (blockForm == null || ratio <= 0) return false;

        // How many whole blocks do we need to break?
        int blocksNeeded = (int) Math.ceil((double) amount / ratio);
        ItemStack blockStack = new ItemStack(blockForm, 1);
        int blocksInInv = InventoryUtils.getAmount(inventory, blockStack);
        if (blocksInInv < blocksNeeded) return false;

        // Remove required blocks
        ItemStack blocksToRemove = new ItemStack(blockForm, blocksNeeded);
        InventoryUtils.removeItem(inventory, blocksToRemove);

        // Give back change (leftover singulars after breaking blocks)
        int unitsFromBlocks = blocksNeeded * ratio;
        int change = unitsFromBlocks - amount;
        if (change > 0) {
            ItemStack changeStack = singular.clone();
            changeStack.setAmount(change);
            int leftover = InventoryUtils.addItem(inventory, changeStack);
            // If inventory is full, attempt to add change as blocks
            if (leftover > 0 && blockForm != null && ratio > 0) {
                int changeBlocks = leftover / ratio;
                int changeRemainder = leftover % ratio;
                if (changeBlocks > 0) InventoryUtils.addItem(inventory, new ItemStack(blockForm, changeBlocks));
                if (changeRemainder > 0) InventoryUtils.addItem(inventory, new ItemStack(singular.getType(), changeRemainder));
            }
        }
        return true;
    }

    /**
     * Adds {@code amount} units of currency to {@code inventory}.
     * Deposits as many full blocks as possible, then the remainder as singulars.
     * Returns {@code true} on success.
     */
    private static boolean addItemCurrency(Inventory inventory, ItemStack singularCurrency, int amount) {
        ItemStack singular = singularCurrency.clone();
        singular.setAmount(1);

        Material blockForm = BlockConversionRegistry.getBlockForm(singular.getType());
        int ratio = BlockConversionRegistry.getCompressionRatio(singular.getType());

        int blocksToAdd = (blockForm != null && ratio > 0) ? (amount / ratio) : 0;
        int singularsToAdd = amount - blocksToAdd * ratio;

        int unadded = 0;
        if (blocksToAdd > 0) {
            unadded += InventoryUtils.addItem(inventory, new ItemStack(blockForm, blocksToAdd)) * ratio;
        }
        if (singularsToAdd > 0) {
            ItemStack singularStack = singular.clone();
            singularStack.setAmount(singularsToAdd);
            unadded += InventoryUtils.addItem(inventory, singularStack);
        }

        if (unadded > 0) {
            // Roll back the portion we couldn't deposit
            ItemStack rollback = singular.clone();
            rollback.setAmount(unadded);
            InventoryUtils.removeItem(inventory, rollback);
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Public API (unchanged signatures)
    // -------------------------------------------------------------------------

    //check to see if the player has enough funds to take out [amount]
    //return false if they do not
    public static boolean hasSufficientFunds(OfflinePlayer player, Inventory inventory, double amount) {
        switch (Shop.getPlugin().getCurrencyType()) {
            case VAULT:
                double balance = Shop.getPlugin().getEconomy().getBalance(player);
                return (balance >= amount);
            case ITEM:
                ItemStack currency = Shop.getPlugin().getItemCurrency().clone();
                currency.setAmount(1);
                int stock = getItemCurrencyAmount(inventory, currency);
                return (stock >= amount);
            case EXPERIENCE:
                int exp = getExperience(player);
                return (exp > amount);
            default:
                return false;
        }
    }

    //check to see if the player has enough space to accept the funds to deposit [amount]
    //return false if they do not
    public static boolean canAcceptFunds(OfflinePlayer player, Inventory inventory, double amount) {
        switch (Shop.getPlugin().getCurrencyType()) {
            case VAULT:
            case EXPERIENCE:
                return true;
            case ITEM: {
                ItemStack currency = Shop.getPlugin().getItemCurrency().clone();
                currency.setAmount(1);

                Material blockForm = BlockConversionRegistry.getBlockForm(currency.getType());
                int ratio = BlockConversionRegistry.getCompressionRatio(currency.getType());

                // Try depositing as blocks + remainder, then fall back to all-singular
                if (blockForm != null && ratio > 0) {
                    int blocks     = (int) amount / ratio;
                    int singulars  = (int) amount % ratio;
                    boolean hasRoomForBlocks    = blocks == 0    || InventoryUtils.hasRoom(inventory, new ItemStack(blockForm, blocks));
                    ItemStack singularStack = currency.clone();
                    singularStack.setAmount(Math.max(1, singulars));
                    boolean hasRoomForSingulars = singulars == 0 || InventoryUtils.hasRoom(inventory, singularStack);
                    if (hasRoomForBlocks && hasRoomForSingulars) return true;
                }
                // Fallback: check room for all-singular amount
                ItemStack check = currency.clone();
                check.setAmount((int) amount);
                return InventoryUtils.hasRoom(inventory, check);
            }
            default:
                return false;
        }
    }

    //gets the current funds of the player
    public static double getFunds(OfflinePlayer player, Inventory inventory) {
        switch (Shop.getPlugin().getCurrencyType()) {
            case VAULT:
                double balance = Shop.getPlugin().getEconomy().getBalance(player);
                return balance;
            case EXPERIENCE:
                return getExperience(player);
            case ITEM:
                ItemStack currency = Shop.getPlugin().getItemCurrency().clone();
                currency.setAmount(1);
                return getItemCurrencyAmount(inventory, currency);
            default:
                return 0;
        }
    }

    //removes [amount] of funds from the player
    //return false if the player did not have sufficient funds or if something went wrong
    public static boolean removeFunds(OfflinePlayer player, Inventory inventory, double amount) {
        switch (Shop.getPlugin().getCurrencyType()) {
            case VAULT:
                EconomyResponse response = Shop.getPlugin().getEconomy().withdrawPlayer(player, amount);
                if (response.transactionSuccess())
                    return true;
                return false;
            case EXPERIENCE:
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    setTotalExperience(onlinePlayer, getTotalExperience(onlinePlayer) - (int) amount);
                    return true;
                } else {
                    PlayerExperience expData = PlayerExperience.loadFromFile(player);
                    if (expData != null) {
                        expData.removeExperienceAmount((int) amount);
                        return true;
                    } else {
                        return false;
                    }
                }
            case ITEM: {
                ItemStack currency = Shop.getPlugin().getItemCurrency().clone();
                currency.setAmount(1);
                return removeItemCurrency(inventory, currency, (int) amount);
            }
            default:
                return false;
        }
    }

    //adds [amount] of funds to the player
    //return false if the player did not have enough room for items or if something went wrong
    public static boolean addFunds(OfflinePlayer player, Inventory inventory, double amount) {
        switch (Shop.getPlugin().getCurrencyType()) {
            case VAULT:
                EconomyResponse response = Shop.getPlugin().getEconomy().depositPlayer(player, amount);
                if (response.transactionSuccess())
                    return true;
            case EXPERIENCE:
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    setTotalExperience(onlinePlayer, getTotalExperience(onlinePlayer) + (int) amount);
                    return true;
                } else {
                    PlayerExperience expData = PlayerExperience.loadFromFile(player);
                    if (expData != null) {
                        expData.addExperienceAmount((int) amount);
                        return true;
                    } else {
                        return false;
                    }
                }
            case ITEM: {
                ItemStack currency = Shop.getPlugin().getItemCurrency().clone();
                currency.setAmount(1);
                return addItemCurrency(inventory, currency, (int) amount);
            }
            default:
                return false;
        }
    }

    private static int getExperience(OfflinePlayer player) {
        if (player.getPlayer() != null) {
            return getTotalExperience(player.getPlayer());
        } else {
            PlayerExperience expData = PlayerExperience.loadFromFile(player);
            if (expData != null) {
                return expData.getExperience();
            }
        }
        return 0;
    }

    public static int getTotalExperience(int level) {
        int xp = 0;
        if (level >= 0 && level <= 15) {
            xp = (int) Math.round(Math.pow(level, 2) + 6 * level);
        } else if (level > 15 && level <= 30) {
            xp = (int) Math.round((2.5 * Math.pow(level, 2) - 40.5 * level + 360));
        } else if (level > 30) {
            xp = (int) Math.round(((4.5 * Math.pow(level, 2) - 162.5 * level + 2220)));
        }
        return xp;
    }

    public static int getTotalExperience(Player player) {
        return Math.round(player.getExp() * player.getExpToLevel()) + getTotalExperience(player.getLevel());
    }

    public static void setTotalExperience(Player player, int amount) {
        int level = 0;
        int xp = 0;
        float a = 0;
        float b = 0;
        float c = -amount;

        if (amount > getTotalExperience(0) && amount <= getTotalExperience(15)) {
            a = 1;
            b = 6;
        } else if (amount > getTotalExperience(15) && amount <= getTotalExperience(30)) {
            a = 2.5f;
            b = -40.5f;
            c += 360;
        } else if (amount > getTotalExperience(30)) {
            a = 4.5f;
            b = -162.5f;
            c += 2220;
        }
        level = (int) Math.floor((-b + Math.sqrt(Math.pow(b, 2) - (4 * a * c))) / (2 * a));
        xp = amount - getTotalExperience(level);
        player.setLevel(level);
        player.setExp(0);
        player.giveExp(xp);
    }
}

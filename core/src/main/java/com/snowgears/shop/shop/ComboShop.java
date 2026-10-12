package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ComboShop extends AbstractShop {

    private double priceBuy;
    private double priceSell;

    public ComboShop(Location signLoc, UUID player, double pri, double priSell, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.COMBO;
        this.signLines = ShopMessage.getSignLines(this, this.type);
        this.priceBuy = pri;
        this.priceSell = priSell;
    }

    public String getPriceSellString() {
        return Shop.getPlugin().getPriceString(this.priceSell, false);
    }

    public String getPriceSellPerItemString() {
        double pricePer = this.getPriceSell() / this.getAmount();
        return Shop.getPlugin().getPriceString(pricePer, true);
    }

    public String getPriceComboString() {
        return Shop.getPlugin().getPriceComboString(this.price, this.priceSell, false);
    }

    public double getPriceBuy(){
        return priceBuy;
    }

    public double getPriceSell(){
        return priceSell;
    }

    public void setPriceSell(double priceSell) {
        this.priceSell = priceSell;
    }

    /**
     * Updates and returns stock in transactions, using the owner's buying funds first
     * and sellable inventory only when buying stock is zero. A zero buy price disables
     * buying stock. With partial sales enabled, reports one for an affordable item or,
     * on the selling fallback with a positive sell price, enough items worth at least
     * one currency unit. Admin shops always report {@link Integer#MAX_VALUE}.
     *
     * @throws ArithmeticException if the transaction amount is zero when counting selling stock
     */
    @Override
    protected int calculateStock(){
        if(this.isAdmin) {
            stock = Integer.MAX_VALUE;
            return stock;
        }
        else {
            stock = 0;
            // A zero buy price disables buying capacity, but the shop may still sell items.
            if (this.getPrice() != 0) {
                double funds = EconomyUtils.getFunds(this.getOwner(), this.getInventory());
                stock = (int) Math.floor(funds / this.getPrice());
                // Check if we should show partial buying capacity
                if (stock == 0 && Shop.getPlugin().getAllowPartialSales()) {
                    if (funds >= this.getPricePerItem()) {
                        stock = 1;
                        return stock;
                    }
                }
            }

            // Check if we have stock to sell items still, even if we don't have funds to buy items anymore
            if (stock == 0) {
                int itemsToSell = InventoryUtils.getAmount(this.getInventory(), this.getItemStack());
                stock = itemsToSell / this.getAmount();
                // Check if we should show partial stock
                if (stock == 0 && this.getPriceSell() > 0 && Shop.getPlugin().getAllowPartialSales()) {
                    // Calculate the minimum items required to show as in stock
                    int minItemAmountRequired = (int) Math.ceil(this.getAmount() / this.getPriceSell());
                    int itemsInShop = InventoryUtils.getAmount(this.getInventory(), this.getItemStack());

                    if(itemsInShop >= minItemAmountRequired){
                        stock = 1;
                        return stock;
                    }
                }
            }
        }
        return stock;
    }
}

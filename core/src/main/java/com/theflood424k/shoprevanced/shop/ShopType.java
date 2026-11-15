package com.theflood424k.shoprevanced.shop;

import com.theflood424k.shoprevanced.util.ShopRevancedMessage;

public enum ShopType {

    SELL(0),

    BUY(1),

    BARTER(2),

    GAMBLE(3),

    COMBO(4);

    private final int slot;

    ShopType(int slot) {
        this.slot = slot;
    }

    @Override
    public String toString() {
        switch (this) {
            case SELL:
                return "sell";
            case BUY:
                return "buy";
            case BARTER:
                return "barter";
            case COMBO:
                return "combo";
            default:
                return "gamble";
        }
    }

    public String toCreationWord() {
        switch (this) {
            case SELL:
                return ShopRevancedMessage.getCreationWord("SELL");
            case BUY:
                return ShopRevancedMessage.getCreationWord("BUY");
            case BARTER:
                return ShopRevancedMessage.getCreationWord("BARTER");
            case COMBO:
                return ShopRevancedMessage.getCreationWord("COMBO");
            default:
                return ShopRevancedMessage.getCreationWord("GAMBLE");
        }
    }
}





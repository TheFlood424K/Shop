package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.shop.ShopType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Parses shop-related input from chat/signs and calculates block geometry
 * for shop creation (e.g., determining which face a sign should attach to).
 */
public class CreationParser {

    private final ShopRevanced plugin;
    private final BlockFace[] wallFaces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public CreationParser(ShopRevanced plugin) {
        this.plugin = plugin;
    }

    public BlockFace calculateBlockFaceForSign(Player player, Block chest, BlockFace facePreference) {
        if (facePreference == BlockFace.UP || facePreference == BlockFace.DOWN)
            facePreference = BlockFace.NORTH;
        Block futureSign = chest.getRelative(facePreference);
        if (UtilMethods.materialIsNonIntrusive(futureSign.getType()))
            return facePreference;
        for (BlockFace face : wallFaces) {
            futureSign = chest.getRelative(face);
            if (UtilMethods.materialIsNonIntrusive(futureSign.getType()))
                return face;
        }
        ShopRevancedMessage.sendMessage("interactionIssue", "signRoom", player, null);
        return null;
    }

    public ShopType getShopType(String input) {
        if (input.toLowerCase().contains(ShopRevancedMessage.getCreationWord("BUY")))
            return ShopType.BUY;
        if (input.toLowerCase().contains(ShopRevancedMessage.getCreationWord("BARTER")))
            return ShopType.BARTER;
        if (input.toLowerCase().contains(ShopRevancedMessage.getCreationWord("GAMBLE")))
            return ShopType.GAMBLE;
        if (input.toLowerCase().contains(ShopRevancedMessage.getCreationWord("COMBO")))
            return ShopType.COMBO;
        if (input.toLowerCase().contains(ShopRevancedMessage.getCreationWord("SELL")))
            return ShopType.SELL;
        return null;
    }
}

package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ArmorStandData;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Iterator;
import java.util.List;

/**
 * Standard display implementation using modern display entities (ItemDisplay, TextDisplay).
 * <p>
 * This implementation spawns display entities visible only to specific players
 * using scoreboard tags and per-player visibility.
 * </p>
 */
public class Display extends AbstractDisplay {

    /**
     * Creates a new Display instance.
     *
     * @param shopSignLocation the location of the shop sign
     */
    public Display(Location shopSignLocation) {
        super(shopSignLocation);
    }

    @Override
    protected void spawnItemPacket(Player player, ItemStack is, Location location) {
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        itemDisplay.setItemStack(is);
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        itemDisplay.setInterpolationDelay(0);
        itemDisplay.setInterpolationDuration(0);
        itemDisplay.setTeleportDuration(0);
        itemDisplay.setBrightness(new org.bukkit.entity.ItemDisplay.Brightness(15, 15));
        itemDisplay.setGlowing(false);
        itemDisplay.setShadowRadius(0.0f);
        itemDisplay.setShadowStrength(0.0f);

        // Make invisible to other players - only show to the specific player
        if (player != null) {
            itemDisplay.addScoreboardTag("shop_display_" + player.getUniqueId());
            player.showEntity(Shop.getPlugin(), itemDisplay);
        }

        this.addEntityID(player, itemDisplay);

        Shop.getPlugin().getLogger().log(java.util.logging.Level.FINE, "Item Location: " + location);
    }

    @Override
    protected void spawnArmorStandPacket(Player player, ArmorStandData armorStandData, String text) {
        boolean hasText = (text != null && !ChatColor.stripColor(text).isEmpty());
        Location location = armorStandData.getLocation();

        if (hasText) {
            // Use TextDisplay for hologram text
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);
            textDisplay.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
            textDisplay.setBackgroundColor(Color.TRANSPARENT);
            textDisplay.setLineWidth(200);
            textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setDefaultBackground(false);
            textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration(0);
            textDisplay.setTeleportDuration(0);
            textDisplay.setGlowing(false);

            // Make invisible to other players - only show to the specific player
            if (player != null) {
                textDisplay.addScoreboardTag("shop_display_" + player.getUniqueId());
                player.showEntity(Shop.getPlugin(), textDisplay);
            }

            this.addDisplayTag(player, textDisplay);
        } else {
            // Use ItemDisplay for armor stand items
            ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
            itemDisplay.setItemStack(armorStandData.getEquipment());
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            itemDisplay.setInterpolationDelay(0);
            itemDisplay.setInterpolationDuration(0);
            itemDisplay.setTeleportDuration(0);
            itemDisplay.setBrightness(new org.bukkit.entity.ItemDisplay.Brightness(15, 15));
            itemDisplay.setGlowing(false);
            itemDisplay.setShadowRadius(0.0f);
            itemDisplay.setShadowStrength(0.0f);

            // Apply rotation if needed
            if (armorStandData.getRightArmPose() != null) {
                EulerAngle angle = armorStandData.getRightArmPose();
                Transformation transform = new Transformation(
                    new org.bukkit.util.Vector(0, 0, 0),
                    new org.bukkit.util.Vector(0, 0, 0),
                    new org.bukkit.util.Vector(1, 1, 1),
                    new EulerAngle(angle.getX(), angle.getY(), angle.getZ())
                );
                itemDisplay.setTransformation(transform);
            }

            // Apply yaw rotation
            itemDisplay.setRotation(org.bukkit.util.Vector.fromAxisAngle(0, armorStandData.getYaw(), 0));

            // Make invisible to other players - only show to the specific player
            if (player != null) {
                itemDisplay.addScoreboardTag("shop_display_" + player.getUniqueId());
                player.showEntity(Shop.getPlugin(), itemDisplay);
            }

            this.addEntityID(player, itemDisplay);
        }

        Shop.getPlugin().getLogger().log(java.util.logging.Level.FINE, "Floating Tag Label Location: " + location);
    }

    @Override
    protected void spawnItemFramePacket(Player player, ItemStack is, Location location, BlockFace facing, boolean isGlowing){
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        itemDisplay.setItemStack(is);
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        itemDisplay.setInterpolationDelay(0);
        itemDisplay.setInterpolationDuration(0);
        itemDisplay.setTeleportDuration(0);
        itemDisplay.setBrightness(new org.bukkit.entity.ItemDisplay.Brightness(15, 15));
        itemDisplay.setGlowing(isGlowing);
        itemDisplay.setShadowRadius(0.0f);
        itemDisplay.setShadowStrength(0.0f);

        // Set rotation based on facing direction
        float yaw = 0;
        switch (facing) {
            case NORTH: yaw = 180; break;
            case EAST: yaw = 90; break;
            case SOUTH: yaw = 0; break;
            case WEST: yaw = 270; break;
        }
        itemDisplay.setRotation(org.bukkit.util.Vector.fromAxisAngle(0, yaw, 0));

        // Make invisible to other players - only show to the specific player
        if (player != null) {
            itemDisplay.addScoreboardTag("shop_display_" + player.getUniqueId());
            player.showEntity(Shop.getPlugin(), itemDisplay);
        }

        this.addEntityID(player, itemDisplay);

        Shop.getPlugin().getLogger().log(java.util.logging.Level.FINE, "ItemFrame Location: " + location);
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        if (onlyDisplayTags) {
            if (player != null) {
                List<Entity> entities = displayTagEntities.remove(player.getUniqueId());
                if (entities != null) {
                    for (Entity entity : entities) {
                        player.hideEntity(Shop.getPlugin(), entity);
                    }
                }
                playersSeeingTags.remove(player.getUniqueId());
            } else {
                // Remove for all players
                for (List<Entity> entities : displayTagEntities.values()) {
                    for (Entity entity : entities) {
                        entity.remove();
                    }
                }
                displayTagEntities.clear();
                playersSeeingTags.clear();
            }
        } else {
            if (player != null) {
                List<Entity> entities = displayEntities.remove(player.getUniqueId());
                if (entities != null) {
                    for (Entity entity : entities) {
                        player.hideEntity(Shop.getPlugin(), entity);
                    }
                }
                playersSeeingDisplay.remove(player.getUniqueId());
            } else {
                // Remove for all players
                for (List<Entity> entities : displayEntities.values()) {
                    for (Entity entity : entities) {
                        entity.remove();
                    }
                }
                displayEntities.clear();
                playersSeeingDisplay.clear();
            }
        }
    }

    @Override
    public String getItemNameNMS(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }
}
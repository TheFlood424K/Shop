package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ArmorStandData;
import com.snowgears.shop.util.DisplayUtil;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;

import java.util.Iterator;
import java.util.UUID;

public class Display extends AbstractDisplay {

    public Display(Location shopSignLocation) {
        super(shopSignLocation);
    }

    @Override
    protected void spawnItemPacket(Player player, ItemStack is, Location location) {
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(is);
            entity.setTransformation(new Transformation(
                new org.bukkit.util.Vector(0, 0, 0),
                new org.bukkit.util.Vector(0, 0, 0),
                new org.bukkit.util.Vector(0.5, 0.5, 0.5),
                new org.bukkit.util.Vector(0, 0, 0)
            ));
            entity.setBillboard(ItemDisplay.Billboard.CENTER);
            entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            entity.setViewRange(1.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setGlowing(false);
            entity.setPersistent(false);
        });

        addEntityID(player, itemDisplay);

        // Show only to the specific player using entity visibility
        if (player != null) {
            player.showEntity(Shop.getPlugin(), itemDisplay);
        } else {
            for (Player onlinePlayer : location.getWorld().getPlayers()) {
                onlinePlayer.showEntity(Shop.getPlugin(), itemDisplay);
            }
        }
    }

    @Override
    protected void spawnArmorStandPacket(Player player, ArmorStandData armorStandData, String text) {
        boolean hasText = (text != null && !text.isEmpty());

        Location location = armorStandData.getLocation();

        if (hasText) {
            // Spawn TextDisplay for hologram text
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.text(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
                entity.setBillboard(TextDisplay.Billboard.CENTER);
                entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                entity.setLineWidth(200);
                entity.setViewRange(1.0f);
                entity.setShadowed(false);
                entity.setPersistent(false);
                entity.setSeeThrough(true);
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            });

            addDisplayTag(player, textDisplay);

            if (player != null) {
                player.showEntity(Shop.getPlugin(), textDisplay);
            } else {
                for (Player onlinePlayer : location.getWorld().getPlayers()) {
                    onlinePlayer.showEntity(Shop.getPlugin(), textDisplay);
                }
            }
        } else {
            // Spawn ItemDisplay for armor stand with item
            ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(armorStandData.getEquipment());
                entity.setTransformation(new Transformation(
                    new org.bukkit.util.Vector(0, 0, 0),
                    eulerAngleToQuaternion(armorStandData.getRightArmPose()),
                    new org.bukkit.util.Vector(0.5, 0.5, 0.5),
                    new org.bukkit.util.Vector(0, 0, 0)
                ));
                entity.setBillboard(ItemDisplay.Billboard.FIXED);
                entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                entity.setViewRange(1.0f);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setGlowing(false);
                entity.setPersistent(false);
            });

            if (armorStandData.isSmall()) {
                itemDisplay.setTransformation(new Transformation(
                    itemDisplay.getTransformation().getTranslation(),
                    itemDisplay.getTransformation().getLeftRotation(),
                    new org.bukkit.util.Vector(0.25, 0.25, 0.25),
                    itemDisplay.getTransformation().getRightRotation()
                ));
            }

            addEntityID(player, itemDisplay);

            if (player != null) {
                player.showEntity(Shop.getPlugin(), itemDisplay);
            } else {
                for (Player onlinePlayer : location.getWorld().getPlayers()) {
                    onlinePlayer.showEntity(Shop.getPlugin(), itemDisplay);
                }
            }
        }
    }

    private org.bukkit.util.Quaternion eulerAngleToQuaternion(EulerAngle eulerAngle) {
        if (eulerAngle == null) {
            return new org.bukkit.util.Quaternion(0, 0, 0, 1);
        }
        double x = Math.toRadians(eulerAngle.getX());
        double y = Math.toRadians(eulerAngle.getY());
        double z = Math.toRadians(eulerAngle.getZ());

        double cy = Math.cos(y * 0.5);
        double sy = Math.sin(y * 0.5);
        double cp = Math.cos(x * 0.5);
        double sp = Math.sin(x * 0.5);
        double cr = Math.cos(z * 0.5);
        double sr = Math.sin(z * 0.5);

        double w = cr * cp * cy + sr * sp * sy;
        double qx = sr * cp * cy - cr * sp * sy;
        double qy = cr * sp * cy + sr * cp * sy;
        double qz = cr * cp * sy - sr * sp * cy;

        return new org.bukkit.util.Quaternion(qx, qy, qz, w);
    }

    @Override
    protected void spawnItemFramePacket(Player player, ItemStack is, Location location, BlockFace facing, boolean isGlowing) {
        // Item frames are not replaced by display entities - they remain as actual item frames
        // This method should not be called with the new Display class, but we keep it for compatibility
        // The old NMS-based Display class handled this, but Paper API doesn't have a direct equivalent
        // Item frames are real entities, not display entities
        // For now, we'll log and skip - the DisplayDisabled fallback will be used if needed
        Shop.getPlugin().getLogger().debug("Item frame display not supported in Paper API Display class");
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        Iterator<Entity> entityIterator = getDisplayEntityIterator(player, onlyDisplayTags);
        if (entityIterator == null) {
            return;
        }

        while (entityIterator.hasNext()) {
            Entity entity = entityIterator.next();
            if (entity != null && entity.isValid()) {
                // Hide from player
                if (player != null) {
                    player.hideEntity(Shop.getPlugin(), entity);
                } else {
                    for (Player onlinePlayer : entity.getWorld().getPlayers()) {
                        onlinePlayer.hideEntity(Shop.getPlugin(), entity);
                    }
                }
                // Remove the entity
                entity.remove();
                entityIterator.remove();
            }
        }

        if (onlyDisplayTags) {
            if (player != null) {
                playersSeeingTags.remove(player.getUniqueId());
            } else {
                playersSeeingTags.clear();
            }
            displayTagEntities.clear();
        } else {
            if (player != null) {
                playersSeeingDisplay.remove(player.getUniqueId());
            } else {
                playersSeeingDisplay.clear();
            }
            displayEntities.clear();
        }
    }

    @Override
    public String getItemNameNMS(ItemStack item) {
        // Use Bukkit's built-in method to get item name
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }
}
package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ArmorStandData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;

public class Display extends AbstractDisplay {

    public Display(Location shopSignLocation) {
        super(shopSignLocation);
    }

    @Override
    protected void spawnItemPacket(Player player, ItemStack is, Location location) {
        if(location == null || location.getWorld() == null)
            return;

        location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(is);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0.5f, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.4f, 0.4f, 0.4f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));

            addDisplayEntity(entity);

            if(player != null && isSameWorld(player)){
                player.showEntity(Shop.getPlugin(), entity);
            }
        });
    }

    @Override
    protected void spawnArmorStandPacket(Player player, ArmorStandData armorStandData, String text) {
        boolean hasText = (text != null && !ChatColor.stripColor(text).isEmpty());

        Location location = armorStandData.getLocation();
        if(location == null || location.getWorld() == null)
            return;

        if(hasText){
            location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
                entity.setRotation((float) armorStandData.getYaw(), 0);

                addDisplayTagEntity(entity);

                if(player != null && isSameWorld(player)){
                    player.showEntity(Shop.getPlugin(), entity);
                }
            });
        }
        else {
            location.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.setItemStack(armorStandData.getEquipment());
                entity.setRotation((float) armorStandData.getYaw(), 0);
                entity.setTransformation(new Transformation(
                        new Vector3f(0, 0.5f, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.4f, 0.4f, 0.4f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));

                addDisplayEntity(entity);

                if(player != null && isSameWorld(player)){
                    player.showEntity(Shop.getPlugin(), entity);
                }
            });
        }
    }

    @Override
    protected void spawnItemFramePacket(Player player, ItemStack is, Location location, BlockFace facing, boolean isGlowing){
        if(location == null || location.getWorld() == null)
            return;

        location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(is);
            if(isGlowing){
                entity.setGlowing(true);
            }
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));

            addDisplayEntity(entity);

            if(player != null && isSameWorld(player)){
                player.showEntity(Shop.getPlugin(), entity);
            }
        });
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        if(onlyDisplayTags){
            ArrayList<Entity> tagCopy = new ArrayList<>(displayTagEntities);
            for(Entity entity : tagCopy){
                if(player != null && isSameWorld(player)){
                    player.hideEntity(Shop.getPlugin(), entity);
                }
                entity.remove();
            }
            displayTagEntities.clear();
            if(player != null){
                playersSeeingTags.remove(player.getUniqueId());
            }
        }
        else {
            ArrayList<Entity> entityCopy = new ArrayList<>(displayEntities);
            for(Entity entity : entityCopy){
                if(player != null && isSameWorld(player)){
                    player.hideEntity(Shop.getPlugin(), entity);
                }
                entity.remove();
            }
            displayEntities.clear();
            if(player != null){
                playersSeeingDisplay.remove(player.getUniqueId());
            }
        }
    }

    @Override
    public String getItemNameNMS(ItemStack item) {
        if (item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        } else {
            return item.getItemMeta().getItemName();
        }
    }
}

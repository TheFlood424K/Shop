package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.ShopRevancedGuiHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettings {

    public enum Option{
        NOTIFICATION_SALE_OWNER, NOTIFICATION_SALE_USER, NOTIFICATION_STOCK,
        GUI_SORT, GUI_FILTER_SHOP_TYPE, GUI_FILTER_SHOP_STOCK
    }

    private UUID player;
    private HashMap<Option, ShopRevancedGuiHandler.GuiIcon> optionsMap;

    public PlayerSettings (Player player){
        this.player = player.getUniqueId();
        initOptionsMap();
    }

    public PlayerSettings (UUID playerUUID){
        this.player = playerUUID;
        initOptionsMap();
    }

    public void initOptionsMap(){
        this.optionsMap = new HashMap<>();
        this.optionsMap.put(Option.NOTIFICATION_SALE_OWNER, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON);
        this.optionsMap.put(Option.NOTIFICATION_SALE_USER, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON);
        this.optionsMap.put(Option.NOTIFICATION_STOCK, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON);

        this.optionsMap.put(Option.GUI_SORT, ShopRevancedGuiHandler.GuiIcon.MENUBAR_SORT_NAME_LOW);
        this.optionsMap.put(Option.GUI_FILTER_SHOP_TYPE, ShopRevancedGuiHandler.GuiIcon.MENUBAR_FILTER_TYPE_ALL);
        this.optionsMap.put(Option.GUI_FILTER_SHOP_STOCK, ShopRevancedGuiHandler.GuiIcon.MENUBAR_FILTER_STOCK_ALL);
    }

    private PlayerSettings (UUID player, HashMap<Option, ShopRevancedGuiHandler.GuiIcon> optionsMap){
        this.player = player;
        this.optionsMap = optionsMap;
    }

    public void setOption(Option option, ShopRevancedGuiHandler.GuiIcon guiIcon){
        optionsMap.put(option, guiIcon);
        saveToFile();
    }

    public ShopRevancedGuiHandler.GuiIcon getGuiIcon(Option option){
        return optionsMap.get(option);
    }

    public boolean toggleNotification(Option option){
        ShopRevancedGuiHandler.GuiIcon icon = getGuiIcon(option);
        if(icon == null)
            return false;
        switch (icon){
            case SETTINGS_NOTIFY_OWNER_ON:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_OFF);
                return true;
            case SETTINGS_NOTIFY_OWNER_OFF:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON);
                return true;
            case SETTINGS_NOTIFY_USER_ON:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_OFF);
                return true;
            case SETTINGS_NOTIFY_USER_OFF:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON);
                return true;
            case SETTINGS_NOTIFY_STOCK_ON:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_OFF);
                return true;
            case SETTINGS_NOTIFY_STOCK_OFF:
                this.setOption(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON);
                return true;
            default:
                return false;
        }
    }

    private void saveToFile(){
        try {
            File fileDirectory = new File(ShopRevanced.getPlugin().getDataFolder(), "Data");

            File settingsDirectory = new File(fileDirectory, "PlayerSettings");
            if (!settingsDirectory.exists())
                settingsDirectory.mkdir();

            File playerSettingsFile = new File(settingsDirectory, this.player.toString() + ".yml");
            if (!playerSettingsFile.exists())
                playerSettingsFile.createNewFile();

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerSettingsFile);

            config.set("player.UUID", this.player.toString());
            for(Map.Entry<Option, ShopRevancedGuiHandler.GuiIcon> entry : optionsMap.entrySet()){
                config.set("player."+entry.getKey().toString(), entry.getValue().toString());
            }

            config.save(playerSettingsFile);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static PlayerSettings loadFromFile(Player player){
        if(player == null)
            return null;
        return loadFromFile(player.getUniqueId());
    }

    public static PlayerSettings loadFromFile(UUID playerUUID){
        if(playerUUID == null)
            return null;
        File fileDirectory = new File(ShopRevanced.getPlugin().getDataFolder(), "Data");

        File settingsDirectory = new File(fileDirectory, "PlayerSettings");
        if (!settingsDirectory.exists())
            settingsDirectory.mkdir();

        File playerSettingsFile = new File(settingsDirectory, playerUUID.toString() + ".yml");

        if (playerSettingsFile.exists()) {

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerSettingsFile);

            UUID uuid = UUID.fromString(config.getString("player.UUID"));
            HashMap<Option, ShopRevancedGuiHandler.GuiIcon> optionsMap = new HashMap<>();

            for(Option option : Option.values()){
                String guiIconString = config.getString("player."+option.toString());
                try{
                    optionsMap.put(option, ShopRevancedGuiHandler.GuiIcon.valueOf(guiIconString));
                } catch(Exception e){
                    switch (option){
                        case NOTIFICATION_STOCK:
                            optionsMap.put(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON);
                            break;
                        case NOTIFICATION_SALE_OWNER:
                            optionsMap.put(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON);
                            break;
                        case NOTIFICATION_SALE_USER:
                            optionsMap.put(option, ShopRevancedGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON);
                            break;
                        case GUI_SORT:
                            optionsMap.put(option, ShopRevancedGuiHandler.GuiIcon.MENUBAR_SORT_NAME_LOW);
                            break;
                        case GUI_FILTER_SHOP_TYPE:
                            optionsMap.put(Option.GUI_FILTER_SHOP_TYPE, ShopRevancedGuiHandler.GuiIcon.MENUBAR_FILTER_TYPE_ALL);
                            break;
                        case GUI_FILTER_SHOP_STOCK:
                            optionsMap.put(Option.GUI_FILTER_SHOP_STOCK, ShopRevancedGuiHandler.GuiIcon.MENUBAR_FILTER_STOCK_ALL);
                            break;
                        default:
                            break;
                    }
                }
            }

            PlayerSettings settings = new PlayerSettings(uuid, optionsMap);
            return settings;
        }
        return null;
    }
}




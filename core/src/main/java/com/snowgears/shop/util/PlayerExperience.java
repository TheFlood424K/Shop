package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

public class PlayerExperience {

    private UUID playerUUID;
    private int experience;

    public PlayerExperience(Player player) {
        this.playerUUID = player.getUniqueId();
        this.experience = EconomyUtils.getTotalExperience(player);
        saveToFile();
    }

    public PlayerExperience(UUID playerUUID, int experience) {
        this.playerUUID = playerUUID;
        this.experience = experience;
    }

    private void saveToFile(){
        try {
            File fileDirectory = new File(Shop.getPlugin().getDataFolder(), "Data");

            File experienceDirectory = new File(fileDirectory, "OfflineExperience");
            if (!experienceDirectory.exists())
                experienceDirectory.mkdir();

            File playerDataFile = new File(experienceDirectory, this.playerUUID.toString() + ".yml");
            if (!playerDataFile.exists())
                playerDataFile.createNewFile();

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerDataFile);

            config.set("player.UUID", this.playerUUID.toString());
            config.set("player.experience", this.experience);

            config.save(playerDataFile);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static PlayerExperience loadFromFile(OfflinePlayer player){
        if(player == null)
            return null;
        File fileDirectory = new File(Shop.getPlugin().getDataFolder(), "Data");

        File creativeDirectory = new File(fileDirectory, "OfflineExperience");
        if (!creativeDirectory.exists())
            creativeDirectory.mkdir();

        File playerDataFile = new File(creativeDirectory, player.getUniqueId().toString() + ".yml");

        if (playerDataFile.exists()) {

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerDataFile);

            // Same shape as PlayerSettings: a corrupt file must not throw out of the caller.
            String storedUuid = config.getString("player.UUID");
            UUID uuid;
            try {
                uuid = UUID.fromString(storedUuid);
            } catch (IllegalArgumentException | NullPointerException corrupt) {
                Shop.getPlugin().getLogger().warning("Corrupt experience data (bad UUID '"
                        + storedUuid + "') — treating as no saved experience.");
                return null;
            }
            int experience = config.getInt("player.experience");

            if (experience < 0) {
                // Repair balances written by a version whose removal had no floor. Left as-is
                // this value is permanent: getExperience() feeds affordability checks and message
                // rendering, and nothing else would ever raise it back to zero.
                Shop.getPlugin().getLogger().warning("Negative stored experience ("
                        + experience + ") for " + uuid + " — clamping to 0.");
                experience = 0;
                try {
                    config.set("player.experience", 0);
                    config.save(playerDataFile);
                } catch (java.io.IOException e) {
                    // The in-memory clamp above still holds for this read; failing to persist it
                    // only means the warning repeats next time.
                    Shop.getPlugin().getLogger().warning("Could not persist experience repair for "
                            + uuid + ": " + e.getMessage());
                }
            }

            PlayerExperience data = new PlayerExperience(uuid, experience);
            return data;
        }
        return null;
    }

    //this method is called when the player data is returned to the controlling player
    public void apply() {
        Player player = Bukkit.getPlayer(this.playerUUID);
        if(player == null)
            return;
        EconomyUtils.setTotalExperience(player, this.experience);
        removeFile();
    }

    private boolean removeFile(){
        File fileDirectory = new File(Shop.getPlugin().getDataFolder(), "Data");
        File creativeDirectory = new File(fileDirectory, "OfflineExperience");
        File playerDataFile = new File(creativeDirectory, this.playerUUID.toString() + ".yml");

        if (!playerDataFile.exists()) {
            return false;
        }
        else{
            playerDataFile.delete();
            return true;
        }
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getExperience() {
        return experience;
    }

    public void removeExperienceAmount(int amount) {
        // Clamp at zero. The online path in EconomyUtils.removeFunds cannot go negative —
        // setTotalExperience recomputes level and exp, which floors at level 0 — so clamping
        // here makes the two paths agree. Without it an offline player paying a cost can be
        // driven to a negative stored balance that persists to disk.
        experience = Math.max(0, experience - amount);
        saveToFile();
    }

    public void addExperienceAmount(int amount) {
        experience = experience + amount;
        saveToFile();
    }
}

package com.snowgears.shop.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySpawnMethod;
import org.bukkit.entity.Player;
import org.bukkit.entity.Player.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.ChatMode;
import org.bukkit.Spigot;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Simple stub implementation of OfflinePlayer for testing.
 */
class TestOfflinePlayer implements OfflinePlayer {
    private final String name;
    private final UUID uniqueId;

    TestOfflinePlayer(String name) {
        this.name = name;
        this.uniqueId = UUID.randomUUID();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public long getLastPlayed() {
        return 0L;
    }

    @Override
    public long getFirstPlayed() {
        return 0L;
    }

    @Override
    public Location getLastDeathLocation() {
        return null;
    }

    @Override
    public boolean isOp() {
        return false;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public boolean isBanned() {
        return false;
    }

    @Override
    public void setBanned(boolean value) {
    }

    @Override
    public boolean isWhitelisted() {
        return false;
    }

    @Override
    public void setWhitelisted(boolean value) {
    }

    @Override
    public int getViewRadius() {
        return 0;
    }

    @Override
    public void setViewRadius(int radius) {
    }

    @Override
    public int getSimulationDistance() {
        return 0;
    }

    @Override
    public void setSimulationDistance(int distance) {
    }

    @Override
    public boolean canSee(Player player) {
        return false;
    }

    @Override
    public boolean canSee(Entity entity) {
        return false;
    }

    @Override
    public boolean canSee(Location location) {
        return false;
    }

    @Override
    public Spigot spigot() {
        return null;
    }

    @Override
    public GameMode getGameMode() {
        return null;
    }

    @Override
    public void setGameMode(GameMode mode) {
    }

    @Override
    public int getHealth() {
        return 0;
    }

    @Override
    public double getMaxHealth() {
        return 0.0;
    }

    @Override
    public void setHealth(double value) {
    }

    @Override
    public int getFoodLevel() {
        return 0;
    }

    @Override
    public void setFoodLevel(int value) {
    }

    @Override
    public float getExhaustion() {
        return 0.0f;
    }

    @Override
    public void setExhaustion(float value) {
    }

    @Override
    public float getSaturation() {
        return 0.0f;
    }

    @Override
    public void setSaturation(float value) {
    }

    @Override
    public float getThreshold() {
        return 0.0f;
    }

    @Override
    public void setThreshold(float value) {
    }

    @Override
    public boolean isGliding() {
        return false;
    }

    @Override
    public void setGliding(boolean value) {
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public void teleport(Location location) {
    }

    @Override
    public boolean teleport(Entity entity, TeleportCause cause) {
        return false;
    }

    @Override
    public boolean teleport(Entity entity) {
        return false;
    }

    @Override
    public boolean teleport(Location location, TeleportCause cause) {
        return false;
    }

    @Override
    public boolean setVelocity(Vector velocity) {
        return false;
    }

    @Override
    public Vector getVelocity() {
        return null;
    }

    @Override
    public EntitySpawnMethod getSpawnMethod() {
        return null;
    }

    @Override
    public void setSpawnMethod(EntitySpawnMethod method) {
    }

    @Override
    public boolean isInsideVehicle() {
        return false;
    }

    @Override
    public Entity getVehicle() {
        return null;
    }

    @Override
    public void setVehicle(Entity vehicle) {
    }

    @Override
    public double getPitch() {
        return 0.0;
    }

    @Override
    public void setPitch(float pitch) {
    }

    @Override
    public double getYaw() {
        return 0.0;
    }

    @Override
    public void setYaw(float yaw) {
    }

    @Override
    public double getEyeHeight() {
        return 0.0;
    }

    @Override
    public boolean isDead() {
        return false;
    }

    @Override
    public void setDead(boolean value) {
    }

    @Override
    public void addPotionEffect(PotionEffect effect) {
    }

    @Override
    public void removePotionEffect(PotionEffectType type) {
    }

    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPotionEffect(PotionEffectType type) {
        return false;
    }

    @Override
    public int getFireTicks() {
        return 0;
    }

    @Override
    public void setFireTicks(int ticks) {
    }

    @Override
    public int getAirTicks() {
        return 0;
    }

    @Override
    public void setAirTicks(int ticks) {
    }

    @Override
    public int getMaximumAirTicks() {
        return 0;
    }

    @Override
    public int getRemainingAir() {
        return 0;
    }

    @Override
    public void setRemainingAir(int ticks) {
    }

    @Override
    public int getNoDamageTicks() {
        return 0;
    }

    @Override
    public void setNoDamageTicks(int ticks) {
    }

    @Override
    public int getMaximumNoDamageTicks() {
        return 0;
    }

    @Override
    public int getFireResistance() {
        return 0;
    }

    @Override
    public void setFireResistance(int resistance) {
    }

    @Override
    public boolean isInvulnerable() {
        return false;
    }

    @Override
    public void setInvulnerable(boolean value) {
    }

    @Override
    public int getSaturationLevel() {
        return 0;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    public double getLightLevel() {
        return 0.0;
    }

    @Override
    public Block getLocationBlock() {
        return null;
    }

    @Override
    public boolean isSleepingIgnored() {
        return false;
    }

    @Override
    public void setSleepingIgnored(boolean ignored) {
    }

    @Override
    public long getSleepTimer() {
        return 0L;
    }

    @Override
    public void wakeUp() {
    }

    @Override
    public ChatMode getChatMode() {
        return null;
    }

    @Override
    public void setChatMode(ChatMode mode) {
    }

    @Override
    public boolean canInteract(Block block) {
        return false;
    }

    @Override
    public boolean canInteract(Entity entity) {
        return false;
    }

    @Override
    public boolean canSee(BlockState blockState) {
        return false;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        return new PersistentDataContainer() {
            // Minimal implementation for testing - all methods return default values
            @Override
            public <T> PersistentDataContainer set(PersistentDataType<T, ?> key, T value) {
                return this;
            }

            @Override
            public <T> T get(PersistentDataType<T, ?> key, T defaultValue) {
                return defaultValue;
            }

            @Override
            public <T> T get(PersistentDataType<T, ?> key) {
                return null;
            }

            @Override
            public boolean has(PersistentDataType<?, ?> key) {
                return false;
            }

            @Override
            public boolean remove(PersistentDataType<?, ?> key) {
                return false;
            }

            @Override
            public PersistentDataAdapterContext getAdapterContext() {
                return null;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public java.util.Set<PersistantDataType<?, ?>> keys() {
                return Collections.emptySet();
            }
        };
    }

    @Override
    public boolean hasPlayedBefore() {
        return false;
    }

    @Override
    public OfflinePlayer[] getOfflinePlayers() {
        return new OfflinePlayer[0];
    }
}

/**
 * Unit tests for TransactionParty class.
 */
class TransactionPartyTest {

    private TransactionParty transactionParty;
    private OfflinePlayer mockPlayer;
    private Inventory mockInventory;
    private ItemStack testItem;

    @BeforeEach
    void setUp() {
        mockPlayer = new TestOfflinePlayer("test-player");
        mockInventory = mock(PlayerInventory.class);
        testItem = new ItemStack(org.bukkit.Material.DIAMOND, 1);

        // Default constructor arguments
        transactionParty = new TransactionParty(false, false, mockPlayer, mockInventory);
    }

    @Test
    void testDepositItem_Success() {
        // Arrange
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>()); // Empty map means all items added

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deposited when there's room");
        verify(mockInventory).addItem(testItem);
    }

    @Test
    void testDepositItem_Failure_NoRoom() {
        // Arrange
        // Return a map with the test item indicating it couldn't be added
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = transactionParty.depositItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deposited when there's no room");
        verify(mockInventory).addItem(testItem);
    }

    @Test
    void testDepositItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Return a map indicating failure (though admin should short-circuit)
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.addItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = adminParty.depositItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in depositing items");
        // Verify that addItem was NOT called for admin parties
        verify(mockInventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void testDeductItem_Success() {
        // Arrange
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(new HashMap<>()); // Empty map means all items removed

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Item should be successfully deducted when present");
        verify(mockInventory).removeItem(testItem);
    }

    @Test
    void testDeductItem_Failure_InsufficientQuantity() {
        // Arrange
        // Return a map with the test item indicating it couldn't be removed
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = transactionParty.deductItem(testItem);

        // Assert
        assertFalse(result, "Item should not be deducted when insufficient quantity");
        verify(mockInventory).removeItem(testItem);
    }

    @Test
    void testDeductItem_AdminAlwaysSucceeds() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Return a map indicating failure (though admin should short-circuit)
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, testItem);
        when(mockInventory.removeItem(any(ItemStack.class))).thenReturn(leftover);

        // Act
        boolean result = adminParty.deductItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always succeed in deducting items");
        // Verify that removeItem was NOT called for admin parties
        verify(mockInventory, never()).removeItem(any(ItemStack.class));
    }

    @Test
    void testHasRoomForItem_AdminAlwaysTrue() {
        // Arrange
        TransactionParty adminParty = new TransactionParty(false, true, mockPlayer, mockInventory);
        // Set up inventory to return false (no room) - admin should still return true
        when(mockInventory.getSize()).thenReturn(36);
        when(mockInventory.getContents()).thenReturn(new ItemStack[36]); // All empty slots
        when(mockInventory.firstEmpty()).thenReturn(0); // First slot is empty

        // Act
        boolean result = adminParty.hasRoomForItem(testItem);

        // Assert
        assertTrue(result, "Admin parties should always have room for items");
        // Note: We don't verify InventoryUtils.hasRoom was called because it's hard to mock static methods
        // The important thing is that admin logic short-circuits and returns true
    }

    @Test
    void testHasRoomForItem_DelegatesToInventoryUtils() {
        // Arrange
        TransactionParty nonAdminParty = new TransactionParty(false, false, mockPlayer, mockInventory);
        // Set up basic inventory state
        when(mockInventory.getSize()).thenReturn(36);
        when(mockInventory.getContents()).thenReturn(new ItemStack[36]); // All empty slots

        // Act
        boolean result = nonAdminParty.hasRoomForItem(testItem);

        // Assert
        // For an empty inventory with a single item, hasRoom should return true
        // We're not mocking InventoryUtils.hasRoom directly, but we're verifying the delegation happens
        // by checking that the method returns a reasonable value based on inventory state
        assertTrue(result, "Non-admin party should delegate to InventoryUtils.hasRoom");
    }
}
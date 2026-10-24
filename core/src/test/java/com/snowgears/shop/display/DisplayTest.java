package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ArmorStandData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Display functionality using Paper API (ItemDisplay, TextDisplay).
 * Verifies that displays are created correctly without NMS dependencies.
 */
class DisplayTest extends BaseMockBukkitTest {

    private Display display;
    private AbstractShop shop;
    private Player player;
    private Location signLocation;
    private Location chestLocation;

    @BeforeEach
    void setUp() {
        // Enable display for testing
        setConfig("displayType", com.snowgears.shop.display.DisplayType.ITEM);

        // Create test player
        player = getServer().addPlayer("TestPlayer");

        // Create test locations
        signLocation = new Location(getServer().getWorlds().get(0), 100, 64, 100);
        chestLocation = new Location(getServer().getWorlds().get(0), 100, 64, 101);

        // Create a test shop
        shop = AbstractShop.create(signLocation, player.getUniqueId(), 10.0, 5.0, 1, false, ShopType.SELL, BlockFace.NORTH);
        shop.setChestLocation(chestLocation);
        shop.setItemStack(new ItemStack(Material.DIAMOND));
        shop.setStock(64);

        // Initialize display
        display = new Display(signLocation);
    }

    @Test
    void testDisplayCreationWithoutNMS() {
        // Verify Display class can be instantiated without NMS
        assertNotNull(display);
        assertTrue(display.isEnabled());
    }

    @Test
    void testSpawnItemDisplayCreatesItemDisplayEntity() {
        // Spawn display for player
        display.spawn(player);

        // Get the display entities for this player
        List<Entity> entities = display.getAllEntityIDs();

        // Should have created at least one entity
        assertFalse(entities.isEmpty(), "Should have created display entities");

        // Verify the entity is an ItemDisplay (Paper API entity)
        Entity entity = entities.get(0);
        assertInstanceOf(ItemDisplay.class, entity, "Display entity should be ItemDisplay");

        // Verify item stack is set
        ItemDisplay itemDisplay = (ItemDisplay) entity;
        assertNotNull(itemDisplay.getItemStack(), "ItemDisplay should have item stack");
        assertEquals(Material.DIAMOND, itemDisplay.getItemStack().getType(), "ItemDisplay should have diamond");
    }

    @Test
    void testSpawnArmorStandPacketCreatesTextDisplayForTags() {
        // Create armor stand data with text (for display tags)
        ArmorStandData armorStandData = new ArmorStandData();
        armorStandData.setLocation(chestLocation.clone().add(0.5, 1.5, 0.5));

        String tagText = "§a$10.00\n§7[12/64]";

        // Spawn armor stand packet with text (creates TextDisplay for holograms)
        display.spawnArmorStandPacket(player, armorStandData, tagText);

        // Get the display tag entities for this player
        List<Entity> tagEntities = display.getAllDisplayTagEntityIDs();

        // Should have created a TextDisplay for the tag
        assertFalse(tagEntities.isEmpty(), "Should have created tag entities");

        // Verify the entity is a TextDisplay (Paper API entity)
        Entity entity = tagEntities.get(0);
        assertInstanceOf(TextDisplay.class, entity, "Tag entity should be TextDisplay");

        // Verify text content
        TextDisplay textDisplay = (TextDisplay) entity;
        assertNotNull(textDisplay.text(), "TextDisplay should have text");
    }

    @Test
    void testSpawnItemFramePacketCreatesItemDisplay() {
        // Spawn item frame packet (creates ItemDisplay for item frames)
        display.spawnItemFramePacket(player, new ItemStack(Material.DIAMOND), chestLocation.clone().add(0.5, 1.5, 0.5), BlockFace.NORTH, true);

        // Get the display entities for this player
        List<Entity> entities = display.getAllEntityIDs();

        // Should have created an entity
        assertFalse(entities.isEmpty(), "Should have created item frame entity");

        // Verify the entity is an ItemDisplay
        Entity entity = entities.get(0);
        assertInstanceOf(ItemDisplay.class, entity, "Item frame entity should be ItemDisplay");

        // Verify glowing state
        ItemDisplay itemDisplay = (ItemDisplay) entity;
        assertTrue(itemDisplay.isGlowing(), "Item frame should be glowing");
    }

    @Test
    void testRemoveDisplayEntitiesCleansUpEntities() {
        // First spawn some displays
        display.spawn(player);
        display.spawnArmorStandPacket(player, new ArmorStandData(), "Test Tag");

        // Verify entities exist
        assertFalse(display.getAllEntityIDs().isEmpty(), "Should have display entities");
        assertFalse(display.getAllDisplayTagEntityIDs().isEmpty(), "Should have tag entities");

        // Remove display entities (not tags)
        display.removeDisplayEntities(player, false);

        // Display entities should be cleaned up for this player
        assertTrue(display.getAllEntityIDs().isEmpty(), "Display entities should be removed");
        assertFalse(display.getAllDisplayTagEntityIDs().isEmpty(), "Tag entities should remain");

        // Remove tag entities
        display.removeDisplayEntities(player, true);

        // All entities should be cleaned up
        assertTrue(display.getAllDisplayTagEntityIDs().isEmpty(), "Tag entities should be removed");
    }

    @Test
    void testRemoveDisplayEntitiesWithNullPlayerRemovesForAll() {
        // Spawn displays
        display.spawn(player);
        display.spawnArmorStandPacket(player, new ArmorStandData(), "Test Tag");

        // Remove with null player (should remove for all)
        display.removeDisplayEntities(null, false);
        display.removeDisplayEntities(null, true);

        // All entities should be cleaned up
        assertTrue(display.getAllEntityIDs().isEmpty(), "Display entities should be removed for all");
        assertTrue(display.getAllDisplayTagEntityIDs().isEmpty(), "Tag entities should be removed for all");
    }

    @Test
    void testDisplayDisabledIsNotEnabled() {
        // Create disabled display
        com.snowgears.shop.display.DisplayDisabled disabledDisplay =
            new com.snowgears.shop.display.DisplayDisabled(signLocation);

        // Verify it's disabled
        assertFalse(disabledDisplay.isEnabled(), "DisplayDisabled should not be enabled");
    }

    @Test
    void testDisplayDisabledDoesNotCreateEntities() {
        com.snowgears.shop.display.DisplayDisabled disabledDisplay =
            new com.snowgears.shop.display.DisplayDisabled(signLocation);

        // Try to spawn - should not create entities
        disabledDisplay.spawn(player);
        disabledDisplay.spawnArmorStandPacket(player, new ArmorStandData(), "Test");
        disabledDisplay.spawnItemFramePacket(player, new ItemStack(Material.DIAMOND), chestLocation, BlockFace.NORTH, false);

        // No entities should be created
        assertTrue(disabledDisplay.getAllEntityIDs().isEmpty(), "Disabled display should not create entities");
        assertTrue(disabledDisplay.getAllDisplayTagEntityIDs().isEmpty(), "Disabled display should not create tag entities");
    }

    @Test
    void testGetItemNameNMS() {
        // Test item name retrieval
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        String name = display.getItemNameNMS(diamond);
        assertEquals("DIAMOND", name, "Should return material name for item without display name");

        // Test with custom display name
        ItemStack namedDiamond = new ItemStack(Material.DIAMOND);
        namedDiamond.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Super Diamond")));
        name = display.getItemNameNMS(namedDiamond);
        assertEquals("Super Diamond", name, "Should return custom display name");
    }

    @Test
    void testShowDisplayTagsCreatesTextDisplays() {
        // Setup shop for display tags
        shop.setType(ShopType.SELL);
        setConfig("displayTagOption", com.snowgears.shop.shop.DisplayTagOption.ALL);

        // Show display tags
        display.showDisplayTags(player);

        // Should have created TextDisplay entities for tags
        List<Entity> tagEntities = display.getAllDisplayTagEntityIDs();
        assertFalse(tagEntities.isEmpty(), "Should have created tag TextDisplay entities");

        // Verify all are TextDisplay
        for (Entity entity : tagEntities) {
            assertInstanceOf(TextDisplay.class, entity, "All tag entities should be TextDisplay");
        }
    }

    @Test
    void testDisplayEntitiesArePlayerSpecific() {
        // Create second player
        Player player2 = getServer().addPlayer("TestPlayer2");

        // Spawn displays for player1
        display.spawn(player);

        // Player2 should not see player1's displays (they're shown via showEntity)
        // The entities exist but are only visible to player1
        List<Entity> player1Entities = display.getAllEntityIDs();
        assertFalse(player1Entities.isEmpty(), "Player1 should have display entities");

        // Spawn for player2
        display.spawn(player2);

        // Now there should be more entities (one set per player)
        List<Entity> allEntities = display.getAllEntityIDs();
        assertTrue(allEntities.size() >= player1Entities.size(), "Should have entities for both players");
    }

    @Test
    void testRemoveDisplayEntitiesIsPlayerSpecific() {
        // Create second player
        Player player2 = getServer().addPlayer("TestPlayer2");

        // Spawn displays for both players
        display.spawn(player);
        display.spawn(player2);

        // Verify both have entities
        List<Entity> allEntitiesBefore = display.getAllEntityIDs();
        assertTrue(allEntitiesBefore.size() >= 2, "Should have entities for both players");

        // Remove display entities for player1 only
        display.removeDisplayEntities(player, false);

        // Player1's entities should be removed
        List<Entity> player1EntitiesAfter = new ArrayList<>();
        for (Entity e : display.getDisplayEntityIDIterator(player, false)) {
            player1EntitiesAfter.add(e);
        }
        assertTrue(player1EntitiesAfter.isEmpty(), "Player1's display entities should be removed");

        // Player2's entities should still exist
        List<Entity> player2EntitiesAfter = new ArrayList<>();
        for (Entity e : display.getDisplayEntityIDIterator(player2, false)) {
            player2EntitiesAfter.add(e);
        }
        assertFalse(player2EntitiesAfter.isEmpty(), "Player2's display entities should remain");

        // getAllEntityIDs should still return player2's entities
        List<Entity> allEntitiesAfter = display.getAllEntityIDs();
        assertEquals(player2EntitiesAfter.size(), allEntitiesAfter.size(),
            "getAllEntityIDs should only return remaining entities");
    }

    @Test
    void testRemoveDisplayTagsIsPlayerSpecific() {
        // Create second player
        Player player2 = getServer().addPlayer("TestPlayer2");

        // Spawn display tags for both players
        ArmorStandData armorStandData = new ArmorStandData();
        armorStandData.setLocation(chestLocation.clone().add(0.5, 1.5, 0.5));
        display.spawnArmorStandPacket(player, armorStandData, "Tag for Player1");
        display.spawnArmorStandPacket(player2, armorStandData, "Tag for Player2");

        // Verify both have tag entities
        List<Entity> allTagEntitiesBefore = display.getAllDisplayTagEntityIDs();
        assertTrue(allTagEntitiesBefore.size() >= 2, "Should have tag entities for both players");

        // Remove display tags for player1 only
        display.removeDisplayEntities(player, true);

        // Player1's tag entities should be removed
        List<Entity> player1TagEntitiesAfter = new ArrayList<>();
        for (Entity e : display.getDisplayEntityIDIterator(player, true)) {
            player1TagEntitiesAfter.add(e);
        }
        assertTrue(player1TagEntitiesAfter.isEmpty(), "Player1's tag entities should be removed");

        // Player2's tag entities should still exist
        List<Entity> player2TagEntitiesAfter = new ArrayList<>();
        for (Entity e : display.getDisplayEntityIDIterator(player2, true)) {
            player2TagEntitiesAfter.add(e);
        }
        assertFalse(player2TagEntitiesAfter.isEmpty(), "Player2's tag entities should remain");

        // getAllDisplayTagEntityIDs should still return player2's entities
        List<Entity> allTagEntitiesAfter = display.getAllDisplayTagEntityIDs();
        assertEquals(player2TagEntitiesAfter.size(), allTagEntitiesAfter.size(),
            "getAllDisplayTagEntityIDs should only return remaining tag entities");
    }

    @Test
    void testItemDisplayConfiguration() {
        display.spawn(player);

        List<Entity> entities = display.getAllEntityIDs();
        ItemDisplay itemDisplay = (ItemDisplay) entities.get(0);

        // Verify ItemDisplay is configured correctly for shop displays
        assertEquals(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED,
            itemDisplay.getItemDisplayTransform(), "Should use FIXED transform");
        assertEquals(0, itemDisplay.getInterpolationDelay(), "Should have no interpolation delay");
        assertEquals(0, itemDisplay.getInterpolationDuration(), "Should have no interpolation duration");
        assertEquals(0, itemDisplay.getTeleportDuration(), "Should have no teleport duration");
        assertEquals(15, itemDisplay.getBrightness().blockLight(), "Should have max block light");
        assertEquals(15, itemDisplay.getBrightness().skyLight(), "Should have max sky light");
        assertFalse(itemDisplay.isGlowing(), "Should not be glowing by default");
        assertEquals(0.0f, itemDisplay.getShadowRadius(), "Should have no shadow");
        assertEquals(0.0f, itemDisplay.getShadowStrength(), "Should have no shadow strength");
    }

    @Test
    void testTextDisplayConfiguration() {
        ArmorStandData armorStandData = new ArmorStandData();
        armorStandData.setLocation(chestLocation.clone().add(0.5, 1.5, 0.5));

        display.spawnArmorStandPacket(player, armorStandData, "§aTest Tag");

        List<Entity> tagEntities = display.getAllDisplayTagEntityIDs();
        TextDisplay textDisplay = (TextDisplay) tagEntities.get(0);

        // Verify TextDisplay is configured correctly for holograms
        assertEquals(org.bukkit.entity.Display.Billboard.CENTER,
            textDisplay.getBillboard(), "Should use CENTER billboard");
        assertTrue(textDisplay.isSeeThrough(), "Should be see-through");
        assertFalse(textDisplay.getDefaultBackground(), "Should not have default background");
        assertEquals(TextDisplay.TextAlignment.CENTER,
            textDisplay.getAlignment(), "Should be center aligned");
        assertEquals(0, textDisplay.getInterpolationDelay(), "Should have no interpolation delay");
        assertEquals(0, textDisplay.getInterpolationDuration(), "Should have no interpolation duration");
        assertEquals(0, textDisplay.getTeleportDuration(), "Should have no teleport duration");
        assertFalse(textDisplay.isGlowing(), "Should not be glowing");
    }
}
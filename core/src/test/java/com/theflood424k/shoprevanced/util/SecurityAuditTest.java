package com.theflood424k.shoprevanced.util;

import com.theflood424k.shoprevanced.ShopRevanced;
import com.theflood424k.shoprevanced.handler.LogHandler;
import com.theflood424k.shoprevanced.testsupport.BaseMockBukkitTest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security-focused tests covering:
 * <ul>
 *   <li>Admin-keyword exact match (no substring bypass)</li>
 *   <li>TransactionParty partial-failure detection (deductItem/depositItem/deductFunds/depositFunds)</li>
 *   <li>LogHandler JDBC property sanitization</li>
 * </ul>
 */
@Tag("security")
class SecurityAuditTest extends BaseMockBukkitTest {

    // ---------- CreationValidator.getShopIsAdmin: exact match only ----------

    @DisplayName("getShopIsAdmin: exact keyword match (case-insensitive, trimmed) → true")
    @Test
    void getShopIsAdmin_exactMatch_returnsTrue() {
        CreationValidator validator = new CreationValidator(getPlugin());
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertTrue(validator.getShopIsAdmin("admin"));
            assertTrue(validator.getShopIsAdmin("ADMIN"));
            assertTrue(validator.getShopIsAdmin("  Admin  "));
        }
    }

    @DisplayName("getShopIsAdmin: substring match must NOT trigger admin → false")
    @Test
    void getShopIsAdmin_substringMatch_returnsFalse() {
        CreationValidator validator = new CreationValidator(getPlugin());
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin("this is an admin shop"));
            assertFalse(validator.getShopIsAdmin("admin shop"));
            assertFalse(validator.getShopIsAdmin("superadmin"));
            assertFalse(validator.getShopIsAdmin("administer"));
        }
    }

    @DisplayName("getShopIsAdmin: null input → false (no NPE)")
    @Test
    void getShopIsAdmin_nullInput_returnsFalse() {
        CreationValidator validator = new CreationValidator(getPlugin());
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin(null));
        }
    }

    @DisplayName("getShopIsAdmin: empty input → false")
    @Test
    void getShopIsAdmin_emptyInput_returnsFalse() {
        CreationValidator validator = new CreationValidator(getPlugin());
        try (MockedStatic<ShopRevancedMessage> srm = Mockito.mockStatic(ShopRevancedMessage.class)) {
            srm.when(() -> ShopRevancedMessage.getCreationWord("ADMIN")).thenReturn("admin");

            assertFalse(validator.getShopIsAdmin(""));
            assertFalse(validator.getShopIsAdmin("   "));
        }
    }

    // ---------- TransactionParty: partial-failure detection ----------

    @DisplayName("deductItem: removeItem partial failure → returns false")
    @Test
    void deductItem_partialFailure_returnsFalse() {
        TransactionParty party = new TransactionParty(false, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory());
        ItemStack item = new ItemStack(Material.DIRT, 5);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            // removeItem returns >0 meaning items could NOT be removed (partial failure)
            iu.when(() -> InventoryUtils.removeItem(any(), any())).thenReturn(2);

            assertFalse(party.deductItem(item));
        }
    }

    @DisplayName("deductItem: full removal → returns true")
    @Test
    void deductItem_fullRemoval_returnsTrue() {
        TransactionParty party = new TransactionParty(false, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory());
        ItemStack item = new ItemStack(Material.DIRT, 5);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.removeItem(any(), any())).thenReturn(0);

            assertTrue(party.deductItem(item));
        }
    }

    @DisplayName("depositItem: addItem partial failure → returns false")
    @Test
    void depositItem_partialFailure_returnsFalse() {
        TransactionParty party = new TransactionParty(false, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory());
        ItemStack item = new ItemStack(Material.DIRT, 5);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.hasRoom(any(), any())).thenReturn(true);
            // addItem returns >0 meaning items could NOT be added (partial failure)
            iu.when(() -> InventoryUtils.addItem(any(), any())).thenReturn(2);

            assertFalse(party.depositItem(item));
        }
    }

    @DisplayName("depositItem: full deposit → returns true")
    @Test
    void depositItem_fullDeposit_returnsTrue() {
        TransactionParty party = new TransactionParty(false, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory());
        ItemStack item = new ItemStack(Material.DIRT, 5);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.hasRoom(any(), any())).thenReturn(true);
            iu.when(() -> InventoryUtils.addItem(any(), any())).thenReturn(0);

            assertTrue(party.depositItem(item));
        }
    }

    @DisplayName("deductFunds (currency item): removeItem partial failure → returns false")
    @Test
    void deductFunds_currencyItem_partialFailure_returnsFalse() {
        ItemStack currency = new ItemStack(Material.EMERALD, 1);
        TransactionParty party = new TransactionParty(true, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory(), currency);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.removeItem(any(), any())).thenReturn(1);

            assertFalse(party.deductFunds(1.0));
        }
    }

    @DisplayName("depositFunds (currency item): addItem partial failure → returns false")
    @Test
    void depositFunds_currencyItem_partialFailure_returnsFalse() {
        ItemStack currency = new ItemStack(Material.EMERALD, 1);
        TransactionParty party = new TransactionParty(true, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory(), currency);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.addItem(any(), any())).thenReturn(1);

            assertFalse(party.depositFunds(1.0));
        }
    }

    @DisplayName("depositFunds (currency item): full deposit → returns true")
    @Test
    void depositFunds_currencyItem_fullDeposit_returnsTrue() {
        ItemStack currency = new ItemStack(Material.EMERALD, 1);
        TransactionParty party = new TransactionParty(true, false, getServer().addPlayer(),
                getServer().addPlayer().getInventory(), currency);

        try (MockedStatic<InventoryUtils> iu = Mockito.mockStatic(InventoryUtils.class)) {
            iu.when(() -> InventoryUtils.addItem(any(), any())).thenReturn(0);

            assertTrue(party.depositFunds(1.0));
        }
    }

    @DisplayName("TransactionParty admin: deductItem/depositItem/deductFunds/depositFunds always succeed")
    @Test
    void adminParty_operationsAlwaysSucceed() {
        TransactionParty admin = new TransactionParty(false, true, getServer().addPlayer(),
                getServer().addPlayer().getInventory());
        ItemStack item = new ItemStack(Material.DIRT, 5);

        assertTrue(admin.deductItem(item));
        assertTrue(admin.depositItem(item));
        assertTrue(admin.deductFunds(100.0));
        assertTrue(admin.depositFunds(100.0));
    }

    // ---------- LogHandler: JDBC property sanitization ----------

    @DisplayName("isValidJdbcProperty: null → false")
    @Test
    void isValidJdbcProperty_null_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty(null));
    }

    @DisplayName("isValidJdbcProperty: empty → false")
    @Test
    void isValidJdbcProperty_empty_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty(""));
        assertFalse(LogHandler.isValidJdbcProperty("   "));
    }

    @DisplayName("isValidJdbcProperty: missing equals sign → false")
    @Test
    void isValidJdbcProperty_noEquals_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("noequalsign"));
    }

    @DisplayName("isValidJdbcProperty: empty key → false")
    @Test
    void isValidJdbcProperty_emptyKey_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("=value"));
    }

    @DisplayName("isValidJdbcProperty: invalid key characters → false")
    @Test
    void isValidJdbcProperty_invalidKey_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("bad key=value"));
        assertFalse(LogHandler.isValidJdbcProperty("bad-key=value"));
        assertFalse(LogHandler.isValidJdbcProperty("bad@key=value"));
    }

    @DisplayName("isValidJdbcProperty: value containing semicolon → false (JDBC injection)")
    @Test
    void isValidJdbcProperty_valueSemicolon_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("key=value;drop table"));
        assertFalse(LogHandler.isValidJdbcProperty("key=value;user=evil"));
    }

    @DisplayName("isValidJdbcProperty: value containing ampersand → false")
    @Test
    void isValidJdbcProperty_valueAmpersand_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("key=value&other=evil"));
    }

    @DisplayName("isValidJdbcProperty: value containing question mark → false")
    @Test
    void isValidJdbcProperty_valueQuestionMark_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("key=value?extra=param"));
    }

    @DisplayName("isValidJdbcProperty: value containing space → false")
    @Test
    void isValidJdbcProperty_valueSpace_returnsFalse() {
        assertFalse(LogHandler.isValidJdbcProperty("key=value with space"));
    }

    @DisplayName("isValidJdbcProperty: valid property → true")
    @Test
    void isValidJdbcProperty_valid_returnsTrue() {
        assertTrue(LogHandler.isValidJdbcProperty("key=value"));
        assertTrue(LogHandler.isValidJdbcProperty("socketTimeout=30"));
        assertTrue(LogHandler.isValidJdbcProperty("useSSL=false"));
        assertTrue(LogHandler.isValidJdbcProperty("allowPublicKeyRetrieval=true"));
    }

    @DisplayName("isValidJdbcProperty: value with only safe chars → true")
    @Test
    void isValidJdbcProperty_safeValueChars_returnsTrue() {
        assertTrue(LogHandler.isValidJdbcProperty("key=abc123_-"));
    }
}

# Bug Memory Cache — Living Document of Shop Plugin Issues

> **Last Updated:** `2026-09-26`  
> **Status:** All 23 documented issues have been resolved  
> **Purpose:** This file serves as a living cache of bugs and fixes for the Shop plugin, helping contributors understand past issues and prevent regressions.

## 📋 Quick Reference Table

| ID | Issue Summary | Status | Fixed In | Severity |
|----|---------------|--------|----------|----------|
| 1 | addShop() Called After setType() (Primary NPE) | ✅ FIXED | `bed25e6` | High |
| 2 | Item Data Set After addShop() (Null-Item Race) | ✅ FIXED | `3098b6d` | High |
| 3 | initializeShop() NPE on Null chestLocation | ✅ FIXED | `f7a301f` | High |
| 4 | Sign-Post Shops Deleted on Reload | ✅ FIXED | `3c37313` | High |
| 5 | calculateStock() Returning -1 Sentinel | ✅ FIXED | `3c37313` | Medium |
| 6 | WorldGuard BUILD Flag Contradiction | ✅ FIXED | `c4bf623` | Medium |
| 7 | onShopSignClick Ignored Sign-Post Shops | ✅ FIXED | `63f0365` | Medium |
| 8 | onShopChestClick Deleted Sign-Post Shops | ✅ FIXED | `63f0365` | Medium |
| 9 | onExplosion Only Protected Wall Signs | ✅ FIXED | `345be5c` | Medium |
| 10 | getShopTouchingBlock Only Found WallSign | ✅ FIXED | `345be5c` | Medium |
| 11 | processBatchDisplayUpdates Used distance() | ✅ FIXED | `345be5c` | Low |
| 12 | Duplicate onPlayerJoin/onLogin Listeners | ✅ FIXED | `345be5c` | Low |
| 13 | CommandHandler Created Duplicate Handlers | ✅ FIXED | Various | High |
| 14 | CommandHandler Constructed with null Permission | ✅ FIXED | Various | High |
| 15 | /shop currency Silently Did Nothing for Non-Ops | ✅ FIXED | Various | Medium |
| 16 | /shop notify Subcommand Missing from Help | ✅ FIXED | Various | Medium |
| 17 | Null commandAlias from Config Caused Failure | ✅ FIXED | Various | Medium |
| 18 | Hopper Protection Only Checks Above Chests | ✅ FIXED | Current | Medium |
| 19 | Version Parsing Vulnerability | ✅ FIXED | Current | Low |
| 20 | Shared Inventory Array Reference | ✅ FIXED | Current | High |
| 21 | Potential NPEs in GUI Config Loading | ✅ FIXED | Current | Medium |
| 22 | Stock Not Updating After Transactions | ✅ FIXED | Current | Medium |
| 23 | Stock Not Updating After Transactions in Tx Handler | ✅ FIXED | Current | Medium |

---

## 🕰️ Historical Bugs (1-17)

These bugs were resolved in earlier development cycles and represent foundational fixes to the shop loading and interaction systems.

<details>
<summary>Click to expand historical bugs details</summary>

### Bug 1 — addShop() Called After setType() (Primary NPE)
**File:** `ShopHandler.java` / `loadShops()`  
**Commits:** [`64cac1e`][c1] → [`bed25e6`][c2] → [`1ad7b3c`][c3]  
**Root cause:** `AbstractDisplay.getShop()` resolves via HashMap lookup in `ShopHandler`. `setType(displayType, true)` calls `getShop()` internally to check chest block. If `addShop()` hadn't been called yet, map returned `null`, throwing NPE.  
**Fix:** Move `addShop(shop)` before `setType()` so shop is registered before display code tries to look it up.

### Bug 2 — Item Data Set After addShop() (Null-Item Race Condition)
**File:** `ShopHandler.java` / `loadShops()`  
**Commit:** [`3098b6d`][c4]  
**Root cause:** Loading order: 1) `addShop(shop)`, 2) `setType(displayType)`, 3) `setItemStack(item)`. Concurrent chunk-load between steps 1-3 saw shop with null item stack, causing `isInitialized()` → false.  
**Fix:** Set all item data before calling `addShop()`, so shop is fully initialized when publicly visible.

### Bug 3 — initializeShop() NPE on Null chestLocation
**File:** `AbstractShop.java` / `initializeShop()`  
**Commit:** [`f7a301f`][c5]  
**Root cause:** `shop.getChestLocation()` can be null when shop is freshly constructed but `load()` failed, or sign placed on tick boundary before block state propagated. Subsequent `.getBlock()` threw NPE that silently swallowed initialization.  
**Fix:** Add explicit null check before display-room block access and return false if chest location unavailable.

### Bug 4 — Sign-Post Shops Deleted on Reload
**File:** `AbstractShop.java` / `load()`  
**Commit:** [`3c37313`][c6]  
**Root cause:** `load()` unconditionally required `WallSign` block data and called `delete()` if it didn't match. Sign-post shops (with `Rotatable` data) were deleted on every server reload.  
**Fix:** Add `Rotatable` branch to `load()` that snaps to nearest cardinal face (reusing `snapToCardinal` logic).

### Bug 5 — calculateStock() Returning -1 Sentinel Propagated to Signs
**File:** `AbstractShop.java` / `calculateStock()`, `updateStock()`  
**Commit:** [`3c37313`][c6]  
**Root cause:** `calculateStock()` returned -1 when inventory or item was null (chunk unloaded, shop not initialized). Callers treated -1 as real stock value, writing to sign display and corrupting partial-sales math.  
**Fix:** Introduce `UNAVAILABLE` sentinel constant (= -1) and add early-return guard in `updateStock()` that skips sign updates when `calculateStock()` returns sentinel.

### Bug 6 — WorldGuard BUILD Flag Contradiction Blocked Shop Creation
**File:** `config.yml` / `createShopFlagChecks`  
**Commit:** [`c4bf623`][c7]  
**Root cause:** `BUILD` appeared in both `denyFlags` and `allowFlags`. In WorldGuard-protected region, `BUILD` is implicitly DENY for non-members — so `denyFlags` check matched immediately and returned false, blocking shop creation.  
**Fix:** Remove `BUILD` from `denyFlags` entirely. Preserve `allowFlags` entry so region owners/members with BUILD=ALLOW can still create shops.

### Bug 7 — onShopSignClick Ignored Sign-Post Shops Entirely
**File:** `ShopListener.java` / `onShopSignClick()`  
**Commit:** [`63f0365`][c8]  
**Root cause:** Click handler gated sign interaction with `event.getClickedBlock().getBlockData() instanceof WallSign`. Sign-post shops use `Rotatable` block data (standing sign), not `WallSign`, so clicks fell through check completely.  
**Fix:** Replace `instanceof WallSign` check with Tag-based check accepting both wall and standing signs: `Tag.WALL_SIGNS.isTagged(clicked.getType()) || Tag.STANDING_SIGNS.isTagged(clicked.getType())`

### Bug 8 — onShopChestClick Deleted Sign-Post Shops on Every Chest Click
**File:** `ShopListener.java` / `onShopChestClick()`  
**Commit:** [`63f0365`][c8]  
**Root cause:** Chest-click validity guard checked shop's attached sign using `instanceof WallSign`. Sign-post shops had `Rotatable` sign data, causing permanent deletion on first chest right-click after every reload.  
**Fix:** Apply same Tag-based check used for Bug 7: check sign validity using `Tag.WALL_SIGNS.isTagged(signBlock.getType()) || Tag.STANDING_SIGNS.isTagged(signBlock.getType())`. Also fixed log message from "sign is not exist" → "sign does not exist".

### Bug 9 — onExplosion Only Protected Wall Signs (Sign-Post Shops Destroyed by Explosions)
**Status:** ✅ **Fixed in [`345be5c`][c12fix]**  
**File:** `ShopListener.java` / `onExplosion()`  
**Severity:** Medium  
**Root cause:** Explosion protection block-list filter checked `Tag.WALL_SIGNS.isTagged(block.getType())` but did not check `Tag.STANDING_SIGNS`. Sign-post shop signs were not removed from explosion block list and would be destroyed by creeper/TNT explosions.  
**Fix:** Mirror Tag-based union: `if (Tag.WALL_SIGNS.isTagged(block.getType()) || Tag.STANDING_SIGNS.isTagged(block.getType()))`

### Bug 10 — getShopTouchingBlock Only Found WallSign-Attached Shops
**Status:** ✅ **Fixed in [`345be5c`][c12fix]**  
**File:** `ShopHandler.java` / `getShopTouchingBlock()`  
**Severity:** Medium  
**Root cause:** `getShopTouchingBlock()` scanned adjacent blocks for `WallSign` to confirm shop presence. Sign-post shops have `Rotatable` block data, so method always returned null for sign-post shops.  
**Fix:** Replace `instanceof WallSign` check with Tag lookup: `Material signType = shopChest.getRelative(newFace).getType(); if(Tag.WALL_SIGNS.isTagged(signType) || Tag.STANDING_SIGNS.isTagged(signType))`

### Bug 11 — processBatchDisplayUpdates Used distance() Instead of distanceSquared() (Redundant Sqrt)
**Status:** ✅ **Fixed in [`345be5c`][c12fix]** (performance issue, not a crash)  
**File:** `ShopHandler.java` / `processBatchDisplayUpdates()`  
**Severity:** Low  
**Root cause:** `getShopLocationsNearLocationWithinDistance()` correctly avoided `Math.sqrt` by accepting/comparing `maxDistanceSquared`. However, inside `processBatchDisplayUpdates()` distance was re-computed with expensive `location.distance()` call (calls `Math.sqrt` internally) to decide which shops go into `displaysToShow` vs `displaysToRemove`, and again in sorting lambda.  
**Fix:** Use `distanceSquared()` throughout and compare against `maxDisplayDistance²`: `double maxDistSq = plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance(); double distSq = playerLocation.distanceSquared(shop.getSignLocation()); if (distSq < maxDistSq)`

### Bug 12 — Duplicate onPlayerJoin / onLogin Listener Methods Cached Name Twice
**Status:** ✅ **Fixed in [`345be5c`][c12fix]** (minor correctness / performance issue)  
**File:** `ShopListener.java`  
**Severity:** Low  
**Root cause:** `ShopListener` registered two `@EventHandler` methods for `PlayerJoinEvent` (`onPlayerJoin` and `onLogin`). Bukkit fired both for every join event. `onPlayerJoin` only cached player name; `onLogin` did shop-cleanup, XP sync, and offline-transaction setup. Name cache call in `onPlayerJoin` ran redundantly alongside `onLogin`.  
**Fix:** Moved `PlayerNameCache.cacheName()` call into existing `onLogin` handler and deleted `onPlayerJoin` entirely, leaving exactly one `PlayerJoinEvent` handler.

### Bug 13 — CommandHandler.register() Created Duplicate Handlers on Every Reload
**Status:** ✅ **Fixed**  
**File:** `CommandHandler.java` / `register()`, `Shop.java` / `reload()`  
**Severity:** High  
**Root cause:** `CommandHandler` registered itself with Bukkit's `CommandMap` via reflection inside its constructor. Because `Shop.java` constructed a new `CommandHandler` during every `plugin.reload()` call, each reload appended another entry to `CommandMap` without removing previous one. Bukkit's `CommandMap` has no built-in deduplication — first registered handler wins for dispatch. After one or more reloads, `/shop` was dispatched to a stale handler instance from previous load cycle.  
**Fix:** Keep single `CommandHandler` instance as field on `Shop`. On reload, call `commandHandler.reregister(commandAlias)` instead of constructing new handler — which first removes old `CommandMap` entry before re-registering updated one.

### Bug 14 — CommandHandler Constructed with null Permission
**Status:** ✅ **Fixed**  
**File:** `Shop.java` (CommandHandler construction), `CommandHandler.java` constructor  
**Severity:** High  
**Root cause:** `CommandHandler` was constructed with `null` passed as `permission` argument, forwarded to `this.setPermission(null)`. On most Bukkit/Paper builds this does not throw, but leaves command with no declared root permission node. Permission plugins and Paper's `ops-permission-level` system check declared permission before dispatching to `execute()`. With `null`, servers using `default-permission: op` at command level would silently block command for non-ops — even when plugin's own `usePerms()` was false.  
**Fix:** Pass `"shop.use"` as permission string instead of `null`: `commandHandler = new CommandHandler(this, "shop.use", commandAlias, ...)`

### Bug 15 — /shop currency Silently Did Nothing for Non-Op Players
**Status:** ✅ **Fixed**  
**File:** `CommandHandler.java` / `execute()` — `currency` branch  
**Severity:** Medium  
**Root cause:** Help text shown on `/shop` (no args) listed `/shop currency` as command available to all players. However, `execute()` branch for `currency` wrapped response in operator/OP guard: non-op, non-operator players received NO output and NO error.  
**Fix:** `currency_output` is now sent to all players; `currency_output_tip` (describes how to change currency) is reserved for operators.

### Bug 16 — /shop notify Subcommand Missing from Help Text
**Status:** ✅ **Fixed** (documentation/UX issue)  
**File:** `CommandHandler.java` / `execute()` — zero-args help block  
**Severity:** Medium  
**Root cause:** `/shop notify user|owner|stock` was fully implemented in both `execute()` and `tabComplete()`, but was never listed in help output shown when player ran `/shop` with no arguments. Players had no way to discover command from in-game help.  
**Fix:** Added `sendCommandMessage("notify", player)` to zero-args help block so command appears alongside `list`, `currency`, etc.

### Bug 17 — Null commandAlias from Config Caused Silent Full Command Failure
**Status:** ✅ **Fixed**  
**File:** `Shop.java` — `commandAlias` config loading  
**Severity:** Medium  
**Root cause:** `commandAlias` was read from `config.yml` and passed directly as command name to `CommandHandler` constructor and `BukkitCommand` super. If config key was missing or returned `null`, `BukkitCommand` received `null` as its name. Subsequent `commandMap.register(null, this)` call inside `register()` threw `NullPointerException` that was caught and printed, but command registration never completed — leaving all `/shop` commands non-functional with only stack trace in console.  
**Fix:** Added null/blank guard when loading `commandAlias`, falling back to `"shop"`: `if (commandAlias == null || commandAlias.isBlank()) { commandAlias = "shop"; plugin.getLogger().warning("'commandAlias' is missing or blank in config.yml — defaulting to 'shop'"); }`

</details>

---

## 🐞 Living Cache: Recently Fixed Issues (18-23)

This section serves as a living cache of recently identified and fixed bugs. These represent the most current issues discovered during systematic code reviews and are actively maintained as we continue to improve the plugin.

### Issue 18 — Hopper Protection Only Checks Above Chests [FIXED]
**File:** `ShopListener.java` / `onShopExpansion()`  
**Severity:** Medium  
**Root cause:** The hopper protection logic only checks for hoppers placed directly above chests (`b.getRelative(BlockFace.UP)`), but hoppers can also be placed on the sides of chests to access their contents. This allows players to bypass hopper protection by placing hoppers on the sides of shop chests.  
**Fix:** Check all six adjacent faces for hoppers, not just the upper face.

### Issue 19 — Version Parsing Vulnerability [FIXED]
**File:** `UtilMethods.java` / `isMCVersion17Plus()`, `isMCVersion14Plus()`  
**Severity:** Low  
**Root cause:** The version parsing methods assume the volume string has at least two parts (major.minor), but if the volume string format is unexpected and contains fewer than two parts, an `ArrayIndexOutOfBoundsException` will be thrown when accessing `parts[1]`.  
**Fix:** Add bounds checking before accessing `parts[1]`.

### Issue 20 — Shared Inventory Array Reference [FIXED]
**File:** `InventoryUtils.java` / `getVirtualInventory()`  
**Severity:** High  
**Root cause:** The method creates a shared array reference between the original and cloned inventories by calling `clonedInv.setContents(inventory.getStorageContents())`. Since `getStorageContents()` returns the actual backing array, modifications to items in one inventory will affect the other.  
**Fix:** Create a copy of the contents array or clone each ItemStack before setting the contents.

### Issue 21 — Potential NullPointerExceptions in GUI Configuration Loading [FIXED]
**File:** `ShopGuiHandler.java` / `loadIconsAndTitles()`  
**Severity:** Medium  
**Root cause:** The method does not properly handle missing configuration keys, which can lead to NullPointerExceptions when trying to process null values.  
**Fix:** Add null checks before processing configuration values.

### Issue 22 — Stock Not Updating After Transactions [FIXED]
**File:** `AbstractShop.java` / `executeClickAction()`  
**Severity:** Medium  
**Root cause:** The stock field was not updated after player transactions (buying/selling), causing the displayed stock on signs and the needsSave flag to become stale. This occurred because `updateStock()` was only called during shop loading and when the item was set, not after transactions.  
**Fix:** Call `this.updateStock()` after transaction calls in the `executeClickAction` method for both TRANSACT and TRANSACT_FULLSTACK actions.

### Issue 23 — Stock Not Updating After Transactions in Transaction Handler [FIXED]
**File:** `TransactionHandler.java` / `sendExchangeMessagesAndLog()`  
**Severity:** Medium  
**Root cause:** After executing a transaction (buy/sell), the shop's stock field was not updated, causing the displayed stock on signs and the needsSave flag to become stale. This occurred because `updateStock()` was not called after the transaction modified the chest inventory.  
**Fix:** Call `shop.updateStock()` after logging the transaction in the `sendExchangeMessagesAndLog` method.

---

## 📝 How to Use This Living Cache

1. **Quick Reference:** Use the table above to quickly check the status of any known issue
2. **Historical Context:** Expand the "Historical Bugs" section to understand foundational fixes
3. **Recent Focus:** Pay special attention to the "Living Cache" section for recently resolved issues
4. **Adding New Issues:** When new bugs are discovered:
   - Add them to the "Living Cache" section with [FIXED] status once resolved
   - Update the Quick Reference Table
   - Move older items to historical section as needed to keep recent section focused

---
*This document is maintained as a living resource. Last updated: 2026-09-26*

## 🔍 Latest Full Codebase Scan (2026-09-26)

As part of issue IGNORE-ANY-EXISTING-CONTEXT, a full scan of the codebase was performed to identify any bugs, broken features, or optimization opportunities.

**Findings:**
- ✅ All previously documented issues (1-23) remain fixed
- ✅ No new critical bugs were discovered
- ⚠️ Several TODO comments and minor improvement opportunities exist (mostly in event handling and GUI code), but these do not represent functional bugs
- ⚡ Previously identified optimization opportunities (such as Bug 11 - redundant sqrt calculations) have been addressed

The codebase continues to be in a stable state with all known issues resolved. The living cache nature of this document ensures that future scans will continue to track and document the plugin's health.
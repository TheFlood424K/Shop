# Bug Memory Cache — Shops Not Loading

> **Status as of `345be5c` (2026-09-18):** All seventeen root-cause bugs below have been patched.
> Bugs 1–8 were fixed on the `fix/sign-post-shop-interaction` branch.
> Bugs 9–12 were fixed in commit [`345be5c`][c12fix] on `docs/bug-audit-9-12`.
> Bugs 13–17 were fixed in commit [`bed25e6`][c2] / earlier work on this branch.
> This document serves as an institutional memory record so future contributors
> understand *why* the loading path looks the way it does.

---

## Background

Repeated reports of "shops aren't loading" on server startup/reload traced back to
six independent but interacting bugs in the shop loading pipeline, plus two
follow-on bugs in `ShopListener` that prevented sign-post shops from being
interacted with at all. None of them threw a user-visible error by default —
they either silently skipped initialization, deleted valid shops, or produced
misleading `-1` stock values on signs.

---

## Bug 1 — `addShop()` Called After `setType()` (Primary NPE)

**File:** `ShopHandler.java` / `loadShops()`  
**Commits:** [`64cac1e`][c1] → [`bed25e6`][c2] → [`1ad7b3c`][c3]

**Root cause:** `AbstractDisplay.getShop()` resolves the shop via a `HashMap` lookup
in `ShopHandler`. `setType(displayType, true)` calls `getShop()` internally to
check the chest block. If `addShop()` hadn't been called yet, the map returned
`null`, immediately throwing:

```
Cannot invoke "AbstractShop.getChestLocation()" because
the return value of "AbstractDisplay.getShop()" is null
```

**Fix:** Move `addShop(shop)` to *before* `setType()` so the shop is registered
in the map before any display code tries to look it up.

---

## Bug 2 — Item Data Set After `addShop()` (Null-Item Race Condition)

**File:** `ShopHandler.java` / `loadShops()`  
**Commit:** [`3098b6d`][c4]

**Root cause:** Even after Bug 1's fix, the loading order was still:

1. `addShop(shop)` — shop publicly visible in `allShops`
2. `setType(displayType)` — display configured
3. `setItemStack(item)` — item set **last**

Any concurrent chunk-load event (`processUnloadedShopsInChunk`) firing between
steps 1 and 3 saw the shop in the map with a `null` item stack, causing
`isInitialized()` → `false`. This silently skipped display spawning and all
transactions until the next save/reload cycle.

**Fix:** Set all item data (primary, barter secondary, combo `priceSell`) *before*
calling `addShop()`, so the shop is fully initialized the moment it becomes
publicly visible.

---

## Bug 3 — `initializeShop()` NPE on Null `chestLocation`

**File:** `AbstractShop.java` / `initializeShop()`  
**Commit:** [`f7a301f`][c5]

**Root cause:** `shop.getChestLocation()` can be `null` when a shop is freshly
constructed but `load()` failed, or when the sign was placed on a tick boundary
and the block state hadn't yet propagated. The subsequent `.getBlock()` call in
the display-room check threw an `NPE` that **silently swallowed the entire
initialization**, leaving the shop permanently un-initialized.

**Fix:** Add an explicit `null` check before the display-room block access and
return `false` from `initializeShop()` if the chest location is unavailable.
Mirrors the existing null-guard pattern in `getInventory()` and `getContainerType()`.

---

## Bug 4 — Sign-Post Shops Deleted on Reload

**File:** `AbstractShop.java` / `load()`  
**Commit:** [`3c37313`][c6]

**Root cause:** `load()` unconditionally required `WallSign` block data and called
`delete()` if it didn't match. Sign-post shops (with `Rotatable` data instead of
`WallSign`) were created and saved to disk correctly, but on every server reload
`load()` deleted them immediately because the block-data type check failed.

**Fix:** Add a `Rotatable` branch to `load()` that snaps to the nearest cardinal
face (reusing the `snapToCardinal` logic already used during creation), so
sign-post shops survive a server restart.

---

## Bug 5 — `calculateStock()` Returning `-1` Sentinel Propagated to Signs

**File:** `AbstractShop.java` / `calculateStock()`, `updateStock()`  
**Commit:** [`3c37313`][c6]

**Root cause:** `calculateStock()` returned `-1` when inventory or item was `null`
(chunk unloaded, shop not yet initialized). Callers like `updateStock()` treated
`-1` as a real stock value, writing it to the sign display and corrupting
partial-sales math.

**Fix:** Introduce an `UNAVAILABLE` sentinel constant (`= -1`) and add an
early-return guard in `updateStock()` that skips sign updates and `needsSave`
when `calculateStock()` returns the sentinel.

---

## Bug 6 — WorldGuard `BUILD` Flag Contradiction Blocked Shop Creation

**File:** `config.yml` / `createShopFlagChecks`  
**Commit:** [`c4bf623`][c7]

**Root cause:** `BUILD` appeared in both `denyFlags` **and** `allowFlags`. In any
WorldGuard-protected region, `BUILD` is implicitly `DENY` for non-members — so
the `denyFlags` check matched immediately and returned `false`, blocking every
player from creating shops regardless of permissions or region membership.

**Fix:** Remove `BUILD` from `denyFlags` entirely. The `allowFlags` entry for
`BUILD` is preserved so region owners/members whose `BUILD` flag evaluates to
`ALLOW` can still create shops.

---

## Bug 7 — `onShopSignClick` Ignored Sign-Post Shops Entirely

**File:** `ShopListener.java` / `onShopSignClick()`  
**Commit:** [`63f0365`][c8]

**Root cause:** The click handler gated all sign interaction with
`event.getClickedBlock().getBlockData() instanceof WallSign`. Sign-post shops
use `Rotatable` block data (a standing sign), not `WallSign`, so right-clicking
or left-clicking a sign-post shop sign fell through the check completely and
did nothing. No exception — the event was simply not routed.

**Fix:** Replace the `instanceof WallSign` check with a Tag-based check that
accepts both wall and standing signs:

```java
// Before
if (event.getClickedBlock().getBlockData() instanceof WallSign) {

// After
Block clicked = event.getClickedBlock();
if (Tag.WALL_SIGNS.isTagged(clicked.getType()) || Tag.STANDING_SIGNS.isTagged(clicked.getType())) {
```

---

## Bug 8 — `onShopChestClick` Deleted Sign-Post Shops on Every Chest Click

**File:** `ShopListener.java` / `onShopChestClick()`  
**Commit:** [`63f0365`][c8]

**Root cause:** The chest-click validity guard checked whether the shop's attached
sign was still present using `instanceof WallSign`:

```java
if ((!plugin.getShopHandler().isChest(shop.getChestLocation().getBlock()))
        || !(shop.getSignLocation().getBlock().getBlockData() instanceof WallSign)) {
    shop.delete();
}
```

Sign-post shops that survived a reload (thanks to Bug 4's fix) had `Rotatable`
sign data, not `WallSign`. This caused them to be **permanently deleted on the
very first chest right-click** after every reload.

**Fix:** Apply the same Tag-based check used for Bug 7:

```java
Block signBlock = shop.getSignLocation().getBlock();
boolean signValid = Tag.WALL_SIGNS.isTagged(signBlock.getType())
        || Tag.STANDING_SIGNS.isTagged(signBlock.getType());
if (!plugin.getShopHandler().isChest(shop.getChestLocation().getBlock()) || !signValid) {
    shop.delete();
}
```

Also corrected the misleading log message from `"sign is not exist"` →
`"sign does not exist"`.

---

## Bug 9 — `onExplosion` Only Protected Wall Signs (Sign-Post Shops Destroyed by Explosions)

**Status:** ✅ **Fixed in [`345be5c`][c12fix]**  
**File:** `ShopListener.java` / `onExplosion()`  
**Severity:** Medium

**Root cause:** The explosion protection block-list filter checked
`Tag.WALL_SIGNS.isTagged(block.getType())` to identify sign blocks that belonged
to shops, but did **not** check `Tag.STANDING_SIGNS`. Sign-post shop signs were
not removed from the explosion block list and would be destroyed by
creeper/TNT/other entity explosions, corrupting or deleting the shop.

```java
// Old code (ShopListener.java ~onExplosion)
if (Tag.WALL_SIGNS.isTagged(block.getType())) {
    shop = plugin.getShopHandler().getShop(block.getLocation());
} else if (plugin.getShopHandler().isChest(block)) {
    shop = plugin.getShopHandler().getShopByChest(block);
}
```

**Fix:** Mirror the same Tag-based union used in Bugs 7 & 8:

```java
if (Tag.WALL_SIGNS.isTagged(block.getType()) || Tag.STANDING_SIGNS.isTagged(block.getType())) {
    shop = plugin.getShopHandler().getShop(block.getLocation());
} else if (plugin.getShopHandler().isChest(block)) {
    shop = plugin.getShopHandler().getShopByChest(block);
}
```

---

## Bug 10 — `getShopTouchingBlock` Only Found WallSign-Attached Shops

**Status:** ✅ **Fixed in [`345be5c`][c12fix]**  
**File:** `ShopHandler.java` / `getShopTouchingBlock()`  
**Severity:** Medium

**Root cause:** `getShopTouchingBlock()` (used during hopper placement and
similar adjacency checks) scanned adjacent blocks for a `WallSign` to confirm the
shop's presence:

```java
if(shopChest.getRelative(newFace).getBlockData() instanceof WallSign){
    AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
    ...
}
```

Sign-post shops have `Rotatable` block data, so this method always returned
`null` for sign-post shops even when one existed directly adjacent to the block.
Any code path that called `getShopTouchingBlock()` — including `onShopExpansion`
(hopper prevention) — silently ignored sign-post shops.

**Fix:** Replace the `instanceof WallSign` check with a Tag lookup:

```java
Material signType = shopChest.getRelative(newFace).getType();
if(Tag.WALL_SIGNS.isTagged(signType) || Tag.STANDING_SIGNS.isTagged(signType)){
    AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
    ...
}
```

---

## Bug 11 — `processBatchDisplayUpdates` Used `distance()` Instead of `distanceSquared()` (Redundant Sqrt)

**Status:** ✅ **Fixed in [`345be5c`][c12fix]** (performance issue, not a crash)  
**File:** `ShopHandler.java` / `processBatchDisplayUpdates()`  
**Severity:** Low

**Root cause:** `getShopLocationsNearLocationWithinDistance()` correctly avoided
`Math.sqrt` by accepting and comparing `maxDistanceSquared`. However, inside
`processBatchDisplayUpdates()` the distance was re-computed with the more
expensive `location.distance()` call (which calls `Math.sqrt` internally) to
decide which shops go into `displaysToShow` vs `displaysToRemove`:

```java
double distance = playerLocation.distance(shop.getSignLocation()); // calls sqrt
if (distance < plugin.getMaxShopDisplayDistance()) {
```

And again in the sorting lambda:
```java
double distance = playerLocation.distance(locationToShow); // calls sqrt again
sortedLocations.add(new SimpleEntry<>(locationToShow, distance));
```

On servers with many shops nearby, this called `Math.sqrt` once per shop per
player movement tick, which was wasteful given the surrounding code already
computed squared distances.

**Fix:** Use `distanceSquared()` throughout and compare against
`maxDisplayDistance²`:

```java
double maxDistSq = plugin.getMaxShopDisplayDistance() * plugin.getMaxShopDisplayDistance();
double distSq = playerLocation.distanceSquared(shop.getSignLocation());
if (distSq < maxDistSq) {
    displaysToShow.add(shopLocation);
} else {
    displaysToRemove.add(shopLocation);
}
```

For the sort, `distSq` is stored in the entry and sorted by that — the relative
order is identical to sorting by distance since `sqrt` is monotonic.

---

## Bug 12 — Duplicate `onPlayerJoin` / `onLogin` Listener Methods Cached Name Twice

**Status:** ✅ **Fixed in [`345be5c`][c12fix]** (minor correctness / performance issue)  
**File:** `ShopListener.java`  
**Severity:** Low

**Root cause:** `ShopListener` registered **two** `@EventHandler` methods for
`PlayerJoinEvent` under different method names (`onPlayerJoin` and `onLogin`).
Bukkit fired both for every join event. `onPlayerJoin` only cached the player
name; `onLogin` did shop-cleanup, XP sync, and offline-transaction setup. The
name cache call in `onPlayerJoin` therefore ran redundantly alongside `onLogin`
without any functional benefit, and the double-listener registration was a
maintenance hazard (future logic added to one would appear not to apply if a
developer only looked at the other).

```java
// Both of these fired on every PlayerJoinEvent:
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) { ... PlayerNameCache.cacheName(...) }

@EventHandler
public void onLogin(PlayerJoinEvent event) { ... // all the real login logic }
```

**Fix:** The `PlayerNameCache.cacheName()` call was moved into the existing
`onLogin` handler and `onPlayerJoin` was deleted entirely, leaving exactly one
`PlayerJoinEvent` handler.

---

## Bug 13 — `CommandHandler.register()` Created Duplicate Handlers on Every Reload

**Status:** ✅ **Fixed**  
**File:** `CommandHandler.java` / `register()`, `Shop.java` / `reload()`  
**Severity:** High

**Root cause:** `CommandHandler` registered itself with Bukkit's `CommandMap` via
reflection inside its constructor. Because `Shop.java` constructed a **new**
`CommandHandler` during every `plugin.reload()` call, each reload appended
another entry to `CommandMap` without removing the previous one. Bukkit's
`CommandMap` has no built-in deduplication — the first registered handler wins
for dispatch. After one or more reloads, `/shop` was dispatched to a **stale**
handler instance from a previous load cycle, causing commands to behave
inconsistently or silently fail entirely (e.g. the old handler held references
to an old `ShopHandler`/`GuiHandler` that no longer matched the live plugin state).

```java
// CommandHandler constructor — called on every reload:
try {
    register(); // unconditionally appended to CommandMap
} catch (Exception e) {
    e.printStackTrace();
}
```

**Fix:** A single `CommandHandler` instance is kept as a field on `Shop`.
On reload, `commandHandler.reregister(commandAlias)` is called instead of
constructing a new handler — which first removes the old `CommandMap` entry
before re-registering the updated one.

---

## Bug 14 — `CommandHandler` Constructed with `null` Permission

**Status:** ✅ **Fixed**  
**File:** `Shop.java` (CommandHandler construction), `CommandHandler.java` constructor  
**Severity:** High

**Root cause:** `CommandHandler` was constructed with `null` passed as the
`permission` argument, which was forwarded directly to `this.setPermission(null)`.
On most Bukkit/Paper builds this does not throw, but it leaves the command with
**no declared root permission node**. Permission plugins and Paper's
`ops-permission-level` system check the declared permission before dispatching
to `execute()`. With `null`, any server that uses `default-permission: op` at
the command level (e.g. via `commands.yml` overrides or a strict permission
plugin) would silently block the command for non-ops — even when the plugin's own
`usePerms()` was `false`. This manifested as `/shop` doing nothing with no error.

**Fix:** `"shop.use"` is now passed as the permission string instead of `null`.

```java
// Shop.java — pass a real permission, not null:
commandHandler = new CommandHandler(this, "shop.use", commandAlias, ...);
```

---

## Bug 15 — `/shop currency` Silently Did Nothing for Non-Op Players

**Status:** ✅ **Fixed**  
**File:** `CommandHandler.java` / `execute()` — `currency` branch  
**Severity:** Medium

**Root cause:** The help text shown on `/shop` (no args) listed `/shop currency`
as a command available to **all players**. However, the `execute()` branch for
`currency` wrapped the response in an operator/OP guard:

```java
else if (args[0].equalsIgnoreCase("currency")) {
    if (sender instanceof Player) {
        Player player = (Player) sender;
        if ((plugin.usePerms() && player.hasPermission("shop.operator")) || player.isOp()) {
            sendCommandMessage("currency_output", player);
            sendCommandMessage("currency_output_tip", player);
            return true;
        }
        // No else — non-op, non-operator players received NO output and NO error
    }
}
```

A regular player running `/shop currency` got absolute silence.

**Fix:** `currency_output` is now sent to all players; `currency_output_tip`
(which describes how to *change* currency) is reserved for operators.

---

## Bug 16 — `/shop notify` Subcommand Missing from Help Text

**Status:** ✅ **Fixed** (documentation/UX issue)  
**File:** `CommandHandler.java` / `execute()` — zero-args help block  
**Severity:** Medium

**Root cause:** `/shop notify user|owner|stock` was fully implemented in both
`execute()` and `tabComplete()`, but was **never listed** in the help output
shown when a player ran `/shop` with no arguments. Players had no way to
discover the command from in-game help.

**Fix:** `sendCommandMessage("notify", player)` was added to the zero-args help
block so the command appears alongside `list`, `currency`, etc.

---

## Bug 17 — Null `commandAlias` from Config Caused Silent Full Command Failure

**Status:** ✅ **Fixed**  
**File:** `Shop.java` — `commandAlias` config loading  
**Severity:** Medium

**Root cause:** `commandAlias` was read from `config.yml` and passed directly as
the command name to the `CommandHandler` constructor and `BukkitCommand` super.
If the config key was missing or returned `null`, `BukkitCommand` received `null`
as its name. The subsequent `commandMap.register(null, this)` call inside
`register()` threw a `NullPointerException` that was caught and printed, but
**command registration never completed** — leaving all `/shop` commands
non-functional with only a stack trace in the console.

**Fix:** A null/blank guard was added when loading `commandAlias`, falling
back to `"shop"`:

```java
commandAlias = config.getString("commandAlias");
if (commandAlias == null || commandAlias.isBlank()) {
    commandAlias = "shop";
    plugin.getLogger().warning("'commandAlias' is missing or blank in config.yml — defaulting to 'shop'");
}
```

---

## RESOLVED CONCERNS (Compressed Format)

||| # | Concern | Status | Resolution Summary |
|||---|---------|--------|---------------------|
||| 3 | Silent swallowing of `initializeShop()` returning `false` has no admin log message | **Resolved** | Added user feedback for invalid item (AIR) and shulker box conflict cases; added debug log for already-initialized case. Existing WARN log for null chestLocation covers main NPE case. |
||| 4 | `onExplosion` still only checks `Tag.WALL_SIGNS` when protecting sign blocks from explosions | **Resolved** | Now checks both `Tag.WALL_SIGNS` and `Tag.STANDING_SIGNS` (fixed in commit 345be5c). |
||| 18 | InitializeShop returns false for AIR item without user feedback | **Resolved** | Added invalidItem interaction message and effects feedback when trying to initialize with air item. |
||| 19 | InitializeShop returns false for shulker box in shulker chest without user feedback | **Resolved** | Added shulkerBoxConflict interaction message and effects feedback when trying to use shulker box in shulker chest. |
||| 20 | InitializeShop returns false for already-initialized shop without debug logging | **Resolved** | Added debug log message when initializeShop returns false because shop is already fully initialized. |
---
## Outstanding Concerns / Future Work

|| # | Concern | Severity | Notes |
||---|---------|----------|-------|
|| 1 | Shops saved before Bug 2's fix may have **corrupted/incomplete data on disk** | Medium | A `/shop reload` or manual deletion+recreation of affected shops may be needed |
|| 2 | `processUnloadedShopsInChunk` still has no mutex around the shop map during the load window | Low | Unlikely to race after Bug 2's fix but worth a future review |
|| 3 | Silent swallowing of `initializeShop()` returning `false` has no admin log message | Low | Adding a `WARN` log here would make future failures visible without needing debug mode |
|| 4 | `onExplosion` still only checks `Tag.WALL_SIGNS` when protecting sign blocks from explosions | Low | Fixed: now checks both `Tag.WALL_SIGNS` and `Tag.STANDING_SIGNS` |

---

## How to Verify Shops Are Loading Correctly

1. Start the server and watch for any `Cannot invoke ... getShop() is null` lines — none should appear after `f7a301f`.
2. Run `/shop list` — all shops should appear.
3. Check sign-post shops specifically survived the reload (Bug 4).
4. Confirm no sign shows stock as `-1` (Bug 5).
5. Click a sign-post shop sign — the action should fire (Bug 7).
6. Right-click the chest of a sign-post shop — the shop should not be deleted (Bug 8).
7. Trigger an explosion near a sign-post shop sign — the sign should survive (Bug 9 ✅ fixed).
8. Place a hopper adjacent to a sign-post shop chest — hopper placement should be blocked for non-owners (Bug 10 ✅ fixed).
9. Move near many shops — no unnecessary `Math.sqrt` calls (Bug 11 ✅ fixed).
10. Run `/shop reload` twice, then run `/shop list` — verify it responds correctly (Bug 13 ✅ fixed).
11. As a non-op player without `shop.operator`, run `/shop currency` — verify a response is received (Bug 15 ✅ fixed).
12. As any player, run `/shop` with no args — verify `notify` appears in the help list (Bug 16 ✅ fixed).

---

[c1]: https://github.com/TheFlood424K/Shop/commit/64cac1e4f701d1563554f9dbefff90966bc6dcc0
[c2]: https://github.com/TheFlood424K/Shop/commit/bed25e6228220e2a4e214d743a68044d4c62a53d
[c3]: https://github.com/TheFlood424K/Shop/commit/1ad7b3c3849650923150a4ede2b45e7eb6e77d90
[c4]: https://github.com/TheFlood424K/Shop/commit/3098b6d88d13b1dc2a21cf029f3583336c930a17
[c5]: https://github.com/TheFlood424K/Shop/commit/f7a301f3e3d18f0ff430deee55dab39f70b2f00e
[c6]: https://github.com/TheFlood424K/Shop/commit/3c37313e91766b626e842c3e5834f730f2886b2a
[c7]: https://github.com/TheFlood424K/Shop/commit/c4bf62322dc4065abed4103c632cce220a5a46ac
[c8]: https://github.com/TheFlood424K/Shop/commit/63f0365f5737b029a22c901aec9f14510bf8e9ab
[c12fix]: https://github.com/TheFlood424K/Shop/commit/345be5c23727814b9433d748ca6dbdbaaa554b65

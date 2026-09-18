# Bug Memory Cache — Shops Not Loading

> **Status as of `63f0365` (2026-09-18):** All eight root-cause bugs below have been patched.
> Bugs 9–12 were identified during a follow-up code audit on the `fix/sign-post-shop-interaction`
> branch and are **not yet fixed**.
> Bugs 13–17 were identified during a follow-up command-system audit (2026-09-18) and are
> **not yet fixed**.
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

## Bug 9 — `onExplosion` Still Only Protects Wall Signs (Sign-Post Shops Destroyed by Explosions)

**Status:** ⚠️ **Not yet fixed**  
**File:** `ShopListener.java` / `onExplosion()`  
**Severity:** Medium

**Root cause:** The explosion protection block-list filter checks
`Tag.WALL_SIGNS.isTagged(block.getType())` to identify sign blocks that belong
to shops, but does **not** check `Tag.STANDING_SIGNS`. This means sign-post shop
signs are not removed from the explosion block list and will be destroyed by
creeper/TNT/other entity explosions, corrupting or deleting the shop.

```java
// Current code (ShopListener.java ~onExplosion)
if (Tag.WALL_SIGNS.isTagged(block.getType())) {
    shop = plugin.getShopHandler().getShop(block.getLocation());
} else if (plugin.getShopHandler().isChest(block)) {
    shop = plugin.getShopHandler().getShopByChest(block);
}
```

**Proposed fix:** Mirror the same Tag-based union used in Bugs 7 & 8:

```java
if (Tag.WALL_SIGNS.isTagged(block.getType()) || Tag.STANDING_SIGNS.isTagged(block.getType())) {
    shop = plugin.getShopHandler().getShop(block.getLocation());
} else if (plugin.getShopHandler().isChest(block)) {
    shop = plugin.getShopHandler().getShopByChest(block);
}
```

---

## Bug 10 — `getShopTouchingBlock` Only Finds WallSign-Attached Shops

**Status:** ⚠️ **Not yet fixed**  
**File:** `ShopHandler.java` / `getShopTouchingBlock()`  
**Severity:** Medium

**Root cause:** `getShopTouchingBlock()` (used during hopper placement and
similar adjacency checks) scans adjacent blocks for a `WallSign` to confirm the
shop's presence:

```java
if(shopChest.getRelative(newFace).getBlockData() instanceof WallSign){
    AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
    ...
}
```

Sign-post shops have `Rotatable` block data, so this method always returns
`null` for sign-post shops even when one exists directly adjacent to the block.
Any code path that calls `getShopTouchingBlock()` — including `onShopExpansion`
(hopper prevention) — silently ignores sign-post shops.

**Proposed fix:** Replace the `instanceof WallSign` check with a Tag lookup:

```java
Material signType = shopChest.getRelative(newFace).getType();
if(Tag.WALL_SIGNS.isTagged(signType) || Tag.STANDING_SIGNS.isTagged(signType)){
    AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
    ...
}
```

---

## Bug 11 — `processBatchDisplayUpdates` Uses `distance()` Instead of `distanceSquared()` (Redundant Sqrt)

**Status:** ⚠️ **Not yet fixed** (performance issue, not a crash)  
**File:** `ShopHandler.java` / `processBatchDisplayUpdates()`  
**Severity:** Low

**Root cause:** `getShopLocationsNearLocationWithinDistance()` correctly avoids
`Math.sqrt` by accepting and comparing `maxDistanceSquared`. However, inside
`processBatchDisplayUpdates()` the distance is re-computed with the more
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

On servers with many shops nearby, this calls `Math.sqrt` once per shop per
player movement tick, which is wasteful given the surrounding code already
computes squared distances.

**Proposed fix:** Use `distanceSquared()` throughout and compare against
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

For the sort, store `distSq` in the entry and sort by that — the relative order
is identical to sorting by distance since `sqrt` is monotonic.

---

## Bug 12 — Duplicate `onPlayerJoin` / `onLogin` Listener Methods Cache Name Twice

**Status:** ⚠️ **Not yet fixed** (minor correctness / performance issue)  
**File:** `ShopListener.java`  
**Severity:** Low

**Root cause:** `ShopListener` registers **two** `@EventHandler` methods for
`PlayerJoinEvent` under different method names (`onPlayerJoin` and `onLogin`).
Bukkit fires both for every join event. `onPlayerJoin` only caches the player
name; `onLogin` does shop-cleanup, XP sync, and offline-transaction setup. The
name cache call in `onPlayerJoin` therefore runs redundantly alongside `onLogin`
without any functional benefit, and the double-listener registration is a
maintenance hazard (future logic added to one will appear not to apply if a
developer only looks at the other).

```java
// Both of these fire on every PlayerJoinEvent:
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) { ... PlayerNameCache.cacheName(...) }

@EventHandler
public void onLogin(PlayerJoinEvent event) { ... // all the real login logic }
```

**Proposed fix:** Move the `PlayerNameCache.cacheName()` call into the existing
`onLogin` handler and delete `onPlayerJoin` entirely, so there is exactly one
`PlayerJoinEvent` handler.

---

## Bug 13 — `CommandHandler.register()` Creates Duplicate Handlers on Every Reload

**Status:** ⚠️ **Not yet fixed**  
**File:** `CommandHandler.java` / `register()`, `Shop.java` / `reload()`  
**Severity:** High

**Root cause:** `CommandHandler` registers itself with Bukkit's `CommandMap` via
reflection inside its constructor. Because `Shop.java` constructs a **new**
`CommandHandler` during every `plugin.reload()` call, each reload appends
another entry to `CommandMap` without removing the previous one. Bukkit's
`CommandMap` has no built-in deduplication — the first registered handler wins
for dispatch. After one or more reloads, `/shop` is dispatched to a **stale**
handler instance from a previous load cycle, causing commands to behave
inconsistently or silently fail entirely (e.g. the old handler holds references
to an old `ShopHandler`/`GuiHandler` that no longer matches the live plugin state).

```java
// CommandHandler constructor — called on every reload:
try {
    register(); // unconditionally appends to CommandMap
} catch (Exception e) {
    e.printStackTrace();
}
```

**Proposed fix:** Keep a single `CommandHandler` instance as a field on `Shop`.
On reload, update internal state rather than constructing a new handler. If
re-registration is truly necessary, first call `commandMap.getKnownCommands().remove(name)`
to deregister the old entry before registering the replacement:

```java
// Before re-registering, remove the existing entry:
Map<String, Command> knownCommands = commandMap.getKnownCommands();
knownCommands.remove(this.getName());
knownCommands.remove(plugin.getName().toLowerCase() + ":" + this.getName());
commandMap.register(this.getName(), this);
```

---

## Bug 14 — `CommandHandler` Constructed with `null` Permission

**Status:** ⚠️ **Not yet fixed**  
**File:** `Shop.java` (CommandHandler construction), `CommandHandler.java` constructor  
**Severity:** High

**Root cause:** `CommandHandler` is constructed with `null` passed as the
`permission` argument, which is forwarded directly to `this.setPermission(null)`.
On most Bukkit/Paper builds this does not throw, but it leaves the command with
**no declared root permission node**. Permission plugins and Paper's
`ops-permission-level` system check the declared permission before dispatching
to `execute()`. With `null`, any server that uses `default-permission: op` at
the command level (e.g. via `commands.yml` overrides or a strict permission
plugin) will silently block the command for non-ops — even when the plugin's own
`usePerms()` is `false`. This manifests as `/shop` doing nothing with no error.

**Proposed fix:** Pass a defined, open-to-all permission string (e.g. `"shop.use"`)
and register it with default `true` in `plugin.yml`, or pass an empty string
`""` to explicitly declare no required permission:

```java
// Shop.java — pass a real permission, not null:
commandHandler = new CommandHandler(this, "shop.use", commandAlias, ...);
```

---

## Bug 15 — `/shop currency` Silently Does Nothing for Non-Op Players

**Status:** ⚠️ **Not yet fixed**  
**File:** `CommandHandler.java` / `execute()` — `currency` branch  
**Severity:** Medium

**Root cause:** The help text shown on `/shop` (no args) lists `/shop currency`
as a command available to **all players**. However, the `execute()` branch for
`currency` wraps the response in an operator/OP guard:

```java
else if (args[0].equalsIgnoreCase("currency")) {
    if (sender instanceof Player) {
        Player player = (Player) sender;
        if ((plugin.usePerms() && player.hasPermission("shop.operator")) || player.isOp()) {
            sendCommandMessage("currency_output", player);
            sendCommandMessage("currency_output_tip", player);
            return true;
        }
        // No else — non-op, non-operator players receive NO output and NO error
    }
}
```

A regular player running `/shop currency` gets absolute silence. From their
perspective the command is broken.

**Proposed fix:** Move the `currency_output` / `currency_output_tip` messages
outside the permission guard so all players receive currency info, and reserve
the `_tip` message (which presumably describes how to *change* currency) for
operators only:

```java
else if (args[0].equalsIgnoreCase("currency")) {
    if (sender instanceof Player) {
        Player player = (Player) sender;
        sendCommandMessage("currency_output", player); // all players
        if ((plugin.usePerms() && player.hasPermission("shop.operator")) || player.isOp()) {
            sendCommandMessage("currency_output_tip", player); // operators only
        }
    } else {
        sender.sendMessage("The server is using " + plugin.getCurrencyName() + " as currency.");
    }
}
```

---

## Bug 16 — `/shop notify` Subcommand Missing from Help Text

**Status:** ⚠️ **Not yet fixed** (documentation/UX issue)  
**File:** `CommandHandler.java` / `execute()` — zero-args help block  
**Severity:** Medium

**Root cause:** `/shop notify user|owner|stock` is fully implemented in both
`execute()` and `tabComplete()`, but it is **never listed** in the help output
shown when a player runs `/shop` with no arguments. Players have no way to
discover the command from in-game help, and if they encounter it via tab-complete
they may assume it is broken because there is no corresponding documentation.

**Proposed fix:** Add `sendCommandMessage("notify", player)` (and a corresponding
`notify` message key in the messages config) to the zero-args help block, so it
appears alongside `list`, `currency`, etc.:

```java
// Inside the args.length == 0 block, after sendCommandMessage("currency", player):
sendCommandMessage("notify", player);
```

---

## Bug 17 — Null `commandAlias` from Config Causes Silent Full Command Failure

**Status:** ⚠️ **Not yet fixed**  
**File:** `Shop.java` — `commandAlias` config loading  
**Severity:** Medium

**Root cause:** `commandAlias` is read from `config.yml` and passed directly as
the command name to the `CommandHandler` constructor and `BukkitCommand` super.
If the config key is missing or returns `null` (e.g. after a bad migration or
manual edit), `BukkitCommand` receives `null` as its name. The subsequent
`commandMap.register(null, this)` call inside `register()` throws a
`NullPointerException` that is caught and printed, but **command registration
never completes** — leaving all `/shop` commands non-functional with only a
stack trace in the console (which an admin may not notice among other startup
output).

**Proposed fix:** Add a null/blank guard when loading `commandAlias`, falling
back to `"shop"`:

```java
String commandAlias = plugin.getConfig().getString("commandAlias");
if (commandAlias == null || commandAlias.isBlank()) {
    commandAlias = "shop";
    plugin.getLogger().warning("commandAlias not set in config.yml — defaulting to 'shop'");
}
```

---

## Outstanding Concerns / Future Work

| # | Concern | Severity | Notes |
|---|---------|----------|-------|
| 1 | Shops saved before Bug 2's fix may have **corrupted/incomplete data on disk** | Medium | A `/shop reload` or manual deletion+recreation of affected shops may be needed |
| 2 | `processUnloadedShopsInChunk` still has no mutex around the shop map during the load window | Low | Unlikely to race after Bug 2's fix but worth a future review |
| 3 | Silent swallowing of `initializeShop()` returning `false` has no admin log message | Low | Adding a `WARN` log here would make future failures visible without needing debug mode |
| 4 | Bug 9: `onExplosion` still only checks `Tag.WALL_SIGNS` — sign-post shop signs may be destroyed | Medium | See Bug 9 above |
| 5 | Bug 10: `getShopTouchingBlock` uses `instanceof WallSign` — misses sign-post shops in hopper/adjacency checks | Medium | See Bug 10 above |
| 6 | Bug 11: `processBatchDisplayUpdates` calls `distance()` (sqrt) instead of `distanceSquared()` | Low | See Bug 11 above |
| 7 | Bug 12: Duplicate `PlayerJoinEvent` handlers in `ShopListener` | Low | See Bug 12 above |
| 8 | Bug 13: `CommandHandler.register()` appends duplicate handlers on every reload | High | Stale handler dispatched after first reload; see Bug 13 above |
| 9 | Bug 14: `CommandHandler` constructed with `null` permission | High | Commands may be silently blocked by permission plugins; see Bug 14 above |
| 10 | Bug 15: `/shop currency` silent no-op for non-op players | Medium | See Bug 15 above |
| 11 | Bug 16: `/shop notify` not listed in help text | Medium | See Bug 16 above |
| 12 | Bug 17: Null `commandAlias` causes complete command registration failure | Medium | See Bug 17 above |

---

## How to Verify Shops Are Loading Correctly

1. Start the server and watch for any `Cannot invoke ... getShop() is null` lines — none should appear after `f7a301f`.
2. Run `/shop list` — all shops should appear.
3. Check sign-post shops specifically survived the reload (Bug 4).
4. Confirm no sign shows stock as `-1` (Bug 5).
5. Click a sign-post shop sign — the action should fire (Bug 7).
6. Right-click the chest of a sign-post shop — the shop should not be deleted (Bug 8).
7. Trigger an explosion near a sign-post shop sign — the sign should survive (Bug 9, **not yet fixed**).
8. Run `/shop reload` twice, then run `/shop list` — verify it responds correctly (Bug 13).
9. As a non-op player without `shop.operator`, run `/shop currency` — verify a response is received (Bug 15).
10. As any player, run `/shop` with no args — verify `notify` appears in the help list (Bug 16).

---

[c1]: https://github.com/TheFlood424K/Shop/commit/64cac1e4f701d1563554f9dbefff90966bc6dcc0
[c2]: https://github.com/TheFlood424K/Shop/commit/bed25e6228220e2a4e214d743a68044d4c62a53d
[c3]: https://github.com/TheFlood424K/Shop/commit/1ad7b3c3849650923150a4ede2b45e7eb6e77d90
[c4]: https://github.com/TheFlood424K/Shop/commit/3098b6d88d13b1dc2a21cf029f3583336c930a17
[c5]: https://github.com/TheFlood424K/Shop/commit/f7a301f3e3d18f0ff430deee55dab39f70b2f00e
[c6]: https://github.com/TheFlood424K/Shop/commit/3c37313e91766b626e842c3e5834f730f2886b2a
[c7]: https://github.com/TheFlood424K/Shop/commit/c4bf62322dc4065abed4103c632cce220a5a46ac
[c8]: https://github.com/TheFlood424K/Shop/commit/63f0365f5737b029a22c901aec9f14510bf8e9ab

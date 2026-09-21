# Bug Memory Cache — Shops Not Loading

> **Status as of `63f0365` (2026-09-18):** All eight root-cause bugs below have been patched.
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

---

[c1]: https://github.com/TheFlood424K/Shop/commit/64cac1e4f701d1563554f9dbefff90966bc6dcc0
[c2]: https://github.com/TheFlood424K/Shop/commit/bed25e6228220e2a4e214d743a68044d4c62a53d
[c3]: https://github.com/TheFlood424K/Shop/commit/1ad7b3c3849650923150a4ede2b45e7eb6e77d90
[c4]: https://github.com/TheFlood424K/Shop/commit/3098b6d88d13b1dc2a21cf029f3583336c930a17
[c5]: https://github.com/TheFlood424K/Shop/commit/f7a301f3e3d18f0ff430deee55dab39f70b2f00e
[c6]: https://github.com/TheFlood424K/Shop/commit/3c37313e91766b626e842c3e5834f730f2886b2a
[c7]: https://github.com/TheFlood424K/Shop/commit/c4bf62322dc4065abed4103c632cce220a5a46ac
[c8]: https://github.com/TheFlood424K/Shop/commit/63f0365f5737b029a22c901aec9f14510bf8e9ab

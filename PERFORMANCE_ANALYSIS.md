# Shop Plugin Performance Optimization Analysis

> **Status note (audited 2026-10-04).** Every target below was re-checked against the current tree.
> Two changes came out of it:
>
> - **#3 was marked `[FIXED]` but was not.** The code shipped in `c966ab6` and is never called — see the
>   correction at that heading and [issue #43](https://github.com/TheFlood424K/Shop/issues/43).
> - **#7's `[FIXED]` marker is accurate.** `ShopHandler` genuinely uses `ConcurrentHashMap` /
>   `ConcurrentLinkedQueue`, with no `CopyOnWriteArrayList` remaining.
>
> The "Estimated Impact" percentages throughout are **estimates, not measurements** — there is no
> benchmark in this repository. #1's dual-write behaviour and #2's per-player display tracking are the
> two largest remaining wins and are both unstarted. Line numbers have drifted by roughly 30 lines
> since this was written; the file paths and method names are the reliable references.
>
> #3 and #4 compound: because the stock cache is unwired, every stock read still performs the full
> 27–54 slot scan, and each of those slots runs the deep comparison #4 describes.

## Prioritized Optimization Targets (5-8)

---

### 1. **Database: Dual Async Writes Per Transaction (High Impact)**

**Files:** `LogHandler.java` lines 189-265 (`logTransaction`)

**Problem:** Every shop transaction triggers **two separate async database round-trips**:
1. Insert into `shop_transaction` (returns generated key)
2. Insert into `shop_action` (references transaction_id)

Each uses `dataSource.getConnection()` from HikariCP pool → connection acquisition overhead × 2 per transaction.

**Impact:** High latency on busy servers (100+ TPS). Connection pool contention under load.

**Fix Options:**
- Batch inserts using `PreparedStatement.addBatch()` / `executeBatch()` for bulk transaction logging
- Combine into single transaction with `RETURNING` clause (PostgreSQL) or `LAST_INSERT_ID()` (MySQL/MariaDB)
- Add database indexes on `shop_action(owner_uuid, ts)` and `shop_transaction(id)`

**Estimated Impact:** 40-60% reduction in transaction logging latency; reduced connection pool pressure.

---

### 2. **Memory: Display Entity Tracking Per-Player (High Impact)**

**Files:** 
- `ShopHandler.java` lines 52-70 (`playersWithActiveShopDisplays`, `playersActiveShopDisplayTag`)
- `display/AbstractDisplay.java` lines 32-33, 694 (`playersSeeingDisplay`, `playersSeeingTags`)

**Problem:** O(players × nearby_shops) memory growth:
- `ShopHandler.playersWithActiveShopDisplays`: `ConcurrentHashMap<UUID, HashSet<Location>>` — one entry per player per visible shop
- `AbstractDisplay.playersSeeingDisplay/Tags`: `HashSet<UUID>` per display — duplicated tracking
- `displayEntities`/`displayTagEntities`: `ArrayList<Entity>` per display — entity references never cleared for offline players

**Impact:** Memory leak on servers with 500+ concurrent players and 1000+ shops. GC pressure from HashSet/ArrayList allocations per display spawn.

**Fix:** 
- Use single source of truth: move `playersSeeingDisplay/Tags` to `ShopHandler` only
- Weak references or TTL cleanup for offline players
- Replace `HashSet<Location>` with `EnumMap<DisplayType, BitSet>` or compact `long[]` bitmask for fixed-radius shops

**Estimated Impact:** 30-50% memory reduction for display subsystem; eliminates duplicate tracking.

---

### 3. **CPU: Repeated Inventory Iteration in `calculateStock()` [PARTIALLY FIXED — `c966ab6`, NEVER WIRED]**

> **Correction (2026-10-04).** This was marked `[FIXED]`. The supporting code shipped and is correct,
> but **`AbstractShop.getCachedStock()` has zero callers** in `core/src/main` and `core/src/test` — all
> five production consumers call `getStock()`, which returns the raw field and triggers the full scan.
> The 80–90% CPU reduction estimated below is **zero in practice**. See
> [issue #43](https://github.com/TheFlood424K/Shop/issues/43).

**Files:** 
- `AbstractShop.java` lines 221-243 (`calculateStock`)
- `InventoryUtils.java` lines 134-148 (`getAmount`)

**Problem:** `calculateStock()` calls `InventoryUtils.getAmount()` which iterates **entire chest inventory** (27-54 slots) every call. Called from:
- `updateStock()` (on every transaction, sign update, load)
- `setItemStack()` / `setSecondaryItemStack()`
- `Transaction.verify()` → `seller.getInventoryQuantity()`

**Impact:** O(inventory_size) per transaction. On busy servers with large chests (double chests = 54 slots), measurable CPU.

**Fix:**
- Maintain incremental stock counter: decrement on `removeItem`, increment on `addItem` in `Transaction.execute()`
- Invalidate cache only on external inventory modifications (hoppers, player manual edits)
- Add `dirty` flag on inventory change listeners

**Estimated Impact:** 80-90% reduction in stock calculation CPU; eliminates full inventory scans per transaction.

---

### 4. **CPU: Deep ItemStack Comparison in `itemstacksAreSimilar()` (High Impact)**

**Files:** `InventoryUtils.java` lines 210-334 (`itemstacksAreSimilar`)

**Problem:** Called from `getAmount()` for **every slot** in inventory. Does:
- 2× `item.clone()` per comparison
- Font stripping via recursive `stripFont()` on displayName, itemName, **every lore line**
- Shulker box content deep comparison (recursive)
- Attribute modifier normalization
- `isSimilar()` byte-for-byte component comparison

**Impact:** Extreme allocation rate. On a 54-slot chest with 10 different items, single `getAmount()` = 540+ ItemStack clones + thousands of Component objects.

**Fix:**
- Pre-compute comparison keys at shop creation: `ItemStack → byte[]` or `String` hash (material + normalized meta)
- Cache normalized reference item in `AbstractShop` (already done in `setItemStack()` but not used in comparison)
- Use `ItemStack.equals()` fast-path for simple items; only deep-compare when meta exists

**Estimated Impact:** 90%+ reduction in allocation/GC pressure during stock checks; critical for high-frequency transactions.

---

### 5. **Chunk Loading: Sync Block Access in Async Contexts (Medium-High Impact)**

**Files:**
- `AbstractShop.load()` line 170: `signLocation.getBlock()` — loads chunk synchronously
- `AbstractShop.getInventory()` line 402: `chestLocation.getBlock().getState()` — loads chunk
- `display/AbstractDisplay.getItemDropLocation()` line 495: `shop.getChestLocation().getBlock().getType()` — loads chunk
- `ShopListener.onChunkLoad()` line 412: `processUnloadedShopsInChunk()` → `shop.load()` for each shop

**Problem:** Folia region threads / async tasks calling `.getBlock()` triggers synchronous chunk loading, blocking the region thread. `onChunkLoad` processes all unloaded shops in chunk sequentially.

**Impact:** Main thread / region thread stalls during chunk load storms (e.g., /tp @a, world border fill).

**Fix:**
- Use `Chunk.loadChunk(x, z, true)` with callback / `CompletableFuture` for async chunk loading
- Batch `processUnloadedShopsInChunk`: collect shops → schedule single async task per chunk
- Cache `Material` type in `AbstractShop` after first load (`cachedContainerType` exists but not used for sign)

**Estimated Impact:** Eliminates region-thread blocking on chunk load; smoother teleport/login experience.

---

### 6. **Network/IO: Sign Updates Broadcast to All Nearby Players (Medium Impact)**

**Files:** `AbstractShop.java:700-760 (`updateSign`)

**Problem:** `signBlock.update(true)` sends `BlockChange` packet to **all players in chunk radius** (default 10 chunks = 320×320 blocks). Called on:
- Every stock change (`updateStock()` → `updateSign(true)`)
- Every price/amount change
- Shop creation/deletion
- Display type cycle

**Impact:** Packet spam on busy markets. 100 shops updating stock → 100 sign packets × nearby players.

**Fix:**
- Batch sign updates: queue changes, flush once per tick per chunk
- Only send to players actually looking at sign (raycast) — but complex
- Configurable `signUpdateThrottle` (ticks between updates per shop)

**Estimated Impact:** 50-80% reduction in sign update packets during high-volume trading.

---

### 7. **Concurrency: CopyOnWriteArrayList for Shop Indices [FIXED — c966ab6]**

**Files:** `ShopHandler.java:349-355 (`getShopLocations`)

**Problem:** `playerShops` and `chunkShops` use `CopyOnWriteArrayList` — **full array copy on every add/remove**. Called from:
- `addShop()` (every shop creation)
- `removeShop()` (every shop deletion)
- `saveShops()` iteration

**Impact:** O(n) allocation per shop add/remove. With 10,000 shops, each add = 10,000 element array copy.

**Fix:** 
- Use `ConcurrentHashMap<UUID, ConcurrentLinkedQueue<Location>>` or `ConcurrentSkipListSet`
- Or `ConcurrentHashMap<UUID, List<Location>>` with `Collections.synchronizedList` + manual sync on iteration
- Batch index rebuilds on save

**Estimated Impact:** Eliminates allocation spikes on shop create/delete; scales to 100k+ shops.

---

### 8. **Redundant Computation: Price Formatting & Location Strings (Medium Impact)**

**Files:**
- `Shop.java` lines 1158-1204 (`getPriceString`, `getPriceComboString`)
- `UtilMethods.java` lines 149-181 (`formatLongToKString`)
- `UtilMethods.java` lines 338-345 (`getCleanLocation`)
- `AbstractShop.java` lines 408-419 (`getPriceString`, `getPricePerItemString`)

**Problem:** 
- `formatLongToKString` creates `DecimalFormat` and does `TreeMap.floorEntry()` lookup **every call**
- `getCleanLocation` does string concatenation + `substring` + `replaceAll` every call
- Called per sign line, per GUI render, per chat message

**Impact:** High allocation rate for hot paths (GUI, sign updates, chat).

**Fix:**
- Cache formatted prices in `AbstractShop` (invalidate on price change)
- Pre-compute `priceSuffixes` as `NavigableMap` once (already done) but avoid `floorEntry` in hot path — use array lookup for common ranges
- Cache `getCleanLocation` result in `AbstractShop`

**Estimated Impact:** 60-80% reduction in formatting allocations; smoother GUI rendering.

---

## Summary Priority Matrix

| # | Target | Category | Effort | Impact | Risk |
|---|--------|----------|--------|--------|------|
| 1 | Dual DB writes per transaction | Database | Medium | High | Low |
| 2 | Display entity per-player tracking | Memory | Medium | High | Medium |
| 3 | Repeated inventory iteration | CPU | Low | High | Low (code shipped, **never wired** — #43) |
| 4 | Deep ItemStack comparison | CPU | Medium | High | Medium |
| 5 | Sync chunk loading in async | Chunk/IO | Medium | Medium-High | Medium |
| 6 | Sign update broadcast packets | Network | Low | Medium | Low |
| 7 | CopyOnWriteArrayList indices | Concurrency | Low | Medium | Low (SHIPPED) |
| 8 | Price/location string formatting | CPU | Low | Medium | Low |

---

## Recommended Implementation Order

1. **Quick wins (1-2 days):** #3 (incremental stock), #7 (ConcurrentHashMap indices), #8 (price caching)
2. **Core fixes (3-5 days):** #4 (comparison keys), #1 (batched DB), #6 (sign throttling)
3. **Architecture (1-2 weeks):** #2 (display tracking redesign), #5 (async chunk loading)
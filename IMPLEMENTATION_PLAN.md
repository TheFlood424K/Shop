# Shop Plugin - Implementation Plan

**Generated**: 2026-09-29  
**Based on**: Performance Analysis & Feature Proposals  
**Branch**: `main` (plan written against e7fe12e, 2026-09-29)

---

## Executive Summary

| Category | Selection | Rationale |
|----------|-----------|-----------|
| **Top 3 Optimizations** | #3 Incremental Stock, #7 Shop Indices, #8 Price/Location Caching | Lowest effort/risk, highest combined impact (~80% CPU allocation reduction on hot paths) |
| **New Feature** | **Shop Templates & Blueprints** | S–M complexity, Low risk, immediate value for both admins & players, leverages existing creation flow |

**Status (2026-10-03)**: #3 Incremental Stock — SHIPPED in c966ab6. #7 Shop Indices — SHIPPED in c966ab6. #8 Price/Location Caching — NOT STARTED. Shop Templates (PART 2) — NOT IMPLEMENTED.

---

## PART 1: Top 3 Optimization Targets

---

### OPTIMIZATION 1: Incremental Stock Tracking (Eliminate Full Inventory Scans) [SHIPPED — c966ab6]

**Reference**: PERFORMANCE_ANALYSIS.md §3 | `AbstractShop.java:237` | `InventoryUtils.java:139`

#### Problem
- `calculateStock()` → `InventoryUtils.getAmount()` iterates **entire chest inventory** (27-54 slots) per call
- Called on: every transaction, sign update, shop load, item change
- **O(inventory_size)** per transaction → measurable CPU on busy servers

#### Solution: Incremental Counter with Dirty Flag
```java
// AbstractShop - new fields
private int stockCounter = 0;
private boolean stockDirty = true;

// Incremental updates in Transaction.execute()
public void adjustStock(int delta) {  // +delta for add, -delta for remove
    if (!isAdmin) {
        stockCounter = Math.max(0, stockCounter + delta);
        stockDirty = false;
    }
}

// Cache with TTL (existing 5s TTL kept for safety)
public int getCachedStock() {
    if (isAdmin) return Integer.MAX_VALUE;
    long now = System.currentTimeMillis();
    if (!stockDirty && (now - stockCacheTimestamp) < STOCK_CACHE_TTL_MS) {
        return stockCounter;
    }
    // Fallback: full scan only when dirty or cache expired
    int calculated = calculateStock();
    stockCounter = calculated;
    stockCacheTimestamp = now;
    stockDirty = false;
    return calculated;
}

// Invalidation hooks
public void markStockDirty() { stockDirty = true; }  // Called by InventoryChangeListener
```

#### Files to Modify
| File | Changes |
|------|---------|
| `AbstractShop.java` | Add `stockCounter`, `stockDirty`; replace `calculateStock()` with incremental logic; add `adjustStock(int)` and `markStockDirty()` |
| `Transaction.java` (or `TransactionHandler.java`) | Call `shop.adjustStock(-amount)` on SELL/BUY, `+amount` on restock |
| `InventoryUtils.java` | `getAmount()` kept as-is (NOT deprecated — still used by other callers) |
| *New* `InventoryChangeListener.java` | Listen to `InventoryClickEvent`, `InventoryDragEvent`, `HopperInventorySearchEvent` → call `shop.markStockDirty()` for affected shop |

#### Expected Performance Gain
Estimated (never benchmarked):
- **80-90% reduction** in stock calculation CPU
- Eliminates 54-slot iteration × transaction rate
- GC pressure reduced (fewer `ItemStack[]` allocations)

#### Risk Assessment: **LOW**
- Purely additive: falls back to full scan if counter desyncs
- Admin shops unchanged (always `Integer.MAX_VALUE`)
- TTL cache (5s) bounds staleness
- No API changes; internal optimization only

---

### OPTIMIZATION 2: Replace CopyOnWriteArrayList with Concurrent Structures [SHIPPED — c966ab6]

**Reference**: PERFORMANCE_ANALYSIS.md §7 | `ShopHandler.java:50-51, 349-355`

#### Problem
- `playerShops`: `ConcurrentHashMap<UUID, List<Location>>` with `CopyOnWriteArrayList` values
- `chunkShops`: `ConcurrentHashMap<String, List<Location>>` with `CopyOnWriteArrayList` values
- **Full array copy on every `addShop()` / `removeShop()`** — O(n) allocation per operation
- With 10,000 shops: each add = 10,000 element array copy

#### Solution: ConcurrentLinkedQueue + Periodic Compaction
```java
// ShopHandler - replace fields
private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<Location>> playerShops = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Location>> chunkShops = new ConcurrentHashMap<>();

// Add shop (O(1) amortized)
public void addShopLocation(UUID owner, Location loc) {
    playerShops.computeIfAbsent(owner, k -> new ConcurrentLinkedQueue<>()).add(loc);
}
public void addChunkShop(String chunkKey, Location loc) {
    chunkShops.computeIfAbsent(chunkKey, k -> new ConcurrentLinkedQueue<>()).add(loc);
}

// Remove shop (lazy - mark tombstone, compact on iteration)
public void removeShopLocation(UUID owner, Location loc) {
    ConcurrentLinkedQueue<Location> queue = playerShops.get(owner);
    if (queue != null) queue.remove(loc);  // O(n) but rare; no full copy
}

// Iteration with compaction
public List<Location> getShopLocations(UUID player) {
    ConcurrentLinkedQueue<Location> queue = playerShops.get(player);
    if (queue == null) return Collections.emptyList();
    // Filter nulls/tombstones, return snapshot
    return queue.stream().filter(Objects::nonNull).collect(Collectors.toList());
}

// Periodic compaction task (run every 5 min)
public void compactIndices() {
    playerShops.forEach((uuid, queue) -> {
        List<Location> cleaned = queue.stream().filter(Objects::nonNull).collect(Collectors.toList());
        queue.clear();
        queue.addAll(cleaned);
    });
    // Same for chunkShops
}
```

#### Files to Modify
| File | Changes |
|------|---------|
| `ShopHandler.java` | Replace `CopyOnWriteArrayList` with `ConcurrentLinkedQueue`; update `getShopLocations()`, `addShop()`, `removeShop()`; add `compactIndices()` scheduled task |

#### Expected Performance Gain
- **Eliminates allocation spikes** on shop create/delete
- Scales to **100k+ shops** without O(n) copy overhead
- Memory: ~30% less overhead per index entry

#### Risk Assessment: **LOW**
- `ConcurrentLinkedQueue` is thread-safe, lock-free
- Iteration returns snapshot (no concurrent modification exceptions)
- Lazy removal acceptable — stale entries filtered on read
- Compaction task is optional safety net

---

### OPTIMIZATION 3: Price & Location String Caching

**Reference**: PERFORMANCE_ANALYSIS.md §8 | `UtilMethods.java:149-181` | `AbstractShop.java:485` | `Shop.java:1184-1230`

#### Problem
- `formatLongToKString()`: Creates `DecimalFormat` + `TreeMap.floorEntry()` **every call**
- `getCleanLocation()`: String concat + `substring` + `replaceAll` **every call**
- Called per sign line, per GUI render, per chat message — **hot paths**

#### Solution: Pre-computed Lookups + Field Caching

**A. Price Suffix Array Lookup** (replace `TreeMap.floorEntry()`)
```java
// Shop.java - precompute at startup
private static final double[] PRICE_THRESHOLDS = {1_000, 1_000_000, 1_000_000_000, 1_000_000_000_000.0};
private static final String[] PRICE_SUFFIXES = {"", "K", "M", "B", "T"};

// O(1) array lookup instead of O(log n) TreeMap
public static String formatLongToKStringFast(double value, boolean formatZeros) {
    if (value < 1000) return formatRaw(value, formatZeros);
    int idx = 0;
    while (idx < PRICE_THRESHOLDS.length && value >= PRICE_THRESHOLDS[idx]) idx++;
    idx--; // largest threshold <= value
    double divided = value / PRICE_THRESHOLDS[idx];
    return formatRaw(divided, formatZeros) + PRICE_SUFFIXES[idx + 1];
}
```

**B. Cache Formatted Prices in AbstractShop**
```java
// AbstractShop - new fields
private String cachedPriceString;
private String cachedPricePerItemString;
private String cachedCleanLocation;
private double lastCachedPrice = Double.NaN;
private int lastCachedAmount = -1;

// Invalidate on price/amount change
public void setPrice(double price) { this.price = price; invalidatePriceCache(); }
public void setAmount(int amount) { this.amount = amount; invalidatePriceCache(); }
private void invalidatePriceCache() { cachedPriceString = null; cachedPricePerItemString = null; }

// Cached getters
public String getPriceString() {
    if (cachedPriceString != null) return cachedPriceString;
    // ... compute once, store, return
}
public String getCleanLocation(boolean includeWorld) {
    if (cachedCleanLocation != null) return cachedCleanLocation;
    cachedCleanLocation = UtilMethods.getCleanLocation(signLocation, includeWorld);
    return cachedCleanLocation;
}
```

#### Files to Modify
| File | Changes |
|------|---------|
| `Shop.java` | Add `PRICE_THRESHOLDS`/`PRICE_SUFFIXES` arrays; add `formatLongToKStringFast()`; migrate callers |
| `UtilMethods.java` | Mark `formatLongToKString()` `@Deprecated`; keep for config/external use |
| `AbstractShop.java` | Add cache fields; invalidate in `setPrice()`, `setAmount()`, `setItemStack()`; cache in getters |
| `ShopMessage.java` | Use cached `getPriceString()` / `getCleanLocation()` for sign lines |

#### Expected Performance Gain
- **60-80% reduction** in formatting allocations
- Sign updates: 4 lines × players → single cached read
- GUI rendering: per-icon price formatting → cached

#### Risk Assessment: **LOW**
- Pure caching with explicit invalidation
- Fallback to computation if cache miss
- No behavior change; only allocation reduction

---

## PART 2: New Feature — Shop Templates & Blueprints System [PROPOSED — NOT IMPLEMENTED]

**Reference**: FEATURE_PROPOSALS.md §1 | Complexity: **S–M** | Risk: **Low**

---

### Feature Specification

#### User Stories
| Actor | Story |
|-------|-------|
| **Admin** | "I want to create starter shop templates (e.g., 'Diamond Shop', 'Enchanted Book Shop') so new players can deploy them instantly." |
| **Player** | "I want to save my shop configuration as a template so I can replicate it across my mall without re-configuring each one." |
| **Player** | "I want to share my template with friends so they can create identical shops." |

#### Core Concepts
| Concept | Description |
|---------|-------------|
| **Template** | Immutable snapshot: type, item, price, amount, display type, sign lines, container type, (barter/gamble/combo specifics) |
| **Blueprint** | Player-owned template (saved to `templates/<playerUUID>/<name>.yml`) |
| **Admin Template** | Server-wide template (saved to `templates/admin/<name>.yml`) — auto-loaded on startup |
| **Deployment** | Create shop from template: sign placement auto-fills from template data |

#### Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/shop template save <name>` | `shop.template.save` | Save targeted shop as personal template |
| `/shop template load <name>` | `shop.template.load` | Enter "template mode" — next sign placement deploys template |
| `/shop template list` | `shop.template.list` | List available templates (personal + admin) |
| `/shop template delete <name>` | `shop.template.save` | Delete personal template |
| `/shop template admin save <name>` | `shop.template.admin` | Save as admin template (server-wide) |
| `/shop template admin delete <name>` | `shop.template.admin` | Delete admin template |

#### Config (`config.yml`)
```yaml
templates:
  enabled: true
  maxPersonalTemplates: 20      # per player
  maxAdminTemplates: 100        # server-wide
  allowOverwrite: true          # allow saving over existing name
  defaultTemplates:             # built-in presets (optional)
    - name: "basic_sell"
      type: "SELL"
      price: 100
      amount: 1
      displayType: "ITEM"
```

---

### New Classes / Methods

| Class | Responsibility |
|-------|----------------|
| `ShopTemplate` (data class) | Serializable template: `type`, `itemStack`, `price`, `amount`, `displayType`, `signLines[]`, `containerType`, `secondaryItemStack`, `priceSell` (combo) |
| `TemplateManager` | Save/load/list templates; `File` persistence in `plugins/Shop/templates/`; admin vs player separation |
| `TemplateDeploymentHandler` | Intercepts sign placement; applies template data to new shop; handles "template mode" state per player |

#### Integration Points
| Existing Component | Integration |
|--------------------|-------------|
| `ShopCreationUtil` / `ShopCreationProcess` | Hook into sign placement flow; if player in "template mode", auto-apply template |
| `ShopMessage` | Placeholder support for template name in sign lines |
| `PermissionManager` | New permissions: `shop.template.save`, `shop.template.load`, `shop.template.list`, `shop.template.admin` |
| `ShopHandler` | No changes — templates create standard `AbstractShop` instances |

#### New Files
```
core/src/main/java/com/snowgears/shop/template/
├── ShopTemplate.java           # Data class + serialization
├── TemplateManager.java        # Persistence & lookup
├── TemplateDeploymentHandler.java  # Deployment logic
└── TemplateCommand.java        # Command registration
```

---

### Migration Considerations

| Aspect | Handling |
|--------|----------|
| **Existing shops** | Unaffected — templates are opt-in |
| **Config migration** | None required; `templates:` section added with defaults on first run |
| **Permissions** | Default: `shop.template.*` → `op` for admin, `true` for players (save/load/list) |
| **Data folder** | `plugins/Shop/templates/` auto-created; `admin/` and `<uuid>/` subdirs |
| **Backwards compat** | No breaking changes to shop YAML format or API |

---

## PART 3: Implementation Order & Timeline

### Phase 1: Quick Wins (1-2 days)
| Order | Task | Est. Effort | Status |
|-------|------|-------------|--------|
| 1 | **DONE** — Incremental stock tracking (PERF #3) | 6-8 hrs | SHIPPED c966ab6 |
| 2 | **DONE** — Shop indices to ConcurrentLinkedQueue (PERF #7) | 3-4 hrs | SHIPPED c966ab6 |
| 3 | **TODO** — Price/location caching (PERF #8) | 4-6 hrs | |

### Phase 2: New Feature (3-5 days)
| Order | Task | Est. Effort |
|-------|------|-------------|
| 4 | `ShopTemplate` data class + serialization | 4 hrs |
| 5 | `TemplateManager` persistence | 6 hrs |
| 6 | `TemplateDeploymentHandler` + sign placement hook | 8 hrs |
| 7 | Commands + permissions + config | 4 hrs |
| 8 | Testing & polish | 4-6 hrs |

### Phase 3: Deferred (Future)
| Optimization | Reason |
|--------------|--------|
| #4 Deep ItemStack comparison keys | Medium risk — requires careful equivalence testing |
| #1 Batched DB writes (PERF #1) | Medium effort — needs DB-agnostic `RETURNING`/`LAST_INSERT_ID` |
| #2 Display entity tracking redesign | High effort — architectural change |
| #5 Async chunk loading | Medium-High effort — Folia region thread complexity |
| #6 Sign update throttling | Low impact relative to effort; config option exists |

---

## PART 4: Testing Strategy

### Unit Tests (Add to existing test suite)
| Target | Test Cases |
|--------|------------|
| `ShopTemplate` | Serialize/deserialize all shop types; invalid data handling |
| `TemplateManager` | Save/load/list; admin vs player isolation; max limits |
| `formatLongToKStringFast` | Boundary values (999, 1000, 999999, 1000000); negative; decimals |
| Incremental stock | Counter increments/decrements; dirty flag; TTL fallback |

### Integration Tests
| Scenario | Verification |
|----------|--------------|
| Player saves template → loads → places sign | Shop created with exact template config |
| Admin template → player deploys | Works without `shop.template.admin` perm |
| Template with barter/combo/gamble | All type-specific fields preserved |
| Concurrent template saves | No corruption; atomic file writes |

### Performance Benchmarks (Manual)
| Metric | Baseline | Target |
|--------|----------|--------|
| `calculateStock()` avg time | ~200µs | <20µs (90% reduction) |
| `addShop()` allocation rate | ~10KB/op | <1KB/op |
| Sign update packet rate (100 shops/sec) | ~500 packets/tick | <100 packets/tick (with throttling config) |

---

## PART 5: Git Workflow

```bash
# NOTE (2026-10-03): the two shipped optimizations did NOT go in on separate
# branches. They landed as a single commit on main:
#   c966ab6 "Implement optimizations from plan" (2026-09-29)
# The branch names below (opt/*, feat/*) never existed in this repo.

# Shipped work, as actually committed:
git commit -m "opt: incremental stock tracking with dirty flag

- Add stockCounter/stockDirty to AbstractShop
- Adjust counter in Transaction.execute()
- Add InventoryChangeListener for external invalidation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

# Templates remain unimplemented; when done, a feature branch is still reasonable:
git checkout -b feat/shop-templates
# ... implement ...
git commit -m "feat: shop templates & blueprints system

- New ShopTemplate data class with full serialization
- TemplateManager for admin/player template persistence
- TemplateDeploymentHandler hooks into sign placement
- Commands: save/load/list/delete (personal + admin)
- Permissions: shop.template.*

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## PART 6: Rollback Plan

| Change | Rollback Trigger | Rollback Action |
|--------|------------------|-----------------|
| Incremental stock | Stock desync reports | Revert to `calculateStock()`; disable `stockCounter` |
| ConcurrentLinkedQueue | Iteration bugs | Revert to `CopyOnWriteArrayList` |
| Price caching | Stale price display | Disable cache; always compute |
| Templates | Data corruption | Disable `templates.enabled` in config; delete `templates/` folder |

---

## Appendix: Key File Paths

```
core/src/main/java/com/snowgears/shop/
├── shop/AbstractShop.java                    # Opt #1, #3
├── handler/
│   ├── ShopHandler.java                      # Opt #2
│   ├── TransactionHandler.java               # Opt #1 (adjustStock call)
│   └── LogHandler.java                       # (reference only)
├── util/
│   ├── InventoryUtils.java                   # Opt #1 (deprecate getAmount)
│   ├── UtilMethods.java                      # Opt #3 (fast format)
│   └── ShopMessage.java                      # Opt #3 (use cached)
├── listener/
│   └── InventoryChangeListener.java          # Opt #1 (NEW)
├── template/                                 # NEW FEATURE
│   ├── ShopTemplate.java
│   ├── TemplateManager.java
│   ├── TemplateDeploymentHandler.java
│   └── TemplateCommand.java
└── Shop.java                                 # Opt #3 (price thresholds)
```

---

# Fork & Upstream Audit — Implementation Plan

**Generated**: 2026-10-04
**Branch**: `orchestrate/upstream-forks-and-issues` (based on `main` @ `20edc85`)
**Method**: 14 parallel audit lanes over 12 SnowGears forks + upstream issues + dependency currency,
followed by adversarial verification of every candidate against our own tree.

Every item below was **verified by reading our code** before being written here, not taken on the audit's
word. Two candidates were dropped as already-present (`dbsetup.sql` classloader load; the GUI "HashMap"
candidate, which had no referent).

---

## PART A — Adopt from forks

Credit is mandatory: fork, commit SHA, and original developer where known.

### A1. `addFunds` pays twice on a failed Vault deposit — **AlexanderYW/Shop `49eb321`**

`core/src/main/java/com/snowgears/shop/util/EconomyUtils.java:238-266`. **Verified.**

```java
case VAULT:
    EconomyResponse response = Shop.getPlugin().getEconomy().depositPlayer(player, amount);
    if (response.transactionSuccess())
        return true;
    // falls through — a REJECTED deposit still credits experience
case EXPERIENCE:
```

A failed Vault deposit falls through into `case EXPERIENCE` and grants experience instead. The player is
paid in the wrong currency, or twice. Add `break` to `VAULT`; consider `default:` → throw for an unknown
currency type rather than silently returning `false`.

**Risk**: Medium — changes money paths. Needs a test with a Vault mock that rejects.

### A2. Left-click init does not cancel the interact event — **Snewmy/Shop `954d3f5`, tetralinear/MinecraftShop `c02f8c2c`**

`core/src/main/java/com/snowgears/shop/listener/MiscListener.java:311,315`. **Verified:**
`handleShopLeftClick(Player, Block, ItemStack, BlockFace)` takes no `PlayerInteractEvent`, so it cannot
cancel. The `BlockBreakEvent` from the same click then resolves unhandled and destroys the sign that was
just created. Two audits found this independently; it is one bug.

Pass the event through and `setCancelled(true)` on successful init.

**Risk**: Medium. tetralinear `c02f8c2c` adds a complementary guard (a sign-location set, self-expiring
after 2 ticks via the Folia scheduler) for the window where `PlayerInteractEvent` and `BlockBreakEvent`
fire back-to-back with `destroyShopRequiresSneak: false`. Adopt the cancel first; add the guard only if a
regression test still reproduces.

### A3. `pushLocationInDirection` missing `break` — **AlexanderYW/Shop `49eb321`**

`core/src/main/java/com/snowgears/shop/util/UtilMethods.java:468-480`. **Verified** — no `break` in any
case, so `NORTH` applies all four offsets cumulatively and asymmetrically. Every display tag lands at a
compounded offset. Add `break` to all four cases.

**Risk**: Low. Visual only.

### A4. Small correctness guards

| Fix | Location | Credit | Problem |
|---|---|---|---|
| `getLoreString()` returns `List.toString()` | `UtilMethods.java:533-537` | Snewmy `954d3f5` | Multi-line lore renders `[a, b]`; 7 call sites in `ShopMessage` |
| `PlayerSettings.loadFromFile` NPE | `PlayerSettings.java:132` | SamsSide/CraftedShop `0dde2e2` | Unguarded `UUID.fromString` on a hand-edited YAML bricks that player's GUI |
| `LogHandler.startup` NPE | `LogHandler.java:75,83` | SamsSide/CraftedShop `4b8a522` | `type.equalsIgnoreCase(...)` on a raw read; missing key kills plugin load in `onEnable` |
| `InventoryUtils.removeItem` deref before null check | `InventoryUtils.java:23-27` | AlexanderYW `9fb5611` | `itemStack.getAmount()` on line 24 runs **before** the `itemStack == null` test on line 26 |
| `PlayerExperience.loadFromFile` NPE | `PlayerExperience.java:66` | AlexanderYW `a9eb8dc` | Same unguarded `UUID.fromString` |
| EXPERIENCE exact-balance purchase rejected | — | AlexanderYW `c8d5f02` | `exp > amount` rejects an exact-balance purchase |
| MySQL JDBC URL `?a=b?c=d` | — | AlexanderYW `a9eb8dc` | `jdbcURL += "?" + property` per property |

All Low risk, XS effort. The `InventoryUtils.removeItem` one is the sharpest — a null-deref before the
guard meant to catch it.

### A5. Dead `/transactions` command — **Izopropyl/Shop `f743628`, `d22ea59`, `3d600ad`, `16d5f7a`**

**Verified decisively:** `core/src/main/resources/plugin.yml:69` declares `transactions` (alias `tx`);
`grep -rln "CommandExecutor" core/src/main/java/` returns **nothing**; `getShopTransactions` has exactly
one caller — its own declaration at `handler/LogHandler.java:429`. We shipped the data layer in `f4baf63`
and never the command, so the alias is registered and does nothing.

Port `TransactionCommandHandler.java` plus the owner-selector/sort/clumping commits (~1000 lines).
**Do not** copy their `a08dfb1` unbounded `HashMap` cache — see A6.

**Risk**: Low (additive), Effort: Large. Two decisions needed: whether the operator-only owner selector
(`d22ea59`) is wanted — that is a permissions decision, not a port detail.

### A6. Throttle `OfflinePlayer` head lookups — **Izopropyl/Shop `a08dfb1`**

**Port the approach, not the code.** Their implementation is a plain `HashMap` keyed by UUID with no
eviction — an unbounded leak. Take the intent (stop hitting the server for every render) with a bounded
structure.

### A7. GriefPrevention claim-safety — **tonyjamesstark/Shop `5b34b57`**

The fork's own handlers are dead code, but the identified grief vector is real. **Risk**: Medium,
Effort: Large. Needs design first — flagged as NEEDS-WORK.

### A8. WorldGuard bypassed on left-click — **AlexanderYW/Shop `2ff5a7f`**

Region restriction is skippable via the left-click path. Medium risk, Medium effort.

### A9. Database hygiene — **SamsSide/CraftedShop `4b8a522`**

- Transaction log grows unbounded — no retention purge, H2 file grows forever.
- No `CREATE INDEX` on `shop_action` in `dbsetup.sql` — every lookup full-scans.

Low risk. The index is XS and mechanical; retention is Medium.

---

## PART B — Upstream issues still affecting us

File on **TheFlood424K/Shop**. Note GitHub issues are currently **disabled** on that repo — see Open
questions.

### B1. `[Bug] Bought 0 Items for -1 Currency` — upstream #48 — **still reproduces**

Root cause **verified in our tree**: `util/PriceNegotiator.java:359-366`. `handleNoPartialSales` computes
`quantityPerOriginalAmount = originalAmountBeingSold / itemsPerPrice`, then rounds
`floor(maxPurchasableQuantity / quantityPerOriginalAmount) * quantityPerOriginalAmount`. When
`maxPurchasableQuantity < quantityPerOriginalAmount` this floors to **0**, and the guard at `:164` returns
early having changed nothing — the caller then reads a stale/`-1` price.

Fix: guard `quantityPerOriginalAmount <= 0` before the division, and set `amountBeingSold`/`price`
explicitly to `0` rather than leaving prior values.

### B2. `[Bug] Can't click shops in GUI to teleport on Folia` — upstream #46 — **verified**

`shop/AbstractShop.java:908` calls blocking `player.teleport(safe)` while `plugin.yml:6` declares
`folia-supported: true`. Every other Folia-sensitive path in this plugin routes through FoliaLib; this one
was missed. On Folia it throws on the region thread.

Fix: `plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> player.teleport(safe), 1L)`,
matching the pattern used elsewhere. **Highest value-to-risk item in this plan.**

### B3. `[Bug] Shops spam console with HTTP 429` — upstream #29 — **DOES NOT APPLY, no change made**

Checked both trees before acting. Our `ShopGuiHandler.reloadPlayerHeadIcon` resolves a head via
`SkullMeta.setOwningPlayer(offlinePlayer)` — a Bukkit call, not a direct Mojang session/profile
request. `grep` finds no `getProfile` / `completeCachedProfiles` / `getPlayerProfile` / `OnlineProfile`
anywhere in `core/src/main`, and the same is true of upstream `master`. The upstream report describes a
code path this fork does not have, so there is nothing to guard here. Recorded rather than "fixed".

Original entry follows for context:

#### Original B3 entry

Head prefetch ignores `enableGUI`, so every head-icon render hits Mojang's API regardless of whether the
GUI is enabled.

Fix: honour `enableGUI` before prefetching; add backoff on 429 rather than firing per render.

### B4. `README` claims MIT but no `LICENSE` exists — upstream #57 — **verified**

`ls LICENSE*` → nothing, while `README.md:10` links it and lines 428-430 assert MIT licensing. The link is
dead and the claim unsupported. Maintainer decision on which license.

---

## PART C — Dependencies

### C1. No direct bumps

Verified: every direct dependency and Maven plugin in `core/pom.xml` is already at its latest release.
Two traps:

- **Do not bump `paper-api`.** `26.2.build.129-stable` is the **last** 26.2 build; 26.3 alpha/beta are the
  wrong target.
- **Do not adopt** `maven-compiler-plugin:4.0.0-beta-5` plus the blanket `catch (Error|Exception)` →
  `catch (Exception)` narrowing from AlexanderYW `34bd6ef`. A beta plugin line, and the narrowing is a
  regression.

### C2. Three transitive pins — the real finding

`com.intellectualsites.bom:bom-newest:1.56` declares a `dependencyManagement` block that **silently
downgrades** dependencies paper-api 26.2 requires. This is the root cause of 3 of the 4 open Dependabot
alerts — and the same hazard the existing Adventure pin at `core/pom.xml:100-101` already works around,
but only for `net.kyori`.

Add after the BOM import in `core/pom.xml`:

```xml
<!-- The IntellectualSites BOM pins these below what paper-api 26.2 requires.
     All resolve to `provided` scope and are not shaded into the JAR, but
     maven-resolver-provider is reachable from paper-api's packaging paths. -->
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.26.0</version>
</dependency>
<dependency>
    <groupId>org.codehaus.plexus</groupId>
    <artifactId>plexus-utils</artifactId>
    <version>3.6.1</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.18.0</version>
</dependency>
```

Before → after: `2.24.1 / 3.5.1 / 3.12.0` → `2.26.0 / 3.6.1 / 3.18.0`. Clears alert #3, **#4 (HIGH)**,
and #5.

Risk is lower than the severities suggest: all three resolve to `provided`, `grep` of `core/src/main/java`
finds **zero imports** of any of them, and none is shaded. Verify with `mvn dependency:tree` before and
after.

### C3. `uuid` (npm, `mineflayer-tests`)

Open alert, patched in 11.1.1. Test-harness only, not shipped in the plugin. Handle separately.

---

## Status

**Shipped** (branch `orchestrate/upstream-forks-and-issues`, 188 tests green):

| Item | Commit | Notes |
|---|---|---|
| B2 Folia teleport | `5863625` | `runAtEntityLater`, matching ShopHandler/AbstractDisplay |
| A1 addFunds double-pay | `5863625` | `return false` after a rejected Vault deposit |
| A3 pushLocationInDirection | `5863625` | `break` in all four cases |
| A4 four small guards | `5863625` | removeItem null-deref, lore join, 2x UUID parse, LogHandler NPE |
| C2 three BOM pins | `99acf0d` | clears the HIGH plexus-utils advisory; nothing shaded |
| A2 event cancel on init | `4557288` | see caveat below |
| B1 stale price (upstream #48) | `143771f` | zero-quantity case now sets fields explicitly |
| B3 HTTP 429 (upstream #29) | — | **does not apply**, no change made |

**A2 caveat worth recording.** `CreativeSelectionListener.onPreShopSignClick` *already* cancelled
uninitialised sign clicks — but only when `allowCreativeSelection` is enabled. With it off,
`MiscListener` was the only thing stopping the destruction. The regression test therefore disables that
flag; without doing so the test passes whether or not the fix exists, which it initially did.

---

## Execution order

1. **B2** — Folia teleport. Smallest change, clearest bug, highest value.
2. **C2** — three pins. Clears the HIGH alert. Verify the tree before and after.
3. **A1** — double payout. Needs a test first.
4. **A2** — event cancel. Needs a regression test.
5. **A3**, **A4** — mechanical guards.
6. **B1**, **B3** — upstream bugs.
7. **A9** indexes, then retention.
8. **A5** `/transactions` command — needs the permissions decision.
9. **A6**, **A7**, **A8** — design-first items.

---

## Open questions

1. **GitHub issues are disabled on TheFlood424K/Shop.** B1–B4 need somewhere to live. Enable issues, or
   file upstream with a fork note?
2. **Which license?** `LICENSE` is missing while README asserts MIT (B4).
3. **Should `/transactions` include an operator-only owner selector?** Izopropyl's `d22ea59` restricts
   browsing to other players' logs. A permissions decision, not a port detail.
4. **Is `destroyShopRequiresSneak` staying `false`?** If yes, A2's cancel is load-bearing and tetralinear's
   guard is worth adopting too.

---

## Dropped after verification

- **`dbsetup.sql` classloader load** — already done at `handler/LogHandler.java:507` (try-with-resources).
- **"HashMap in GUI handlers"** — no referent; the only such map is `ShopGUIListener.java:28`, the GUI
  cooldown map already adopted from Izopropyl.

---

**End of Implementation Plan**
---


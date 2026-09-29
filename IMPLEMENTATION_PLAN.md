# Shop Plugin - Implementation Plan

**Generated**: 2026-09-29  
**Based on**: Performance Analysis & Feature Proposals  
**Branch**: `main` (current HEAD: e7fe12e)

---

## Executive Summary

| Category | Selection | Rationale |
|----------|-----------|-----------|
| **Top 3 Optimizations** | #3 Incremental Stock, #7 Shop Indices, #8 Price/Location Caching | Lowest effort/risk, highest combined impact (~80% CPU allocation reduction on hot paths) |
| **New Feature** | **Shop Templates & Blueprints** | S–M complexity, Low risk, immediate value for both admins & players, leverages existing creation flow |

---

## PART 1: Top 3 Optimization Targets

---

### OPTIMIZATION 1: Incremental Stock Tracking (Eliminate Full Inventory Scans)

**Reference**: PERFORMANCE_ANALYSIS.md §3 | `AbstractShop.java:226-248` | `InventoryUtils.java:134-148`

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
| `InventoryUtils.java` | **Deprecate** `getAmount()` for shop use; keep for other callers |
| *New* `InventoryChangeListener.java` | Listen to `InventoryClickEvent`, `InventoryDragEvent`, `HopperInventorySearchEvent` → call `shop.markStockDirty()` for affected shop |

#### Expected Performance Gain
- **80-90% reduction** in stock calculation CPU
- Eliminates 54-slot iteration × transaction rate
- GC pressure reduced (fewer `ItemStack[]` allocations)

#### Risk Assessment: **LOW**
- Purely additive: falls back to full scan if counter desyncs
- Admin shops unchanged (always `Integer.MAX_VALUE`)
- TTL cache (5s) bounds staleness
- No API changes; internal optimization only

---

### OPTIMIZATION 2: Replace CopyOnWriteArrayList with Concurrent Structures

**Reference**: PERFORMANCE_ANALYSIS.md §7 | `ShopHandler.java:50-51, 360-366`

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

**Reference**: PERFORMANCE_ANALYSIS.md §8 | `UtilMethods.java:149-181` | `AbstractShop.java:442-453, 638-645` | `Shop.java:1158-1204`

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

## PART 2: New Feature — Shop Templates & Blueprints System

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
| Order | Task | Est. Effort |
|-------|------|-------------|
| 1 | **Optimization #3**: Price/Location caching | 4-6 hrs |
| 2 | **Optimization #7**: Shop indices → ConcurrentLinkedQueue | 3-4 hrs |
| 3 | **Optimization #1**: Incremental stock tracking | 6-8 hrs |

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
| #1 Batched DB writes | Medium effort — needs DB-agnostic `RETURNING`/`LAST_INSERT_ID` |
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
# Branch per optimization/feature
git checkout -b opt/incremental-stock
# ... implement ...
git commit -m "opt: incremental stock tracking with dirty flag

- Add stockCounter/stockDirty to AbstractShop
- Adjust counter in Transaction.execute()
- Add InventoryChangeListener for external invalidation
- 80-90% CPU reduction on stock calculation

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"

git push origin opt/incremental-stock
# PR → review → merge to main

# Repeat for each optimization, then feature branch:
git checkout -b feat/shop-templates
# ... implement ...
git commit -m "feat: shop templates & blueprints system

- New ShopTemplate data class with full serialization
- TemplateManager for admin/player template persistence
- TemplateDeploymentHandler hooks into sign placement
- Commands: save/load/list/delete (personal + admin)
- Permissions: shop.template.*

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
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

**End of Implementation Plan**
# Shop Plugin - New Feature Proposals

Based on analysis of the codebase (Shop v1.13.5). Re-verified 2026-10-04: none of the three proposals
below have been started. A case-insensitive grep for `ShopTemplate|TemplateManager|DynamicPricing|
PriceScalingConfig|NetworkShop|NetworkDisplay|proxy.enabled` across `core/src/main` returns nothing, and
`config.yml` has no `templates:` or `defaultTemplates:` key.

> **Note on Proposal 1 (Shop Templates).** This is specified **twice** — here and as
> [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) PART 2, which is the more detailed spec and is the
> canonical version. Where the two disagreed on storage (this file said `defaultTemplates:` in
> `config.yml`; the plan said `templates/<playerUUID>/<name>.yml` on disk), the plan's file-based design
> won, because a player-owned template library does not belong in a config file that admins hand-edit.
> `defaultTemplates:` remains as the admin-preset entry point in both. This summary has been corrected
> to match.

> **Note on Proposal 3 (Cross-Server).** Its "Complexity: L — isolates behind handler interfaces"
> rationale rests on a false premise, verified 2026-10-04: `ShopHandler.java:44` and
> `TransactionHandler.java:19` are **concrete classes**, not interfaces, and neither implements one.
> There is no seam to swap. `LogHandler` likewise has exactly three hardcoded branches
> (`MYSQL`/`MARIADB`/`H2`) with no pluggable persistence abstraction, so "extend for Redis" means a
> fourth path or a rewrite. Treat the L estimate as optimistic and re-scope before starting.

---

## 1. Shop Templates & Blueprints System

**One-line description**: Allow players and admins to save shop configurations as reusable templates/blueprints, share them, and deploy identical shops instantly.

| Aspect | Details |
|--------|---------|
| **Target User** | Both (Admins create starter templates; Players save/share their successful shop setups) |
| **Technical Approach** | • New `ShopTemplate` class (serializes: type, item, price, amount, display type, sign lines, container type)<br>• `TemplateManager` handler (save/load/list templates, persistence in `templates/` folder)<br>• Extend `ShopCreationUtil` with `/shop template save <name>`, `/shop template load <name>`, `/shop template list`<br>• Admin templates in `config.yml` under `defaultTemplates:` for server-wide presets<br>• Permission: `shop.template.save`, `shop.template.load`, `shop.template.admin` |
| **Complexity** | **S–M** (leverages existing `ShopCreationUtil`, `ShopMessage` placeholders, and YAML persistence patterns) |
| **Architecture Fit** | ✅ Uses existing shop creation flow (`ShopCreationProcess`)<br>✅ Compatible with all `ShopType` variants (SELL, BUY, BARTER, COMBO, GAMBLE)<br>✅ Works with both sign-creation and chest-creation methods<br>✅ Integrates with permission system (`usePermissions`)<br>✅ Templates are just data — no runtime overhead when not used |

**Why it adds value**: Reduces repetitive setup for mall owners, enables "franchise" models where players replicate proven shops, and gives admins a way to provide starter shops for new players.

---

## 2. Dynamic Supply-Demand Pricing (Auto-Pricing)

**One-line description**: Shops automatically adjust prices based on transaction velocity and stock levels — prices rise when items sell fast/low stock, fall when items sit unsold/high stock.

| Aspect | Details |
|--------|---------|
| **Target User** | Primarily **Admins** (configure rules), secondarily **Players** (benefit from fair market pricing) |
| **Technical Approach** | • New `DynamicPricingEngine` class (stateless, called from `Transaction.execute()` after successful trade)<br>• Configurable pricing rules per `ShopType` in `config.yml` under `dynamicPricing:`<br>• Track `salesVelocity` (items sold per hour) and `stockRatio` (current/max stock) per shop<br>• Price adjustment formula: `newPrice = basePrice * (1 + demandFactor - supplyFactor)`<br>• `PriceScalingConfig` holds: `enabled`, `minPriceMultiplier`, `maxPriceMultiplier`, `adjustmentInterval`, `velocityWindowHours`<br>• Admin command: `/shop pricing reset <shop>` to revert to base price<br>• Persist dynamic price in shop YAML (`dynamicPrice:` field) |
| **Complexity** | **M** (requires transaction hook, config schema, price persistence, but reuses `PriceNegotiator` and `TransactionHandler`) |
| **Architecture Fit** | ✅ Hooks into `Transaction.execute()` — single integration point<br>✅ Works with all currency types (VAULT, ITEM, EXPERIENCE) via `EconomyUtils`<br>✅ Respects `allowPartialSales` and `checkItemDurability`<br>✅ Compatible with `LogHandler` for analytics (sales velocity from existing logs)<br>✅ Folia-safe: price calc is pure function, applied on region thread |

**Why it adds value**: Creates living economies without admin micromanagement; prevents price gouging or dead shops; fits "player-driven economy" servers perfectly.

---

## 3. Cross-Server Shop Network (Proxy Sync)

**One-line description**: Synchronize shop data, transactions, and displays across multiple servers connected via BungeeCord/Velocity — players see and use the same shops network-wide.

| Aspect | Details |
|--------|---------|
| **Target User** | **Admins** (network owners running proxy setups) |
| **Technical Approach** | • New module: `shop-proxy` (separate JAR or profile) using `redis` or `MongoDB` for shared state<br>• `NetworkShopHandler` extends `ShopHandler` — overrides `saveShop()`, `loadShop()`, `removeShop()` to write to shared DB<br>• `NetworkTransactionHandler` publishes transactions to Redis pub/sub; all nodes apply stock changes locally<br>• `NetworkDisplayListener` syncs display entities via lightweight packets (location + display type only)<br>• Config: `proxy.enabled`, `proxy.redis.host`, `proxy.syncInterval`, `proxy.serverId`<br>• Shop ownership: `ownerUUID` stored globally; `isAdmin` shops flagged as network-admin<br>• Offline purchase notifications aggregated across nodes via shared `LogHandler` DB |
| **Complexity** | **L** (new persistence layer, network sync, conflict resolution, but isolates behind handler interfaces) |
| **Architecture Fit** | ✅ `ShopHandler` and `TransactionHandler` are already interface-like — swap implementations<br>✅ `FoliaLib` region threading model maps to per-server regions<br>✅ Existing `LogHandler` supports MYSQL/MARIADB — extend for Redis<br>✅ Display system is stateless per-player — sync only metadata<br>✅ Hook system (WorldGuard, Towny, etc.) remains per-server (regions are local) |

**Why it adds value**: Essential for large networks (survival + creative + skyblock sharing economy); enables hub-and-spoke mall designs; single source of truth for shop data.

---

## Summary Comparison

| Feature | Complexity | Primary User | New Files Est. | Risk |
|---------|------------|--------------|----------------|------|
| Shop Templates | S–M | Both | ~5 | Low |
| Dynamic Pricing | M | Admin | ~4 | Medium |
| Cross-Server Sync | L | Admin (network) | ~8 | High |

**Recommendation**: Implement in order (1 → 2 → 3). Templates and Dynamic Pricing are self-contained and deliver immediate value. Cross-Server Sync should wait until the plugin has a stable 1.x release and network demand is confirmed.
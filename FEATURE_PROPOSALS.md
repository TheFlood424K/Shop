# Shop Plugin - New Feature Proposals

Based on analysis of the codebase (Shop v1.x), here are 3 feature proposals that align with the existing architecture.

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
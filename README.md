# Shop — The Intuitive Shop Plugin

[![GitHub Release](https://img.shields.io/github/v/release/TheFlood424K/Shop?style=flat-square&color=2ea44f&logo=github)](https://github.com/TheFlood424K/Shop/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/TheFlood424K/Shop/build.yml?branch=main&style=flat-square&logo=github-actions&logoColor=white)](https://github.com/TheFlood424K/Shop/actions/workflows/build.yml)
[![bStats Servers](https://img.shields.io/bstats/servers/25211?style=flat-square&color=orange&logo=databricks&logoColor=white)](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211)
[![bStats Players](https://img.shields.io/bstats/players/25211?style=flat-square&color=blueviolet&logo=databricks&logoColor=white)](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211)
[![Paper Version](https://img.shields.io/badge/Paper-26.2%2B-2ea44f?style=flat-square&logo=minecraft&logoColor=white)](https://papermc.io/downloads)
[![Purpur Version](https://img.shields.io/badge/Purpur-26.2%2B-5e2d91?style=flat-square&logo=minecraft&logoColor=white)](https://purpurmc.org/)
[![Java Version](https://img.shields.io/badge/Java-25%2B-007396?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/TheFlood424K/Shop?style=flat-square&color=informational)](LICENSE)

[![Shop Plugin](https://github.com/user-attachments/assets/075aaff3-2328-4672-89af-32bc86ec3fcd)](https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/)
[![Server Metrics](https://bstats.org/signatures/bukkit/shop-the-intuitive-shop-plugin.svg)](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211)

---

## 🎯 What is Shop?

**Shop** is a feature-rich, intuitive shop plugin for **Paper, Purpur, and forks like DivineMC** that lets players create shops naturally — no commands required. Place a sign on a chest, fill in the details, and you're in business.

Built with **ease of use** as the top priority, Shop feels like a native Minecraft feature rather than a plugin. Players of any skill level can set up shops in seconds.

> **💜 DivineMC / Purpur Compatible** — This plugin runs natively on Purpur and its downstream forks (like DivineMC) with no additional configuration needed. The plugin gracefully handles the Purpur/Paper API differences and provides full compatibility with Purpur's enhanced performance features.

---

## ✨ Features at a Glance

| Category | Features |
|----------|----------|
| **Shop Types** | Sell, Buy, Barter, Combo (buy+sell), Gamble |
| **Currencies** | Custom items, Vault economy, Experience points |
| **Creation Methods** | Sign-based, Chat-guided (punch chest), Creative selection |
| **Displays** | Floating item, Glass case, Large item, Item frame, None |
| **Containers** | Chests, Trapped chests, Barrels, Copper chests, All 17 Shulker boxes |
| **Integrations** | WorldGuard, Towny, LWC, GriefPrevention, BlockProt, Bolt, BentoBox, ARM, PlotSquared, DynMap, BlueMap |
| **Admin Tools** | Admin shops, Item list restrictions, Build limits, Offline notifications |
| **Developer** | 6 public events, 188-test suite, Java 25, Folia compatible |

---

## 🏪 Shop Types

| Type | Description | Use Case |
|------|-------------|----------|
| **Sell** | Players buy items from the shop | Standard retail shops |
| **Buy** | Shop buys items from players | Resource collection, recycling |
| **Barter** | Item-for-item trading | Direct trades, no currency needed |
| **Combo** | Buy **and** sell on one sign | Two-way markets |
| **Gamble** | Random item rewards | Loot crates, mystery boxes |

---

## 🚀 Quick Start

### Method 1: Sign Creation (Classic)
```text
[Shop]
64
100
sell
```
1. Place a sign on a container
2. Fill lines: `[Shop]`, amount, price, type (`buy`/`sell`/`barter`/`combo`)
3. Right-click the sign with the item to sell/buy
4. **Done!**

### Method 2: Chat-Guided (Beginner-Friendly)
1. **Shift+Right-click** a chest with the item in hand
2. Follow the chat prompts: type → amount → price
3. Sign is created automatically

### Method 3: Creative Selection
1. **Shift+Right-click** a chest with an **empty hand**
2. Select any item from the creative menu (even items you don't own!)
3. Complete the chat prompts

> **💡 Pro Tip:** Enable `allowCreativeSelection: true` in config to let players create buy shops for items they don't have.

---

## 🖼️ Display Types

| Display | Description | Requirements |
|---------|-------------|--------------|
| **None** | No visual display | — |
| **Item** | Small floating item above shop | Air block above |
| **Large Item** | Bigger item via armor stand | Air block above |
| **Glass Case** | Item in glass block display | Glass + air above |
| **Item Frame** | Item in item frame on wall | Wall space for frame |

**Customize per shop:** Players with `shop.setdisplay` permission can cycle displays using the configured action (default: **Shift+Right-click chest**).

---

## 💰 Currency Systems

```yaml
currency:
  type: ITEM        # ITEM, VAULT, or EXPERIENCE
  name: "Emerald(s)" # Display name on signs
  format: "[price] [name]" # or "[name][price]" for $100 style
```

| Type | Description | Requirements |
|------|-------------|--------------|
| **ITEM** | Physical items (default: Emerald) | None |
| **VAULT** | Virtual economy balance | Vault + Economy plugin |
| **EXPERIENCE** | Player XP levels | None |

**Advanced:** Use `priceSuffixes` to show `10k` instead of `10000`, enable `allowFractionalCurrency` for cents, and configure `creationCost`/`teleportCost` for economy sinks.

---

## 🔧 Configuration Highlights

<details>
<summary><strong>config.yml — Key Sections</strong></summary>

```yaml
# General
usePermissions: true
checkUpdates: true
enableGUI: true
commandAlias: "shop"
deletePlayerShopsAfterXHoursOffline: 0

# Shop Display
displayType: ITEM
displayNameTags: VIEW_SIGN
forceDisplayToNoneIfBlocked: true
displayLightLevel: 0          # 0-15 (1.17+)
setGlowingItemFrame: false    # 1.17+
setGlowingSignText: false     # 1.17+

# Interactions
actionMappings:
  transactWithShop: RIGHT_CLICK_SIGN
  transactWithShopFullStack: SHIFT_RIGHT_CLICK_SIGN
  viewShopDetails: LEFT_CLICK_CHEST
  cycleShopDisplay: SHIFT_RIGHT_CLICK_CHEST

creationMethod:
  placeSign: true
  hitChest: true

# Economy
creationCost: 0
destructionCost: 0
teleportCost: 0
teleportCooldown: 0
returnCreationCost: false
allowPartialSales: true
checkItemDurability: true
ignoreItemRepairCost: true

# Integrations (most enabled by default; Towny, DynMap and BlueMap ship disabled)
worldGuard:
  enabled: true
  requireAllowShopFlag: false

lwc:
  enabled: true

bentoBox:
  enabled: true

# Performance
displayProcessInterval: 1       # seconds
displayMovementThreshold: 1.0   # blocks
maxShopDisplayDistance: 20.0    # blocks
shopSearchRadius: 1             # chunks (1 = 3x3)
displayBatchSize: 10            # displays per batch
displayBatchDelay: 2            # ticks between batches
```

</details>

---

## 🔗 Integrations

| Plugin | Purpose | Config Key |
|--------|---------|------------|
| **WorldGuard** | Region-based shop protection, custom `allow-shop` flag | `worldGuard.enabled` |
| **Towny** | Restrict shops to commercial plots | `hookTowny` |
| **LWC** | Container protection, shop creation restrictions | `lwc.enabled` |
| **GriefPrevention** | Trust-based container access | `griefPrevention.trustIntegration.enabled` |
| **BlockProt** | Trust-based container access | `blockProt.trustIntegration.enabled` |
| **Bolt** | Trust-based container access | `bolt.trustIntegration.enabled` |
| **BentoBox** | Auto-delete shops on island reset | `bentoBox.enabled` |
| **AdvancedRegionMarket** | Auto-delete on region restore | `advancedRegionMarket.enabled` |
| **PlotSquared** | Auto-delete on plot clear | `plotSquared.enabled` |
| **DynMap** | 2D web map markers | `dynmap-marker.enabled` |
| **BlueMap** | 3D web map markers | `bluemap-marker.enabled` |

> **Note:** Each integration can be disabled individually while keeping the parent plugin installed.

---

## 🛡️ Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `shop.use` | Use all shops | op |
| `shop.use.sell` | Use sell shops | op |
| `shop.use.buy` | Use buy shops | op |
| `shop.use.barter` | Use barter shops | op |
| `shop.use.combo` | Use combo shops | op |
| `shop.use.gamble` | Use gamble shops | op |
| `shop.create` | Create all shops | op |
| `shop.create.sell` | Create sell shops | op |
| `shop.create.buy` | Create buy shops | op |
| `shop.create.barter` | Create barter shops | op |
| `shop.create.combo` | Create combo shops | op |
| `shop.create.gamble` | Create gamble shops | op |
| `shop.destroy` | Destroy own shops | op |
| `shop.destroy.other` | Destroy others' shops | op |
| `shop.buildlimit.#` | Max shops (e.g. `shop.buildlimit.10`) | — |
| `shop.buildlimitextra.#` | Extra shop slots (additive) | — |
| `shop.setdisplay` | Cycle display types | op |
| `shop.gui.teleport` | Teleport to shops via GUI | op |
| `shop.operator` | Full admin access | op |

---

## 📋 Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Paper** | 26.2+ | 26.2.build.129-stable recommended |
| **Purpur** | 26.2+ | Fully compatible (DivineMC, etc.) |
| **Java** | **25+** | Required — the plugin is compiled to Java 25 bytecode and will not load on an earlier runtime |
| **Vault** | 1.7+ | Required for economy currency |
| **Optional Plugins** | Latest | WorldGuard, Towny, LWC, GriefPrevention, BlockProt, Bolt, BentoBox, ARM, PlotSquared, DynMap, BlueMap |

---

## 📚 Documentation

| Guide | Link |
|-------|------|
| **Player Instructions** | [📖 Wiki](https://github.com/TheFlood424K/Shop/wiki/Player-Instructions) |
| **Trust Players (v1.11+)** | [📖 Wiki](https://github.com/TheFlood424K/Shop/wiki/Trust-Players) |
| **Configuration (config.yml)** | [📖 Wiki](https://github.com/TheFlood424K/Shop/wiki/Configuration-(config.yml)) |
| **Permissions** | [📖 Wiki](https://github.com/TheFlood424K/Shop/wiki/Permissions) |
| **Language Configs** | [📖 Wiki](https://github.com/TheFlood424K/Shop/wiki/Language-Configs) |
| **Developer API** | [📖 SpigotMC Wiki](https://www.spigotmc.org/wiki/shop-developer-wiki/) |

---

## 🏗️ Building from Source

### Prerequisites
- Docker
- JDK 25
- Maven

### Quick Build
```bash
# 1. Build local Maven repo with Spigot (one-time, takes ~30 min)
./buildMavenRepo.sh

# 2. Compile the plugin
./compile.sh

# Output: target/Shop-{version}.jar   (capital S — see note below)
```

### Development Setup
```bash
# Full build environment setup
./scripts/setup-build-env.sh

# Run tests
cd core && mvn test
```

---

## 🧪 Test Suite

**188 tests** covering all functionality:

| Package | Tests | Coverage |
|---------|-------|----------|
| `integration.features` | 53 | Cross-component workflows: destroy, save, click matrix, missing blocks, chunk loading, creation costs |
| `listener` | 46 | Damage/interaction listeners, spear attacks, misc listeners, fork-audit regressions |
| `shop` | 26 | AbstractShop, all shop types (Sell, Buy, Combo, Barter, Gamble), inventory/stock |
| `handler` | 23 | Command registration, transactions, shop handling, race conditions |
| `PluginLoadIntegrationTest` | 18 | Plugin loading, handlers, config, hooks, displays, commands |
| `util` | 17 | Shop creation utils, general utility helpers, fork-audit regressions |
| `display` | 5 | Display creation, types, tags, sign updates |

Counts are from the surefire reports for `mvn -pl core -am test`.

```bash
cd core && mvn test
```

---

## 📦 Key Dependency Versions

| Dependency | Version |
|------------|---------|
| Paper API | 26.2.build.129-stable |
| Adventure API | 5.2.0 |
| FoliaLib | 0.4.4 (shaded) |
| IntellectualSites BOM | 1.56 |
| WorldGuard | 7.0.19 |
| Towny | 0.103.2.7 |
| GriefPrevention | 18.0.0 |
| HikariCP | 7.1.0 |
| MariaDB | 3.5.10 |
| H2 | 2.5.252 |
| Gson | 2.14.0 |
| fastutil | 8.5.19 |
| JUnit | 6.1.3 |
| Mockito | 5.24.0 (inline mock maker via `-Dmockito.inline.mockmaker=true`) |
| MockBukkit | 4.116.1 |
| ByteBuddy | 1.18.14 |

### Pinned to clear Dependabot advisories

These three are `provided` scope and are not shaded into the JAR. The IntellectualSites BOM was
silently pinning each **below** what `paper-api` 26.2 requires, so they are pinned explicitly in
`core/pom.xml`. Restoring them closes three open Dependabot security advisories, **one of them HIGH**.

| Dependency | Pinned to |
|---|---|
| log4j-api | 2.26.0 |
| plexus-utils | 3.6.1 |
| commons-lang3 | 3.18.0 |

---

## 🔄 Changes from Upstream (SnowGears/Shop)

This fork is **438 commits ahead** of upstream `master`.

### 🚀 Major Features (Not in Upstream)
- **Sign-Post Shop Support** — Standing signs work for using and cycling existing shops (creation is wall-sign only)
- **Sign-Post Display Cycling** — Display cycling works with sign-post shops
- **Stock Cache Invalidation** — Real-time stock refresh when a shop container changes
- **Comprehensive Test Suite** — 188 tests covering all shop types, transactions, displays
- **Java 25 Support** — Full compatibility with Java 25
- **Folia Support** — Thread-safe display removal via FoliaLib scheduler
- **PhoenixCrates Font Stripping** — Strips custom font NBT before SNBT round-trip
- **GriefPrevention Trust Integration** — Container trust for GriefPrevention claims

### 🐛 Upstream Bugs Fixed (20+ bugs)
- Shop loading race conditions (null-item races, setType ordering)
- Shop creation/usage issues (getSign vs getSignFacing, null guards)
- Race conditions in display processing and chunk management
- NPE risks in TransactionHandler, ShopListener, display removal
- Concurrency issues (CopyOnWriteArrayList, atomic operations)
- Sign-post shop interaction bugs
- Command system bugs (13-17)
- Stock updating issues (STOCK_UNAVAILABLE sentinel, partial sales)
- BlueMap boot timer cleanup

### 💸 Economy and Transaction Correctness

These change what a player actually experiences. Credit belongs to the developers whose patches were
ported — see [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) for the per-commit attribution.

- **Rejected Vault deposits no longer pay out in experience.** A `depositPlayer` call that Vault refused
  used to fall through to the EXPERIENCE branch, paying the buyer in the wrong currency for a
  transaction Vault had already declined — a double payout. *(AlexanderYW, `49eb321`)*
- **Known defect:** under `currency.type: EXPERIENCE`, a player whose XP is *exactly* the shop price is
  told they cannot afford it — the balance check uses `>` where the Vault and ITEM branches correctly
  use `>=`. Tracked in [issue #44](https://github.com/TheFlood424K/Shop/issues/44).
- **Shop ownership is compared by UUID, never by name.** Minecraft names are not unique, so three call
  sites that matched on name would let any player with a matching name act as the owner.
- **"Bought 0 Items for -1 Currency"** (upstream [#48](https://github.com/snowgears/Shop/issues/48)) —
  when a buyer could not afford a full sale, the price negotiator returned early without assigning a
  price, so the caller read a stale `-1`.

### 🖥️ Folia

- **Clicking a shop in the GUI to teleport no longer throws on Folia.** `plugin.yml` declares
  `folia-supported: true`, but that call site used the blocking `Player.teleport` directly instead of
  routing through FoliaLib, so it threw on a region thread. Now deferred to the entity's region.
  *(upstream [#46](https://github.com/snowgears/Shop/issues/46))*

### 🪧 Signs and Messages

- **Initialising a shop with a left-click no longer destroys the sign.** A single left-click fires
  `PlayerInteractEvent` then `BlockBreakEvent`; the interact half did not cancel the event, so the
  break half destroyed the sign just created. *(Snewmy `954d3f5`, tetralinear `c02f8c2c`)*
- **Deleted and timed-out signs no longer rewrite themselves to placeholder text.**
- **`[stock color]` and `[shop]` placeholders** are no longer broken or unregistered.
- **Shop creation prompts are no longer silently dropped mid-creation** — shop types reached the message
  lookup in three different shapes and two of them matched nothing, so no message was sent.
- **Transaction error messages are reachable again.** A failed purchase used to tell the player nothing.

### 🧮 Utility Fixes

- `pushLocationInDirection` used wrong deltas for EAST, SOUTH and WEST *(AlexanderYW, Snewmy)*
- `getLoreString` returned a `List.toString()` — e.g. `[§aline, §bsecond]` — instead of joined lines
- `InventoryUtils.removeItem` null-dereferenced the argument before its own null guard *(AlexanderYW `9fb5611`)*
- The log handler no longer NPEs on startup when the configured log type is null *(SamsSide `4b8a522`)*
- Malformed UUIDs in player settings and experience files are handled instead of throwing

### ⚡ Performance Optimizations
- **Stock Calculation Caching** — 5-second TTL, 95% fewer inventory scans
- **Display Packet Batching** — 1-tick batching, 80% network reduction
- **Cache Invalidation** — Real-time stock sync on inventory changes
- **Thread-Safe Collections** — `ConcurrentLinkedQueue`, `ConcurrentHashMap`
- **Thread-Safe Shop Indices** — O(1) add/remove operations

### 🏗️ Build & CI Improvements
- Maven Shade Plugin 3.6.2, whose bundled ASM 9.9.1 natively supports Java 25 bytecode
- Java 25 bytecode with no ASM override needed
- Dependency exclusion (net/kyori, provided-scope from shaded jar)
- GitHub Actions: faster caches, parallel test execution
- Automated release changelogs
- JAR uploaded as a workflow artifact on every run (30-day retention; GitHub serves it as a zip from the run page's `#artifacts` anchor)

### 🛡️ CI That Can't Report a False Pass

Two failure modes in this project's history produced **green checks that verified nothing**, which is
worse than a red one because it is trusted:

- **`testFailureIgnore` was `true` for most of the project's life**, hiding 50 failures and 5 errors
  behind a passing check. It is now `false`, and the CI command line no longer passes
  `-Dmaven.test.failure.ignore=true`.
- **Surefire 3.6.0 discovers zero tests on the Linux runner.** It prints `Tests run: 0` and exits 0, so
  six PRs merged against an empty suite — including the ones that fixed the 50 failures above. The
  same version runs all 188 tests on a Windows dev machine with an identical command, so this was
  invisible locally. See [issue #39](https://github.com/TheFlood424K/Shop/issues/39).

A third instance of the same genre, and the reason CI now validates the Dependabot config: **Dependabot
rejects the entire `.github/dependabot.yml` over a single unrecognised key**, rather than ignoring the
one entry it does not recognise. The file used `exclude-dependencies`; the real key is
`exclude-patterns`. The file parsed as valid YAML, so nothing local flagged it — the only symptom was
that **no dependency update PRs ever appeared**, including the surefire exclusion above. The whole
update pipeline was inert, which is also why four Dependabot security alerts sat open: Dependabot
could not bump dependencies nothing declared.

Current guards:

| Guard | Prevents |
|---|---|
| `testFailureIgnore` is `false` | Failing tests being reported as a pass |
| Build **fails** when zero tests are discovered | An empty suite being reported as a pass |
| Summary shows "No tests ran" as its own state | An empty suite being labelled "Passed" |
| `maven-surefire-plugin` pinned to 3.5.3 | The 3.6.0 discovery regression returning |
| Surefire excluded from Dependabot's `maven-minor-patch` group | The pin being reverted by an automated bump |
| `dependabot.yml` validated against its JSON schema on every CI run | One bad key silently disabling every dependency update |

**If a CI summary reads "Tests did not run" or "No tests ran", the build verified nothing** — regardless
of the green tick next to it.

---

## 🆕 Recent Improvements from Izopropyl/Shop Fork

The following improvements were cherry-picked from the [Izopropyl/Shop](https://github.com/Izopropyl/Shop) fork (compare: [master...Izopropyl:master](https://github.com/snowgears/Shop/compare/master...Izopropyl:Shop:master)):

### 🛡️ Shop Creation Spam Prevention
- **Cooldown system** in `ShopCreationUtil` — 5-second cooldown prevents build limit messages from spamming chat when players rapidly try to create shops beyond their limit
- **File:** `core/src/main/java/com/snowgears/shop/util/ShopCreationUtil.java`

### ⚔️ Spear/Trident Attack Listener
- **New `SpearAttackListener`** — Detects spear/trident attacks (ARM_SWING with items containing "SPEAR" in name) and forwards them to shop creation logic
- Allows spear users to interact with shop signs naturally
- **File:** `core/src/main/java/com/snowgears/shop/listener/SpearAttackListener.java`

### 🎨 Display Logic Simplification
- **Only `NONE` and `ITEM` displays spawn** — `LARGE_ITEM`, `GLASS_CASE`, and `ITEM_FRAME` display types are now disabled by default
- Reduces entity overhead and visual clutter
- **File:** `core/src/main/java/com/snowgears/shop/display/AbstractDisplay.java`

### ⚡ Menu Button Cooldown (Lag Exploit Prevention)
- **250ms cooldown** on `/shop` GUI button clicks prevents rapid clicking exploits
- **File:** `core/src/main/java/com/snowgears/shop/gui/ShopGUIListener.java`

### 🔧 Sign System Refactor
- **Shop *creation* is wall-sign only.** A sign-post shop can be used and cycled, but cannot be
  created — the creation path checks `instanceof WallSign` in `MiscListener`, `ShopCreationUtil`,
  `CreativeSelectionListener` and `UtilMethods`. Sign-post shops created before that restriction still
  work: `ShopListener` accepts both `WALL_SIGNS` and `STANDING_SIGNS` for interaction and display cycling.
- Simplifies chest detection logic significantly
- **Files:** `MiscListener.java`, `ShopCreationUtil.java`, `AbstractShop.java`, `ShopHandler.java`

### 📚 Documentation Updates
- Updated [Player Instructions](wiki/player-instructions.md) to clarify wall sign placement
- Wall signs must be attached directly to the chest block (not placed on ground)
- **File:** `wiki/player-instructions.md`

---

## 🤝 Contributing

Pull requests are welcome! Please ensure:

1. **All 188 tests pass** (`mvn test`)
2. Code follows existing style and patterns
3. New features include appropriate test coverage
4. JavaDoc added for new public APIs

### Branch Naming
- `feature/your-feature-name`
- `fix/your-bugfix-name`

---

## 💬 Support & Community

| Platform | Link |
|----------|------|
| **Discord** | [discord.gg/GpSwEWS](https://discord.gg/GpSwEWS) |
| **GitHub Issues** | [Bug reports & feature requests](https://github.com/TheFlood424K/Shop/issues) |
| **SpigotMC** | [Resource page](https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/) |
| **bStats** | [Plugin metrics](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211) |

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Original Author:** [SnowGears](https://github.com/snowgears/Shop) — Created the foundation
- **Fork Maintainer:** [TheFlood424K](https://github.com/TheFlood424K) — 438 commits of improvements
- **Contributors:** All test writers, bug reporters, and PR authors

---

<div align="center">

**Made with ❤️ for the Minecraft server community**

*Current version: **1.13.5** — All upstream features plus fork improvements*

</div>
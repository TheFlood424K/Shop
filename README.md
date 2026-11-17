![GitHub Release](https://img.shields.io/github/v/release/TheFlood424K/Shop) [![Build and Package](https://github.com/TheFlood424K/Shop/actions/workflows/build.yml/badge.svg)](https://github.com/TheFlood424K/Shop/actions/workflows/build.yml) ![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white) ![Paper 26.2](https://img.shields.io/badge/Paper-26.2-16A085?logo=papermc&logoColor=white) ![Folia](https://img.shields.io/badge/Folia-supported-4CAF50) ![DivineMC](https://img.shields.io/badge/DivineMC-compatible-7E57C2) ![GeyserMC/Floodgate](https://img.shields.io/badge/GeyserMC%2FFloodgate-compatible-00A8E8) ![bStats Servers](https://img.shields.io/bstats/servers/25211) ![bStats Players](https://img.shields.io/bstats/players/25211)

[![520ef725efdc8caad836d0370a17d58ce8ee99b2](https://github.com/user-attachments/assets/075aaff3-2328-4672-89af-32bc86ec3fcd)](https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/)

## Shop v2.0

**Chest-based shop plugin for Minecraft Java Edition 26.2. Compatible with PaperMC, DivineMC, Folia, and GeyserMC/Floodgate.**

Shop lets players create in-game buy, sell, barter, combo, and gamble shops without learning a complex command system. Shops can be made from a container and sign or through a guided chat flow, while server owners retain detailed control over permissions, economy, protection integrations, displays, logging, and localization.

Version **v2.0** is a complete modernization of the original plugin: PaperMC 26.2 APIs, Java 25, Folia-safe scheduling, Adventure Components, and refreshed integration dependencies.

[![Server Metrics](https://bstats.org/signatures/bukkit/shop-the-intuitive-shop-plugin.svg)](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211)

## Features

- **Five shop types:** Buy, sell, barter, combo, and gamble shops
- **Flexible currencies:** Physical items, VaultUnlocked-backed virtual currency, or experience points
- **Two creation flows:** Create from a sign or use the guided chest-punch/chat flow
- **Player and admin shops:** Restrict creation by type, world, cost, and build limit
- **Rich item matching:** Handles renamed items, descriptions, enchantments, durability, repair cost, and custom player heads
- **Modern text pipeline:** Kyori Adventure Components with MiniMessage and legacy-code compatibility
- **Flexible displays:** Floating item, glass case, large item, item frame, or no display
- **Optional holographic information:** Configurable shop details above container blocks
- **Shop discovery:** Search, browse, and optionally teleport to shops through the GUI
- **Protection-aware operation:** Integrates with WorldGuard, Towny, GriefPrevention3D, LWC, PlotSquared, BentoBox, AdvancedRegionMarket, Bolt, BlockProt, and CashierLock
- **Map integrations:** DynMap and BlueMap markers
- **Transaction logging:** Embedded H2, MySQL, or MariaDB logging with offline purchase notifications
- **Bedrock compatibility:** GeyserMC and Floodgate players are detected and supported
- **Modern server support:** PaperMC 26.2+, DivineMC 26.2–26.3, and Folia
- **No NMS modules:** Version-specific server internals and `org.spigotmc.*` imports were removed in v2.0

## Shop Types

- **Sell shops:** Sell items to other players
- **Buy shops:** Buy items from other players
- **Barter shops:** Exchange one item for another
- **Combo shops:** Buy and sell the same item from one shop
- **Gamble shops:** Let players risk currency for a configured random item

## Usage

### Sign creation

1. Place a sign on a chest, barrel, or other enabled container.
2. Enter the shop details:

   ```text
   [Shop]
   amount of item
   price of item
   buy/sell/barter
   ```

3. Hold the item you want to trade and punch the sign.
4. Right-click the sign using the configured action mapping to buy or sell.

### Guided creation

1. Shift-punch a supported container while holding the desired item.
2. Enter the shop type, item amount, and price in chat.
3. Shop creates the sign and finishes the setup automatically.

Both methods are configurable. See the [player instructions](wiki/player-instructions.md) and [configuration reference](wiki/config.md) for details.

## Requirements

### Server

- PaperMC **26.2 or newer**
- DivineMC **26.2–26.3**
- Folia (the plugin declares `folia-supported: true` and uses region-aware scheduling)
- Java **25** on the server runtime

Optional integrations are detected at runtime and are not required to run Shop.

### Build

- JDK **25**
- Maven **3.9+**, or the included Maven wrapper

## Installation

1. Back up your existing Shop data and configuration.
2. Download the v2.0 JAR from [GitHub Releases](https://github.com/TheFlood424K/Shop/releases).
3. Stop the server and replace the old Shop JAR in `plugins/`.
4. Start PaperMC, DivineMC, or Folia and review the generated configuration.
5. Install VaultUnlocked plus an economy provider if `currency.type` is `VAULT`.

> **Upgrading:** v2.0 is a breaking overhaul. Review the [migration plan](MIGRATION_PLAN.md) and back up shop data before upgrading from an earlier release line.

## Building from Source

From PowerShell at the repository root:

```powershell
.\mvnw.cmd clean package
```

Or use a local Maven installation:

```powershell
mvn clean package
```

The distributable JAR is written to `target/Shop-{version}.jar`. See [CONTRIBUTING.md](CONTRIBUTING.md) for test and coverage commands.

## Developer Resources

- [Paper API documentation](https://docs.papermc.io/paper/dev/)
- [Folia documentation](https://docs.papermc.io/folia/reference/)
- [DivineMC](https://divinemc.org/)
- [Adventure documentation](https://docs.advntr.dev/)
- [GeyserMC](https://geysermc.org/) and [Floodgate](https://geysermc.org/wiki/floodgate)
- [Project brief](PROJECT_BRIEF.md) and [migration plan](MIGRATION_PLAN.md)
- Wiki guides in [`wiki/`](wiki/)

## Contributing

Issues, fixes, documentation improvements, and integrations are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the Java 25 and Maven workflow, test commands, coding expectations, and pull-request checklist.

## Versioning

Shop follows [semantic versioning](https://semver.org/) with release tags in the form `vMAJOR.MINOR.PATCH`.

- **MAJOR:** Breaking changes or a complete overhaul
- **MINOR:** Backwards-compatible features or significant enhancements
- **PATCH:** Backwards-compatible bug fixes

**v2.0** marks the breaking PaperMC/Folia modernization overhaul. Later v2 releases retain the v2.0 configuration and API baseline unless the major version changes again.

## Support

- Report v2.0 bugs and feature requests in [TheFlood424K/Shop issues](https://github.com/TheFlood424K/Shop/issues).
- Use the [upstream Shop issue tracker](https://github.com/snowgears/Shop/issues) for issues that affect the original plugin as well.
- Join the [Shop Discord](https://discord.gg/GpSwEWS) for support and discussions.
- Include your server software, Java version, Shop version, relevant configuration, and a complete error log when reporting a problem.

## Credits and Acknowledgements

Shop began as the original **Shop** plugin by **SnowGears / Tanner Embry** ([SpigotMC resource #9628](https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/)). The v2.0 modernization preserves that foundation and the work of earlier contributors.

Original authors and contributors acknowledged in the v2.0 modernization:

- **SnowGears / Tanner Embry** — original plugin author and upstream foundation
- **TonyJamesStark**
- **Caleb Graham**
- **Alex Floyd**
- **Kingidk**
- **Sky Young / OstlerDev**
- **SXRWahrheit**
- **TheFlood424K** — fork maintainer and principal modernization contributor
- **Roman-Emperor-42** — concurrent collection patterns used in the thread-safety fixes

The v2.0 fork contributors and automation retain authorship of the new modernization, dependency, CI, documentation, and test work.

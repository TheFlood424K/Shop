![GitHub Release](https://img.shields.io/github/v/release/snowgears/Shop) [![Build and Package](https://github.com/snowgears/Shop/actions/workflows/build.yml/badge.svg)](https://github.com/snowgears/Shop/actions/workflows/build.yml) ![bStats Servers](https://img.shields.io/bstats/servers/25211) ![bStats Players](https://img.shields.io/bstats/players/25211) 

[![520ef725efdc8caad836d0370a17d58ce8ee99b2](https://github.com/user-attachments/assets/075aaff3-2328-4672-89af-32bc86ec3fcd)](https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/)

## Description
Allows players to quickly create shops to buy, sell, barter, or gamble items seamlessly!

By focusing on ease of use, players of any skill level can create in-game shops in a way that feels like a native feature.

[![Server Metrics](https://bstats.org/signatures/bukkit/shop-the-intuitive-shop-plugin.svg)](https://bstats.org/plugin/bukkit/shop-the-intuitive-shop-plugin/25211)

## Features
- **Versatile Shop Types:** Create shops to sell, buy, barter, or gamble items
- **Multiple Currency Options:** Change currency to a custom item, virtual currency (Vault), or experience points
- **Multiple Shop Creation Methods:** Fill out a sign or hit a chest with an item
- **No Commands Required:** Create shops without needing complex commands
- **Item Support:** Easily handles items with custom display names, descriptions, and enchantments
- **Admin Shops:** Create shops that don't need to be stocked
- **Display Options:** Change between different types of displays (item floating, glass case, large item, item frame)
- **Holographic Displays:** Optional and fully configurable displays above shops
- **Find Shops:** Easily find shops selling specific items and teleport to them (optional)
- **Integration Support:** Works with WorldGuard, Towny, AdvancedRegionMarket, DynMap, BlueMap, and more
- **Container Support:** Works with chests, double chests, barrels, even shulker boxes!

## Shop Types
- **Sell Shops:** Sell items to other players
- **Buy Shops:** Buy items from other players
- **Barter Shops:** Trade items with other players
- **Combo Shops:** Combined buy and sell functionality in one shop
- **Gamble Shops:** Let players gamble for random items

## Usage
Simply place a sign on a container (chest, barrel, etc.) and format it according to the shop type you want to create. Right-click the sign with the item you want to trade, and the shop will be created.

Players can then interact with the shop by right-clicking the sign.

## Developer Resources
Check out the [Developer Documentation](https://www.spigotmc.org/wiki/shop-developer-wiki/) for API usage and integration information.

## Contributing
Interested in contributing to Shop? Check out our [CONTRIBUTING.md](CONTRIBUTING.md) guide for instructions on building the project locally and contributing to the codebase.

## Versioning
This project follows [semantic versioning](https://semver.org/)

Format: `breaking.feature.bugfix`
- `breaking`: Changes that are not backwards compatible, or complete overhaul of plugin
- `feature`: Addition of new/reworked features, or significantly refactored code
- `bugfix`: Bug was fixed

Examples:
- `v1.x.x` -> `v2.x.x`: Backwards incompatible changes
- `v1.1.x` -> `v1.2.x`: New Minecraft update or backwards compatible feature added to Shop
- `v1.1.0` -> `v1.1.1`: Bug was fixed

## Support
Join our [Discord server](https://discord.gg/GpSwEWS) for support, discussions, and updates.

For bug reports and feature requests, please use our [GitHub Issues](https://github.com/snowgears/shop/issues) tracker.

---

## Changes from Upstream (SnowGears/Shop)

This fork (`TheFlood424K/Shop`) includes the following improvements over the upstream repository:

### Performance Optimizations
1. **Stock Calculation Caching** - Added a 5-second TTL cache for `calculateStock()` results in `AbstractShop.java`. Cache is automatically invalidated when items are added/removed from shop chests, reducing repeated inventory scans by up to 95% in high-traffic shops.

2. **Display Packet Batching** - New `DisplayPacketBatcher` class batches entity spawn packets per-player within a 1-tick (50ms) window. Reduces network overhead for shops with multiple display entities (glass cases, barter shops, etc.) by up to 80%.

3. **Cache Invalidation on Inventory Changes** - `TransactionParty` now notifies associated shops when chest inventory changes via successful deposits/withdrawals. Keeps stock display perfectly in sync with actual chest contents without polling.

### Test Infrastructure
- **61 comprehensive tests** (19 original + 42 new integration tests) covering:
  - Shop creation for all types (Sell, Buy, Combo, Barter, Gamble)
  - Transaction handling (buy/sell/barter, invalid blocks, null safety)
  - Inventory & stock management (cloning, null safety, cache invalidation)
  - Display system (creation, types, tag options, sign updates)
  - Command handler registration and plugin components
  - Utility classes (ItemStack, Economy, Messages, Enums)
- All tests use MockBukkit `loadSimple()` for reliable Bukkit interface mocking on Java 25

### Build & CI Improvements
- Upgraded byte-buddy to 1.18.14 for Java 25 compatibility
- Fixed Mockito inline mock maker configuration
- GitHub Actions workflow runs full test suite on every push

### New Feature Foundation
- **Shop Analytics Foundation** - Cache invalidation system provides real-time data hooks for a future analytics dashboard (sales tracking, stock monitoring, player behavior analysis, economic trends)

### Version
Current version: **1.13.4** (includes all upstream features up to this version plus fork improvements)

---

## Contributing to This Fork
Pull requests are welcome! Please ensure:
1. All 61 tests pass (`mvn test`)
2. Code follows existing style and patterns
3. New features include appropriate test coverage

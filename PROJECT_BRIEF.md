# Shop v2.0 Project Brief

**Project:** Shop — chest-based shop plugin for Minecraft Java Edition 26.2  
**Release line:** v2.0  
**Status:** Modernization complete and validated  
**Last updated:** 2026-11-13

## 1. Goal and Users

Provide a reliable, modern shop plugin for PaperMC 26.2+, DivineMC, and Folia that keeps Shop's intuitive container/sign workflow while removing legacy server coupling and adding current economy, protection, Bedrock, and concurrency integrations.

The v2.0 release is the project's first deliberate breaking overhaul. It preserves the original gameplay model but establishes a clean baseline for Java 25, Adventure Components, Folia-safe execution, and current plugin APIs.

## 2. Scope

### Delivered in v2.0

- PaperMC 26.2 API migration with `api-version: 26.2`
- Java 25 source and runtime baseline
- Folia support with region-aware scheduling and safe world access
- DivineMC 26.2–26.3 compatibility
- Removal of all `org.spigotmc.*` imports and version-specific NMS modules
- BungeeCord chat API replacement with Kyori Adventure `Component`
- MiniMessage-aware message rendering with legacy-code compatibility
- VaultUnlocked economy integration
- GriefPrevention3D claim integration
- GeyserMC/Floodgate Bedrock-player compatibility
- Current protection, map, claim, and container integrations
- Dependency, database, connection-pool, test, and CI refresh
- Comprehensive documentation and a passing **165-test** baseline

### Deliberately Out of Scope

- Redesigning Shop's shop types or sign-based user experience
- Replacing the H2/MySQL/MariaDB transaction-log model
- Removing legacy plugin integrations that remain compatible
- Introducing a new configuration format beyond the v2.0 baseline
- Adding new gameplay mechanics unrelated to modernization

## 3. Stack and Architecture

| Area | v2.0 choice |
| --- | --- |
| Server API | PaperMC API `26.2.build.135-stable` |
| Supported servers | PaperMC 26.2+, DivineMC 26.2–26.3, Folia |
| Java | Java 25 |
| Build | Maven multi-module build (`parent`, `core`, `dist`) |
| Text | Kyori Adventure Components; Adventure BOM `5.2.0` |
| Scheduling | FoliaLib `0.5.2` plus region-safe Bukkit scheduling |
| Economy | VaultUnlocked `2.15.0` and a compatible economy provider |
| Protection | GriefPrevention3D and existing region/claim integrations |
| Bedrock | Geyser API `2.11.2-SNAPSHOT`, Floodgate API `2.2.5-SNAPSHOT` |
| Storage | H2 `2.5.252`, MariaDB client `4.0.0`, HikariCP `7.1.0` |
| Tests | JUnit `6.1.3`, Mockito `5.24.0`, MockBukkit `4.117.0` |

The root Maven `<revision>` property controls the generated artifact version. The v2.0 modernization is complete; release versioning remains a maintainer action and must not be inferred from the branch name alone.

### Module layout

- **parent** — dependency management, compiler/release settings, and shared build configuration
- **core** — plugin implementation, integrations, resources, and automated tests
- **dist** — assembles the final installable Shop JAR

### Runtime layers

1. **Bootstrap and configuration** — loads `config.yml`, validates optional integrations, and initializes logging, metrics, and update checking.
2. **Shop domain** — owns shop creation, item matching, pricing, display types, and transaction rules.
3. **Player interaction** — processes sign and container interactions, commands, GUI actions, and chat-based creation.
4. **Adapters** — isolates optional integrations such as WorldGuard, Towny, VaultUnlocked, GriefPrevention3D, LWC, map plugins, Bolt, BlockProt, and CashierLock.
5. **Cross-server support** — detects GeyserMC/Floodgate and applies Bedrock-safe interaction behavior.
6. **Persistence and presentation** — transaction logging, offline notifications, holograms, Adventure rendering, and update checks.

### Concurrency model

Folia compatibility depends on region-owned scheduling, synchronized state where shared access is unavoidable, and concurrent collections for frequently mutated runtime data. New code must not use assumptions that all worlds share one main thread.

### Text model

Shop messages are represented as Adventure `Component` objects. `ShopMessage` supports MiniMessage-style tags, while `LegacyComponentSerializer` preserves legacy `&` code compatibility. Placeholder values must not be interpreted as MiniMessage input.

## 4. Key Files

| Path | Responsibility |
| --- | --- |
| `pom.xml` | Parent version, Java 25 compiler settings, and shared dependency management |
| `core/pom.xml` | Plugin dependencies and test configuration |
| `dist/pom.xml` | Final `Shop-{version}.jar` assembly |
| `core/src/main/resources/plugin.yml` | Paper API version, Folia flag, commands, permissions, and soft dependencies |
| `core/src/main/resources/config.yml` | Runtime defaults and integration switches |
| `core/src/main/java/com/snowgears/shop/Shop.java` | Plugin bootstrap, integration detection, and dependency wiring |
| `core/src/main/java/com/snowgears/shop/listener/` | Shop, sign, container, and interaction event handling |
| `core/src/main/java/com/snowgears/shop/gui/` | Shop browsing and transaction interfaces |
| `core/src/main/java/com/snowgears/shop/hook/` | Optional plugin adapters |
| `core/src/main/java/com/snowgears/shop/util/` | Adventure messages, update checks, item and server helpers |
| `core/src/main/java/com/snowgears/shop/logger/` | H2/MySQL/MariaDB transaction logging |
| `core/src/test/` | Unit and MockBukkit coverage |
| `wiki/` | User-facing configuration and integration documentation |

## 5. How to Work

1. Create a focused branch from the current upstream base.
2. Keep changes compatible with Java 25, PaperMC 26.2, DivineMC, and Folia unless the task explicitly changes that support baseline.
3. Use Adventure Components for user-facing text; do not reintroduce Bungee chat components or Spigot API imports.
4. Keep optional integrations behind runtime detection and configuration checks.
5. Schedule Folia-sensitive work on the correct region/global scheduler.
6. Add focused tests for new behavior and regression coverage for fixes.
7. Run `.\mvnw.cmd test` for the standard test set and `.\mvnw.cmd clean package` before review.
8. Update the relevant wiki page and configuration reference with user-visible changes.
9. Submit a pull request describing behavior, compatibility, migration impact, and test evidence.

## 6. Safety and Constraints

- Never commit credentials, database passwords, tokens, or production logs.
- Treat downloaded plugin data and configuration as untrusted input.
- Preserve existing player data unless an explicit, documented migration is required.
- Do not silently change economy, transaction, claim, or permission semantics.
- Use least privilege for database accounts and avoid embedding secrets in `config.yml`.
- Validate optional integrations when disabled or when their plugin is absent.
- Use thread-safe collections and region-safe scheduling for mutable shared state.
- Never expose stack traces or sensitive configuration details to players.
- Keep breaking changes out of patch releases and document them clearly.

## 7. Current State

### Completed

- The v2.0 PaperMC 26.2 and Java 25 migration is complete.
- Folia-safe scheduling and world access are implemented.
- DivineMC compatibility is confirmed for the 26.2–26.3 target range.
- All `org.spigotmc.*` imports have been removed.
- Adventure Components and MiniMessage rendering have replaced the Bungee chat pipeline.
- VaultUnlocked, GriefPrevention3D, and GeyserMC/Floodgate integrations are implemented.
- Modern dependencies and CI are in place.
- The automated suite passes with **165 tests**.
- The build completes successfully and produces the distribution JAR.

### Remaining Maintainer Work

These are release-process tasks, not unfinished migration phases:

- Set and publish the intended Maven release revision and Git tag.
- Publish v2.0 release notes and the distribution JAR.
- Run a final smoke test on the target Paper, DivineMC, and Folia server matrix.
- Review optional integration behavior on production-like server configurations.

See [MIGRATION_PLAN.md](MIGRATION_PLAN.md) for acceptance criteria and [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow.

## 8. Team and Handoff

- Producer: scope, coordination, and release handoff
- Dev: implementation and verification
- QA: optional independent behavioral verification

The v2.0 work was consolidated into a single modernization commit. CI logs, test reports, and the tagged release are the authoritative handoff artifacts. Record future architectural decisions in the relevant issue or pull request and update this brief when the supported platform baseline changes.

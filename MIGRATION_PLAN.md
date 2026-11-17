# Shop v2.0 Modernization Migration Plan

## Status

**COMPLETE — v2.0 modernization shipped in commit `ea80b981`**

- **Build:** PASSING — Maven multi-module build completes successfully
- **Tests:** PASSING — 165 automated tests pass
- **Server baseline:** PaperMC 26.2+, DivineMC 26.2–26.3, and Folia
- **Java baseline:** Java 25
- **Legacy API cleanup:** COMPLETE — no `org.spigotmc.*`, Bungee chat components, or `player.spigot().sendMessage()` usage remains

This document records the completed v2.0 overhaul and the small set of release/maintenance tasks that remain outside the migration itself.

## Migration Goals

1. Move Shop to a supported modern server API without NMS modules.
2. Make plugin execution safe and predictable on Folia's regionized scheduler.
3. Replace the legacy chat stack with Adventure Components while retaining usable formatting controls.
4. Replace or remove obsolete integration dependencies.
5. Add current ecosystem integrations, including Bedrock players and modern claim protection.
6. Refresh build, dependency, CI, and test infrastructure.
7. Publish documentation that accurately describes the v2.0 support baseline.

## Completed Phases

### Phase 0: Build and CI Baseline — Complete

- Upgraded the parent build to Java 25 and Maven multi-module structure.
- Aligned the Paper dependency with API version `26.2.build.135-stable`.
- Added the Adventure BOM and FoliaLib for modern text and scheduling.
- Updated the GitHub Actions workflow to use Temurin JDK 25, Maven caching, test reporting, and build artifacts.
- Established a repeatable local build with the Maven wrapper.

### Phase 1: PaperMC 26.2 API Migration — Complete

- Set `api-version: 26.2` in `plugin.yml`.
- Removed version-specific server internals and all `org.spigotmc.*` imports.
- Replaced obsolete server API usage with Paper-supported equivalents.
- Updated commands, permissions, plugin metadata, and resource loading for the current API.

### Phase 2: Folia and DivineMC Support — Complete

- Declared `folia-supported: true`.
- Replaced unsafe global assumptions with region-aware scheduling through FoliaLib and supported scheduler APIs.
- Made mutable shared state use appropriate concurrent collection types.
- Moved world/block/sign access to scheduler contexts that own the affected region.
- Confirmed the Paper-based API surface used by Shop is compatible with DivineMC 26.2–26.3.

### Phase 3: Adventure Text Migration — Complete

- Replaced `net.md_5.bungee.api.chat.*` components with Kyori Adventure `Component` objects.
- Replaced `player.spigot().sendMessage()` calls with `player.sendMessage(Component)`.
- Replaced direct legacy color translation with `LegacyComponentSerializer` where legacy text compatibility is required.
- Added MiniMessage-aware rendering in `ShopMessage` while protecting placeholder values from tag interpretation.
- Updated item names, hover text, GUI output, signs, logs, and listeners to use the Adventure model.

### Phase 4: Economy and Protection Integration Refresh — Complete

- Replaced the legacy Vault dependency with VaultUnlocked `2.15.0`.
- Preserved the `VAULT` configuration value as the currency type while updating the provider and documentation for VaultUnlocked.
- Replaced the legacy GriefPrevention dependency with GriefPrevention3D.
- Added claim-aware shop creation, build, and access checks through the GriefPrevention3D listener.
- Retained compatible protection, claim, container, and map integrations already present in Shop.

### Phase 5: Cross-Server and Bedrock Support — Complete

- Added Geyser API and Floodgate API dependencies.
- Added runtime detection for Geyser-Spigot and Floodgate.
- Added a Geyser/Floodgate hook so Bedrock-connected players are recognized consistently by Shop interactions.
- Exposed a `geyser.enabled` configuration switch for servers that do not want the integration active.

### Phase 6: Dependency, Persistence, and Test Refresh — Complete

- Refreshed Adventure, H2, MariaDB, HikariCP, JUnit, Mockito, MockBukkit, and optional integration dependencies.
- Updated the update checker to use the GitHub Releases API for `TheFlood424K/Shop` instead of the SpigotMC update API.
- Updated component-aware test helpers and assertions.
- Expanded coverage for the migrated text, scheduling, integration, and configuration behavior.
- Confirmed the final suite with **165 passing tests**.

### Phase 7: Documentation and Release Preparation — Complete

- Rewrote the README for the v2.0 release line, feature set, requirements, installation, credits, and support paths.
- Updated the project brief to describe the completed architecture and current state.
- Updated this plan with completed phases and checked acceptance criteria.
- Updated the contributing guide for Java 25 and Maven.
- Updated the wiki for Adventure Components, Paper 26.2, Folia, VaultUnlocked, GriefPrevention3D, GeyserMC/Floodgate, and current trust integrations.

## Acceptance Criteria

- [x] Maven build succeeds on Java 25.
- [x] All 165 automated tests pass.
- [x] PaperMC API 26.2 is used and declared in plugin metadata.
- [x] DivineMC 26.2–26.3 is included in the supported server baseline.
- [x] Folia is supported and the plugin declares `folia-supported: true`.
- [x] No `org.spigotmc.*` imports remain.
- [x] No Bungee chat component imports or `player.spigot().sendMessage()` calls remain.
- [x] User-facing text uses Adventure Components with MiniMessage and legacy compatibility.
- [x] VaultUnlocked replaces the legacy Vault dependency.
- [x] GriefPrevention3D replaces the legacy GriefPrevention dependency.
- [x] GeyserMC/Floodgate support is implemented and configurable.
- [x] Dependency and CI updates are complete.
- [x] README, project brief, migration plan, contributing guide, and wiki reflect v2.0.
- [x] The modernization is consolidated in the single v2.0 commit.

## Upgrade Notes for Server Operators

v2.0 is intentionally breaking. Before upgrading:

1. Stop the server and back up `plugins/Shop/`, its database, and the existing Shop JAR.
2. Use a PaperMC 26.2+ (or supported DivineMC/Folia) server with Java 25.
3. Replace the old JAR rather than running v1.x and v2.0 side by side.
4. Install VaultUnlocked and a compatible economy provider if virtual currency is enabled.
5. Start the server, review any migration or configuration messages, and test one shop of each type.
6. Verify protection, claim, map, and trust integrations on a production-like copy of the server.

The v2.0 configuration retains the existing `VAULT` currency type name, but the backing API is now VaultUnlocked. Optional integrations are detected at runtime and can be disabled in `config.yml`.

## Remaining Work

The migration has no unfinished implementation phases. The following optional maintenance and release tasks remain:

- Set the root Maven release revision to the published v2.0 version and create the matching Git tag.
- Publish the distribution JAR and v2.0 release notes.
- Smoke-test the final release artifact on representative PaperMC, DivineMC, and Folia installations.
- Expand CI with a live-server matrix if maintainers want automated compatibility coverage beyond the Java build and test suite.
- Continue updating optional dependencies and test coverage in future patch releases.
- Add new gameplay features only in a separately scoped roadmap; v2.0 is a modernization baseline, not a redesign.

## Success Criteria

The v2.0 modernization is successful when a supported server loads Shop without legacy API errors, a server owner can configure and use all core shop types, optional integrations behave correctly, Bedrock-connected players can interact through Geyser/Floodgate, and the project can be built and validated reproducibly with Java 25 and Maven. These criteria are met by the completed implementation and test suite; the remaining release tasks above do not block modernization completion.

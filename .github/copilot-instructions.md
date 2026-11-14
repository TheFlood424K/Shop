# Shop v2.0 — GitHub Copilot Instructions

## Project

**Shop** — chest-based shop plugin for Minecraft Java Edition 26.2.  
Release line: **v2.0** (modernization complete).  
Repository: `TheFlood424K/Shop`

## Supported Baseline

| Area | Version |
|------|---------|
| Java | 25 |
| Server API | PaperMC 26.2+ (`api-version: 26.2`) |
| Supported servers | PaperMC 26.2+, DivineMC 26.2–26.3, Folia |
| Build | Maven multi-module (`parent`, `core`, `dist`) |
| Text | Kyori Adventure Components |
| Scheduling | FoliaLib + region-safe Bukkit scheduling |
| Economy | VaultUnlocked 2.15.0 |
| Protection | GriefPrevention3D |
| Bedrock | GeyserMC/Floodgate |

## Build & Test Commands (from repo root)

```powershell
# Compile
.\mvnw.cmd clean compile

# Run full test suite (165 tests)
.\mvnw.cmd test

# Build distribution JAR
.\mvnw.cmd clean package

# Coverage report
.\mvnw.cmd -Pcoverage-report verify
```

## Hard Constraints (never violate)

- **No `org.spigotmc.*` imports** — removed in v2.0
- **No BungeeCord chat components** (`net.md_5.bungee.api.chat.*`) — use Adventure `Component`
- **No `player.spigot().sendMessage()`** — use `player.sendMessage(Component)`
- **No NMS modules** or version-specific server internals
- **No legacy Spigot APIs** where Paper-supported equivalents exist
- **No hard dependencies** for integrations that can remain soft

## Text Model

All user-facing text uses Adventure `Component` objects. `ShopMessage` supports MiniMessage-style tags with `LegacyComponentSerializer` for legacy `&` code compatibility. Placeholder values are plain text, never MiniMessage input.

## Concurrency Model

Folia compatibility requires region-owned scheduling. Mutable shared state uses concurrent collections. World/block/sign access occurs in scheduler contexts that own the affected region. Never assume all worlds share one main thread.

## Coding Principles

SOLID, DRY, KISS, YAGNI, OWASP, DOP, FP, DDD.  
Prefer immutability, composition over inheritance, minimal mutability, thread-safe collections.  
Write doc comments for all exposed API elements.  
Keep changes focused; one logical change per pull request.

## Module Layout

- `pom.xml` — parent build, dependency management, Java 25 settings
- `core/` — plugin implementation, resources, hooks, listeners, tests
- `dist/` — distribution assembly for `Shop-{version}.jar`
- `wiki/` — user-facing documentation
- `.github/workflows/` — CI build, test, artifact, release automation

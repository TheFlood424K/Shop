---
description: Shop v2.0 project identity and constraints
trigger: always_on
---

# Shop v2.0 — Project Identity

**Shop** — chest-based shop plugin for Minecraft Java Edition 26.2. Release line: v2.0.

**Supported baseline:** Java 25, PaperMC 26.2+, DivineMC 26.2–26.3, Folia. Maven multi-module (parent, core, dist).

**Hard constraints:**
- No `org.spigotmc.*` imports
- No BungeeCord chat components — use Adventure `Component`
- No `player.spigot().sendMessage()` — use `player.sendMessage(Component)`
- No NMS modules or version-specific server internals
- No hard dependencies for integrations that can remain soft

**Build/test:** `.\mvnw.cmd clean compile` | `.\mvnw.cmd test` | `.\mvnw.cmd clean package`

**Text:** Adventure `Component` for all user-facing text. `ShopMessage` supports MiniMessage tags with `LegacyComponentSerializer` for legacy `&` codes. Placeholders are plain text.

**Concurrency:** Folia-safe region-owned scheduling. Thread-safe collections for mutable shared state. No assumption that all worlds share one main thread.

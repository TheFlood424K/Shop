# Shop v2.0 — Project Overview

**Shop** — chest-based shop plugin for Minecraft Java Edition 26.2. Release line: v2.0 (modernization complete).

**Supported baseline:** Java 25, PaperMC 26.2+, DivineMC 26.2–26.3, Folia.

**Hard constraints:**
- No `org.spigotmc.*` imports, no Bungee chat, no `player.spigot().sendMessage()`
- No NMS modules; Adventure `Component` for all user-facing text
- No hard dependencies for optional integrations

**Build/test:** `.\mvnw.cmd clean compile` | `.\mvnw.cmd test` | `.\mvnw.cmd clean package`

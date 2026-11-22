# CLAUDE.md — Shop Agent Instructions

## Project

**Shop** is a chest-based shop plugin for Minecraft Java Edition 26.2.

Repository: `TheFlood424K/Shop`

Treat the root `pom.xml` and `core/pom.xml` as the source of truth for the current project revision and dependency versions.

## Canonical guidance

Read before acting:

1. `@.github/copilot-instructions.md` — canonical repository guidance.
2. Every file under `.github/instructions/` whose `applyTo` pattern matches the files in scope.
3. `@.github/skills/shop-plugin-development/SKILL.md` — the end-to-end Shop development workflow.

Repository-specific agents are under `.github/agents/`:

- `shop-implementer.agent.md` for implementation
- `shop-reviewer.agent.md` for read-only review
- `shop-qa.agent.md` for focused testing

## Standalone baseline

- Java 25.
- PaperMC 26.2+ (`api-version: 26.2`), with Folia-compatible scheduling.
- Maven multi-module build: `parent`, `core`, and `dist`.
- Kyori Adventure `Component` for all user-facing text.
- Economy, protection, Bedrock, and other optional integrations remain soft: detect them at runtime and tolerate their absence.

## Hard constraints

- Do not introduce `org.spigotmc.*` imports, BungeeCord chat components, `player.spigot().sendMessage()`, NMS modules, or version-specific server internals. Prefer Paper-supported APIs.
- Use Adventure `Component` for user-facing text and `ShopRevancedMessage` where the existing message model applies. Render placeholder values as literal text or literal `Component` segments, never MiniMessage source.
- On Folia, use region- or entity-owned scheduling for region-sensitive work and thread-safe state for values shared across scheduler contexts.
- Keep changes focused, document exposed APIs, and add focused tests for new or fixed behavior.

## Build and validation

Run from the repository root:

```powershell
# Compile
.\mvnw.cmd clean compile

# Run the complete test suite
.\mvnw.cmd test

# Build the distribution JAR
.\mvnw.cmd clean package

# Generate the coverage report
.\mvnw.cmd -Pcoverage-report verify
```

Use the narrowest useful Maven selector while iterating, then run the complete suite. Report exact commands and results; do not use a hard-coded test count as validation evidence.

## Delegation

- Keep delegated tasks bounded and independent. Use up to three concurrent subagents only when work genuinely benefits from parallel context.
- Use only models confirmed in the active local custom provider's catalog. Never use or fall back to a GPT/OpenAI model. If no eligible model can be confirmed, continue without delegation.

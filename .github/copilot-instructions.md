# Shop — GitHub Copilot Instructions

## Project

**Shop** is a chest-based shop plugin for Minecraft Java Edition 26.2. The current project version and dependency versions are defined by the root `pom.xml` and `core/pom.xml`; do not duplicate version numbers in guidance.

Repository: `TheFlood424K/Shop`

## Supported baseline

| Area | Baseline |
|------|----------|
| Java | 25 |
| Server API | PaperMC 26.2+ (`api-version: 26.2`) |
| Supported servers | PaperMC 26.2+, DivineMC 26.2–26.3, Folia |
| Build | Maven multi-module (`parent`, `core`, `dist`) |
| Text | Kyori Adventure Components |
| Scheduling | FoliaLib + region-safe Bukkit scheduling |
| Economy | VaultUnlocked |
| Protection | GriefPrevention3D |
| Bedrock | GeyserMC/Floodgate |

## Build and validation commands

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

## Hard constraints

- Do not introduce `org.spigotmc.*` imports, BungeeCord chat components, `player.spigot().sendMessage()`, NMS modules, or version-specific server internals.
- Prefer Paper-supported APIs over legacy equivalents.
- Keep optional integrations soft. Detect them at runtime and tolerate their absence.
- Keep the change focused. Do not modify the release version, CI, or unrelated documentation unless the task asks for it.

## Text model

Use Adventure `Component` for all user-facing text. Route messages through `ShopRevancedMessage` where the existing message model applies. Render placeholder values as literal text or literal `Component` segments; never place untrusted placeholder content into MiniMessage source. Preserve legacy `&` formatting only where it remains an intentional compatibility feature.

## Concurrency model

Folia compatibility requires region- or entity-owned scheduling. Never access worlds, blocks, signs, or other region-sensitive state from asynchronous work. Use thread-safe collections or immutable values for mutable state shared across scheduler contexts. Treat transaction guards as atomic state, and schedule cross-region player and inventory operations separately or through an explicit safe handoff.

## Integration model

Integration APIs remain `provided` dependencies unless a hard runtime dependency is essential. Keep runtime detection, `plugin.yml` soft-dependency metadata, configuration keys, enable/disable lifecycle, absent-plugin behavior, listener registration, and reflection isolation aligned.

## Coding principles

Apply SOLID, DRY, KISS, YAGNI, OWASP, DOP, FP, and DDD. Prefer immutability, composition, minimal mutability, and thread-safe collections. Document exposed API elements. Add focused regression coverage for every bug fix and focused coverage for each new behavior.

## Module layout

- `pom.xml` — parent build, dependency management, Java settings, and current project revision
- `core/` — plugin implementation, resources, integrations, listeners, and tests
- `dist/` — distribution assembly for `Shop-{version}.jar`
- `wiki/` — user-facing documentation
- `.github/workflows/` — CI build, test, artifact, and release automation

## Agentic workflow

- Read `.github/instructions/` files whose `applyTo` patterns match the task.
- Use `.github/agents/shop-implementer.agent.md` for implementation, `.github/agents/shop-reviewer.agent.md` for read-only review, and `.github/agents/shop-qa.agent.md` for focused testing.
- Use `.github/skills/shop-plugin-development/SKILL.md` for the end-to-end development workflow.
- Keep delegated tasks bounded and independent. Use up to three subagents at a time when work genuinely benefits from parallel context.
- When delegating in this local environment, use only a model confirmed in the active custom provider's catalog. Never use or fall back to a GPT/OpenAI model. If no eligible model can be confirmed, continue without delegation.

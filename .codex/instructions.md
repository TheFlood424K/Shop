# Codex Instructions — Shop Plugin v2.0

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

## Available Tools and Skills

### wuniq / KaKeKiKoKu Studio (Knowledge Engine)

**MCP:** `com-wuniq-wuniq` — tool: `run`

At the start of every session, run `open list`, then `open "<path>"`. Read folder sidecar contexts before working. Sync after changes. Record decisions and rationale in `.wuniq` files.

### Modernization Workflow Canvas

**Canvas:** `modernization-workflow`

Use `configure_workflow`, `set_step_status`, `get_workflow_state`, `refresh_from_artifacts` to track Assess/Plan/Execute progress.

### Available MCP Servers

| Server | Tool Prefix | Purpose |
|--------|------------|---------|
| `com-wuniq-wuniq` | `com-wuniq-wuniq-run` | KaKeKiKoKu knowledge engine |
| `github` | `github-*` | GitHub API operations |
| `github-mcp-server` | `github-mcp-server-*` | GitHub MCP search and file access |
| `apify-mcp-server` | `apify-mcp-server-*` | Web scraping and automation via Apify Actors |
| `cc-dec-YHcEPIxsdfEC` | `cc-dec-YHcEPIxsdfEC-*` | Code analysis and security scanning via Semgrep |
| `com-memxus-memxus` | `com-memxus-memxus-*` | Persistent memory across sessions |
| `computer-use` | `computer-use-*` | Desktop application UI automation |
| `GitKraken` | `GitKraken-*` | Git repository operations |
| `microsoft-learn` | `microsoft-learn-*` | Microsoft documentation lookup |
| `playwright` | `playwright-*` | Browser automation and testing |

### Available Skills

- `ai-team-orchestration:ai-team-dev` — AI development team (Nova, Sage, Milo)
- `ai-team-orchestration:ai-team-producer` — Planning and coordination
- `ai-team-orchestration:ai-team-qa` — QA engineer
- `java-mcp-development:java-mcp-expert` — MCP server development in Java
- `java-upgrade` — Upgrade Java projects to newer JDK versions
- `java-debugging` — Debug Java applications systematically
- `java-docs` — Java Javadoc documentation
- `microsoft-docs` — Microsoft technologies documentation
- `microsoft-code-reference` — Code samples and API signatures
- `skeptic-review` — Adversarial structural critique
- `strategy-stakes-review` — Strategic fit and opportunity cost
- `evidence-calibration-review` — Per-claim evidence-tier audit
- `adjudicator-synthesis` — Synthesize council critiques into SHIP/REVISE/HOLD
- `voice-identity-review` — Voice and identity review
- `automate-this` — Screen recording automation
- `apify:apify` — Apify Agent
- `awesome-copilot:meta-agentic-project-scaffold` — Project scaffolding
- `awesome-copilot:suggest-awesome-github-copilot-skills` — Suggest skills
- `awesome-copilot:suggest-awesome-github-copilot-instructions` — Suggest instructions
- `awesome-copilot:suggest-awesome-github-copilot-agents` — Suggest agents

## Subagent Parallelism

Use up to three subagents at a time to speed up independent work. For parallelizable tasks (e.g., reading multiple files, running independent searches, making edits to separate files), dispatch subagents concurrently rather than sequentially. Only use subagents when the work genuinely benefits from separate context; simple lookups and direct file edits should be handled inline. Monitor subagent results and integrate them before proceeding to dependent steps.
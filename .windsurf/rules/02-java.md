---
description: Java-specific rules for Shop v2.0
trigger:
  - glob
globs:
  - "*.java"
---

# Java Rules (Shop v2.0)

## Stack
Java 25, Maven, Bukkit/Paper API 26.2, Adventure Components 5.2.0.

## Effective Java Principles
- Static factory methods over constructors where appropriate
- Builder for many constructor parameters
- Prefer dependency injection, composition over inheritance
- Minimize mutability; prefer immutable objects
- Minimize accessibility of classes and members
- Use interfaces to define types; prefer interfaces to abstract classes
- Use `@Override` consistently
- Prefer lambdas and method references to anonymous classes
- Check parameters for validity; make defensive copies
- Return empty collections, not nulls; use Optionals judiciously
- Use exceptions only for exceptional conditions
- Synchronize access to shared mutable data; document thread safety

## Shop-Specific
- No `org.spigotmc.*` imports; no NMS modules
- Adventure `Component` for all user-facing text (no Bungee chat)
- Folia-safe scheduling for region-sensitive operations
- Thread-safe collections for shared mutable state
- Optional integrations detected at runtime; handle absent plugins gracefully

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

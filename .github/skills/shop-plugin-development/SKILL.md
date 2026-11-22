---
name: shop-plugin-development
description: Develop, test, and review Shop v2.0 Paper/Folia plugin changes using the repository's Java 25, atomic-transaction, Adventure, Folia ownership, soft-integration, and release conventions.
---

# Shop plugin development

Use this skill for implementation, bug fixing, integration work, focused testing, and review of Shop v2.0 code.

## Load the repository guidance

Read these files before acting:

1. `.github/copilot-instructions.md`
2. Every applicable file under `.github/instructions/`, including integration/config/plugin-metadata and Maven dependency instructions when those paths are in scope
3. `CONTRIBUTING.md` for the contribution and release checklist

Repository-specific Copilot agents are available under `.github/agents/`:

- `shop-implementer.agent.md` owns focused production implementation and directly required configuration, metadata, dependency, and documentation changes.
- `shop-reviewer.agent.md` owns read-only diff review and findings.
- `shop-qa.agent.md` owns focused test design, test-source changes, and test execution.

Keep those roles distinct. Do not turn a read-only review into an implementation task, and do not report QA results that were not actually run.

## Development workflow

1. Inspect the current behavior, nearby tests, and the relevant dependency, configuration, or plugin-metadata source of truth.
2. Define the smallest change that satisfies the requested behavior.
3. Implement it within the `com.theflood424k.shoprevanced` package tree.
4. Preserve Java 25 and PaperMC 26.2 compatibility without legacy APIs or server internals.
5. Keep user-facing text in Adventure `Component`. Render placeholder values as literal `Component` segments appended to trusted template output; never pass placeholder content through MiniMessage parsing.
6. Use Folia region- or entity-owned scheduling for region-sensitive operations and thread-safe shared state. When player and shop/container inventory owners may differ, use explicit scheduler handoffs for each owner and pass immutable snapshots or results between contexts.
7. Acquire transaction guards atomically with compare-and-set or an equivalent operation, and release them in `finally`. Do not rely on a plain boolean check-then-act guard.
8. Keep optional integrations soft. Keep runtime detection, `plugin.yml` soft-dependency metadata, configuration keys/defaults, listener registration/unregistration, and absent-plugin behavior aligned. Keep optional APIs `provided` and reflective access isolated.
9. Add focused tests for new or fixed behavior. MockBukkit can test handoff and state logic where feasible, but it cannot prove real Folia region/entity scheduling; state that limitation and require a Folia-compatible server check when the ownership guarantee matters.
10. Update directly affected configuration, plugin metadata, dependency declarations, and user documentation.
11. Run focused Maven checks, then `.\mvnw.cmd test`; run `.\mvnw.cmd clean package` when packaging or release contracts may be affected.
12. Review `git diff --check` and `git diff` for unrelated or generated files.

## Delegation policy

When delegating any part of the task to a subagent, select only a model available in the active local custom provider's catalog. Never use a GPT/OpenAI model, and never silently fall back to one. If the eligible catalog cannot be inspected, proceed without delegation.

## Completion criteria

A task is complete only when the behavior is implemented, focused tests cover it, applicable validation passes, affected documentation and metadata are current, and the final diff contains no unrelated or generated artifacts. Report exact validation commands and results, and distinguish test evidence from guarantees that require a real Paper/Folia server.

---
description: Shop project guidance from the canonical repository instructions
trigger: always_on
---

# Shop Project Guidance

Read and follow `.github/copilot-instructions.md` for project-wide requirements, then apply any matching file under `.github/instructions/` for the paths being changed. Treat the Maven build files as the source of truth for versions and dependencies.

## Agentic Workflow

- Delegated subagents must use only models confirmed in the active local custom-provider catalog.
- Never use or fall back to GPT/OpenAI models.
- If no eligible model can be confirmed, continue without delegation.

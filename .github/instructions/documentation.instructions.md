---
applyTo: "README.md,CONTRIBUTING.md,wiki/**/*.md,MIGRATION_PLAN.md,PROJECT_BRIEF.md"
---

# Documentation instructions

- Write for server administrators and contributors; state prerequisites, behavior, and migration steps concretely.
- Keep compatibility claims aligned with `pom.xml`, `core/pom.xml`, plugin metadata, and the CI workflow.
- Do not duplicate dependency versions or test counts unless they are authoritative. Prefer linking to the relevant source of truth.
- Document configuration keys, permissions, economy behavior, persistence changes, and soft-integration fallbacks when affected.
- For breaking changes, include an upgrade path and an explicit compatibility note.
- Verify every documented command from the repository root and use the Maven wrapper on Windows.
- Keep the pull-request checklist in `CONTRIBUTING.md` consistent with `.github/pull_request_template.md`.

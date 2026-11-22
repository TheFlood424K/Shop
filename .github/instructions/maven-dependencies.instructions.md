---
applyTo: "pom.xml,core/pom.xml,dist/pom.xml"
---

# Maven dependency instructions

- Treat the root `pom.xml` and module `pom.xml` files as the authoritative dependency and version source of truth. Do not duplicate versions in instructions or documentation when the manifests already define them.
- Keep optional integration API dependencies in `provided` scope unless the plugin cannot function without them. A compile-time integration dependency must also align with runtime detection, `plugin.yml` soft-dependency metadata, configuration, and listener lifecycle.
- Preserve Java 25 and the supported PaperMC API baseline. Prefer existing dependency management and repositories; do not add a dependency or repository without a demonstrated requirement and manifest justification.
- Keep the parent, `core`, and `dist` module relationships and the root `<revision>` release contract intact unless the task explicitly changes them.
- When an integration dependency changes, inspect runtime plugin-name detection, `plugin.yml` metadata, configuration keys/defaults, and listener registration/unregistration in the same change.
- Do not remove or upgrade a dependency without checking its supplied/optional scope, transitive behavior, and compatibility with supported Paper, DivineMC, and Folia deployments.
- Validate dependency changes with the narrowest useful module selector, then the complete Maven test suite. Use `.\mvnw.cmd clean package` when packaging or shading behavior may be affected.
- Report exact Maven commands and results; do not use a hard-coded test count as validation evidence.

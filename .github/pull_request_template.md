# Summary

<!-- Describe the user-visible or operational change and why it is needed. -->

## Related issue

<!-- Link the issue, for example "Fixes #123". Use "N/A" when there is no issue. -->

## Change type

<!-- Replace with one or more applicable types: Bug fix, Feature, Integration, Documentation, Build, CI, or release, Refactor or maintenance. -->

`Type`

## Compatibility and safety

- [ ] The change was reviewed against Java 25, PaperMC 26.2+, DivineMC 26.2–26.3, and Folia; not-applicable surfaces are identified.
- [ ] If the change touches Folia-sensitive scheduling, it uses region- or entity-owned scheduling, and cross-region player/shop inventory work uses an explicit scheduler handoff.
- [ ] If the change touches transaction guards or shared mutable state, it remains concurrency-safe; transaction guards are acquired atomically and released on every path.
- [ ] If the change touches user-facing text, it uses Adventure `Component`; placeholder values are rendered as literal `Component` segments and are not parsed as MiniMessage source.
- [ ] For each changed optional integration, `plugin.yml` soft-dependency metadata, configuration keys/defaults, runtime detection, listener registration/unregistration, and absent-plugin behavior remain aligned.
- [ ] Optional integrations remain optional, optional APIs remain `provided` where appropriate, and reflective access remains isolated.
- [ ] No legacy Spigot/Bungee APIs, NMS, or version-specific server internals were introduced.

## Testing evidence

<!-- Record exact commands and their results. Name focused tests for changed behavior. Use "N/A - <reason>" when a command is not applicable. -->

| Command | Result |
| --- | --- |
| `<focused Maven command>` | `<passed/failed and focused test scope>` |
| `.\mvnw.cmd test` | `<passed/failed/not run - reason>` |
| `.\mvnw.cmd clean package` | `<passed/failed/not run - reason>` |

- [ ] Focused tests were added or updated for changed behavior, or the reason none were needed is documented.
- [ ] Folia-sensitive evidence distinguishes unit/handoff coverage from validation that requires a real Folia server; MockBukkit results are not presented as proof of real region/entity ownership.

## Documentation and migration

- [ ] Relevant README, wiki, configuration, plugin metadata, dependency, or migration documentation was updated.
- [ ] Configuration, permissions, persistence, economy, dependency, metadata, or API compatibility impacts are described below.

<!-- Write "N/A" when there is no migration impact. -->

## Release impact

- [ ] The root `<revision>` is unchanged, or the version change is intentional and documented.
- [ ] The semver impact is patch/minor/major, or no release impact.

## Final checklist

- [ ] The change is focused and contains no unrelated edits.
- [ ] No secrets, production data, private logs, or generated build artifacts are included.
- [ ] The implementation and validation evidence are ready for review.

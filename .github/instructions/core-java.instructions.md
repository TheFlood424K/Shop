---
applyTo: "core/src/main/java/**/*.java"
---

# Core Java instructions

- Target Java 25 and the PaperMC 26.2 API. Treat `pom.xml` and `core/pom.xml` as the dependency source of truth.
- Keep implementation classes in the `com.theflood424k.shoprevanced` package tree.
- Use Adventure `Component` for all user-facing text. Route messages through `ShopRevancedMessage` where the existing message model applies. Parse trusted template syntax only from configuration; render placeholder values as literal `Component` segments appended to the formatted template. Never serialize placeholder content into MiniMessage source or otherwise reparse it.
- Never introduce `org.spigotmc.*`, BungeeCord chat components, `player.spigot().sendMessage()`, NMS modules, or version-specific server internals.
- On Folia, schedule world, block, sign, and region-sensitive work in the scheduler context that owns the affected location or entity. Use entity schedulers for player-owned work.
- When one operation can touch a player inventory and a shop/container inventory owned by different regions, do not read or mutate both from one context. Use an explicit region- or entity-owned scheduler handoff for each mutation and pass immutable snapshots or results between contexts; do not assume the two owners share a region.
- Treat transaction guards as atomic state. Acquire a guard with an atomic compare-and-set or equivalent operation and release it in a `finally` block; do not implement check-then-act behavior with a plain boolean.
- Use thread-safe collections or immutable values for mutable state shared across scheduler contexts. Do not assume all worlds share one main thread.
- Keep economy, protection, Bedrock, and other optional integrations soft. Keep runtime detection, `plugin.yml` soft-dependency metadata, configuration keys and defaults, listener registration/unregistration, and absent-plugin behavior aligned for every integration. Keep optional API dependencies `provided` unless a hard runtime dependency is essential, and isolate reflective access.
- Preserve the surrounding architecture and public behavior unless the task requires a deliberate change. Keep exposed API elements documented and minimize mutability.
- Add or update focused tests for behavior introduced or fixed by the change.

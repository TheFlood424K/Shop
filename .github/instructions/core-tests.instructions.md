---
applyTo: "core/src/test/java/**/*.java"
---

# Test instructions

- Test observable behavior rather than implementation details.
- Add a focused regression test for every bug fix and focused coverage for each new behavior introduced by the task.
- Cover relevant success, failure, permission, economy, inventory, configuration, and absent-integration paths without duplicating the production implementation in the test.
- Use JUnit 6, Mockito, and MockBukkit APIs already managed by `core/pom.xml`; do not add a new test framework without a manifest change and justification.
- Keep tests deterministic. Avoid arbitrary sleeps, timing assumptions, shared mutable fixtures, and dependence on execution order.
- MockBukkit cannot prove that real Folia region or entity schedulers own the affected state. For Folia-sensitive changes, add focused tests where feasible for the handoff decision, callback ordering, and thread-safe shared state, but do not describe those tests as proof of real Folia ownership. Record the limitation and require a Folia-compatible server check when that guarantee matters.
- For atomic transaction guards, test that only one contender acquires the guard and that every success and failure path releases it. Prefer deterministic coordination such as barriers or latches over sleeps.
- For cross-region player/shop inventory work, focus tests on the explicit scheduler handoff and on preventing direct cross-owner access. A focused test does not replace validation on a real Folia server.
- For message placeholders, include a focused case with MiniMessage-like or otherwise adversarial placeholder text and assert that it is rendered literally rather than parsed as template syntax.
- For integration changes, cover the relevant enabled, disabled, and absent-plugin paths where feasible, including listener lifecycle or metadata/config parity checks.
- Use the narrowest useful Maven selector while iterating, then run `.\mvnw.cmd test` for final validation.
- Report the exact commands and results. Do not rely on or add a hard-coded total test count because the suite evolves.

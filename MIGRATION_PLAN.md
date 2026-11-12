# Shop Migration Plan — Remaining Work After Stable Build

## Status

- **Compilation**: PASSING — all Spigot/Bungee chat APIs migrated to Kyori Adventure Components
- **CI build**: In progress — test failures (60/151) expected to be resolved by `§` serialization fix
- **Branch**: `theflood424k-studious-carnival` (PR #409)

## What's Complete

| Task | Status |
|------|--------|
| ShopMessage.java — all Bungee → Adventure migration | ✅ Done |
| ItemNameUtil.java — TextComponent → Component | ✅ Done |
| ItemHoverEventHelper.java — bungee HoverEvent → Adventure | ✅ Done |
| UpdateChecker.java — player.spigot().sendMessage → player.sendMessage | ✅ Done (TODO for SpigotMC API) |
| ShopGuiHandler.java — ChatColor → Adventure | ✅ Done |
| AbstractShop.java — ChatColor → Adventure | ✅ Done |
| MiscListener.java — unused TextComponent import removed | ✅ Done |
| No `net.md_5.bungee.api.chat.*` imports in main source | ✅ Done |
| No `player.spigot().sendMessage()` calls | ✅ Done |
| All `TextComponent`/`BaseComponent` replaced with `Component` | ✅ Done |
| pom.xml aligned with Paper 26.2, Adventure BOM 5.2.0 | ✅ Done |
| plugin.yml Paper-native (api-version 26.2, folia-supported: true) | ✅ Done |
| Committed and pushed to GitHub PR branch | ✅ Done |

## Blocker: 60 Failing Tests

**Root cause**: Tests assert legacy `§` color codes (e.g. `"§eEnter in chat..."`), but `sendMessage(Component)` queues Adventure Components, not Strings. `MockBukkit.nextMessage()` (String) does not dequeue Component messages.

**Fix applied** (commit `8a9e12e`): `BaseMockBukkitTest.waitForNextMessage()` and `PlayerMessageTestUtil.drainMessages()` now drain Component messages via `nextComponentMessage()` and serialize with `LegacyComponentSerializer.legacy('§')` to match test expectations.

**Pending**: CI run triggered by push `8a9e12e` — verify all 151 tests pass.

## Migration Plan: Remaining Work (Post-Stable Build)

### Phase 1: Folia Support Verification

**Goal**: Confirm the plugin runs correctly on Folia (thread-safe regionized server).

| Task | Details |
|------|---------|
| Verify `plugin.yml` has `folia-supported: true` | Already set — confirm in CI |
| Audit for non-thread-safe code | Search for `Bukkit.getScheduler().runTask()`, synchronous DB access, etc. |
| Verify FoliaLib 0.5.2 integration | Already in pom.xml — confirm shade relocation works |
| Check `AbstractShop.updateSign()` thread safety | Sign block modifications should be on main thread |

### Phase 2: DivineMC Support Verification

**Goal**: Confirm the plugin is compatible with DivineMC server software.

| Task | Details |
|------|---------|
| Audit API usage for Paper-specific features | Check for any `org.paper*` or `io.papermc*` imports |
| Verify no Spigot-specific behavior relied upon | Search for `org.spigotmc*` references |
| Test on DivineMC if available | May require separate CI matrix job |

### Phase 3: Remaining Spigot Dependency Cleanup

**Goal**: Minimize Spigot dependencies; add TODO doc strings where Spigot is unavoidable.

| File | Current Status | Action |
|------|---------------|--------|
| `UpdateChecker.java` | Queries `api.spigotmc.org` for version info | Add TODO doc string to migrate to Paper/Modrinth API |
| `pom.xml` | Paper API + FoliaLib + Adventure BOM | Verify no orphaned Spigot deps |
| `core/pom.xml` | shade plugin relocates HikariCP/mariadb/folialib | Confirm relocation config correct |

### Phase 4: Test Suite Hardening

**Goal**: Ensure tests work with Adventure Component-based messages.

| Task | Details |
|------|---------|
| Verify `waitForNextMessage()` Component drain works | CI will confirm |
| Add Component-aware assertions to new tests | Use `nextComponentMessage()` + `LegacyComponentSerializer.legacy('§')` |
| Review sign color tests (`ShopSignStockColorDesyncOnLoadTest`) | Signs use `ChatColor.translateAlternateColorCodes` (Bukkit API, acceptable) |
| Add tests for `sendMessage(Component)` path | Cover both success and fallback (String) paths |

### Phase 5: Documentation Updates

| Task | Details |
|------|---------|
| Update `PROJECT_BRIEF.md` current state | Mark migration as complete, note remaining items |
| Add DECISIONS.md or record key decisions | Component serialization strategy, sign line approach |
| Document TODO items for future upgrades | SpigotMC API migration, newer Adventure versions |

## Acceptance Criteria for Stable Build

- [ ] CI build passes (all 151 tests pass, GitHub Actions JDK 25 Maven)
- [ ] No `net.md_5.bungee.api.chat.*` imports remain
- [ ] No `player.spigot().sendMessage()` calls remain
- [ ] All Spigot deps either removed or have TODO doc strings
- [ ] Changes committed and pushed to GitHub PR branch

## Next Immediate Action

Wait for CI run triggered by push `8a9e12e` to confirm test failures are resolved. If tests pass, proceed to Phase 1 (Folia verification). If tests still fail, investigate remaining failure modes (sign color, message queue timing).
# Shop Migration - Sprint Plan

## Goal

Stable build of Shop plugin migrated from Spigot to Paper MC, supporting MC 26.2+ with Folia and DivineMC, no NMS modules, minimal Spigot deps, CI via GitHub Actions.

## Context

- Relevant request: User wants full Spigot-to-Paper migration with MC 26.2+/Folia/DivineMC support
- Repository: TheFlood424K/Shop, branch: theflood424k-studious-carnival
- Important constraints: No local compilation; CI handles builds; Spigot deps only when necessary with TODO comments

## In Scope

1. Migrate ShopMessage.java — replace all Bungee chat with Adventure Component
2. Migrate ItemNameUtil.java — return Component instead of TextComponent
3. Migrate ItemHoverEventHelper.java — replace bungee HoverEvent with Adventure HoverEvent
4. Migrate UpdateChecker.java — replace player.spigot().sendMessage(), add TODO for SpigotMC API
5. Migrate ShopGuiHandler.java — replace ChatColor with Adventure equivalents
6. Migrate AbstractShop.java — replace ChatColor with Adventure equivalents
7. Clean up MiscListener.java — remove unused TextComponent import
8. Verify pom.xml and CI workflows align with Paper 26.2/Java 25/Folia/DivineMC
9. Commit changes and push to GitHub PR branch
10. Create migration plan for remaining work after stable build

## Out of Scope

- NMS modules
- Full migration beyond stability
- Local compilation

## Tasks

1. [ ] ShopMessage.java: imports, registerPlaceholder, replacePlaceholder, format, formatMiniMessage
2. [ ] ShopMessage.java: componentFromLegacy, textComponentFromComponent, embedItem, hover events
3. [ ] ShopMessage.java: all placeholder registrations (new TextComponent → Component.empty())
4. [ ] ShopMessage.java: sendMessage, sendMiniMessage (player.spigot().sendMessage → player.sendMessage)
5. [ ] ShopMessage.java: formatMessage methods (ChatColor → LegacyComponentSerializer)
6. [ ] ItemNameUtil.java: TextComponent → Component
7. [ ] ItemHoverEventHelper.java: bungee HoverEvent → Adventure HoverEvent
8. [ ] UpdateChecker.java: player.spigot().sendMessage → player.sendMessage, TODO comment
9. [ ] ShopGuiHandler.java: ChatColor → Adventure
10. [ ] AbstractShop.java: ChatColor → Adventure
11. [ ] MiscListener.java: remove unused import
12. [ ] Verify pom.xml and CI workflows
13. [ ] Commit and push to GitHub PR branch
14. [ ] Create migration plan for remaining work

## Acceptance Criteria

- [ ] CI build passes (GitHub Actions JDK 25 Maven)
- [ ] No `net.md_5.bungee.api.chat.*` imports remain in source
- [ ] No `player.spigot().sendMessage()` calls remain
- [ ] All `TextComponent`/`BaseComponent` replaced with `Component`
- [ ] All `ChatColor.translateAlternateColorCodes` replaced with `LegacyComponentSerializer`
- [ ] Spigot deps used only when necessary, with TODO doc strings
- [ ] Changes committed and pushed to GitHub PR branch

## Verification

- Automated: GitHub Actions build workflow (JDK 25, Maven)
- Manual: N/A (no local compilation permitted)
- Independent review: optional — code review after CI passes
- QA: optional — behavioral verification after stable build

## PR Checklist

- Branch name follows repo policy
- Description: short summary, why, acceptance criteria
- Verification steps included
- Tests added or existing tests updated
- PROJECT_BRIEF updated if behavior changed
- Required checks: CI build must pass

## Risks and Decisions

- Risk: Adventure Component API differs from Bungee TextComponent (no toLegacyText, no addExtra in same way) — mitigated by using LegacyComponentSerializer for serialization
- Decision: Use LegacyComponentSerializer to convert between Adventure Component and legacy §-colored strings where Bukkit API still expects strings (signs, etc.)
- Decision: Keep Bukkit API (org.bukkit) where it's not Spigot-specific — only replace net.md_5.bungee classes

## QA risk matrix

- High impact × High uncertainty = QA required (message formatting changes affect all player messages)
- QA recommended after CI build passes

## Next Action

Continue ShopMessage.java migration — replace all remaining TextComponent/BaseComponent with Component, update all placeholder registrations, fix hover events and sendMessage methods.

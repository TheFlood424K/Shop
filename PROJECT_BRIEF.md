# PROJECT_BRIEF.md - Shop Minecraft Plugin

> Last updated: migration session

## 1. Goal and Users

Migrate the Shop Minecraft plugin from Spigot to Paper MC. Fully support Minecraft 26.2+ as well as Folia and DivineMC. Avoid NMS modules; minimize Spigot dependencies (with TODO comments where used). Update outdated dependencies and fix breaking changes. Primary goal is stability and plugin functionality. Establish GitHub Actions CI.

## 2. Current Scope

**In scope**
- Replace Bungee chat classes (`net.md_5.bungee.api.chat.*`) with Adventure (`net.kyori.adventure.text.Component`) equivalents
- Replace `player.spigot().sendMessage()` with `player.sendMessage(Component)`
- Replace `ChatColor.translateAlternateColorCodes` with `LegacyComponentSerializer`
- Migrate ShopMessage.java, ItemNameUtil.java, ItemHoverEventHelper.java, UpdateChecker.java, ShopGuiHandler.java, AbstractShop.java, MiscListener.java
- Update pom.xml and CI workflows as needed
- Commit changes periodically and push to GitHub PR branch
- Create migration plan for remaining work after stable build

**Out of scope**
- NMS modules
- Full migration to latest APIs beyond stability (can be done later)
- Local compilation (CI handles builds)

## 3. Stack and Architecture

- Runtime/language: Java 25, Maven
- Frameworks/libraries: Paper API 26.2, FoliaLib 0.5.2, Adventure BOM 5.2.0
- Data/services: YAML configs (chatConfig.yml, signConfig.yml, displayConfig.yml)
- Deployment: GitHub Actions CI (JDK 25, Temurin, Maven)
- Tests/checks: CI build validation

Architecture: Maven multi-module (parent pom + core + dist). Core contains all plugin source.

## 4. Key Files

| Area | Path | Purpose |
|---|---|---|
| Message formatting | core/src/main/java/com/snowgears/shop/util/ShopMessage.java | Central message formatting; heavy Bungee/Adventure migration |
| Item names | core/src/main/java/com/snowgears/shop/util/ItemNameUtil.java | Returns TextComponent, mixes Adventure with bungee ChatColor |
| Item hover | core/src/main/java/com/snowgears/shop/util/ItemHoverEventHelper.java | Uses bungee HoverEvent/Item/Content classes |
| Update checker | core/src/main/java/com/snowgears/shop/util/UpdateChecker.java | player.spigot().sendMessage, SpigotMC API URL |
| GUI handler | core/src/main/java/com/snowgears/shop/gui/ShopGuiHandler.java | ChatColor.translateAlternateColorCodes usage |
| Abstract shop | core/src/main/java/com/snowgears/shop/shop/AbstractShop.java | ChatColor usage for sign lines |
| Misc listener | core/src/main/java/com/snowgears/shop/listener/MiscListener.java | Unused TextComponent import to remove |
| Dependencies | core/pom.xml | Paper API, FoliaLib, Adventure BOM versions |
| CI | .github/workflows/build.yml | JDK 25 Maven build |
| Plugin config | core/src/main/resources/plugin.yml | Already Paper-native (api-version 26.2, folia-supported true) |

## 5. How to Work

- Setup: No local setup needed (CI handles builds)
- Run: `mvn clean package` (via GitHub Actions)
- Test: GitHub Actions build workflow
- Deploy: Not applicable (plugin JAR)
- Repository rules: No local compilation; commit and push periodically

## 6. Safety and Constraints

- No local compilation permitted — CI (GitHub Actions) validates builds
- Only use Spigot dependencies when absolutely necessary, with TODO doc strings
- Prioritize newer, more up-to-date APIs where there is a choice
- Stability and plugin functionality over full migration

## 7. Current State

**Working**
- Project already uses Paper API 26.2, Java 25, FoliaLib 0.5.2
- plugin.yml already Paper-native (api-version 26.2, folia-supported: true)
- ShopMessage.java imports updated (Bungee `*` import removed, Adventure imports added)

**Known issues**
- 443 `org.bukkit` imports across 72 files (most are Bukkit API, not Spigot — acceptable)
- Bungee chat classes still used in ShopMessage.java, ItemNameUtil.java, ItemHoverEventHelper.java, UpdateChecker.java
- `player.spigot().sendMessage()` still used in multiple files
- `ChatColor.translateAlternateColorCodes` still used in multiple files

**Next**
- Complete ShopMessage.java migration (replacePlaceholder, format, formatMiniMessage, componentFromLegacy, textComponentFromComponent, embedItem, hover events, all placeholders)
- Migrate ItemNameUtil.java, ItemHoverEventHelper.java, UpdateChecker.java, ShopGuiHandler.java, AbstractShop.java, MiscListener.java
- Verify CI build passes
- Commit and push changes
- Create migration plan for remaining work

## 8. Team and Handoff

- Producer: scope, coordination, and merge
- Dev: implementation and verification
- QA: optional independent behavioral verification

Onboarding checklist (add to brief):
- Setup verified: CI build via GitHub Actions
- Tests run: CI build workflow
- Lint pass: CI build workflow
- Where to find CI logs: GitHub Actions tab

Where to record decisions:
- Add short entries to DECISIONS.md or create an issue with the decision tag.

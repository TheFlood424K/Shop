# Contributing to Shop v2.0

Thank you for contributing to Shop, the chest-based shop plugin for Minecraft Java Edition 26.2. This guide covers the Java 25 and Maven workflow used by the v2.0 codebase.

## Support Baseline

All contributions must preserve the current compatibility target unless the pull request explicitly updates that target:

- PaperMC **26.2+**
- DivineMC **26.2–26.3**
- Folia
- Java **25**
- Kyori Adventure Components for user-facing text
- VaultUnlocked for the virtual economy provider
- GriefPrevention3D for claim protection
- GeyserMC/Floodgate for Bedrock-connected players

Do not reintroduce NMS modules, `org.spigotmc.*` imports, Bungee chat components, or `player.spigot().sendMessage()` calls.

## Prerequisites

Install:

- **JDK 25** — [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25), [Oracle JDK](https://www.oracle.com/java/technologies/downloads/#java25), or another Java 25 distribution
- **Maven 3.9+** — [Apache Maven downloads](https://maven.apache.org/download.cgi)
- **Git**

The repository includes a Maven wrapper (`mvnw` and `mvnw.cmd`), so a separate Maven installation is optional. The wrapper is the most reproducible way to build the project.

Verify your environment:

```powershell
java -version
.\mvnw.cmd --version
```

`JAVA_HOME` must point to a Java 25 installation. Paper API and the required integration APIs resolve from their configured Maven repositories; a local Spigot server or BuildTools checkout is not needed.

## Getting Started

1. Fork `TheFlood424K/Shop` on GitHub.
2. Clone your fork:

   ```powershell
   git clone https://github.com/<your-user>/Shop.git
   Set-Location Shop
   ```

3. Add the upstream repository if it is not already configured:

   ```powershell
   git remote add upstream https://github.com/TheFlood424K/Shop.git
   git fetch upstream
   ```

4. Create a focused branch from the current upstream base:

   ```powershell
   git switch -c feature/short-description
   ```

## Project Layout

| Module | Contents |
| --- | --- |
| `pom.xml` | Parent build, dependency management, Java 25 release settings, and project revision |
| `core/` | Plugin implementation, resources, hooks, listeners, and tests |
| `dist/` | Distribution assembly for `Shop-{version}.jar` |
| `wiki/` | User-facing documentation |
| `.github/workflows/` | CI build, test, artifact, and release automation |

Core code lives under `core/src/main/java/com/theflood424k/shoprevanced/`. Tests live under `core/src/test/java/com/theflood424k/shoprevanced/`.

## Building and Testing

Run commands from the repository root.

### Compile

```powershell
.\mvnw.cmd clean compile
```

### Run the complete test suite

```powershell
.\mvnw.cmd test
```

Add focused tests for new behavior and regression coverage for fixes. Report the exact test command and result; do not rely on a hard-coded test count because the suite evolves.

### Build the distribution

```powershell
.\mvnw.cmd clean package
```

The installable JAR is written to `target/Shop-{version}.jar`.

If a local Maven installation is preferred, replace `.\mvnw.cmd` with `mvn` in all commands.

### Skip tests for a local iteration build

Use this only when tests are not needed for the current iteration:

```powershell
.\mvnw.cmd clean package -DskipTests
```

A final submission must still pass the normal test command.

### Coverage report

```powershell
.\mvnw.cmd -Pcoverage-report verify
```

### Clean generated output

```powershell
.\mvnw.cmd clean
```

## Coding Guidelines

- Target Java 25 and PaperMC 26.2 APIs.
- Keep optional integrations optional and detect their plugins at runtime.
- Use Adventure `Component` objects for player-facing messages.
- Preserve legacy `&` formatting only where it remains an intentional compatibility feature.
- Render placeholder substitutions as literal text or literal Adventure `Component` segments; never feed placeholder content into MiniMessage source.
- Use Folia-safe scheduling for world, block, sign, and region-sensitive operations. If player and shop/container inventories may have different region owners, use explicit scheduler handoffs for each owner.
- Use thread-safe collections for mutable data shared between scheduler contexts, and acquire transaction guards atomically with guaranteed release on every path.
- Keep database and network work off region-owned server threads where practical.
- Do not add hard dependencies for integrations that can remain soft dependencies.
- Follow the surrounding code style and keep changes focused.
- Never commit credentials, tokens, production databases, or private server logs.

## Integration Guidelines

When changing an integration:

1. Confirm the modern dependency is already present in `core/pom.xml`; do not add an obsolete API.
2. Keep plugin detection in one clearly defined location.
3. Align runtime detection, `plugin.yml` soft-dependency metadata, configuration keys and defaults, listener registration/unregistration, and absent-plugin fallback as one contract.
4. Handle the plugin being absent without failing Shop startup.
5. Cover the integration's important behavior with a focused test.
6. Update `wiki/config.md` and any integration-specific wiki page.

VaultUnlocked retains the `VAULT` value in the `currency.type` configuration for compatibility; do not rename it without a migration.

## Versioning and Releases

Shop uses [semantic versioning](https://semver.org/) and release tags of the form `vMAJOR.MINOR.PATCH`.

- **MAJOR:** breaking changes or a complete overhaul
- **MINOR:** backwards-compatible features
- **PATCH:** backwards-compatible bug fixes

**v2.0** is the breaking modernization overhaul for PaperMC 26.2, Java 25, Folia, and the refreshed integrations.

The root `pom.xml` `<revision>` property controls the generated artifact version. When preparing a release:

1. Set `<revision>` to the intended semantic version without the leading `v`.
2. Merge the version change to the default branch and wait for **Build and Package** to succeed.
3. Run the **Release** workflow from the Actions tab and enter the same semantic version.
4. The workflow selects the latest successful default-branch artifact, verifies that it matches the requested version, and creates a matching annotated Git tag.
5. The workflow generates `CHANGELOG.md` from commits since the previous release tag and publishes the JAR and changelog with the GitHub release.

Do not push release tags manually. The workflow rejects a version that does not match `<revision>`, and it refuses to overwrite an existing tag or release. Update version-specific documentation before publishing.

Do not use a patch release for a breaking configuration or API change.

## Contribution Process

1. Keep one logical change per pull request.
2. Add or update tests for behavior that changed.
3. Update the relevant documentation.
4. Run the complete test suite and packaging build.
5. Review `git diff` for accidental generated files or unrelated changes.
6. Commit with a clear message, for example:

   ```powershell
   git add <changed-files>
   git commit -m "Fix: prevent duplicate shop transaction messages"
   ```

7. Push the branch and open a pull request against `main`.

## Agent-Assisted Contributions

When an agent delegates part of a task to a subagent, use only a model confirmed in the active local custom provider's catalog. Never use or fall back to a GPT/OpenAI model. If no eligible model can be confirmed, continue without delegation.

## Pull Request Checklist

Use `.github/pull_request_template.md` as the authoritative pull-request entry point. The summary should confirm:

- [ ] The change is scoped to one issue or feature.
- [ ] Java 25 and PaperMC 26.2+ compatibility was reviewed, including Folia and DivineMC impacts where relevant.
- [ ] Folia-sensitive work uses region- or entity-owned scheduling; cross-region inventory work has an explicit scheduler handoff.
- [ ] Transaction guards and shared mutable state are concurrency-safe.
- [ ] User-facing text uses Adventure `Component`, and placeholder values are rendered literally rather than parsed as MiniMessage.
- [ ] Changed optional integrations retain metadata, configuration, detection, and listener-lifecycle parity.
- [ ] No legacy Spigot/Bungee APIs, NMS, or version-specific internals were introduced.
- [ ] New or changed behavior has focused tests, and any Folia limitation is stated accurately.
- [ ] `.\mvnw.cmd test` passes.
- [ ] `.\mvnw.cmd clean package` passes, or the reason it was not run is documented.
- [ ] Configuration, plugin metadata, dependency, migration, and wiki documentation are current.
- [ ] The pull request explains compatibility, migration, and release impact.
- [ ] No secrets, generated artifacts, production data, private logs, or unrelated files are included.

## Reporting Bugs

Open an issue in [TheFlood424K/Shop](https://github.com/TheFlood424K/Shop/issues) and include:

- Shop version and Java version
- Server software and exact version
- Relevant `config.yml` sections with secrets removed
- Steps to reproduce
- Full relevant stack trace or log excerpt
- Expected and actual behavior

For issues that also affect the original upstream plugin, the maintainers may coordinate with [snowgears/Shop](https://github.com/snowgears/Shop/issues).

Thank you for helping make Shop v2.0 stable, modern, and useful on PaperMC, DivineMC, Folia, and GeyserMC/Floodgate deployments.

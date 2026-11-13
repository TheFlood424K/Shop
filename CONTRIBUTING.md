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
   git remote add upstream https://github.com/snowgears/Shop.git
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

Core code lives under `core/src/main/java/com/snowgears/shop/`. Tests live under `core/src/test/`.

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

The current v2.0 baseline contains **165 passing tests**. Add focused tests for new behavior and regression coverage for fixes.

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
- Treat placeholder substitutions as plain text, not MiniMessage input.
- Use Folia-safe scheduling for world, block, sign, and region-sensitive operations.
- Use thread-safe collections for mutable data shared between scheduler contexts.
- Keep database and network work off region-owned server threads where practical.
- Do not add hard dependencies for integrations that can remain soft dependencies.
- Follow the surrounding code style and keep changes focused.
- Never commit credentials, tokens, production databases, or private server logs.

## Integration Guidelines

When changing an integration:

1. Confirm the modern dependency is already present in `core/pom.xml`; do not add an obsolete API.
2. Keep plugin detection in one clearly defined location.
3. Respect the matching `enabled` option in `config.yml`.
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

1. Set `<revision>` to the intended release version.
2. Run `.\mvnw.cmd clean package` and confirm all tests pass.
3. Create and push a matching annotated Git tag.
4. Let the tag workflow publish the release artifact.
5. Update release notes and version-specific documentation.

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

## Pull Request Checklist

- [ ] The change is scoped to one issue or feature.
- [ ] Java 25 and PaperMC 26.2 remain compatible.
- [ ] Folia-safe scheduling and thread safety were considered.
- [ ] No legacy Spigot/Bungee APIs were reintroduced.
- [ ] New or changed behavior has focused tests.
- [ ] `.\mvnw.cmd test` passes.
- [ ] `.\mvnw.cmd clean package` passes.
- [ ] Configuration and wiki documentation are current.
- [ ] The pull request explains compatibility and migration impact.
- [ ] No secrets, generated artifacts, or unrelated files are included.

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

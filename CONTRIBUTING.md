# Contributing to Shop

Thank you for your interest in contributing to Shop, the intuitive shop plugin for Minecraft! This guide will help you set up your development environment and understand our contribution process.

## Getting Started

### Prerequisites

Before you begin, ensure you have the following requirements installed:

- **Paper 26.2+**: Shop targets Paper 26.2 and newer, including Folia and DivineMC 26.2–26.3 deployments.
- **JDK 25**: Ensure you have JDK 25 installed. You can download it from the [Oracle website](https://www.oracle.com/java/technologies/downloads/#java25)
- **Maven**: Verify Maven is installed. You can download it from the [Maven website](https://maven.apache.org/download.cgi)

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub.
2. **Clone your fork** to your local machine:
   ```
   git clone https://github.com/snowgears/Shop.git
   cd Shop
   ```

## Building the Project

Paper API dependencies are resolved directly from Maven, so no local Spigot or BuildTools repository is required.

### Build Environment Setup

Building requires JDK 25 and Maven. Ensure `JAVA_HOME` points to your JDK 25 installation. Paper API dependencies are
resolved directly from the Paper Maven repository; no Spigot server or BuildTools checkout is needed.

If an optional integration API cannot be resolved from its upstream repository, install it into your local Maven
repository as described by that integration's documentation, then rerun the build.

### Compiling the Plugin

From the repository root, use Maven to compile and test the plugin:

```powershell
mvn clean package
```

To skip tests while building the plugin:

```powershell
mvn clean package -DskipTests
```

The compiled plugin will be stored in the `target` directory with the filename `Shop-{version}.jar`.

If you need to update the version:
1. Update `revision` in the root `pom.xml`.
2. Run `mvn clean package`.
3. The plugin will be built to `target/Shop-{version}.jar`.

## Versioning Guidelines

This project follows [semantic versioning](https://semver.org/) with the format: `breaking.feature.bugfix`

- `breaking`: Changes that are not backwards compatible, or complete overhaul of plugin
- `feature`: Addition of new/reworked features, or significantly refactored code
- `bugfix`: Bug was fixed

Examples:
- `v1.x.x` → `v2.x.x`: Backwards incompatible changes
- `v1.1.x` → `v1.2.x`: New Minecraft update or backwards compatible feature added
- `v1.1.0` → `v1.1.1`: Bug was fixed

## Contribution Process

1. **Create a new branch** for your feature or bugfix:
   ```
   git checkout -b feature/your-feature-name
   ```
   or
   ```
   git checkout -b fix/your-bugfix-name
   ```

2. **Make your changes**, following the code style of the project.

3. **Test your changes** thoroughly.

4. **Commit your changes** with a clear and descriptive commit message:
   ```
   git commit -m "Add feature: your feature description"
   ```
   or
   ```
   git commit -m "Fix: your bugfix description"
   ```

5. **Push your branch** to your fork:
   ```
   git push origin feature/your-feature-name
   ```

6. **Create a pull request** against the main repository's master branch.

## Pull Request Guidelines

- Provide a clear description of what your changes accomplish
- Reference any related issues in your pull request description
- Ensure all tests pass
- Update documentation if necessary

Thank you for contributing to Shop! 
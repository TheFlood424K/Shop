# Contributing to Shop

Thank you for your interest in contributing to Shop, the intuitive shop plugin for Minecraft! This guide will help you set up your development environment and understand our contribution process.

## Getting Started

### Prerequisites

Before you begin, ensure you have the following requirements installed:

- **Docker**: Install Docker from the [official website](https://www.docker.com/get-started)
- **JDK 25**: Ensure you have JDK 25 installed. You can download it from the [Adoptium website](https://adoptium.net/temurin/releases/?version=25)
- **Maven**: Verify Maven is installed. You can download it from the [Maven website](https://maven.apache.org/download.cgi)

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub.
2. **Clone your fork** to your local machine:
   ```
   git clone https://github.com/snowgears/Shop.git
   cd Shop
   ```

## Building the Project

### Building the Local Maven Repository

Before compiling the plugin, you need to set up the local Maven repository with the supported versions of Spigot:

1. Navigate to the root directory of the repository.
2. Run the script to build the local Maven repository:
   ```shell
   # Pull the latest image from Docker Hub and extract the Maven repo from it
   ./buildMavenRepo.sh
   
   # Alternatively, you can build the image locally on your machine, it just might take an hour or two
   # Useful if you add a new version to the Dockerfile
   ./buildMavenRepo.sh local
   ```

This script will compile all supported versions of Spigot and copy the Maven repository into the Shop folder for later use.

### Build Environment Setup

Building also requires a JDK 25 Maven toolchain and two integration APIs
(Dynmap and BlockProt) that are not reliably resolvable from their upstream Maven/JitPack repositories. The
`compile.sh` and `runDev.sh` scripts handle all of this automatically by invoking `scripts/setup-build-env.sh`,
which registers the toolchain and installs those APIs from their stable GitHub Releases (each step is a no-op once
satisfied). Ensure `JAVA_HOME` points at your JDK 25 installation before running them. To run the setup on its own:

```shell
./scripts/setup-build-env.sh
```

### Compiling the Plugin

After setting up the local Maven repository, you can compile the Shop plugin:

1. Navigate to the root directory of the repository.
2. Run the script to compile the plugin:
   ```shell
   ./compile.sh
   ```

The compiled plugin will be stored in the `target` directory with the filename `shop-{version}.jar`.

If you need to update the version:
1. Update the version in `/pom.xml`
2. Run `./compile.sh`
3. The plugin will be built to `/target/shop-{version}.jar`

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
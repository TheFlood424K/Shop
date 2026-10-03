#!/bin/bash

# build-spigot-maven.sh
# Native Spigot BuildTools runner for GitHub Actions CI
# Builds Spigot/Bukkit artifacts and installs them to ~/.m2/repository
# Idempotent: skips versions already present in the local Maven repository

set -euo pipefail

# Configuration
BUILDTOOLS_URL="https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
BUILDTOOLS_JAR="$HOME/BuildTools.jar"
MAVEN_REPO="$HOME/.m2/repository"
MEMORY="-Xmx2G"

# Version mappings: JDK version -> list of Minecraft versions
# JDK 13: 1.14.4, 1.15.2, 1.16.1, 1.16.3, 1.16.5
# JDK 17: 1.17.1, 1.18, 1.18.2, 1.19, 1.19.3, 1.19.4, 1.20, 1.20.2, 1.20.4
# JDK 21: 1.20.6, 1.21, 1.21.1

declare -A JDK_VERSIONS=(
    ["13"]="1.14.4 1.15.2 1.16.1 1.16.3 1.16.5"
    ["17"]="1.17.1 1.18 1.18.2 1.19 1.19.3 1.19.4 1.20 1.20.2 1.20.4"
    ["21"]="1.20.6 1.21 1.21.1"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# Check if a version is already installed in ~/.m2/repository
# Checks for spigot-<version>.jar and bukkit-<version>.jar
version_installed() {
    local version="$1"
    local spigot_jar="$MAVEN_REPO/org/spigotmc/spigot/$version/spigot-$version.jar"
    local bukkit_jar="$MAVEN_REPO/org/bukkit/bukkit/$version/bukkit-$version.jar"

    # Check if both artifacts exist
    if [[ -f "$spigot_jar" && -f "$bukkit_jar" ]]; then
        return 0
    fi
    return 1
}

# Download BuildTools.jar if not present
download_buildtools() {
    if [[ -f "$BUILDTOOLS_JAR" ]]; then
        log_info "BuildTools.jar already exists at $BUILDTOOLS_JAR"
        return 0
    fi

    log_info "Downloading BuildTools.jar from $BUILDTOOLS_URL"
    if curl -fL -o "$BUILDTOOLS_JAR" "$BUILDTOOLS_URL"; then
        log_success "BuildTools.jar downloaded successfully"
    else
        log_error "Failed to download BuildTools.jar"
        exit 1
    fi
}

# Run BuildTools for a specific version with a specific JDK
run_buildtools() {
    local jdk_version="$1"
    local mc_version="$2"
    local remapped_flag=""

    # Use --remapped for 1.17+
    if [[ "$mc_version" =~ ^1\.1[7-9]\. ]] || [[ "$mc_version" =~ ^1\.[2-9][0-9]*\. ]] || [[ "$mc_version" =~ ^1\.[1-9][0-9]+\. ]]; then
        # More precise: 1.17 and above
        local major_minor="${mc_version%.*}" # e.g., 1.17, 1.18, 1.20
        local minor="${major_minor##*.}"
        if [[ $minor -ge 17 ]]; then
            remapped_flag="--remapped"
        fi
    fi

    log_info "Building Minecraft $mc_version with JDK $jdk_version $remapped_flag"

    # Check if already installed
    if version_installed "$mc_version"; then
        log_success "Version $mc_version already installed in ~/.m2/repository, skipping"
        return 0
    fi

    # Run BuildTools
    # --rev <version> specifies the Minecraft version
    # --output-dir specifies where to put the built jars (but we want them in ~/.m2)
    # BuildTools installs to ~/.m2/repository by default

    local cmd=(java $MEMORY -jar "$BUILDTOOLS_JAR" --rev "$mc_version")

    if [[ -n "$remapped_flag" ]]; then
        cmd+=("$remapped_flag")
    fi

    log_info "Running: ${cmd[*]}"

    if "${cmd[@]}"; then
        log_success "Successfully built $mc_version with JDK $jdk_version"

        # Verify installation
        if version_installed "$mc_version"; then
            log_success "Verified: $mc_version artifacts installed in ~/.m2/repository"
        else
            log_warn "Build completed but artifacts not found in ~/.m2/repository for $mc_version"
        fi
    else
        log_error "Failed to build $mc_version with JDK $jdk_version"
        return 1
    fi
}

# Main execution
main() {
    log_info "Starting Spigot BuildTools native build"
    log_info "Maven repository: $MAVEN_REPO"
    log_info "BuildTools jar: $BUILDTOOLS_JAR"

    # Ensure Maven repository directory exists
    mkdir -p "$MAVEN_REPO"

    # Download BuildTools
    download_buildtools

    # Track results
    local total=0
    local success=0
    local skipped=0
    local failed=0
    local failed_versions=()

    # Process each JDK version and its associated Minecraft versions
    for jdk in 13 17 21; do
        log_info "=== Processing JDK $jdk ==="

        # Set JAVA_HOME for the specific JDK (GitHub Actions setup-java puts them in standard locations)
        # On ubuntu-latest with setup-java, JDKs are typically at:
        # /usr/lib/jvm/temurin-13-jdk, /usr/lib/jvm/temurin-17-jdk, /usr/lib/jvm/temurin-21-jdk
        # Or we can rely on the PATH being set correctly by setup-java

        local jdk_path="/usr/lib/jvm/temurin-${jdk}-jdk"
        if [[ -d "$jdk_path" ]]; then
            export JAVA_HOME="$jdk_path"
            export PATH="$JAVA_HOME/bin:$PATH"
            log_info "Using JDK $jdk at $JAVA_HOME"
        else
            # Try alternative locations
            for alt in "/usr/lib/jvm/java-${jdk}-temurin" "/usr/lib/jvm/java-${jdk}-openjdk" "/opt/java/jdk-${jdk}"; do
                if [[ -d "$alt" ]]; then
                    export JAVA_HOME="$alt"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    log_info "Using JDK $jdk at $JAVA_HOME (alternative)"
                    break
                fi
            done
        fi

        # Verify Java version
        java -version 2>&1 | head -1

        # Process each version for this JDK
        for version in ${JDK_VERSIONS[$jdk]}; do
            ((total++))
            if run_buildtools "$jdk" "$version"; then
                if version_installed "$version"; then
                    ((success++))
                else
                    ((skipped++))
                fi
            else
                ((failed++))
                failed_versions+=("$version (JDK $jdk)")
            fi
        done
    done

    # Summary
    log_info "=== Build Summary ==="
    log_info "Total versions: $total"
    log_success "Successful: $success"
    log_warn "Skipped (already installed): $skipped"
    if [[ $failed -gt 0 ]]; then
        log_error "Failed: $failed"
        for fv in "${failed_versions[@]}"; do
            log_error "  - $fv"
        done
        exit 1
    else
        log_success "All versions built successfully!"
    fi
}

# Run main
main "$@"
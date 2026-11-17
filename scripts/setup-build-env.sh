#!/bin/sh
set -e

# Prepares the local environment for building Shop. Safe to run repeatedly; every step is a no-op once satisfied.
#   1. Registers a JDK 25 Maven toolchain. MockBukkit 4 (v26.2) requires tests to run on JDK 25, while the
#      plugin itself can target an older release level (see maven-toolchains-plugin in shop-core/pom.xml).
#   2. Installs the integration APIs that are not reliably resolvable from their upstream Maven/JitPack repos
#      (Dynmap, BlockProt) from their stable GitHub Releases. See the respective install scripts for details.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Discover and verify a real JDK 25 home. We verify every candidate's reported version because
# helpers like `/usr/libexec/java_home -v 25` can return a fallback JDK when no 25 is registered.
is_jdk25() {
  [ -n "$1" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -Eq 'version "25(\.|")'
}

JDK25_HOME=""

# 1. Check current JAVA_HOME and GitHub Actions JAVA_HOME_25_X64
for env_candidate in "${JAVA_HOME:-}" "${JAVA_HOME_25_X64:-}"; do
  if is_jdk25 "${env_candidate}"; then
    JDK25_HOME="${env_candidate}"
    break
  fi
done

# 2. Check SDKMAN, Linux/Ubuntu JVMs, and Windows (Git Bash) install locations
if [ -z "${JDK25_HOME}" ]; then
  for candidate in \
    "${HOME}"/.sdkman/candidates/java/25* \
    /usr/lib/jvm/*25* \
    "/c/Program Files/Java/jdk-25"* \
    "/c/Program Files/Eclipse Adoptium/jdk-25"* \
    "/c/Program Files/BellSoft/LibericaJDK-25"*; do
    if is_jdk25 "${candidate}"; then
      JDK25_HOME="${candidate}"
      break
    fi
  done
fi

# 3. Check macOS java_home utility
if [ -z "${JDK25_HOME}" ] && [ -x /usr/libexec/java_home ]; then
  candidate="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
  if is_jdk25 "${candidate}"; then
    JDK25_HOME="${candidate}"
  fi
fi

# 4. Fallback: check `java` on PATH and resolve its home directory
if [ -z "${JDK25_HOME}" ] && command -v java >/dev/null 2>&1; then
  candidate="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java\.home/ {print $2}' | tr -d '\r')"
  if is_jdk25 "${candidate}"; then
    JDK25_HOME="${candidate}"
  fi
fi

if [ -z "${JDK25_HOME}" ]; then
  echo "ERROR: Could not find a JDK 25 installation (required to run tests with MockBukkit v26.2)." >&2
  echo "       Install one (e.g. 'sdk install java 25-tem') or set JAVA_HOME to a JDK 25." >&2
  exit 1
fi

echo "Using JDK 25 toolchain: ${JDK25_HOME}"

mkdir -p ~/.m2
cat > ~/.m2/toolchains.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>25</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>${JDK25_HOME}</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
EOF

"${SCRIPT_DIR}/install-dynmap-api.sh"
"${SCRIPT_DIR}/install-blockprot-api.sh"

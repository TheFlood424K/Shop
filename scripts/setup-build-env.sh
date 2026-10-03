#!/bin/sh
set -e

# Prepares the local environment for building Shop. Safe to run repeatedly; every step is a no-op once satisfied.
#   1. Registers a JDK 25 Maven toolchain. The plugin, its tests (MockBukkit 4) and Mockito's inline
#      mock maker all run on JDK 25; see the maven-toolchains-plugin config in core/pom.xml and the
#      ci job matrix, which pins Temurin 25.
#   2. Installs the integration APIs that are not reliably resolvable from their upstream Maven/JitPack repos
#      (Dynmap, BlockProt) from their stable GitHub Releases. See the respective install scripts for details.
#
# This mirrors .github/actions/setup-build-env, which does the same thing on CI. Keep the two in sync.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Discover and verify a real JDK 25 home. We can't simply trust $JAVA_HOME — it is often unset or points at
# a different major version. We verify every candidate's reported version because helpers like
# `/usr/libexec/java_home -v 25` can return an older JDK when no 25 is registered.
is_jdk25() {
  [ -n "$1" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q 'version "25'
}

JDK25_HOME=""
if is_jdk25 "${JAVA_HOME:-}"; then
  JDK25_HOME="${JAVA_HOME}"
fi
if [ -z "${JDK25_HOME}" ]; then
  for candidate in "${HOME}"/.sdkman/candidates/java/25*; do
    if is_jdk25 "${candidate}"; then JDK25_HOME="${candidate}"; break; fi
  done
fi
if [ -z "${JDK25_HOME}" ] && [ -x /usr/libexec/java_home ]; then
  candidate="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
  if is_jdk25 "${candidate}"; then JDK25_HOME="${candidate}"; fi
fi

if [ -z "${JDK25_HOME}" ]; then
  echo "ERROR: Could not find a JDK 25 installation (required to build and test the plugin)." >&2
  echo "       Install one (e.g. 'sdk install java 25.0.2-tem') or set JAVA_HOME to a JDK 25." >&2
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
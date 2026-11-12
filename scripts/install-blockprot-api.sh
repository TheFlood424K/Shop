#!/usr/bin/env bash
set -euo pipefail

# Installs the BlockProt API jar into the local Maven repository under the coordinates we use for compilation:
#   com.github.spnda:BlockProt:${BLOCKPROT_VERSION}
#
# Why this exists:
# - Our core module depends on BlockProt's API for compilation (optional runtime integration, scope=provided).
# - BlockProt is a multi-module Gradle project consumed via JitPack. JitPack only serves these coordinates while a
#   built artifact is cached; once the cache is evicted it returns 404 for every file ("Build ok" but no jar),
#   even though the version's git tag still appears in maven-metadata.xml. Fresh CI builds (empty ~/.m2) then fail
#   with "Could not resolve dependencies ... com.github.spnda:BlockProt:jar:<version>".
# - BlockProt publishes a self-contained, shaded "-all" jar on GitHub Releases, which is stable and reachable.
#
# This installs that jar as a "provided" compile-time dependency with a generated (dependency-less) POM. We only
# need the de.sean.blockprot.bukkit.* API classes to compile; the runtime plugin still supplies the real impl.

# The Maven coordinate version we want present locally (must match <blockprot.version> in shop-core/pom.xml).
BLOCKPROT_VERSION="${BLOCKPROT_VERSION:-1.2.6}"

GROUP_ID="com.github.spnda"
ARTIFACT_ID="BlockProt"
PACKAGING="jar"

# BlockProt GitHub releases use a bare "<version>" tag and ship the shaded jar "blockprot-spigot-<version>-all.jar".
DOWNLOAD_URL="https://github.com/spnda/BlockProt/releases/download/${BLOCKPROT_VERSION}/blockprot-spigot-${BLOCKPROT_VERSION}-all.jar"

LOCAL_REPO="${MAVEN_LOCAL_REPO:-$HOME/.m2/repository}"
DEST_DIR="${LOCAL_REPO}/${GROUP_ID//.//}/${ARTIFACT_ID}/${BLOCKPROT_VERSION}"
DEST_JAR="${DEST_DIR}/${ARTIFACT_ID}-${BLOCKPROT_VERSION}.jar"
DEST_POM="${DEST_DIR}/${ARTIFACT_ID}-${BLOCKPROT_VERSION}.pom"

if [ -f "${DEST_JAR}" ] && [ -f "${DEST_POM}" ]; then
  echo "BlockProt API already installed: ${DEST_JAR}"
  exit 0
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

jar_path="${tmp_dir}/blockprot-spigot-${BLOCKPROT_VERSION}-all.jar"

echo "Downloading BlockProt from: ${DOWNLOAD_URL}"
curl -fsSL --retry 3 --retry-delay 2 --max-time 120 -o "${jar_path}" "${DOWNLOAD_URL}"

mkdir -p "${DEST_DIR}"

echo "Installing into Maven local repo (${LOCAL_REPO}) as ${GROUP_ID}:${ARTIFACT_ID}:${BLOCKPROT_VERSION}"
mvn -B -q \
  -Dmaven.repo.local="${LOCAL_REPO}" \
  org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
  -DgroupId="${GROUP_ID}" \
  -DartifactId="${ARTIFACT_ID}" \
  -Dversion="${BLOCKPROT_VERSION}" \
  -Dpackaging="${PACKAGING}" \
  -Dfile="${jar_path}" \
  -DgeneratePom=true

echo "Installed: ${DEST_JAR}"

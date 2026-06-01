#!/usr/bin/env bash
set -euo pipefail

# Installs a Dynmap jar into the local Maven repository under the coordinates we use for compilation:
#   us.dynmap:dynmap-api:${DYNMAP_API_VERSION}
#
# Why this exists:
# - Our core module depends on Dynmap's API for compilation (optional runtime integration).
# - Some CI environments (notably certain GitLab runners / restricted networks) cannot reach Dynmap's Maven repo
#   (https://repo.mikeprimm.com), causing Maven builds (and dependency:go-offline) to fail.
# - Dynmap publishes downloadable jars on GitHub Releases, which are often reachable even when the Maven repo is not.
#
# This script fetches the Dynmap *spigot* jar from GitHub Releases and installs it as a "provided" compile-time
# dependency. We only need the API classes to compile; the runtime plugin still supplies the real implementation.

# The Maven coordinate version we want present locally (must match core/pom.xml).
DYNMAP_API_VERSION="${DYNMAP_API_VERSION:-3.2-beta-1}"

# Dynmap does not publish all dynmap-api versions as GitHub Release artifacts. When the Maven repo is unreachable,
# we install the API coordinates using a Dynmap *spigot* release jar that contains the required org.dynmap.* types.
# This value controls which GitHub Release tag/jar we download.
DYNMAP_RELEASE_VERSION="${DYNMAP_RELEASE_VERSION:-3.3-beta-2}"

GROUP_ID="us.dynmap"
ARTIFACT_ID="dynmap-api"
PACKAGING="jar"

# Dynmap GitHub releases use tag format "v<version>" and jar filename "Dynmap-<version>-spigot.jar"
DOWNLOAD_URL="https://github.com/webbukkit/dynmap/releases/download/v${DYNMAP_RELEASE_VERSION}/Dynmap-${DYNMAP_RELEASE_VERSION}-spigot.jar"

LOCAL_REPO="${MAVEN_LOCAL_REPO:-$HOME/.m2/repository}"
DEST_DIR="${LOCAL_REPO}/${GROUP_ID//.//}/${ARTIFACT_ID}/${DYNMAP_API_VERSION}"
DEST_JAR="${DEST_DIR}/${ARTIFACT_ID}-${DYNMAP_API_VERSION}.jar"

if [ -f "${DEST_JAR}" ]; then
  echo "Dynmap API already installed: ${DEST_JAR}"
  exit 0
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

jar_path="${tmp_dir}/Dynmap-${DYNMAP_RELEASE_VERSION}-spigot.jar"

echo "Downloading Dynmap from: ${DOWNLOAD_URL}"
curl -fsSL --retry 3 --retry-delay 2 --max-time 120 -o "${jar_path}" "${DOWNLOAD_URL}"

mkdir -p "${DEST_DIR}"

echo "Installing into Maven local repo (${LOCAL_REPO}) as ${GROUP_ID}:${ARTIFACT_ID}:${DYNMAP_API_VERSION}"
mvn -B -q \
  -Dmaven.repo.local="${LOCAL_REPO}" \
  -DgroupId="${GROUP_ID}" \
  -DartifactId="${ARTIFACT_ID}" \
  -Dversion="${DYNMAP_API_VERSION}" \
  -Dpackaging="${PACKAGING}" \
  -Dfile="${jar_path}" \
  -DgeneratePom=true \
  install:install-file

echo "Installed: ${DEST_JAR}"



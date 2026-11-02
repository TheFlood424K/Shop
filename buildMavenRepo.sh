#!/bin/bash
set -e

# Define variables
IMAGE_NAME="spigot-buildtools"
CONTAINER_NAME="spigot-builder"
LOCAL_DIR="./SpigotBuildTools"
SPIGOT_REV="${SPIGOT_REV:-26.2}"

if [ -d "${LOCAL_DIR}" ] && [ "$(ls -A "${LOCAL_DIR}")" ]; then
  echo "SpigotBuildTools directory already exists, skipping build..."
else
  mkdir -p "${LOCAL_DIR}"

  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true

  # Build locally to compile the requested Minecraft revision (26.2)
  echo "Building Docker image locally for Spigot ${SPIGOT_REV}..."
  docker build \
    --build-arg REV="${SPIGOT_REV}" \
    -t "${IMAGE_NAME}" \
    -f buildMavenRepo.Dockerfile .

  echo "Running Spigot BuildTools container..."
  docker run --name "${CONTAINER_NAME}" -e REV="${SPIGOT_REV}" "${IMAGE_NAME}"

  docker cp "${CONTAINER_NAME}:/root/.m2" "${LOCAL_DIR}"
  mv "${LOCAL_DIR}/.m2" "${LOCAL_DIR}/maven"

  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true

  echo "Build completed for Minecraft ${SPIGOT_REV} and files copied to ${LOCAL_DIR}."
fi

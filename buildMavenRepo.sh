#!/bin/bash
set -e

# Define variables
IMAGE_NAME="spigot-buildtools"
CONTAINER_NAME="spigot-builder"
LOCAL_DIR="./SpigotBuildTools"
DOCKERHUB_IMAGE="ostlerdev/spigot-maven:latest"
SPIGOT_REV="${SPIGOT_REV:-26.2}"

# Check if SpigotBuildTools directory already exists and has content
if [ -d "${LOCAL_DIR}" ] && [ "$(ls -A "${LOCAL_DIR}")" ]; then
  echo "SpigotBuildTools directory already exists, skipping build..."
else
  # Create local directory if it doesn't exist
  mkdir -p "${LOCAL_DIR}"

  # Safely clean up any leftover container if it exists
  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true

  # Determine whether to use local build or Docker Hub
  if [ "$1" == "local" ]; then
      docker build --cache-from "${DOCKERHUB_IMAGE}" -t "${IMAGE_NAME}" -f buildMavenRepo.Dockerfile .
  else
      docker pull "${DOCKERHUB_IMAGE}"
      IMAGE_NAME="${DOCKERHUB_IMAGE}"
  fi

  # Run the container in the foreground to stream logs live
  echo "Starting Spigot BuildTools container for revision ${SPIGOT_REV}..."
  # Run the container in the background and stream logs with a timeout/safety check
  docker run -d --name "${CONTAINER_NAME}" -e REV="${SPIGOT_REV}" "${IMAGE_NAME}"

  # Stream logs in the background, but stop after 10 minutes if truly stuck
  timeout 600 docker logs -f "${CONTAINER_NAME}" || true

  # If container is still running after timeout, dump last logs and inspect
  if [ "$(docker inspect -f '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null)" = "true" ]; then
    echo "ERROR: BuildTools timed out after 10 minutes. Last container logs:"
    docker logs "${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    exit 1
  fi

  # Copy the Maven repo from the docker image to the local directory
  docker cp "${CONTAINER_NAME}:/root/.m2" "${LOCAL_DIR}"
  mv "${LOCAL_DIR}/.m2" "${LOCAL_DIR}/maven"

  # Clean up the container
  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true

  echo "Build completed for Minecraft ${SPIGOT_REV} and files copied to ${LOCAL_DIR}."
fi

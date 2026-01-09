#!/bin/bash
set -e

# Configuration
REPO_USER="piryth"
TAG="latest"

# 1. Login
echo "Logging in to Docker Hub..."
docker login

# 2. Build and Push each stage
# Based on targets defined in your Dockerfile
STAGES=(
  "git-clone"
  "npm-lint"
  "npm-test"
  "npm-install"
  "sonarqube"
)

for STAGE in "${STAGES[@]}"; do
  IMAGE_NAME="${REPO_USER}/deployzilla-step-${STAGE}:${TAG}"

  echo "--- Building ${STAGE} as ${IMAGE_NAME} ---"

  # Build specific target from the shared Dockerfile
  docker build \
    --file src/main/resources/docker/Dockerfile \
    --target "${STAGE}" \
    --tag "${IMAGE_NAME}" \
    src/main/resources/docker

  echo "--- Pushing ${IMAGE_NAME} ---"
  docker push "${IMAGE_NAME}"
done

echo "Done!"
#!/bin/bash
set -e

# 1. Get the absolute path of the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 2. Calculate the project root (Assuming script is in the project root)
# If the script is in root: PROJECT_ROOT=$SCRIPT_DIR
PROJECT_ROOT="$SCRIPT_DIR"

# 3. Define the absolute path to the Docker context
DOCKER_CONTEXT="$PROJECT_ROOT/src/main/resources/docker"

# Check if context exists
if [ ! -d "$DOCKER_CONTEXT" ]; then
  echo "Error: Docker context not found at $DOCKER_CONTEXT"
  exit 1
fi

# Configuration
REPO_USER="piryth"
TAG="latest"
STAGES=("git-clone" "npm-lint" "npm-test" "npm-install" "sonarqube")

echo "Logging in to Docker Hub..."
docker login

for STAGE in "${STAGES[@]}"; do
  IMAGE_NAME="${REPO_USER}/deployzilla-step-${STAGE}:${TAG}"

  echo "--- Building ${STAGE} as ${IMAGE_NAME} ---"

  # 4. Use the absolute path for file and context
  docker build \
    --file "$DOCKER_CONTEXT/Dockerfile" \
    --target "${STAGE}" \
    --tag "${IMAGE_NAME}" \
    "$DOCKER_CONTEXT"

  echo "--- Pushing ${IMAGE_NAME} ---"
  docker push "${IMAGE_NAME}"
done

echo "Done!"
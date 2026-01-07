#!/bin/sh
set -e

# Default values
: "${GIT_BRANCH:=main}"
: "${TARGET_DIR:=.}"

if [ -z "$GIT_REPO" ]; then
    echo "Error: GIT_REPO environment variable is required"
    exit 1
fi

DEST_PATH="/workspace/$TARGET_DIR"

echo "Using GIT_REPO=$GIT_REPO"
echo "Using GIT_BRANCH=$GIT_BRANCH"
echo "Cloning to $DEST_PATH"

# Ensure target directory is clean or empty if it exists (basic safety)
if [ -d "$DEST_PATH" ] && [ "$(ls -A $DEST_PATH)" ]; then
   echo "Warning: Target directory $DEST_PATH is not empty."
fi

# Clone command
# Note: In a real scenario you might handle private keys here or assume they are mounted/configured via SSH config
git clone --depth 1 --branch "$GIT_BRANCH" "$GIT_REPO" "$DEST_PATH"

echo "Clone complete."

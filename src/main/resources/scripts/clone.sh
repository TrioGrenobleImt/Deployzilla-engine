#!/bin/bash

PROJECT_URL=""

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --project_url) PROJECT_URL="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [[ -z "$PROJECT_URL" ]]; then
    echo "Error: Missing argument."
    echo "Usage: $0 --project_url <url>"
    exit 1
fi

# 3. Extract project name from URL
# 'basename' extracts the last part of the path.
# The second argument ".git" tells it to strip that suffix if present.
PROJECT_NAME=$(basename "$PROJECT_URL" .git)

# Define the target directory
TARGET_DIR="$HOME/apps/$PROJECT_NAME"

# 4. Check if directory exists
if [ -d "$TARGET_DIR" ]; then
    echo "---------------------------------------------------"
    echo "⚠️  Directory already exists: $TARGET_DIR"
    echo "Abort: Repository was not cloned to avoid overwriting."
    echo "---------------------------------------------------"
    exit 0
fi

# 5. Clone the repository
echo "---------------------------------------------------"
echo "Project: $PROJECT_NAME"
echo "URL:     $PROJECT_URL"
echo "Target:  $TARGET_DIR"
echo "---------------------------------------------------"

# Ensure the parent directory exists
mkdir -p "$HOME/apps"

# Execute git clone
git clone "$PROJECT_URL" "$TARGET_DIR"

# Check if clone was successful
if [ $? -eq 0 ]; then
    echo "✅ Success: Repository cloned into $TARGET_DIR"
else
    echo "❌ Error: Git clone failed."
    exit 1
fi
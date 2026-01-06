figlet deployzilla

echo "Starting stage - cloning repository"

# Initialize variables
PROJECT_NAME=""
PROJECT_URL=""

# 1. Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --project_name) PROJECT_NAME="$2"; shift ;;
        --project_url) PROJECT_URL="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

# 2. Validate that arguments were provided
if [[ -z "$PROJECT_NAME" ]] || [[ -z "$PROJECT_URL" ]]; then
    echo "Error: Missing arguments."
    echo "Usage: $0 --project_name <name> --project_url <url>"
    exit 1
fi

# Define the target directory
TARGET_DIR="$HOME/apps/$PROJECT_NAME"

# 3. Check if directory exists
if [ -d "$TARGET_DIR" ]; then
    echo "---------------------------------------------------"
    echo "⚠️  Directory already exists: $TARGET_DIR"
    echo "Abort: Repository was not cloned to avoid overwriting."
    echo "---------------------------------------------------"
    exit 0
fi

# 4. Clone the repository
echo "---------------------------------------------------"
echo "Project: $PROJECT_NAME"
echo "URL:     $PROJECT_URL"
echo "Target:  $TARGET_DIR"
echo "---------------------------------------------------"

# Ensure the parent directory exists (~/apps)
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
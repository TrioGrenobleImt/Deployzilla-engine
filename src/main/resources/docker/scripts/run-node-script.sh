#!/bin/sh
set -e

COMMAND=$1

if [ -z "$COMMAND" ]; then
  echo "Error: No command provided."
  exit 1
fi

detect_pm() {
  if [ -f "pnpm-lock.yaml" ]; then
    PM="pnpm"
  elif [ -f "yarn.lock" ]; then
    PM="yarn"
  else
    PM="npm"
  fi
}

detect_pm

if [ "$COMMAND" = "install" ]; then
  echo "Installing dependencies using $PM..."
  corepack enable
  if [ "$PM" = "pnpm" ]; then
    pnpm install --frozen-lockfile
  elif [ "$PM" = "yarn" ]; then
    yarn install --frozen-lockfile
  else
    npm ci
  fi
else
  echo "Running script '$COMMAND' using $PM..."
  corepack enable

  ARGS=""
  if [ "$COMMAND" = "lint" ]; then
     # For npm we need '--' to pass flags to the script, for yarn/pnpm we can usually just append
     if [ "$PM" = "npm" ]; then
       ARGS="-- --cache"
     else
       ARGS="--cache"
     fi
  fi

  if [ "$PM" = "pnpm" ]; then
    pnpm run "$COMMAND" $ARGS
  elif [ "$PM" = "yarn" ]; then
    yarn run "$COMMAND" $ARGS
  else
    npm run "$COMMAND" $ARGS
  fi
fi

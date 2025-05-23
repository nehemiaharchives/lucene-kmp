#!/bin/bash

# This script is for coding agents such as GitHub Copilot Agent mode which is not meant to read output from the console.

if [ -f build_output.txt ]; then
  rm build_output.txt
fi
TASK=${1:-build} # Use the first argument as TASK, or default to 'build'
./gradlew $TASK --quiet --console=plain > build_output.txt 2>&1

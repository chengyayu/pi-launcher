#!/bin/bash
# Bootstrap script: makes sure the Gradle wrapper is usable.
#
# The wrapper jar is committed, so normally you do not need this at all.
# It only helps when the jar is missing (e.g. an export without binaries).

set -e
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    echo "Gradle wrapper already present."
    exit 0
fi

if ! command -v gradle >/dev/null 2>&1; then
    echo "gradle/wrapper/gradle-wrapper.jar is missing and no 'gradle' executable was found."
    echo "Either restore the jar from git, or install Gradle and run:"
    echo "    gradle wrapper --gradle-version 9.5.1"
    exit 1
fi

echo "Regenerating the Gradle wrapper..."
gradle wrapper --gradle-version 9.5.1
echo "Gradle wrapper ready. Run: ./gradlew build"

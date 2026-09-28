#!/bin/sh
# Builds the signed App Bundle for Google Play.
# The upload key is ~/s1/raa-upload.jks; its password sits in the macOS Keychain as "raa-upload-key".
set -eu
cd "$(dirname "$0")/.."
# AGP needs JDK 17+; a global JAVA_HOME often points elsewhere.
JAVA_HOME="${RAA_JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME
export RAA_STORE_FILE="${RAA_STORE_FILE:-$HOME/s1/raa-upload.jks}"
RAA_STORE_PASSWORD="$(security find-generic-password -s raa-upload-key -w)"
export RAA_STORE_PASSWORD
./gradlew clean testDebugUnitTest lintRelease bundleRelease
echo "AAB: app/build/outputs/bundle/release/app-release.aab"

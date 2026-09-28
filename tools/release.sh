#!/bin/sh
# Builds the signed App Bundle for Google Play.
#
#   RAA_STORE_FILE=/path/to/upload.jks tools/release.sh
#
# The keystore password comes from RAA_STORE_PASSWORD or, on macOS, from the Keychain item named
# by RAA_KEYCHAIN_SERVICE (default raa-upload-key). RAA_JAVA_HOME overrides the JDK (17+).
set -eu
cd "$(dirname "$0")/.."
: "${RAA_STORE_FILE:?set RAA_STORE_FILE to the upload keystore}"
export RAA_STORE_FILE
if [ -z "${RAA_STORE_PASSWORD:-}" ]; then
  RAA_STORE_PASSWORD="$(security find-generic-password -s "${RAA_KEYCHAIN_SERVICE:-raa-upload-key}" -w)"
fi
export RAA_STORE_PASSWORD
if [ -n "${RAA_JAVA_HOME:-}" ]; then export JAVA_HOME="$RAA_JAVA_HOME"; fi
./gradlew clean testDebugUnitTest lintRelease bundleRelease
echo "AAB: app/build/outputs/bundle/release/app-release.aab"

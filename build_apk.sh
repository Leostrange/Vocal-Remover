#!/bin/bash

# Vocal Remover - Android Build Script
# Builds the APK for the Vocal Remover application

set -e  # Exit on error

echo "=========================================="
echo "  Vocal Remover - Android Build Script"
echo "=========================================="
echo ""

# Navigate to project directory
cd "$(dirname "$0")"

# Check Java version
echo "Java version:"
java -version 2>&1
echo ""

# Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "Warning: ANDROID_HOME not set. Checking common locations..."
    for dir in /opt/android-sdk /usr/local/android-sdk ~/Android/Sdk; do
        if [ -d "$dir" ]; then
            export ANDROID_HOME="$dir"
            echo "Found Android SDK at: $ANDROID_HOME"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "Error: Android SDK not found. Please set ANDROID_HOME."
    exit 1
fi

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean --quiet

# Build debug APK
echo ""
echo "Building debug APK..."
./gradlew assembleDebug --stacktrace

# Check for APK files
echo ""
echo "Build completed successfully!"
echo ""
echo "APK files:"
find app/build/outputs/apk -name "*.apk" -type f 2>/dev/null | while read apk; do
    size=$(du -h "$apk" | cut -f1)
    echo "  - $apk ($size)"
done

echo ""
echo "=========================================="
echo "  Build Complete!"
echo "=========================================="

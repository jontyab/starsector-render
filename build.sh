#!/bin/bash
set -e
cd "$(dirname "$0")"

# Download ASM if needed
[ -f asm-9.7.1.jar ] || curl -sLO https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar

# Compile
rm -rf build && mkdir build
javac --release 17 -cp asm-9.7.1.jar -d build src/missionlaunch/*.java

# Bundle ASM
(cd build && jar xf ../asm-9.7.1.jar org/)

# Package
jar cfm mission-launch.jar MANIFEST.MF -C build .
rm -rf build asm-9.7.1.jar

echo "Built mission-launch.jar ($(du -h mission-launch.jar | cut -f1))"

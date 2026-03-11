#!/bin/bash

DIR="$( cd "$( dirname "$BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"
cd "../Resources/Java"

APP_ROOT="$(cd "$DIR/../.." && pwd)"

export JAVA_HOME=../../Home-arm
[ -d "$JAVA_HOME" ] || export JAVA_HOME=../../Home
"$JAVA_HOME/bin/java" \
    -Xdock:name="Starsector" \
    -Xdock:icon=../../Resources/s_icon128.icns \
    -Dapple.laf.useScreenMenuBar=false \
    -Dcom.apple.macos.useScreenMenuBar=false \
    -Dapple.awt.showGrowBox=false \
    -Dfile.encoding=UTF-8 \
    ${EXTRAARGS} \
	-noverify \
	-XX:+UnlockDiagnosticVMOptions \
	-XX:+UnlockExperimentalVMOptions \
	-XX:+UseZGC \
	-XX:+AlwaysPreTouch \
	-XX:+DisableExplicitGC \
	-XX:-DontCompileHugeMethods \
	-Xms4096m \
	-Xmx4096m \
	-Xss4m \
	-Djava.library.path=../../Resources/Java/native/macosx-arm:../../Resources/Java/native/macosx \
	-Djava.system.class.loader=com.genir.renderer.loaders.AppClassLoader \
	-javaagent:../../Resources/Java/fr.jar \
	-Djava.util.Arrays.useLegacyMergeSort=true \
	-XX:-BytecodeVerificationLocal \
	-XX:-BytecodeVerificationRemote \
	--enable-preview \
	--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
	--add-opens=java.base/java.nio=ALL-UNNAMED \
	--add-opens=java.base/java.util=ALL-UNNAMED \
	--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
	--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
	--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
	--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
	--add-opens=java.base/java.lang.ref=ALL-UNNAMED \
	--add-opens=java.base/java.text=ALL-UNNAMED \
	--add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
	--add-opens=java.desktop/java.awt=ALL-UNNAMED \
	--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
	--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
	--add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
	-Dcom.fs.starfarer.settings.paths.saves="$APP_ROOT/saves" \
	-Dcom.fs.starfarer.settings.paths.screenshots="$APP_ROOT/screenshots" \
	-Dcom.fs.starfarer.settings.paths.mods="$APP_ROOT/mods" \
	-Dcom.fs.starfarer.settings.paths.logs="$APP_ROOT/logs" \
	-Dcom.fs.starfarer.settings.osx=true \
	-cp ../../Resources/Java/native/macosx-arm/*:../../Resources/Java/fr.jar:../../Resources/Java/AppleJavaExtensions.jar:../../Resources/Java/commons-compiler-jdk.jar:../../Resources/Java/commons-compiler.jar:../../Resources/Java/fs.sound_obf.jar:../../Resources/Java/janino.jar:../../Resources/Java/jinput.jar:../../Resources/Java/jogg-0.0.7.jar:../../Resources/Java/jorbis-0.0.15.jar:../../Resources/Java/json.jar:../../Resources/Java/log4j-1.2.9.jar:../../Resources/Java/lwjgl.jar:../../Resources/Java/lwjgl_util.jar:../../Resources/Java/starfarer.api.jar:../../Resources/Java/starfarer_obf.jar:../../Resources/Java/fs.common_obf.jar:../../Resources/Java/xstream-1.4.10.jar:../../Resources/Java/txw2-3.0.2.jar:../../Resources/Java/jaxb-api-2.4.0-b180830.0359.jar:../../Resources/Java/webp-imageio-0.1.6.jar \
    com.fs.starfarer.StarfarerLauncher \
    "$@"

exit 0

#!/usr/bin/env sh
# Simplified Gradle wrapper script (Unix). Generated for WinAgentBridge.

DIR="$(cd "$(dirname "$0")" && pwd)"

GRADLE_WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
  echo "Missing gradle-wrapper.jar. If you opened this in Android Studio, use its Gradle wrapper generation or run: gradle wrapper" >&2
  exit 1
fi

JAVA_CMD=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
JAVA_CMD=${JAVA_CMD:-java}

exec "$JAVA_CMD" -classpath "$GRADLE_WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"

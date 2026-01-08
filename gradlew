#!/usr/bin/env sh
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Missing $WRAPPER_JAR. Run 'gradle wrapper' to generate it." >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD=java
fi

exec "$JAVA_CMD" $JAVA_OPTS -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"

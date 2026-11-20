#!/usr/bin/env sh
set -eu
if ! command -v java >/dev/null 2>&1; then
  echo "Java 25 is required." >&2
  exit 1
fi
JAVA_MAJOR=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [ "${JAVA_MAJOR:-0}" -lt 25 ]; then
  echo "Java 25 is required; detected Java ${JAVA_MAJOR:-unknown}." >&2
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.6.0 is required (install it or import the project in an IDE with Gradle support)." >&2
  exit 1
fi
gradle build

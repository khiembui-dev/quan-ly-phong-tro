#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script — script-only (no JAR).
# Bootstraps Maven from `distributionUrl` in .mvn/wrapper/maven-wrapper.properties
# on first run, then delegates all args to it.
# ----------------------------------------------------------------------------
set -e

if [ -z "$JAVA_HOME" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVACMD=java
  else
    echo "Error: JAVA_HOME not set and 'java' not found on PATH." >&2
    exit 1
  fi
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_PROPS="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$WRAPPER_PROPS" ] || { echo "Missing $WRAPPER_PROPS" >&2; exit 1; }

DIST_URL=$(grep '^distributionUrl=' "$WRAPPER_PROPS" | cut -d= -f2-)
DIST_VER=$(echo "$DIST_URL" | sed -E 's/.*apache-maven-([0-9.]+)-bin\.zip.*/\1/')
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
MVN_DIR="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$DIST_VER"
MVN_BIN="$MVN_DIR/apache-maven-$DIST_VER/bin/mvn"

if [ ! -x "$MVN_BIN" ]; then
  echo "Downloading Maven $DIST_VER (one-time)…" >&2
  mkdir -p "$MVN_DIR"
  TMP_ZIP="$MVN_DIR/maven.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP_ZIP" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP_ZIP" "$DIST_URL"
  else
    echo "Need curl or wget to download Maven." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP_ZIP" -d "$MVN_DIR"
  else
    "$JAVACMD" -cp "" -e 'new java.util.zip.ZipInputStream(new java.io.FileInputStream("'"$TMP_ZIP"'"))' >/dev/null 2>&1 || true
    echo "Need 'unzip' on PATH." >&2; exit 1
  fi
  rm -f "$TMP_ZIP"
  chmod +x "$MVN_BIN"
fi

exec "$MVN_BIN" "$@"

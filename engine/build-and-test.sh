#!/usr/bin/env bash
# Kompiluje i testuje silnik kompilatorem Kotlina dołączonym do Gradle (bez pobierania czegokolwiek).
# Uruchomienie z folderu engine/:  ./build-and-test.sh
set -euo pipefail
cd "$(dirname "$0")"
GRADLE_BIN="$(readlink -f "$(command -v gradle)")"
LIB="$(dirname "$GRADLE_BIN")/../lib"
pick() { ls "$LIB"/$1 2>/dev/null | grep -v sources | head -1; }
STDLIB="$(pick 'kotlin-stdlib-[0-9]*.jar')"
CP="$(pick 'kotlin-compiler-embeddable-*.jar'):$STDLIB:$(pick 'kotlin-script-runtime-*.jar'):$(pick 'kotlin-reflect-*.jar'):$(pick 'trove4j*.jar'):$(pick 'kotlinx-coroutines-core-jvm*.jar'):$(pick 'annotations-*.jar')"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$STDLIB" \
  $(find src -name '*.kt') -d "$OUT" 2>&1 | grep -v 'JAVA_TOOL_OPTIONS' || true
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" pl.lokaty.engine.CalendarTestKt ../test-data/engine/cases.json

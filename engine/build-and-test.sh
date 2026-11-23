#!/usr/bin/env bash
# Kompiluje i testuje silnik kompilatorem Kotlina dołączonym do Gradle (bez pobierania bibliotek).
# Uruchomienie z folderu engine/:  bash build-and-test.sh
set -eu
cd "$(dirname "$0")"
GRADLE_BIN="$(readlink -f "$(command -v gradle)")"
LIB="$(dirname "$GRADLE_BIN")/../lib"
echo "Gradle: $GRADLE_BIN"
echo "Java: $(java -version 2>&1 | grep -v JAVA_TOOL | head -1)"
# Wybiera pierwszy plik pasujący do wzorca; brak pliku nie przerywa skryptu.
pick() { ls "$LIB"/$1 2>/dev/null | grep -v -- '-sources' | head -1 || true; }
STDLIB="$(pick 'kotlin-stdlib-[0-9]*.jar')"
COMPILER="$(pick 'kotlin-compiler-embeddable-*.jar')"
if [ -z "$STDLIB" ] || [ -z "$COMPILER" ]; then
  echo "BŁĄD: w $LIB nie znaleziono kompilatora lub biblioteki Kotlina. Zawartość:"
  ls "$LIB" | grep -i -E 'kotlin|trove|annotations|coroutines' || true
  exit 1
fi
CP="$COMPILER:$STDLIB"
for w in 'kotlin-script-runtime-*.jar' 'kotlin-reflect-*.jar' 'trove4j*.jar' 'kotlinx-coroutines-core-jvm*.jar' 'annotations-*.jar'; do
  f="$(pick "$w")"; [ -n "$f" ] && CP="$CP:$f"
done
echo "Kompilator: $COMPILER"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$STDLIB" \
  $(find src -name '*.kt') -d "$OUT"
for T in CalendarTestKt InterestTestKt; do
  echo "== $T"
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" "pl.lokaty.engine.$T" ../test-data/engine/cases.json
done
echo "== OptimizerTestKt"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" pl.lokaty.engine.OptimizerTestKt ../test-data/engine/optimizer-cases.json
echo "== AdvisorTestKt"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" pl.lokaty.engine.AdvisorTestKt ../test-data/snapshots/valid/przyklad-minimalny.json
echo "== BreakTestKt"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" pl.lokaty.engine.BreakTestKt
echo "== PerfTestKt"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$STDLIB" pl.lokaty.engine.PerfTestKt

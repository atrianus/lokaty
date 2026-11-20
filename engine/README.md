# Silnik finansowy

Kotlin (JVM), czysta funkcja: bez zegara, sieci i plików (poza testami). Reguły: dokument „Specyfikacja silnika finansowego”.

- `src/main/kotlin/pl/lokaty/engine/Calendar.kt`: dodawanie miesięcy, Wielkanoc, święta, dni robocze.
- `src/test/kotlin/...`: testy czytają przypadki z `../test-data/engine/cases.json`.
- `./build-and-test.sh`: kompiluje i uruchamia testy kompilatorem Kotlina dołączonym do Gradle (bez pobierania bibliotek). Uruchamia to też CI (zadanie `engine`).

Dalsze kroki: odsetki i podatek (K1–K5), zerwanie (K5–K7), walidacja (K11).

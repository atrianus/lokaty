# Silnik finansowy

Kotlin (JVM), czysta funkcja: bez zegara, sieci i plików (poza testami). Reguły: dokument „Specyfikacja silnika finansowego”.

- `src/main/kotlin/pl/lokaty/engine/Calendar.kt`: dodawanie miesięcy, Wielkanoc, święta, dni robocze.
- `src/test/kotlin/...`: testy czytają przypadki z `../test-data/engine/cases.json`.
- `./build-and-test.sh`: kompiluje i uruchamia testy kompilatorem Kotlina dołączonym do Gradle (bez pobierania bibliotek). Uruchamia to też CI (zadanie `engine`).

Dalsze kroki: odsetki i podatek (K1–K5), zerwanie (K5–K7), walidacja (K11).

Optymalizator (`Optimizer.kt`): strategie z nóg, horyzont, porównanie z zapisaną decyzją, odciski. Przypadki O1–O12: `../test-data/engine/optimizer-cases.json` (generuje `tools/generate_optimizer_cases.py` z pełnego przeszukania w `tools/optimizer_reference.py`).

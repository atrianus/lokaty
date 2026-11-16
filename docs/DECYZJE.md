# Rejestr decyzji technicznych (Etap 1)

Stan na 16.11.2026. Źródło: dokument „Etap 1: architektura techniczna” i dokument projektowy v2.2.

| Nr | Decyzja | Wybór | Status |
| --- | --- | --- | --- |
| D1 | Forma modułu silników | Kotlin JVM bez zależności od Androida, obliczenia na BigDecimal | Do potwierdzenia: czy w planach nie ma iOS ani wersji webowej (wtedy rozważamy Kotlin Multiplatform) |
| D2 | Lokalna baza danych | Room (SQLite) | Przyjęta domyślnie |
| D3 | Język backendu | Python do skryptów zbierających i publikujących oferty; FastAPI i PostgreSQL odłożone do czasu, gdy będą potrzebne | Przyjęta domyślnie |
| D4 | Publikacja ofert | Dzienna, niezmienna migawka ofert jako plik JSON z wersją | Przyjęta domyślnie |
| D5 | Pozyskiwanie ofert w V1 | Półautomatyczne: skrypt tworzy szkic („niezweryfikowana”), człowiek zatwierdza | Zatwierdzona 16.11.2026 |
| D6 | Zadanie w tle i powiadomienia | WorkManager i powiadomienia lokalne | Przyjęta domyślnie |
| D7 | Repozytorium | Jedno repozytorium (monorepo) | Przyjęta domyślnie |
| D8 | Kalendarz dni roboczych | Reguły polskich świąt liczone w silniku | Przyjęta domyślnie |
| D9 | Hosting i budżet | Publiczne repozytorium GitHub, GitHub Actions, GitHub Pages, koszt 0 zł | Zatwierdzona 16.11.2026 |
| D10 | Kopia zapasowa danych w telefonie | Systemowa kopia wyłączona, ochroną jest eksport danych | Zatwierdzona 16.11.2026 |

## Ograniczenia wariantu bezkosztowego (D9)

- GitHub Pages w planie darmowym działa tylko dla publicznych repozytoriów.
- Warunki GitHub Pages wykluczają prowadzenie komercyjnego serwisu: wariant traktujemy jako start i pilotaż, a przed uruchomieniem komercyjnym sprawdzamy warunki albo przenosimy pliki na inny hosting.
- Sprawdzić przed Etapem 14: jednorazowa opłata za konto deweloperskie Google Play.

# Automatyczne zaciąganie ofert

Przebieg dzienny (`.github/workflows/collect.yml`, 5:30 UTC):
1. `collector/detect_changes.py` pobiera strony, zapisuje tekst i listę stron pobranych poprawnie (`state/last_run.json`).
2. `collector/check_rates.py` sprawdza, czy stawki zweryfikowanych ofert są jeszcze na stronach.
3. `extractor/sync_offers.py` (parsery z `parsers.py`): jeśli zmieniła się TYLKO stawka istniejącej oferty (bez zmian terminu, kwot, dat; zmiana nie większa niż 1 pkt proc.; stawka 0–10%), oferta aktualizuje się sama. Wszystko inne trafia do `sync-report.md` jako „WYMAGA PRZEGLĄDU”. Strony niepobrane dziś są pomijane.
4. `extractor/llm_extract.py` (tylko z sekretem `ANTHROPIC_API_KEY`): dla stron zmienionych od ostatniego razu model robi szkic w `offers/draft/llm/`. Cytaty, stawki i kwoty są mechanicznie sprawdzane w tekście strony; konflikt z parserem = zgłoszenie.
5. Zmiany są commitowane, po zmianie ofert uruchamia się publikacja, a sprawy do przeglądu trafiają do Issue „Lokaty: oferty do przeglądu”.

Nowy bank: dopisz parser w `parsers.py` (funkcja + wpis w `PARSERS`), dodaj próbkę strony do `test-data/extractor/` i testy w `tools/test_extractor.py`.
Zasada: parser, który nic nie znajdzie, nigdy nie kasuje ani nie zmienia ofert.

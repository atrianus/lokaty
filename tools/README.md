# Narzędzia

- `validate_snapshots.py`: sprawdza migawki ofert względem schematu z `contracts/` oraz kontrole między polami. Uruchomienie: `python tools/validate_snapshots.py` (wymaga `pip install jsonschema`).
- `test_schema.py`: testy schematu. Uruchomienie: `python -m unittest discover -s tools -v`.

Wszystko to uruchamia się automatycznie w CI przy każdej zmianie.

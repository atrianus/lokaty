# Przypadki kontrolne

Wspólne dane testowe dla silnika i backendu (pliki JSON).

- `snapshots/valid/`: poprawne przykładowe migawki ofert (dane wymyślone, tylko do testów). Każdy plik jest sprawdzany w CI.
- Przypadki odrzucane powstają w `tools/test_schema.py` przez psucie poprawnego przykładu, żeby każdy z nich miał jedną jasną wadę.

Przypadki dla silnika finansowego (dane wejściowe, źródło zasad, oczekiwany wynik, różnica, ocena) dojdą w Etapie 5.

Tolerancje: testy jednostkowe silnika porównują dokładnie z obliczeniem referencyjnym; porównanie z kalkulatorami banków ma tolerancję 1 zł na pojedynczą lokatę.

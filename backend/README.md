# Backend (bez serwera i bazy w V1)

W wariancie bezkosztowym dane o ofertach to pliki w repozytorium, a skrypty uruchamia harmonogram GitHub Actions.

- `collector/`: skrypty Python tworzące szkice ofert (status „niezweryfikowana”),
- `offers/`: szkice i zweryfikowane oferty jako pliki,
- `publisher/`: dzienne, niezmienne migawki JSON i ich podpis.

Weryfikacja oferty: skrypt tworzy szkic jako propozycję zmiany, człowiek sprawdza ją w przeglądarce i zatwierdza.

Klucz podpisu migawek jest przechowywany wyłącznie w sekretach GitHub Actions, nigdy w repozytorium.

Implementacja: Etap 3.

# Kontrakt danych: migawka ofert

`offer-snapshot.schema.json` to schemat JSON (wersja `1.0`) migawki ofert: jedyne miejsce, które łączy backend z aplikacją. Opis pól jest w dokumencie „Etap 2: model danych”.

Zasady:

- kwoty i stawki jako tekst dziesiętny (`"5.00"`), nigdy liczba JSON,
- pola tak/nie mają wartości `yes`/`no`/`unknown`: brak wiedzy nie wygląda jak „nie”,
- w migawce są tylko oferty `verified` i `expired`, szkice (`draft`) nie trafiają do niej,
- opublikowana migawka nie jest zmieniana, poprawka to nowa rewizja,
- suma kontrolna i podpis są poza plikiem migawki (`snapshots/latest.json` i plik obok migawki),
- zmiana niełamiąca podnosi wersję pomocniczą (`1.1`), łamiąca główną (`2.0`).

Kontrole, których schemat sam nie wykonuje (powtórzone identyfikatory, oferta wskazująca nieistniejący bank, `offer_id` spoza banku oferty), robi `tools/validate_snapshots.py`.

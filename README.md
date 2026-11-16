# Lokaty (nazwa robocza)

Aplikacja na Androida do optymalizacji portfela lokat bankowych: porównuje istniejące oferty, buduje strategie (sekwencje i podział lokat) i wskazuje rozwiązanie o najwyższym zysku netto. Aplikacja nie zakłada lokat i nie wykonuje operacji bankowych, tylko podpowiada.

## Status

- Etap 0 (specyfikacja): domknięty, dokument projektowy v2.2.
- Etap 1 (architektura techniczna): zatwierdzony, ten szkielet jest jego wynikiem.
- Następny krok: Etap 2 (model danych, schemat migawki ofert w `contracts/`).

## Struktura repozytorium

| Katalog | Zawartość |
| --- | --- |
| `docs/` | Dokumentacja, rejestr decyzji (`DECYZJE.md`) |
| `contracts/` | Schemat JSON migawki ofert (jedno źródło prawdy) |
| `engine/` | Silnik finansowy i optymalizacji (Kotlin, bez zależności od Androida) |
| `android-app/` | Aplikacja Android (Compose, Room) |
| `backend/` | Zbieranie i publikacja ofert (skrypty Python, bez serwera i bazy w V1) |
| `test-data/` | Przypadki kontrolne z oczekiwanym wynikiem |
| `tools/` | Skrypty pomocnicze |
| `.github/workflows/` | Automatyzacje (testy, w przyszłości dzienne zbieranie ofert) |

## Zasady, których nie wolno łamać

1. **To repozytorium jest publiczne.** Nigdy nie wgrywamy do niego danych użytkowników, haseł, kluczy ani sekretów. Klucz podpisu migawek trafia wyłącznie do sekretów GitHub Actions.
2. Dane o lokatach użytkownika zostają w jego telefonie.
3. Prowizje i linki partnerskie nie mają wpływu na ranking strategii.
4. Oferta bez potwierdzenia człowieka ma status „niezweryfikowana”.

## Licencja

Do ustalenia. Dopóki nie dodamy pliku licencji, kod jest widoczny publicznie, ale wszystkie prawa pozostają zastrzeżone.

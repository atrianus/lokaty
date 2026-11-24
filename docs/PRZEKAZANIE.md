# Przekazanie projektu „Lokaty” do nowego czatu

Stan na 22.11.2026. Ten plik pozwala agentowi w nowym czacie od razu wiedzieć, co jest zrobione i co dalej.

## 1. Co wkleić na początku nowego czatu

> Kontynuujemy projekt aplikacji Android do optymalizacji lokat bankowych w Polsce. Przeczytaj plik `docs/PRZEKAZANIE.md` w repozytorium https://github.com/atrianus/lokaty (jest publiczne) i dokumenty z sekcji 6. Pracuj po polsku, krok po kroku, jestem początkującym założycielem i chcę minimalny koszt (najlepiej zero). Zacznij od sekcji 8 („Następne kroki”) i zapytaj mnie, który krok robimy.

Jeśli agent nie ma dostępu do repozytorium, użytkownik załącza ten plik i paczkę `lokaty-calosc.zip`.

## 2. Projekt i użytkownik

- Aplikacja na Androida pomaga optymalizować lokaty bankowe w Polsce: buduje strategie z istniejących ofert (np. A+A, A+B), liczy zysk netto po podatku i codziennie porównuje je z decyzją użytkownika.
- Użytkownik to początkujący założyciel startupu bez dochodu z aplikacji: **koszt ma być zerowy lub minimalny**, prosi o sugestie i podpowiedzi, odpowiada zwykle krótko („zgoda z rekomendacją”).
- Język: polski. Styl: krótko, konkretnie, instrukcje klik po kliku dla GitHuba (nie zakładaj znajomości Gita ani Androida).
- Plan Claude: darmowy; w środowisku agenta Bash nie ma sieci (brak instalacji pakietów, brak pobierania stron skryptem); WebSearch i WebFetch działają, ale nie otworzą prywatnych stron GitHuba ani logów Actions. Logi i raporty z GitHuba użytkownik wkleja lub robi zrzut ekranu.

## 3. Zasady produktu (dokument v2.2, zatwierdzone)

- Strategie tylko z istniejących ofert, bez hipotetycznych przyszłych; oferta musi być ważna w dniu założenia nogi.
- Horyzont jest miękkim limitem: strategie go przekraczające są pokazane osobno z dodatkowymi dniami i dodatkowym zyskiem; użytkownik może je odrzucić, odrzucone są zapamiętane.
- Przycisk „Sprawdź strategię teraz”. Próg powiadomienia domyślnie 100 zł, konfigurowalny; zmiany poniżej progu są oznaczone, nie ukryte.
- Zerwanie lokaty liczone i pokazywane osobno, **nigdy nie rekomendowane** (odsetki proporcjonalne; przy niewiadomych wynik ostrożny i optymistyczny oraz „zweryfikuj w banku”).
- Kapitał dostępny domyślnie w następnym dniu roboczym (reguła per oferta). Arytmetyka dziesiętna. Tolerancja testów względem kalkulatora banku 1 zł na lokatę.
- Kwalifikowalność (nowi klienci itp.) tylko informacja, użytkownik może ręcznie wykluczyć ofertę. Limit BFG tylko ostrzega.
- Produkty niestandardowe liczone przybliżeniem i oznaczone. Poza V1: konta oszczędnościowe i waluty obce.
- Dane użytkownika tylko na telefonie, silnik liczy na urządzeniu; backend tylko publikuje oferty.
- Prowizje nigdy nie wpływają na kolejność strategii (niezmiennik i test); V1 bez prowizji i reklam banków. Aplikacja jest bliżej porównywarki niż doradcy (nie zakłada lokat), bez głębokiej analizy prawnej.
- Oferty zbierane codziennie od startu, bez uzupełniania wstecz.

## 4. Decyzje techniczne

| Nr | Decyzja | Status |
| --- | --- | --- |
| D1 | Silnik: Kotlin JVM, `BigDecimal`, bez zależności od Androida | przyjęta |
| D2 | Lokalna baza: Room | przyjęta |
| D3 | Backend: skrypty Python, bez serwera i bazy w V1 | przyjęta |
| D4 | Publikacja: dzienna, niezmienna migawka ofert JSON z wersją (schemat 1.0) | przyjęta |
| D5 | Pozyskiwanie ofert: półautomatyczne (skrypt tworzy szkic, człowiek zatwierdza); w przyszłości automatyzacja lub API dla banków | zatwierdzona |
| D6 | WorkManager i powiadomienia lokalne (bez FCM w V1) | przyjęta |
| D7 | Jedno repozytorium (monorepo) | przyjęta |
| D8 | Dni robocze i święta liczone w silniku (Wigilia wolna od 2025) | przyjęta |
| D9 | Hosting bezkosztowy: publiczne repozytorium GitHub, Actions, Pages | zatwierdzona (tylko start i pilotaż: warunki Pages wykluczają usługę komercyjną) |
| D10 | Systemowa kopia zapasowa wyłączona, ochroną jest eksport/import JSON (import zastępuje wszystkie dane) | zatwierdzona |

Dalsze decyzje: progi kwotowe to osobne oferty; pola `tax_timing` i `payout_period` w schemacie 1.0; założenia domyślne V1 (podatek przy każdej kapitalizacji, `half_up`, 365 dni, dostępność w następnym dniu roboczym, ostatni dzień miesiąca przy braku dnia) z etykietą „przybliżony”; optymalizator: do 3 nóg, 4 pokazywane strategie, luka do 7 dni, jedno powiadomienie na strategię (następne po wzroście przewagi o kolejny próg), odrzucona strategia wraca po wzroście wyniku o co najmniej próg.

Banki startowe (10): PKO BP, Pekao, Erste Bank Polska (dawniej Santander Bank Polska), ING Bank Śląski, mBank, BNP Paribas, Bank Millennium, Alior, Citi Handlowy, VeloBank (`backend/banks.json`).

## 5. Stan prac

| Etap | Co | Stan |
| --- | --- | --- |
| 1 | Architektura, szkielet repozytorium, CI | gotowe, zielone |
| 2 | Model danych, schemat migawki 1.0 z walidatorem i 32 testami | gotowe, zielone |
| 3 | Pozyskiwanie ofert: `banks.json`, wykrywacz zmian stron (`collect.yml`), kontrola stawek (`check_rates.py`, raport `rates-report.md`), 4 zweryfikowane oferty (PKO 3 i 6 mies., mBank standard i Intensive) w `backend/offers/`, szkic Pekao w `backend/offers/draft/`, publikator migawki (`backend/publisher/publish.py`) i workflow `publish.yml` (GitHub Pages) | gotowe do uruchomienia; wymaga jednorazowo włączenia Pages (Settings → Pages → Source: GitHub Actions); ING i Citi nadal bez automatycznego odczytu; pełny parser stawek zastąpiono kontrolą obecności stawki na stronie |
| 4 | Specyfikacja silnika finansowego, 13 przypadków kontrolnych K1–K11 (`test-data/engine/cases.json`) | gotowe |
| 5 | Silnik w Kotlinie: kalendarz, odsetki, podatek, kapitalizacja, zerwanie, walidacja kwoty | gotowe, testy przechodzą, zadanie CI `engine` zielone |
| 6 | Optymalizator strategii + doradca (zapisana decyzja, próg, odrzucone), przypadki O1–O12 | gotowe, testy lokalnie OK; CI do potwierdzenia po wgraniu |
| 7 | Aplikacja Android (Compose, Room, WorkManager): szkielet zbudowany i działający na telefonie (APK z CI) | gotowe |
| 7c | Przeprojektowany wygląd (Material 3, tryb ciemny, karty strategii z osią czasu), zawarte lokaty („Zawarłem pierwszą lokatę”), sekcja „Opcje ze zerwaniem” (`BreakAnalysis`, test `BreakTest`), eksport/import JSON (import zastępuje wszystko), pobieranie migawki z GitHub Pages z pamięcią podręczną (plik, nie tabela), ikona; wersja 0.2.0 | kod gotowy, **niekompilowany lokalnie** (kompiluje CI); nowa baza Room v2 (przy aktualizacji dane lokalne są kasowane) |

Repozytorium: https://github.com/atrianus/lokaty. Zadania CI w `.github/workflows/ci.yml`: `structure`, `schema` (testy Python: 32), `engine` (testy Kotlin przez `bash engine/build-and-test.sh`), `android` (budowa APK, artefakt `lokaty-debug-apk`). Osobny workflow `collect.yml` uruchamia wykrywacz zmian codziennie o 5:30 UTC.

Struktura: `contracts/` (schemat), `backend/` (`banks.json`, `collector/`, `offers/draft/`, `publisher/` pusty), `engine/` (Kotlin: `Calendar`, `Interest`, `Optimizer`, `Advisor`, `Snapshot`, `MiniJson`), `android-app/`, `test-data/`, `tools/` (testy schematu, wykrywacza, skrypty referencyjne Python), `docs/`.

## 6. Dokumenty (Claude Docs; czytaj narzędziem docs po identyfikatorze projektu)

| Dokument | Identyfikator projektu |
| --- | --- |
| Projekt v2.2 (zasady produktu) | dbe1176e-0747-4d5a-bad6-942b06b7717d |
| Etap 1: architektura | 96cfaf44-3766-432c-9417-4b02ed14f1a4 |
| Etap 2: model danych | ad29e5c7-c434-49c9-9e2b-927035397b2d |
| Specyfikacja silnika finansowego | b05c44b4-bbfe-4aae-9e02-781483c658a3 (https://claude.ai/artifact/Nn7Jp116VisbTLDYuxvYQS) |
| Etap 6: projekt optymalizatora | 35c4628b-50f9-4c7f-be8d-47ce341d183f (https://claude.ai/artifact/7e5vVgytsM48p8DEhjRKAE) |

Jeśli dokumentów nie da się odczytać, ich treść streszcza ten plik i `docs/DECYZJE.md`.

## 7. Otwarte kwestie i ryzyka

- **Podatek (status roboczy):** 19% zaokrąglany w górę do grosza (art. 63 § 1a Ordynacji) i moment poboru przy kapitalizacji oparte na źródłach wtórnych. Do potwierdzenia w tekście ustawy (ISAP) i na kalkulatorach 3–5 banków (przypadki K3, K4).
- Dzień roboczy i Wigilia w konkretnych bankach, liczenie dni (365/366), stawka przy zerwaniu: do sprawdzenia w regulaminach.
- Alior: 3 zweryfikowane oferty na nowe środki (24.11.2026, strona banku): 92 dni 3,3%, 182 dni 3,4%, 365 dni 3,0%; min. 1 000 zł, limit 300 000 zł na termin; nowe środki względem salda z 9.10.2026; zerwanie = utrata odsetek; brak daty końca oferty i reguły liczenia dni. Dokumenty: tabela oprocentowania i regulamin rachunków depozytowych na stronie oferty. Starszy szkic (analizy.pl, saldo z 24.07) był nieaktualny i został usunięty.
- Pekao (szkice, `backend/offers/draft/`): standardowa 3/6/12 mies. = 2,00/1,80/1,60% ze źródła wtórnego; strona banku potwierdza tylko „do 2,0%”, min. 1 000 zł, odsetki na koniec, zerwanie bez odsetek; tabela oprocentowania (PDF) zablokowana dla narzędzia (robots), wymaga wklejenia przez użytkownika. Promocje „Lokuj z nami” (3 mies., 3,3–3,4%, 5 000–100 000 zł, nowi klienci i saldo do 5 tys. zł) edycje IV–VI wygasły; edycja VII nieodczytana. Millennium i VeloBank: lokaty na nowe środki oznaczone jako wycofane (analizy.pl).
- Wykrywacz zmian: ING (treść ładowana skryptem) i Citi (HTTP 403) nie dają się odczytać automatycznie; adresy innych banków sprawdzone tylko częściowo; możliwe fałszywe alarmy (daty, liczniki na stronach).
- Wydajność optymalizatora: 100 ofert, 12 mies., 3 nogi to ok. 0,5 s (zimny start 1,3 s) na serwerze; na telefonie niezmierzone.
- Lista optymalizatora pokazuje tylko strategie niezdominowane (najlepsza zgodna z pełnym przeszukaniem).
- GitHub Pages: warunki wykluczają usługę komercyjną; przed komercyjnym startem przenieść hosting lub sprawdzić warunki. Opłata jednorazowa za konto Google Play do sprawdzenia (Etap publikacji).
- Podpis migawki (algorytm): nierozstrzygnięty; suma kontrolna i podpis mają leżeć poza plikiem migawki.
- Aplikacja Android jeszcze nigdy nie została skompilowana: spodziewaj się 1–3 rund poprawek po pierwszym przebiegu `android` w CI.

## 8. Następne kroki (w proponowanej kolejności)

1. **Potwierdzić CI po wgraniu paczek z Etapów 6–7:** zadania `engine` (cztery sekcje testów + wydajność) i `android`. Przy czerwonym `android` użytkownik wkleja log błędu, agent poprawia kod.
2. **Zainstalować APK** wg `docs/INSTRUKCJA-APK.md` i obejrzeć aplikację na telefonie; zebrać uwagi do ekranów.
3. **Etap 3, dalej:** parser stawek dla PKO i mBanku na zapisanych stronach (`backend/collector/state/`); rozwiązanie dla ING i Citi; weryfikacja szkiców ofert i przeniesienie zatwierdzonych do `backend/offers/`; skrypt publikujący migawkę (`publisher/`) i GitHub Pages; decyzja o podpisie migawki.
4. **Etap 7c/3 — po wgraniu:** włączyć Pages i uruchomić `publish.yml` (Actions → Publikacja migawki ofert → Run workflow); w aplikacji „Dane → Pobierz oferty teraz” powinno pokazać „pobrane z sieci”. Obejrzeć nowy wygląd i zebrać uwagi.
5. **Kolejne oferty:** weryfikacja kolejnych banków (Pekao, Santander/Erste, Millennium, Alior, VeloBank, BNP; ING i Citi ręcznie), przeniesienie szkiców do `backend/offers/`; decyzja o podpisie migawki; pomiar na telefonie; osobne ekrany/akcje dla zawartych lokat kolejnych nóg.
6. **Weryfikacja reguł podatkowych** w ISAP i na kalkulatorach banków; zmiana statusu reguł z „roboczy” na „potwierdzony”.
7. Pomiar wydajności optymalizatora na prawdziwym telefonie.
8. Dalsze etapy z dokumentu v2.2 (testy z użytkownikami, publikacja w Google Play, kwestie prawne i komercyjne).

## 8a. Wyniki testów 23.11.2026 (środowisko agenta)

- Python (`tools/`): 42 testy OK. Silnik Kotlin: Calendar, Interest, Optimizer, Advisor, BreakTest, PerfTest OK (100 ofert, 3 nogi: 0,6–0,7 s; zimny start 1,3–1,4 s). Publikator zbudował migawkę (4 oferty, 2 banki) i przeszedł walidator.
- Nie da się sprawdzić lokalnie: kompilacji aplikacji Android (zadanie CI `android`), testu na telefonie, `check_rates.py` (brak sieci w Bash).
- W tej sesji WebFetch otwierał tylko adresy zwrócone wcześniej przez WebSearch; strony banków otwierały się tylko po wklejeniu adresu przez użytkownika (Alior, Pekao: tak; Millennium, VeloBank, Erste, BNP: nie sprawdzone), więc nowe oferty mogły powstać tylko jako szkice ze źródeł wtórnych.

## 9. Jak pracujemy (praktyczne uwagi)

- Agent przygotowuje kod i testy w środowisku (Python, Kotlin przez kompilator dołączony do Gradle), pakuje zmienione pliki do `.zip`; użytkownik wgrywa je do GitHuba **przez przeglądarkę** (Add file → Upload files) i potwierdza CI w zakładce Actions.
- Folder `.github` nie wgrywa się przeciąganiem: pliki workflow użytkownik tworzy ręcznie (Add file → Create new file, nazwa `.github/workflows/<plik>.yml`) lub nadpisuje przez edycję (ołówek).
- Skrypty w repozytorium uruchamiamy przez `bash <plik>`, bo wgranie przez przeglądarkę gubi prawo wykonywania.
- Wartości referencyjne testów liczą niezależne skrypty Python (`tools/engine_reference.py`, `tools/optimizer_reference.py`); są to obliczenia według reguł projektu, nie porównanie z bankiem.
- Zmiana schematu migawki: wersja pomocnicza (1.1) dla zmian niełamiących, główna (2.0) dla łamiących; żadna migawka jeszcze nie została opublikowana.
- Pamięć użytkownika zawiera plik `/areas/aplikacja-lokaty.md` z historią decyzji (agent z dostępem do pamięci powinien go przeczytać).

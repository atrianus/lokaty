# Jak zainstalować wersję testową na telefonie

Nie potrzebujesz Android Studio ani kabla: aplikację buduje GitHub, a Ty pobierasz gotowy plik.

1. W repozytorium wejdź w zakładkę **Actions** i otwórz najnowszy przebieg „Struktura repozytorium” z zielonym znaczkiem.
2. Przewiń na sam dół do sekcji **Artifacts** i pobierz **lokaty-debug-apk** (plik .zip).
3. Rozpakuj zip: w środku jest plik `app-debug.apk`.
4. Przenieś go na telefon (e-mail do siebie, dysk w chmurze albo kabel).
5. Na telefonie otwórz plik `app-debug.apk`. Android zapyta o zgodę na instalację z tego źródła: zezwól tylko dla tej aplikacji, np. przeglądarki lub menedżera plików.
6. Uruchom „Lokaty”. Na Androidzie 13 i nowszych zezwól na powiadomienia.

Uwagi:

- To wersja testowa z **danymi przykładowymi** (wymyślone banki i oferty). Nie służy do prawdziwych decyzji.
- Artefakty w GitHubie wygasają po 90 dniach; wtedy uruchom przebieg ponownie.
- Jeśli telefon blokuje instalację, w ustawieniach wyszukaj „Instalowanie nieznanych aplikacji” i zezwól dla programu, z którego otwierasz plik.

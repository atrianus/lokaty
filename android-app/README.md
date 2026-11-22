# Aplikacja Android

Stack: Kotlin, Jetpack Compose, Room, zadanie okresowe w tle (WorkManager), powiadomienia lokalne. Silnik (`../engine`) jest osobnym modułem JVM bez zależności od Androida.

Stan (Etap 7, wersja testowa 0.1.0):

- trzy zakładki: Strategie (przycisk „Sprawdź strategię teraz”, zatwierdź/odrzuć), Ustawienia (kapitał, horyzont, próg), Dane,
- Room: ustawienia, zatwierdzona decyzja, odrzucone strategie,
- zadanie `DailyCheckWorker` raz na dobę porównuje najlepszą strategię z zatwierdzoną i wysyła powiadomienie lokalne,
- oferty: przykładowa migawka z `src/main/assets/snapshot-przyklad.json` (dane wymyślone).

Czego jeszcze nie ma: pobieranie prawdziwej migawki z GitHub Pages i pamięć podręczna, oznaczanie zawartych lokat (`LockedLeg`), sekcja „Opcje ze zerwaniem”, eksport i import danych, ikona aplikacji.

Zasady:

- dane portfela i strategii zostają na urządzeniu,
- systemowa kopia zapasowa wyłączona (`allowBackup=false`), ochroną jest eksport,
- logi nie zawierają kwot, banków ani strategii użytkownika,
- zegar (`LocalDate.now`) jest tylko w warstwie aplikacji, silnik dostaje datę jako parametr.

Budowa: CI (zadanie `android`) buduje APK i zapisuje go jako artefakt `lokaty-debug-apk`. Instrukcja instalacji: `../docs/INSTRUKCJA-APK.md`.

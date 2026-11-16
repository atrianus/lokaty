# Silnik obliczeń (Kotlin JVM)

Moduł bez zależności od Androida, testowalny samodzielnie.

Planowane pakiety: `model`, `calendar` (dni robocze i święta), `finance` (odsetki, podatek, zaokrąglenia, zerwanie), `optimizer` (kombinacje strategii).

Zasady:

- wszystkie kwoty i stawki na `BigDecimal`, typ zmiennoprzecinkowy jest zabroniony,
- silnik nie ma sieci, plików ani zegara systemowego: data obliczenia jest parametrem,
- każdy wynik ma poziom pewności (dokładny, przybliżony, niezweryfikowany) i ślad obliczeń,
- brak znanej reguły nie jest zastępowany przypuszczeniem,
- `finance` i `calendar` są odbierane osobno od `optimizer` (etapy 5 i 6).

Implementacja zaczyna się dopiero po Etapie 2 i specyfikacji silnika finansowego.

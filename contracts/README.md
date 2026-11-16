# Kontrakt danych: migawka ofert

Tu powstanie schemat JSON migawki ofert (Etap 2). To jedyne miejsce, które łączy backend z aplikacją.

Planowane pola oferty: `offer_id`, `bank_id`, `product_name`, `rate_annual`, `term_days`, `interest_type`, `day_count_rule`, `rounding_rule`, `amount_min`, `amount_max`, `valid_from`, `valid_until`, `conditions`, `early_termination`, `availability_rule`, `bfg_covered`, `source_url`, `retrieved_at`, `last_verified_at`, `document_version`, `status`, `completeness`.

Zasady:

- kwoty i stawki jako tekst dziesiętny, nie liczba zmiennoprzecinkowa,
- migawka ma numer wersji schematu, datę i sumę kontrolną,
- opublikowana migawka nie jest zmieniana, poprawka to nowa rewizja,
- aplikacja zapisuje przy każdej rekomendacji identyfikator użytej migawki.

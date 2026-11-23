# Zweryfikowane oferty

Pliki `*.json` w tym folderze (jedna oferta = jeden plik, `verification: "verified"`) trafiają do migawki publikowanej na GitHub Pages
(`backend/publisher/publish.py`, workflow `publish.yml`). Szkice w `draft/` nie są publikowane.

Zmiana, dodanie lub usunięcie pliku w tym folderze po wgraniu do `main` uruchamia publikację automatycznie.
Codzienny workflow `collect.yml` sprawdza, czy stawka z oferty nadal jest na stronie banku (`backend/collector/rates-report.md`);
wpis `missing` oznacza, że trzeba zajrzeć na stronę i poprawić albo usunąć ofertę.

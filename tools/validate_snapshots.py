"""Walidacja migawek ofert: schemat JSON plus kontrole między polami.

Użycie:  python tools/validate_snapshots.py [plik_lub_katalog ...]
Bez argumentów sprawdza test-data/snapshots/valid oraz snapshots/ (jeśli istnieje).
Kod wyjścia 0 = wszystko poprawne, 1 = są błędy.
"""
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parent.parent
SCHEMA = json.loads((ROOT / "contracts" / "offer-snapshot.schema.json").read_text(encoding="utf-8"))
_VALIDATOR = Draft202012Validator(SCHEMA)


def validate(snapshot):
    """Zwraca listę komunikatów o błędach (pusta lista = migawka poprawna)."""
    errors = [
        f"{'/'.join(str(p) for p in e.absolute_path) or '(korzeń)'}: {e.message}"
        for e in sorted(_VALIDATOR.iter_errors(snapshot), key=lambda e: list(e.absolute_path))
    ]
    if errors:
        return errors  # kontrole między polami mają sens tylko dla poprawnej struktury

    bank_ids = [b["bank_id"] for b in snapshot["banks"]]
    for dup in sorted({b for b in bank_ids if bank_ids.count(b) > 1}):
        errors.append(f"banks: powtórzony bank_id '{dup}'")

    offer_ids = [o["offer_id"] for o in snapshot["offers"]]
    for dup in sorted({o for o in offer_ids if offer_ids.count(o) > 1}):
        errors.append(f"offers: powtórzony offer_id '{dup}'")

    known = set(bank_ids)
    for o in snapshot["offers"]:
        if o["bank_id"] not in known:
            errors.append(f"{o['offer_id']}: bank_id '{o['bank_id']}' nie istnieje w banks")
        if not o["offer_id"].startswith(o["bank_id"] + "/"):
            errors.append(f"{o['offer_id']}: offer_id musi zaczynać się od '{o['bank_id']}/'")
    return errors


def _files(args):
    targets = [Path(a) for a in args] or [ROOT / "test-data" / "snapshots" / "valid", ROOT / "snapshots"]
    for t in targets:
        if t.is_file():
            yield t
        elif t.is_dir():
            yield from sorted(p for p in t.rglob("*.json") if p.name != "latest.json")


def main(argv):
    failed = 0
    checked = 0
    for path in _files(argv):
        checked += 1
        errors = validate(json.loads(path.read_text(encoding="utf-8")))
        if errors:
            failed += 1
            print(f"BŁĄD {path}")
            for e in errors:
                print(f"  - {e}")
        else:
            print(f"OK   {path}")
    print(f"Sprawdzono plików: {checked}, z błędami: {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

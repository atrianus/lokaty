"""Buduje migawkę ofert (schemat 1.0) ze zweryfikowanych ofert i zapisuje ją do folderu site/ (GitHub Pages).

Wejście:  backend/banks.json, backend/offers/*.json (tylko zweryfikowane; szkice leżą w offers/draft i nie są brane)
Wyjście:  <out>/snapshot.json, <out>/snapshot.sha256, <out>/index.html
Użycie:   python backend/publisher/publish.py [--out site] [--allow-empty]
Kod wyjścia: 0 = opublikowano, 2 = brak zweryfikowanych ofert (bez --allow-empty), 1 = błąd danych (np. niezgodność ze schematem).
Tylko biblioteka standardowa plus jsonschema (używane też przez CI) do walidacji.
"""
import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools"))


def load_offers(offers_dir):
    return [json.loads(p.read_text(encoding="utf-8")) for p in sorted(offers_dir.glob("*.json"))]


def build(banks_file, offers_dir, now=None, revision=1):
    """Zwraca migawkę (słownik). Zawiera tylko banki, które mają oferty."""
    now = now or datetime.now(timezone.utc)
    banks = json.loads(banks_file.read_text(encoding="utf-8"))
    banks = banks["banks"] if isinstance(banks, dict) else banks
    offers = [o for o in load_offers(offers_dir) if o.get("verification") in ("verified", "expired")]
    used = {o["bank_id"] for o in offers}
    unknown = used - {b["bank_id"] for b in banks}
    if unknown:
        raise ValueError(f"oferty z bankami spoza banks.json: {sorted(unknown)}")
    return {
        "schema_version": "1.0",
        "snapshot_date": now.strftime("%Y-%m-%d"),
        "revision": revision,
        "generated_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "banks": [{"bank_id": b["bank_id"], "name": b["name"], "bfg_covered": b.get("bfg_covered", "unknown")} for b in banks if b["bank_id"] in used],
        "offers": sorted(offers, key=lambda o: o["offer_id"]),
    }


def write(snapshot, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    text = json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n"
    (out_dir / "snapshot.json").write_text(text, encoding="utf-8")
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    (out_dir / "snapshot.sha256").write_text(f"{digest}  snapshot.json\n", encoding="utf-8")
    (out_dir / "index.html").write_text(
        "<!doctype html><meta charset=utf-8><title>Lokaty: migawka ofert</title>"
        f"<p>Migawka ofert z {snapshot['snapshot_date']}, rewizja {snapshot['revision']}, banki: {len(snapshot['banks'])}, oferty: {len(snapshot['offers'])}.</p>"
        "<p><a href=snapshot.json>snapshot.json</a> · <a href=snapshot.sha256>suma kontrolna</a></p>\n",
        encoding="utf-8",
    )
    return digest


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="site")
    ap.add_argument("--allow-empty", action="store_true")
    a = ap.parse_args(argv)
    from validate_snapshots import validate  # noqa: E402  (tools/ dodane do sys.path wyżej)

    revision = int(os.environ.get("GITHUB_RUN_NUMBER", "1"))
    snap = build(ROOT / "backend" / "banks.json", ROOT / "backend" / "offers", revision=revision)
    if not snap["offers"] and not a.allow_empty:
        print("Brak zweryfikowanych ofert: nic nie publikuję.")
        return 2
    errors = validate(snap)
    if errors:
        print("Migawka niezgodna ze schematem:")
        for e in errors:
            print(" -", e)
        return 1
    digest = write(snap, ROOT / a.out)
    print(f"Opublikowano: {len(snap['offers'])} ofert, {len(snap['banks'])} banków, rewizja {revision}, sha256 {digest[:12]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

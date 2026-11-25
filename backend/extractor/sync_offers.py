"""Synchronizacja zweryfikowanych ofert ze stanem stron (wynik parsers.py).

Reguły (zatwierdzone przez użytkownika 25.11.2026):
- zmieniła się TYLKO stawka istniejącej oferty (termin, kwoty i daty bez zmian), nowa stawka w
  granicach rozsądku -> oferta aktualizowana automatycznie;
- wszystko inne (inny termin/kwota/daty, oferta zniknęła ze strony, parser nic nie znalazł,
  skok stawki) -> tylko raport „WYMAGA PRZEGLĄDU”, oferta bez zmian;
- oferta znaleziona na stronie, której nie mamy -> szkic w backend/offers/draft/auto/.
Zawsze kończy się kodem 0: błąd parsera nie może zatrzymać reszty workflow."""
import json
import sys
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
BACKEND = HERE.parent
sys.path.insert(0, str(HERE))
import parsers  # noqa: E402

MAX_RATE = Decimal("10")
MAX_JUMP = Decimal("1.00")  # punkty procentowe; większa zmiana zawsze do człowieka
COMPARED = ("term", "amount_min", "amount_max", "valid_from", "valid_until")


def now_iso(now):
    return now.strftime("%Y-%m-%dT%H:%M:%SZ")


def load_offers(offers_dir):
    out = {}
    for p in sorted(offers_dir.glob("*.json")):
        d = json.loads(p.read_text(encoding="utf-8"))
        if isinstance(d, dict) and "offer_id" in d:
            out[d["offer_id"]] = (p, d)
    return out


def save(path, offer):
    path.write_text(json.dumps(offer, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def draft_from_parsed(offer_id, parsed, source_url, now):
    bank = offer_id.split("/")[0]
    o = {"offer_id": offer_id, "bank_id": bank, "product_name": offer_id.split("/")[1],
         "verification": "draft", "term": parsed["term"], "interest_type": "at_maturity",
         "rate_annual": parsed["rate_annual"], "tax_timing": "at_end", "day_count_rule": "unknown",
         "rounding": {"source": "default", "mode": "half_up"},
         "conditions": {"new_customer_only": "unknown", "new_funds_only": "unknown", "account_required": "unknown",
                        "one_time_only": "unknown", "notes": ["Szkic automatyczny z parsera: warunki do uzupełnienia przez człowieka"]},
         "early_termination": {"allowed": "unknown", "interest_treatment": "bank_specific"},
         "availability_rule": "next_business_day", "source_url": source_url,
         "retrieved_at": now_iso(now), "last_verified_at": now_iso(now), "completeness": "partial",
         "missing": ["conditions", "early_termination", "day_count_rule"]}
    for k in ("amount_min", "amount_max", "valid_from", "valid_until"):
        if parsed.get(k):
            o[k] = parsed[k]
    return o


def fresh_sources(state_dir, now):
    """Strony pobrane poprawnie dzisiaj (UTC). Brak pliku albo stara data = nic nie jest świeże."""
    p = state_dir / "last_run.json"
    if not p.exists():
        return set()
    d = json.loads(p.read_text(encoding="utf-8"))
    if d.get("run_at", "")[:10] != now.strftime("%Y-%m-%d"):
        return set()
    return set(d.get("fresh", []))


def run(backend=BACKEND, state_dir=None, now=None, write=True, fresh=None):
    now = now or datetime.now(timezone.utc)
    state_dir = state_dir or backend / "collector" / "state"
    fresh = fresh_sources(state_dir, now) if fresh is None else set(fresh)
    offers_dir = backend / "offers"
    sources = {s["id"]: s for s in json.loads((backend / "collector" / "sources.json").read_text(encoding="utf-8"))["sources"]}
    offers = load_offers(offers_dir)
    updated, confirmed, review, drafts, notes = [], [], [], [], []

    for sid, (fn, prefix) in parsers.PARSERS.items():
        path = state_dir / f"{sid}.txt"
        if sid not in fresh:
            notes.append(f"{sid}: pominięta, bo strona nie została poprawnie pobrana w tym przebiegu (oferty bez zmian)")
            continue
        if not path.exists():
            notes.append(f"{sid}: brak zapisanej strony (pierwszy przebieg wykrywacza jeszcze się nie odbył)")
            continue
        try:
            parsed = fn(path.read_text(encoding="utf-8"))
        except Exception as e:  # parser nie może zatrzymać reszty
            parsed = {}
            notes.append(f"{sid}: błąd parsera {type(e).__name__}: {e}")
        mine = {i: v for i, v in offers.items() if i.startswith(prefix) and v[1].get("verification") == "verified"}
        if not parsed:
            if mine:
                review.append(f"{sid}: parser niczego nie znalazł na stronie (zmienił się układ albo strona niedostępna); oferty bez zmian: {', '.join(sorted(mine))}")
            continue
        for oid, (p, o) in sorted(mine.items()):
            f = parsed.get(oid)
            if f is None:
                review.append(f"{oid}: nie ma już na stronie banku (promocja zakończona? nowa edycja?); oferta bez zmian")
                continue
            diffs = [f"{k}: {o.get(k)} -> {f[k]}" for k in COMPARED if f.get(k) is not None and o.get(k) != f[k]]
            if diffs:
                review.append(f"{oid}: zmiana warunków ({'; '.join(diffs)}); oferta bez zmian")
                continue
            old, new = Decimal(o["rate_annual"]), Decimal(f["rate_annual"])
            if old != new:
                if not (Decimal("0") < new <= MAX_RATE) or abs(new - old) > MAX_JUMP:
                    review.append(f"{oid}: podejrzana zmiana stawki {old}% -> {new}% (poza granicami); oferta bez zmian")
                    continue
                o["rate_annual"] = f["rate_annual"]
                o["retrieved_at"] = o["last_verified_at"] = now_iso(now)
                if write:
                    save(p, o)
                updated.append(f"{oid}: stawka {old}% -> {new}% (aktualizacja automatyczna)")
            else:
                o["retrieved_at"] = o["last_verified_at"] = now_iso(now)
                if write:
                    save(p, o)
                confirmed.append(oid)
        known = set(offers)
        for oid, f in sorted(parsed.items()):
            if oid in known:
                continue
            drafts.append(oid)
            if write:
                d = backend / "offers" / "draft" / "auto"
                d.mkdir(parents=True, exist_ok=True)
                save(d / (oid.replace("/", "__") + ".json"), draft_from_parsed(oid, f, sources[sid]["url"], now))

    review += [f"nowa oferta na stronie: {oid} (szkic w backend/offers/draft/auto/, do uzupełnienia i zatwierdzenia)" for oid in drafts]
    return {"updated": updated, "confirmed": confirmed, "review": review, "drafts": drafts, "notes": notes}


def report(r, now):
    L = [f"# Synchronizacja ofert {now.strftime('%Y-%m-%d %H:%M')} UTC", ""]
    L.append("## WYMAGA PRZEGLĄDU" if r["review"] else "## Nic nie wymaga przeglądu")
    L += [f"- {x}" for x in r["review"]] + [""]
    L += ["## Zaktualizowane automatycznie"] + ([f"- {x}" for x in r["updated"]] or ["- brak"]) + [""]
    L += ["## Nowe szkice (do zatwierdzenia)"] + ([f"- {x}" for x in r["drafts"]] or ["- brak"]) + [""]
    L += ["## Potwierdzone bez zmian", ", ".join(r["confirmed"]) or "brak", ""]
    if r["notes"]:
        L += ["## Uwagi"] + [f"- {x}" for x in r["notes"]] + [""]
    return "\n".join(L)


def main():
    now = datetime.now(timezone.utc)
    r = run(now=now)
    text = report(r, now)
    (HERE / "sync-report.md").write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())

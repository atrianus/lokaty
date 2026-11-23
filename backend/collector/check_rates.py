"""Kontrola stawek: czy oprocentowanie zapisane w ofertach nadal widnieje na stronie banku.

Dla każdej oferty (zweryfikowanej i szkicu) szuka jej stawki (np. 3,40 lub 3,4) z znakiem % w tekście strony
zapisanym przez wykrywacz zmian (backend/collector/state/<id>.txt). Strona jest dopasowana po adresie źródła
(source_url oferty = url w sources.json). To kontrola, nie automatyczna zmiana: wynik trafia do raportu dla człowieka.

Statusy: ok (stawka jest na stronie), missing (stawki nie ma: zmiana albo błąd w ofercie), no_page (brak zapisanej strony
albo oferta bez źródła w sources.json), skipped (oferta bez stawki).
"""
import json
import re
import sys
from decimal import Decimal, InvalidOperation
from pathlib import Path

HERE = Path(__file__).resolve().parent
BACKEND = HERE.parent
CONTEXT = re.compile(r"(\d{1,2}(?:[.,]\d{1,2})?)\s*%")


def rate_forms(rate):
    """Zapisy stawki do szukania: 3.40 -> {'3,40', '3,4'} (oraz z kropką); 3 -> {'3', '3,00'}."""
    d = Decimal(rate)
    two = f"{d:.2f}"
    forms = {two, two.rstrip("0").rstrip(".")}
    return forms | {f.replace(".", ",") for f in forms}


def rates_on_page(text):
    """Wszystkie stawki procentowe znalezione na stronie (jako Decimal), do kontekstu w raporcie."""
    out = set()
    for m in CONTEXT.finditer(text):
        try:
            out.add(Decimal(m.group(1).replace(",", ".")))
        except InvalidOperation:
            pass
    return sorted(out)


def check_offer(offer, text):
    rate = offer.get("rate_annual")
    if rate is None:
        return "skipped", "oferta bez stałej stawki"
    found = rates_on_page(text)
    pattern = "|".join(re.escape(f) for f in sorted(rate_forms(rate), key=len, reverse=True))
    if re.search(rf"(?<![\d,.])(?:{pattern})\s*%", text):
        return "ok", f"stawka {rate}% jest na stronie"
    shown = ", ".join(str(r).replace(".", ",") + "%" for r in found[:12]) or "brak"
    return "missing", f"stawki {rate}% nie ma na stronie; na stronie: {shown}"


def load_offers(backend):
    files = sorted((backend / "offers").glob("*.json")) + sorted((backend / "offers" / "draft").glob("*.json"))
    offers = []
    for p in files:
        d = json.loads(p.read_text(encoding="utf-8"))
        offers += d["offers"] if isinstance(d, dict) and "offers" in d else [d]
    return offers


def run(backend=BACKEND, state_dir=None):
    state_dir = state_dir or (backend / "collector" / "state")
    sources = json.loads((backend / "collector" / "sources.json").read_text(encoding="utf-8"))["sources"]
    by_url = {s["url"]: s for s in sources}
    results, seen = [], set()
    for o in load_offers(backend):
        if o["offer_id"] in seen:
            continue  # ta sama oferta w pliku szkiców zbiorczym i osobnym pliku
        seen.add(o["offer_id"])
        src = by_url.get(o.get("source_url"))
        path = state_dir / f"{src['id']}.txt" if src else None
        if src is None or not path.exists():
            results.append((o["offer_id"], o["verification"], "no_page", "brak zapisanej strony źródłowej"))
            continue
        status, detail = check_offer(o, path.read_text(encoding="utf-8"))
        results.append((o["offer_id"], o["verification"], status, detail))
    return results


def report(results):
    order = {"missing": 0, "no_page": 1, "skipped": 2, "ok": 3}
    lines = ["# Kontrola stawek ofert", ""]
    for oid, ver, st, detail in sorted(results, key=lambda r: (order[r[2]], r[0])):
        lines.append(f"- **{st}** `{oid}` ({ver}): {detail}")
    return "\n".join(lines) + "\n"


def main():
    res = run()
    text = report(res)
    (HERE / "rates-report.md").write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())

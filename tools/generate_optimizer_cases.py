"""Generuje test-data/engine/optimizer-cases.json. Uruchom: python tools/generate_optimizer_cases.py"""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from datetime import date
from decimal import Decimal as D
from optimizer_reference import search

def off(id, months, rate, vf="2026-11-01", vu=None, **kw):
    o = {"id": id, "bank_id": id.split("/")[0], "term_months": months, "rate": rate, "valid_from": vf}
    if vu: o["valid_until"] = vu
    o.update(kw); return o

S = date(2026, 11, 16)
cases = []
def add(id, desc, offers, horizon, limits=None, rejected=(), locked=None, capital="10000.00", note=None):
    lim = {"max_legs": 3, "max_gap_days": 7, "max_shown": 4, **(limits or {})}
    exp = search(offers, D(capital), S, date.fromisoformat(horizon), lim["max_legs"], lim["max_gap_days"], lim["max_shown"], rejected, locked)
    c = {"id": id, "description": desc, "input": {"offers": offers, "capital": capital, "start": S.isoformat(), "horizon_end": horizon,
         "limits": lim, "rejected": list(rejected), "locked": locked or []}, "expected": exp}
    if note: c["note"] = note
    cases.append(c)

A = off("bank-a/a-3m", 3, "5.00"); C = off("bank-a/a-6m", 6, "5.20")
add("O1", "dwie lokaty 3-miesięczne kontra jedna 6-miesięczna", [A, C], "2027-06-30")
add("O2", "A+B lepsza niż A+A, gdy B ma wyższą stawkę i zaczyna ważność w dniu dostępności", [A, off("bank-b/b-3m", 3, "5.50", vf="2027-02-17")], "2027-06-30")
add("O3", "oferta ważna w dniu startu, ale nie w dniu założenia drugiej nogi: tylko pierwsza noga", [off("bank-d/d-3m", 3, "6.00", vu="2026-11-30")], "2027-06-30")
add("O4", "żadna oferta nie jest ważna w dniu drugiej nogi: tylko strategie jednonożne", [A | {"valid_until": "2026-12-31"}, C | {"valid_until": "2026-12-31"}], "2027-12-31")
add("O5", "luka 7 dni dopuszczona (E), luka 8 dni odrzucona (F)", [off("bank-e/e-3m", 3, "5.50", vf="2027-02-24"), off("bank-f/f-3m", 3, "5.50", vf="2027-02-25"), A | {"valid_until": "2026-12-31"}], "2027-06-30")
add("O6", "strategia przekraczająca horyzont z dodatkowymi dniami i zyskiem", [A], "2027-03-31")
add("O9", "zawarta noga zostaje bez przeliczenia, kontynuacja liczona od jej dostępności",
    [off("bank-a/a-3m", 3, "6.00"), off("bank-b/b-3m", 3, "5.50")], "2027-06-30",
    locked=[{"offer_id": "bank-a/a-3m", "start": "2026-11-16", "amount": "10000.00", "payout": "10102.08", "available_date": "2027-02-17"}],
    note="Zawarta noga ma stawkę 5,00% (wypłata 10102.08), mimo że oferta a-3m ma teraz 6,00%.")
add("O10", "oferta jednorazowa nie wchodzi dwa razy", [off("bank-g/g-3m", 3, "6.00", one_time_only="yes"), A], "2027-06-30")
add("O11", "kwota poza przedziałem oferty odrzuca ofertę dla nogi, nie całą strategię",
    [off("bank-h/h-3m", 3, "6.00", amount_max="10050.00"), A, off("bank-i/i-3m", 3, "7.00", amount_min="20000.00")], "2027-06-30")
add("O12", "remis: identyczne oferty, kolejność deterministyczna", [off("bank-x/x-3m", 3, "5.00"), off("bank-y/y-3m", 3, "5.00")], "2027-02-28", limits={"max_legs": 1})
out = {"note": "Obliczenie referencyjne (pełne przeszukanie bez przycinania), nie porównanie z bankiem. Generowane przez tools/generate_optimizer_cases.py", "cases": cases}
Path(__file__).resolve().parents[1].joinpath("test-data/engine/optimizer-cases.json").write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(len(cases), "przypadków")
for c in cases:
    e = c["expected"]; print(c["id"], [(x["legs"], x["final_value"]) for x in e["in_horizon"][:2]], "over:", [(x["legs"], x["extra_days"], x["extra_profit"]) for x in e["over_horizon"][:1]])

"""Generuje test-data/engine/cases.json z obliczenia referencyjnego. Uruchom: python tools/generate_engine_cases.py"""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from engine_reference import *

S = lambda x: str(x)
iso = lambda d: d.isoformat()
OFFER = lambda **kw: {"interest_type": "at_maturity", "rate_annual": "5.00", "term": {"value": 3, "unit": "months"},
    "day_count_rule": "actual_365", "rounding": {"source": "default", "mode": "half_up"},
    "availability_rule": "next_business_day", **kw}
K = D(10000)
cases = []
def add(id, desc, status, inp, exp): cases.append({"id": id, "description": desc, "rule_status": status, "input": inp, "expected": exp})

s = date(2026, 11, 16); e = add_months(s, 3)
n, i, t, net = at_maturity(K, D(5), s, e)
add("K1", "at_maturity, 3 mies., actual_365", "założenie (daty i liczba dni)",
    {"offer": OFFER(), "amount": "10000.00", "start_date": iso(s), "scenario": "maturity"},
    {"maturity_date": iso(e), "available_date": iso(next_business_day(e)), "days": n, "interest_gross": S(i), "tax": S(t), "interest_net": S(net), "payout": S(K + net), "confidence": "approximate"})

s2 = date(2027, 3, 1); e2 = add_months(s2, 12)
for rule, cid in (("actual_365", "K2a"), ("actual_actual", "K2b")):
    n, i, t, net = at_maturity(K, D(5), s2, e2, rule)
    add(cid, f"at_maturity, 12 mies. przez rok przestępny, {rule}", "założenie" if rule == "actual_actual" else "założenie (daty)",
        {"offer": OFFER(day_count_rule=rule, term={"value": 12, "unit": "months"}), "amount": "10000.00", "start_date": iso(s2), "scenario": "maturity"},
        {"maturity_date": iso(e2), "days": n, "interest_gross": S(i), "tax": S(t), "interest_net": S(net), "confidence": "approximate"})

add("K3", "zaokrąglenie podatku w górę do grosza", "art. 63 §1a Ordynacji, do potwierdzenia",
    {"tax_of_interest": ["10.01", "0.01", "100.00"]},
    {"results": [{"interest": x, "tax": S(tax(D(x))), "net": S(D(x) - tax(D(x)))} for x in ("10.01", "0.01", "100.00")]})

for each, cid in ((True, "K4a"), (False, "K4b")):
    g, t, net = capitalized_monthly(K, D(6), s, 3, each)
    add(cid, "capitalized monthly, 6%, 3 mies., podatek " + ("przy każdej kapitalizacji" if each else "na końcu"), "moment podatku do decyzji",
        {"offer": OFFER(interest_type="capitalized", capitalization_period="monthly", rate_annual="6.00", tax_timing="each_capitalization" if each else "at_end"),
         "amount": "10000.00", "start_date": iso(s), "scenario": "maturity"},
        {"interest_gross": S(g), "tax": S(t), "interest_net": S(net), "payout": S(K + net), "confidence": "approximate"})

b = s + timedelta(40); n, i, t, net = at_maturity(K, D(5), s, b)
brk = lambda tr: {"offer": OFFER(early_termination={"allowed": "yes", "interest_treatment": tr}), "amount": "10000.00", "start_date": iso(s), "scenario": {"break_on": iso(b)}}
add("K5", "zerwanie proportional po 40 dniach", "przybliżony", brk("proportional"),
    {"days": n, "interest_gross": S(i), "tax": S(t), "interest_net": S(net), "payout": S(K + net), "confidence": "approximate"})
add("K6", "zerwanie lose_all", "dokładny", brk("lose_all"), {"interest_gross": "0.00", "tax": "0.00", "interest_net": "0.00", "payout": "10000.00", "confidence": "exact"})
add("K7", "zerwanie unknown: dwa wyniki", "niezweryfikowany", brk("unknown"),
    {"conservative": {"payout": "10000.00"}, "optimistic": {"payout": S(K + net)}, "labels": ["early_termination_unknown"], "confidence": "unverified"})

av = [date(2027, 2, 19), date(2026, 12, 23), date(2027, 3, 26)]
add("K8", "następny dzień roboczy", "lista świąt ze źródła", {"maturity_dates": [iso(d) for d in av]},
    {"available_dates": [iso(next_business_day(d)) for d in av]})
me = [(date(2027, 1, 31), 1), (date(2026, 11, 30), 3), (date(2027, 8, 31), 6)]
add("K9", "koniec lokaty w miesiącach", "założenie", {"cases": [{"start": iso(a), "months": m} for a, m in me]},
    {"maturity_dates": [iso(add_months(a, m)) for a, m in me]})
ys = [2026, 2027, 2028]
add("K10", "Wielkanoc i Boże Ciało", "algorytm", {"years": ys},
    {"easter": [iso(easter(y)) for y in ys], "corpus_christi": [iso(easter(y) + timedelta(60)) for y in ys]})
add("K11", "kwota poza przedziałem oferty", "dokładny",
    {"offer": OFFER(amount_min="1000.00"), "amount": "500.00", "start_date": iso(s), "scenario": "maturity"},
    {"errors": ["amount_below_min"], "result": None})

out = {"note": "Obliczenie referencyjne (Decimal), nie porównanie z kalkulatorem banku. Generowane przez tools/generate_engine_cases.py", "cases": cases}
Path(__file__).resolve().parents[1].joinpath("test-data/engine/cases.json").write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(len(cases), "przypadków")

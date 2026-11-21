"""Obliczenie referencyjne optymalizatora: pełne przeszukanie bez przycinania (Decimal).
Reguły: dokument „Etap 6: projekt optymalizatora strategii”. Obsługuje oferty at_maturity."""
from datetime import date
from decimal import Decimal as D
from engine_reference import add_months, at_maturity, next_business_day

def search(offers, capital, start, horizon, max_legs=3, max_gap=7, max_shown=4, rejected=(), locked=None):
    out = []
    def rec(avail, cap, legs, used, depth):
        for o in sorted(offers, key=lambda x: x["id"]):
            if o.get("one_time_only") == "yes" and o["id"] in used: continue
            s = max(avail, date.fromisoformat(o["valid_from"]))
            if (s - avail).days > max_gap: continue
            if o.get("valid_until") and s > date.fromisoformat(o["valid_until"]): continue
            if o.get("amount_min") and cap < D(o["amount_min"]): continue
            if o.get("amount_max") and cap > D(o["amount_max"]): continue
            end = add_months(s, o["term_months"])
            _, _, _, net = at_maturity(cap, D(o["rate"]), s, end)
            av = next_business_day(end)
            new = legs + [(o["id"], s)]
            out.append((new, cap + net, av))
            if depth + 1 < max_legs and av <= horizon:
                rec(av, cap + net, new, used | ({o["id"]} if o.get("one_time_only") == "yes" else set()), depth + 1)
    base = []
    cap, avail, used = capital, start, set()
    if locked:
        for l in locked:
            base.append((l["offer_id"], date.fromisoformat(l["start"])))
        cap = D(l["payout"]); avail = date.fromisoformat(l["available_date"])
    rec(avail, cap, base, used, 0)
    key = lambda t: (-t[1], t[2], len(t[0]), [x[0] for x in t[0]])
    out.sort(key=key)
    def fp(t): return "|".join(f"{i}@{s.isoformat()}" for i, s in t[0])
    inh = [t for t in out if t[2] <= horizon and fp(t) not in rejected]
    over = [t for t in out if t[2] > horizon and fp(t) not in rejected]
    best = inh[0][1] if inh else None
    if best is not None: over = [t for t in over if t[1] > best]
    fmt = lambda t: {"legs": [i for i, _ in t[0]], "starts": [s.isoformat() for _, s in t[0]], "final_value": str(t[1]), "available_date": t[2].isoformat()}
    res = {"in_horizon": [fmt(t) for t in inh[:max_shown]], "over_horizon": [fmt(t) for t in over[:max_shown]]}
    for t, r in zip(over[:max_shown], res["over_horizon"]):
        r["extra_days"] = (t[2] - horizon).days
        r["extra_profit"] = str(t[1] - best) if best is not None else None
    res["all_in_horizon"] = [{"legs": [i for i, _ in t[0]], "final_value": str(t[1])} for t in inh]
    res["all_over_horizon"] = [{"legs": [i for i, _ in t[0]], "final_value": str(t[1])} for t in over]
    return res

"""Obliczenie referencyjne silnika (Decimal), źródło wartości w test-data/engine/cases.json.
Reguły: dokument „Specyfikacja silnika finansowego”. To NIE jest porównanie z kalkulatorem banku."""
import calendar
from datetime import date, timedelta
from decimal import Decimal as D, ROUND_HALF_UP, ROUND_UP

def q(x, mode=ROUND_HALF_UP): return x.quantize(D("0.01"), rounding=mode)
def tax(i): return q(i * D("0.19"), ROUND_UP)

def add_months(d, m):
    y = d.year + (d.month - 1 + m) // 12
    mo = (d.month - 1 + m) % 12 + 1
    return date(y, mo, min(d.day, calendar.monthrange(y, mo)[1]))

def easter(y):
    a = y % 19; b = y // 100; c = y % 100; d = b // 4; e = b % 4; f = (b + 8) // 25; g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30; i = c // 4; k = c % 4; l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    return date(y, (h + l - 7 * m + 114) // 31, (h + l - 7 * m + 114) % 31 + 1)

def holidays(y):
    e = easter(y)
    fixed = [(1, 1), (1, 6), (5, 1), (5, 3), (8, 15), (11, 1), (11, 11), (12, 25), (12, 26)]
    if y >= 2025: fixed.append((12, 24))
    return {date(y, m, d) for m, d in fixed} | {e, e + timedelta(1), e + timedelta(49), e + timedelta(60)}

def next_business_day(d):
    d += timedelta(1)
    while d.weekday() >= 5 or d in holidays(d.year): d += timedelta(1)
    return d

def at_maturity(k, rate, start, end, rule="actual_365"):
    n = (end - start).days
    if rule == "actual_365":
        raw = k * rate / 100 * n / 365
    else:
        ny = date(start.year + 1, 1, 1)
        raw = k * rate / 100 * (D((ny - start).days) / 365 + D((end - ny).days) / 366)
    i = q(raw); t = tax(i)
    return n, i, t, i - t

def capitalized_monthly(k, rate, start, months, tax_each=True):
    pts = [add_months(start, m) for m in range(months + 1)]
    bal = k; gross = D(0); taxes = []
    for a, b in zip(pts, pts[1:]):
        i = q(bal * rate / 100 * (b - a).days / 365); gross += i
        if tax_each:
            t = tax(i); taxes.append(t); bal += i - t
        else:
            bal += i
    if not tax_each:
        t = tax(gross); return gross, t, gross - t
    return gross, sum(taxes), bal - k

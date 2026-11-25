"""Parsery deterministyczne: z tekstu strony banku (zapisanego przez wykrywacz zmian)
wyciągają stawkę, termin, kwoty i daty. Tylko biblioteka standardowa.

Każdy parser zwraca {offer_id: {pola}}. Pusty wynik oznacza „strona się zmieniła”,
nigdy „oferty nie ma”: nic się wtedy nie zmienia automatycznie, tylko trafia do raportu."""
import re
from datetime import date
from decimal import Decimal

MONTHS = {"stycznia": 1, "lutego": 2, "marca": 3, "kwietnia": 4, "maja": 5, "czerwca": 6, "lipca": 7,
          "sierpnia": 8, "września": 9, "października": 10, "listopada": 11, "grudnia": 12}


def norm(text):
    return re.sub(r"\s+", " ", text.replace("\xa0", " "))


def rate(s):
    """„3,4” -> „3.40”"""
    return str(Decimal(s.replace(",", ".")).quantize(Decimal("0.01")))


def money(s):
    digits = re.sub(r"\D", "", s)
    return digits or None


def pl_date(day, month_name, year):
    return date(int(year), MONTHS[month_name.lower()], int(day)).isoformat()


def alior_nowe_srodki(text):
    t = norm(text)
    m = re.search(r"Oprocentowanie lokaty wynosi:(.{0,200}?)Lokata", t) or re.search(r"Oprocentowanie lokaty wynosi:(.{0,200})", t)
    if not m:
        return {}
    pairs = re.findall(r"(\d+(?:,\d+)?)\s*%\s*na\s*(\d+)\s*dni", m.group(1))
    lo = re.search(r"Minimalna kwota lokaty wynosi\s*([\d ]+?)\s*zł", t)
    hi = re.search(r"oddzielnie dla każdego z dostępnych okresów, wynosi\s*([\d ]+?)\s*zł", t)
    out = {}
    for r, days in pairs:
        out[f"alior/nowe-srodki-{days}d"] = {
            "rate_annual": rate(r), "term": {"value": int(days), "unit": "days"},
            "amount_min": money(lo.group(1)) if lo else None, "amount_max": money(hi.group(1)) if hi else None}
    return out


def pko_bp_nowe_srodki(text):
    t = norm(text)
    pairs = re.findall(r"(\d+(?:,\d+)?)\s*%\s*w skali roku\s*na\s*(\d+)\s*mies", t)
    amt = re.search(r"od\s*(\d+)\s*tys\. zł\s*do\s*maks\.\s*(\d+)\s*tys\. zł", t)
    per = re.search(r"Okres oferowania\s*(\d{2})\.(\d{2})-(\d{2})\.(\d{2})\.(\d{4})", t)
    valid_from = valid_until = None
    if per:
        d1, m1, d2, m2, y = map(int, per.groups())
        valid_until = date(y, m2, d2).isoformat()
        valid_from = date(y - 1 if m1 > m2 else y, m1, d1).isoformat()
    out = {}
    for r, months in pairs:
        f = {"rate_annual": rate(r), "term": {"value": int(months), "unit": "months"},
             "amount_min": str(int(amt.group(1)) * 1000) if amt else None,
             "amount_max": str(int(amt.group(2)) * 1000) if amt else None}
        if valid_from:
            f["valid_from"], f["valid_until"] = valid_from, valid_until
        out[f"pko-bp/nowe-srodki-{months}m"] = f
    return out


def mbank_promo_3m(text):
    t = norm(text)
    hi_rate = re.search(r"(\d+,\d+)\s*%\s*w skali roku\s*[-–]\s*jeśli w chwili składania wniosku o lokatę promocyjną jesteś posiadaczem", t)
    lo_rate = re.search(r"(\d+,\d+)\s*%\s*w skali roku\s*[-–]\s*jeśli w chwili składania wniosku o lokatę promocyjną nie jesteś posiadaczem", t)
    lo = re.search(r"minimalną kwotę jednej lokaty\s*([\d ]+?)\s*PLN", t)
    hi = re.search(r"Maksymalna kwota wszystkich lokat promocyjnych, które możesz założyć w ramach promocji:\s*([\d ]+?)\s*PLN", t)
    until = re.search(r"Do promocji możesz przystąpić do (\d{2})\.(\d{2})\.(\d{4})", t)
    base = {"term": {"value": 3, "unit": "months"},
            "amount_min": money(lo.group(1)) if lo else None, "amount_max": money(hi.group(1)) if hi else None}
    if until:
        base["valid_until"] = date(int(until.group(3)), int(until.group(2)), int(until.group(1))).isoformat()
    out = {}
    if hi_rate:
        out["mbank/promo-3m-intensive"] = {**base, "rate_annual": rate(hi_rate.group(1))}
    if lo_rate:
        out["mbank/promo-3m-standard"] = {**base, "rate_annual": rate(lo_rate.group(1))}
    return out


def pekao_lokuj_z_nami_7(text):
    t = norm(text)
    r = re.search(r"(\d+(?:,\d+)?)\s*%\s*w skali roku na\s*(\d+)\s*miesi", t)
    lo = re.search(r"minimalna kwota lokaty to\s*([\d. ]+?)\s*zł", t)
    hi = re.search(r"maksymalnie na lokatę możesz wpłacić\s*([\d. ]+?)\s*zł", t)
    frm = re.search(r"przystąpić od (\d{1,2}) (\w+) (\d{4}) r\.", t)
    until = re.search(r"umowę lokaty możesz zawrzeć do (\d{1,2}) (\w+) (\d{4}) r\.", t)
    if not r:
        return {}
    f = {"rate_annual": rate(r.group(1)), "term": {"value": int(r.group(2)), "unit": "months"},
         "amount_min": money(lo.group(1)) if lo else None, "amount_max": money(hi.group(1)) if hi else None}
    try:
        if frm:
            f["valid_from"] = pl_date(*frm.groups())
        if until:
            f["valid_until"] = pl_date(*until.groups())
    except (KeyError, ValueError):
        pass
    return {"pekao/lokuj-z-nami-7": f}


# source_id -> (parser, prefiks offer_id obsługiwany przez parser)
PARSERS = {
    "alior-nowe-srodki": (alior_nowe_srodki, "alior/nowe-srodki-"),
    "pko-bp-nowe-srodki": (pko_bp_nowe_srodki, "pko-bp/nowe-srodki-"),
    "mbank-promo-3m": (mbank_promo_3m, "mbank/promo-3m-"),
    "pekao-lokuj-z-nami-7": (pekao_lokuj_z_nami_7, "pekao/lokuj-z-nami-"),
}

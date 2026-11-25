"""Testy parserów, synchronizacji ofert i ekstrakcji modelem (bez sieci)."""
import json
import shutil
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "backend" / "extractor"))
sys.path.insert(0, str(ROOT / "backend" / "collector"))
import llm_extract  # noqa: E402
import parsers  # noqa: E402
import sync_offers  # noqa: E402

FIX = ROOT / "test-data" / "extractor"
NOW = datetime(2026, 11, 25, 5, 30, tzinfo=timezone.utc)
FIELDS = ("rate_annual", "term", "amount_min", "amount_max", "valid_from", "valid_until")


def page(sid):
    return (FIX / f"{sid}.txt").read_text(encoding="utf-8")


class ParsersTest(unittest.TestCase):
    def test_parsery_zgadzaja_sie_ze_zweryfikowanymi_ofertami(self):
        offers = {}
        for p in (ROOT / "backend" / "offers").glob("*.json"):
            d = json.loads(p.read_text(encoding="utf-8"))
            offers[d["offer_id"]] = d
        for sid, (fn, prefix) in parsers.PARSERS.items():
            parsed = fn(page(sid))
            self.assertTrue(parsed, sid)
            for oid, f in parsed.items():
                self.assertIn(oid, offers)
                for k in FIELDS:
                    if f.get(k) is not None:
                        self.assertEqual(f[k], offers[oid].get(k), f"{oid} {k}")

    def test_pusta_strona_daje_pusty_wynik(self):
        for fn, _ in parsers.PARSERS.values():
            self.assertEqual(fn("Strona w przebudowie. Wróć później."), {})

    def test_alior_trzy_terminy(self):
        self.assertEqual(sorted(parsers.alior_nowe_srodki(page("alior-nowe-srodki"))),
                         ["alior/nowe-srodki-182d", "alior/nowe-srodki-365d", "alior/nowe-srodki-92d"])

    def test_pko_okres_przez_rok(self):
        r = parsers.pko_bp_nowe_srodki("3,3% w skali roku na 3 mies. od 1 tys. zł do maks. 250 tys. zł Okres oferowania 15.12-14.01.2027 r.".replace("14.01.2027 r.", "14.01.2027"))
        self.assertEqual(r["pko-bp/nowe-srodki-3m"]["valid_from"], "2026-12-15")
        self.assertEqual(r["pko-bp/nowe-srodki-3m"]["valid_until"], "2027-01-14")


class SyncTest(unittest.TestCase):
    def setUp(self):
        self.b = Path(tempfile.mkdtemp())
        (self.b / "collector" / "state").mkdir(parents=True)
        shutil.copytree(ROOT / "backend" / "offers", self.b / "offers", ignore=shutil.ignore_patterns("draft"))
        shutil.copy(ROOT / "backend" / "collector" / "sources.json", self.b / "collector" / "sources.json")
        for sid in parsers.PARSERS:
            shutil.copy(FIX / f"{sid}.txt", self.b / "collector" / "state" / f"{sid}.txt")
        self.fresh = set(parsers.PARSERS)

    def offer(self, oid):
        return json.loads((self.b / "offers" / (oid.replace("/", "__") + ".json")).read_text(encoding="utf-8"))

    def edit_page(self, sid, old, new):
        p = self.b / "collector" / "state" / f"{sid}.txt"
        t = p.read_text(encoding="utf-8")
        self.assertIn(old, t)
        p.write_text(t.replace(old, new), encoding="utf-8")

    def sync(self, **kw):
        return sync_offers.run(self.b, now=NOW, fresh=kw.pop("fresh", self.fresh), **kw)

    def test_bez_zmian_potwierdza(self):
        r = self.sync()
        self.assertEqual(r["review"], [])
        self.assertEqual(r["updated"], [])
        self.assertEqual(len(r["confirmed"]), 8)
        self.assertEqual(self.offer("alior/nowe-srodki-92d")["last_verified_at"], "2026-11-25T05:30:00Z")

    def test_sama_zmiana_stawki_aktualizuje_automatycznie(self):
        self.edit_page("alior-nowe-srodki", "3,6% na 92 dni", "3,5% na 92 dni")
        r = self.sync()
        self.assertEqual(self.offer("alior/nowe-srodki-92d")["rate_annual"], "3.50")
        self.assertEqual(len(r["updated"]), 1)
        self.assertEqual(r["review"], [])

    def test_zmiana_kwoty_nie_aktualizuje_tylko_raportuje(self):
        import re
        f = self.b / "collector" / "state" / "alior-nowe-srodki.txt"
        t = f.read_text(encoding="utf-8")
        t2 = re.sub(r"(wynosi\s)300[\s\xa0]000", r"\g<1>200 000", t)
        self.assertNotEqual(t, t2)
        f.write_text(t2, encoding="utf-8")
        r = self.sync()
        self.assertTrue(any("amount_max" in x for x in r["review"]))
        self.assertEqual(self.offer("alior/nowe-srodki-92d")["amount_max"], "300000")

    def test_skok_stawki_o_wiecej_niz_punkt_do_przegladu(self):
        self.edit_page("alior-nowe-srodki", "3,6% na 92 dni", "5,5% na 92 dni")
        r = self.sync()
        self.assertTrue(any("podejrzana" in x for x in r["review"]))
        self.assertEqual(self.offer("alior/nowe-srodki-92d")["rate_annual"], "3.60")

    def test_oferta_zniknela_ze_strony(self):
        self.edit_page("alior-nowe-srodki", "3,6% na 92 dni; ", "")
        r = self.sync()
        self.assertTrue(any("alior/nowe-srodki-92d" in x and "nie ma już" in x for x in r["review"]))

    def test_nowa_edycja_alior_zmienia_stawke_i_dzien_odniesienia_to_przeglad(self):
        """Regresja z 25.11.2026: nowa edycja (inny dzień odniesienia i data limitów) nie może przejść jako sama zmiana stawki."""
        old = (FIX / "alior-nowe-srodki-edycja-2026-10-14.txt").read_text(encoding="utf-8")
        (self.b / "collector" / "state" / "alior-nowe-srodki.txt").write_text(old, encoding="utf-8")
        before = self.offer("alior/nowe-srodki-92d")
        r = self.sync()
        self.assertTrue(any("valid_from" in x for x in r["review"]))
        self.assertEqual(self.offer("alior/nowe-srodki-92d"), before)

    def test_zmiana_dat_okresu_to_przeglad(self):
        self.edit_page("pko-bp-nowe-srodki", "Okres oferowania 15.11-14.12.2026", "Okres oferowania 15.12-14.01.2027")
        r = self.sync()
        self.assertTrue(any("valid_until" in x for x in r["review"]))

    def test_zepsuty_parser_nie_zmienia_ofert(self):
        (self.b / "collector" / "state" / "pko-bp-nowe-srodki.txt").write_text("Strona w przebudowie", encoding="utf-8")
        before = self.offer("pko-bp/nowe-srodki-3m")
        r = self.sync()
        self.assertTrue(any("niczego nie znalazł" in x for x in r["review"]))
        self.assertEqual(self.offer("pko-bp/nowe-srodki-3m"), before)

    def test_strona_nieswieza_jest_pomijana(self):
        before = self.offer("alior/nowe-srodki-92d")
        self.edit_page("alior-nowe-srodki", "3,6% na 92 dni", "3,5% na 92 dni")
        r = self.sync(fresh=set())
        self.assertEqual(self.offer("alior/nowe-srodki-92d"), before)
        self.assertEqual(r["updated"], [])

    def test_brak_last_run_to_nic_nie_swieze(self):
        self.assertEqual(sync_offers.fresh_sources(self.b / "collector" / "state", NOW), set())
        (self.b / "collector" / "state" / "last_run.json").write_text(json.dumps({"run_at": "2026-11-24T05:30:00Z", "fresh": ["x"]}), encoding="utf-8")
        self.assertEqual(sync_offers.fresh_sources(self.b / "collector" / "state", NOW), set())

    def test_nowa_oferta_trafia_do_szkicu(self):
        (self.b / "offers" / "alior__nowe-srodki-365d.json").unlink()
        r = self.sync()
        self.assertEqual(r["drafts"], ["alior/nowe-srodki-365d"])
        d = json.loads((self.b / "offers" / "draft" / "auto" / "alior__nowe-srodki-365d.json").read_text(encoding="utf-8"))
        self.assertEqual(d["verification"], "draft")
        self.assertEqual(d["rate_annual"], "3.00")


class LlmTest(unittest.TestCase):
    TEXT = page("pekao-lokuj-z-nami-7")

    def good(self):
        return {"product_name": "Lokuj z nami VII", "term_value": 3, "term_unit": "months", "rate_annual": "4,2",
                "amount_min": "5000", "amount_max": "100000",
                "evidence": {"rate": "4,2% w skali roku na 3 miesiące", "term": "na 3 miesiące", "amounts": "maksymalnie na lokatę możesz wpłacić 100.000 zł"}}

    def test_poprawna_oferta_przechodzi(self):
        self.assertEqual(llm_extract.verify(self.good(), self.TEXT), [])

    def test_wymyslony_cytat_odrzucony(self):
        o = self.good()
        o["evidence"]["conditions"] = "bez żadnych warunków dla wszystkich"
        self.assertTrue(any("cytat" in w for w in llm_extract.verify(o, self.TEXT)))

    def test_wymyslona_stawka_odrzucona(self):
        o = self.good()
        o["rate_annual"] = "5,5"
        self.assertTrue(any("stawki" in w for w in llm_extract.verify(o, self.TEXT)))

    def test_wymyslona_kwota_odrzucona(self):
        o = self.good()
        o["amount_max"] = "250000"
        self.assertTrue(any("kwoty" in w for w in llm_extract.verify(o, self.TEXT)))

    def test_konflikt_z_parserem(self):
        o = self.good()
        o["rate_annual"] = "4,2"
        res = llm_extract.process_page("pekao-lokuj-z-nami-7", {"url": "u"}, self.TEXT.replace("4,2%", "4,2%"), [o])
        self.assertEqual(res["conflicts"], [])
        self.assertEqual(len(res["offers"]), 1)

    def test_bez_klucza_nic_nie_robi(self):
        self.assertIn("skipped", llm_extract.run(key="", write=False))

    def test_wolanie_tylko_dla_zmienionych_stron_i_blad_nie_przerywa(self):
        b = Path(tempfile.mkdtemp())
        (b / "collector" / "state").mkdir(parents=True)
        shutil.copy(ROOT / "backend" / "collector" / "sources.json", b / "collector" / "sources.json")
        shutil.copy(FIX / "pekao-lokuj-z-nami-7.txt", b / "collector" / "state" / "pekao-lokuj-z-nami-7.txt")
        shutil.copy(FIX / "alior-nowe-srodki.txt", b / "collector" / "state" / "alior-nowe-srodki.txt")
        (b / "collector" / "state" / "last_run.json").write_text(json.dumps({"fresh": ["pekao-lokuj-z-nami-7", "alior-nowe-srodki"]}), encoding="utf-8")
        calls = []

        def caller(text, url, key):
            calls.append(url)
            if "aliorbank" in url:
                raise OSError("błąd sieci")
            return [self.good()]

        old = llm_extract.HERE
        llm_extract.HERE = b
        try:
            r = llm_extract.run(b, key="k", now=NOW, caller=caller)
            self.assertEqual(len(calls), 2)
            self.assertTrue(any(p.get("error") for p in r["pages"]))
            self.assertTrue((b / "offers" / "draft" / "llm" / "pekao-lokuj-z-nami-7.json").exists())
            llm_extract.run(b, key="k", now=NOW, caller=caller)
            self.assertEqual(len(calls), 3)  # pekao z cache pominięte, alior (błąd) ponowione
        finally:
            llm_extract.HERE = old


if __name__ == "__main__":
    unittest.main()

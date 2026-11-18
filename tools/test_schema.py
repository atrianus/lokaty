"""Testy schematu migawki ofert. Uruchomienie: python -m unittest discover -s tools -v"""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_snapshots import ROOT, validate  # noqa: E402

BASE = json.loads(
    (ROOT / "test-data" / "snapshots" / "valid" / "przyklad-minimalny.json").read_text(encoding="utf-8")
)


def variant(mutate):
    s = copy.deepcopy(BASE)
    mutate(s)
    return s


def offer(s):
    return s["offers"][0]


class ValidCases(unittest.TestCase):
    def test_przyklad_minimalny_przechodzi(self):
        self.assertEqual(validate(BASE), [])

    def test_brak_valid_until_przechodzi(self):
        self.assertNotIn("valid_until", offer(BASE))
        self.assertEqual(validate(BASE), [])

    def test_oferta_wygasla_przechodzi(self):
        self.assertEqual(validate(variant(lambda s: offer(s).update(verification="expired"))), [])

    def test_stawka_progresywna_z_harmonogramem_przechodzi(self):
        def m(s):
            o = offer(s)
            o.pop("rate_annual")
            o.update(interest_type="progressive", rate_schedule=[{"from_day": 0, "rate_annual": "4.00"}])
        self.assertEqual(validate(variant(m)), [])


class InvalidCases(unittest.TestCase):
    def assert_rejected(self, mutate):
        self.assertNotEqual(validate(variant(mutate)), [])

    def test_stawka_jako_liczba_json(self):
        self.assert_rejected(lambda s: offer(s).update(rate_annual=5.0))

    def test_pole_tak_nie_jako_boolean(self):
        self.assert_rejected(lambda s: offer(s)["conditions"].update(new_funds_only=True))

    def test_szkic_nie_trafia_do_migawki(self):
        self.assert_rejected(lambda s: offer(s).update(verification="draft"))

    def test_brak_source_url(self):
        self.assert_rejected(lambda s: offer(s).pop("source_url"))

    def test_brak_retrieved_at(self):
        self.assert_rejected(lambda s: offer(s).pop("retrieved_at"))

    def test_progresywna_bez_harmonogramu(self):
        def m(s):
            offer(s).pop("rate_annual")
            offer(s)["interest_type"] = "progressive"
        self.assert_rejected(m)

    def test_nieznana_regula_dostepnosci(self):
        self.assert_rejected(lambda s: offer(s).update(availability_rule="whenever"))

    def test_bank_oferty_nie_istnieje(self):
        self.assert_rejected(lambda s: s["banks"].clear())

    def test_zdublowany_offer_id(self):
        self.assert_rejected(lambda s: s["offers"].append(copy.deepcopy(offer(s))))

    def test_zdublowany_bank_id(self):
        self.assert_rejected(lambda s: s["banks"].append(copy.deepcopy(s["banks"][0])))

    def test_offer_id_z_innego_banku(self):
        self.assert_rejected(lambda s: offer(s).update(offer_id="inny-bank/lokata-3m"))

    def test_kapitalizacja_bez_okresu(self):
        self.assert_rejected(lambda s: offer(s).update(interest_type="capitalized"))

    def test_bank_term_bez_liczby_dni(self):
        self.assert_rejected(lambda s: offer(s).update(availability_rule="bank_term"))

    def test_niepelna_oferta_bez_listy_brakow(self):
        self.assert_rejected(lambda s: offer(s).pop("missing"))

    def test_nieznane_pole(self):
        self.assert_rejected(lambda s: offer(s).update(prowizja="10.00"))

    def test_data_w_zlym_formacie(self):
        self.assert_rejected(lambda s: s.update(snapshot_date="16.11.2026"))


if __name__ == "__main__":
    unittest.main()

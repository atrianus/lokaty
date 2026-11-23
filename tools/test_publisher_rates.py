"""Testy publikatora migawki i kontroli stawek (stdlib unittest)."""
import json
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "backend" / "publisher"))
sys.path.insert(0, str(ROOT / "backend" / "collector"))

import check_rates  # noqa: E402
import publish  # noqa: E402
from validate_snapshots import validate  # noqa: E402

NOW = datetime(2026, 11, 23, 6, 0, tzinfo=timezone.utc)


class PublisherTest(unittest.TestCase):
    def test_real_offers_build_valid_snapshot(self):
        snap = publish.build(ROOT / "backend" / "banks.json", ROOT / "backend" / "offers", NOW, revision=7)
        self.assertEqual(validate(snap), [])
        self.assertEqual(snap["revision"], 7)
        self.assertTrue(snap["offers"])
        self.assertTrue(all(o["verification"] in ("verified", "expired") for o in snap["offers"]))

    def test_banks_only_those_with_offers(self):
        snap = publish.build(ROOT / "backend" / "banks.json", ROOT / "backend" / "offers", NOW)
        self.assertEqual({b["bank_id"] for b in snap["banks"]}, {o["bank_id"] for o in snap["offers"]})

    def test_drafts_are_never_published(self):
        snap = publish.build(ROOT / "backend" / "banks.json", ROOT / "backend" / "offers", NOW)
        self.assertNotIn("pekao/standardowa-12m", [o["offer_id"] for o in snap["offers"]])

    def test_unknown_bank_is_error(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            (d / "banks.json").write_text(json.dumps({"banks": [{"bank_id": "a", "name": "A"}]}), encoding="utf-8")
            (d / "offers").mkdir()
            (d / "offers" / "x.json").write_text(json.dumps({"offer_id": "b/x", "bank_id": "b", "verification": "verified"}), encoding="utf-8")
            with self.assertRaises(ValueError):
                publish.build(d / "banks.json", d / "offers", NOW)

    def test_write_creates_files_and_checksum(self):
        snap = publish.build(ROOT / "backend" / "banks.json", ROOT / "backend" / "offers", NOW)
        with tempfile.TemporaryDirectory() as d:
            digest = publish.write(snap, Path(d))
            self.assertIn(digest, (Path(d) / "snapshot.sha256").read_text())
            self.assertEqual(json.loads((Path(d) / "snapshot.json").read_text(encoding="utf-8"))["revision"], 1)
            self.assertTrue((Path(d) / "index.html").exists())


class RatesTest(unittest.TestCase):
    PAGE = "3,3% w skali roku na 3 mies. lub 3,4% w skali roku na 6 mies. Odsetki naliczamy na koniec umowy, 19% podatku."

    def test_forms(self):
        self.assertEqual(check_rates.rate_forms("3.40"), {"3.40", "3.4", "3,40", "3,4"})
        self.assertIn("3,00", check_rates.rate_forms("3"))

    def test_found_and_missing(self):
        self.assertEqual(check_rates.check_offer({"rate_annual": "3.30"}, self.PAGE)[0], "ok")
        self.assertEqual(check_rates.check_offer({"rate_annual": "3.40"}, self.PAGE)[0], "ok")
        st, detail = check_rates.check_offer({"rate_annual": "3.50"}, self.PAGE)
        self.assertEqual(st, "missing")
        self.assertIn("3,3%", detail)

    def test_no_false_match_inside_other_number(self):
        # 13,3% nie jest stawką 3,3%
        self.assertEqual(check_rates.check_offer({"rate_annual": "3.30"}, "Oferta 13,3% w skali roku")[0], "missing")

    def test_no_rate_is_skipped(self):
        self.assertEqual(check_rates.check_offer({"rate_annual": None}, self.PAGE)[0], "skipped")

    def test_run_on_fixture(self):
        with tempfile.TemporaryDirectory() as d:
            b = Path(d)
            (b / "collector" / "state").mkdir(parents=True)
            (b / "offers" / "draft").mkdir(parents=True)
            (b / "collector" / "sources.json").write_text(json.dumps({"sources": [{"id": "s1", "bank_id": "a", "url": "https://x/y"}]}), encoding="utf-8")
            (b / "collector" / "state" / "s1.txt").write_text(self.PAGE, encoding="utf-8")
            (b / "offers" / "o1.json").write_text(json.dumps({"offer_id": "a/o1", "verification": "verified", "rate_annual": "3.30", "source_url": "https://x/y"}), encoding="utf-8")
            (b / "offers" / "o2.json").write_text(json.dumps({"offer_id": "a/o2", "verification": "verified", "rate_annual": "9.99", "source_url": "https://x/y"}), encoding="utf-8")
            (b / "offers" / "o3.json").write_text(json.dumps({"offer_id": "a/o3", "verification": "verified", "rate_annual": "1.00", "source_url": "https://inny"}), encoding="utf-8")
            res = {r[0]: r[2] for r in check_rates.run(b)}
            self.assertEqual(res, {"a/o1": "ok", "a/o2": "missing", "a/o3": "no_page"})
            self.assertIn("**missing** `a/o2`", check_rates.report(check_rates.run(b)))


if __name__ == "__main__":
    unittest.main()

"""Pilnuje, żeby cases.json zgadzał się z obliczeniem referencyjnym i ze specyfikacją."""
import json, unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
C = {c["id"]: c for c in json.loads((ROOT / "test-data/engine/cases.json").read_text(encoding="utf-8"))["cases"]}

class T(unittest.TestCase):
    def test_wartosci_ze_specyfikacji(self):
        e = C["K1"]["expected"]
        self.assertEqual((e["days"], e["interest_gross"], e["tax"], e["interest_net"], e["available_date"]), (92, "126.03", "23.95", "102.08", "2027-02-17"))
        self.assertEqual(C["K2a"]["expected"]["interest_gross"], "501.37")
        self.assertEqual(C["K2b"]["expected"]["interest_gross"], "501.15")
        self.assertEqual([r["tax"] for r in C["K3"]["expected"]["results"]], ["1.91", "0.01", "19.00"])
        self.assertEqual(C["K4a"]["expected"]["payout"], "10122.97")
        self.assertEqual(C["K4b"]["expected"]["interest_net"], "123.12")
        self.assertEqual(C["K5"]["expected"]["interest_net"], "44.37")
        self.assertEqual(C["K8"]["expected"]["available_dates"], ["2027-02-22", "2026-12-28", "2027-03-30"])
        self.assertEqual(C["K9"]["expected"]["maturity_dates"], ["2027-02-28", "2027-02-28", "2028-02-29"])
        self.assertEqual(C["K10"]["expected"]["easter"], ["2026-04-05", "2027-03-28", "2028-04-16"])
        self.assertEqual(C["K10"]["expected"]["corpus_christi"], ["2026-06-04", "2027-05-27", "2028-06-15"])

    def test_komplet_przypadkow(self):
        self.assertEqual(sorted(C), sorted(["K1", "K2a", "K2b", "K3", "K4a", "K4b", "K5", "K6", "K7", "K8", "K9", "K10", "K11"]))

if __name__ == "__main__":
    unittest.main()

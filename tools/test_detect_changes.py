import sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend" / "collector"))
import detect_changes as d

PAGE = "<html><head><title>x</title><script>var a=1;</script></head><body><h1>Lokata</h1><p>" + "Oprocentowanie 3,30% w skali roku. " * 10 + "</p></body></html>"
SRC = {"id": "t", "bank_id": "b", "url": "http://x"}


class T(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def test_tekst_bez_skryptow(self):
        t = d.html_to_text(PAGE)
        self.assertIn("Oprocentowanie 3,30%", t)
        self.assertNotIn("var a", t)

    def test_nowa_potem_bez_zmian_potem_zmiana(self):
        self.assertEqual(d.check(SRC, self.dir, lambda u: PAGE)[0], "new")
        self.assertEqual(d.check(SRC, self.dir, lambda u: PAGE)[0], "unchanged")
        self.assertEqual(d.check(SRC, self.dir, lambda u: PAGE.replace("3,30", "3,50"))[0], "changed")

    def test_blad_pobrania_nie_przerywa(self):
        def boom(u): raise OSError("brak sieci")
        res = d.run([SRC, {**SRC, "id": "u"}], self.dir, boom)
        self.assertEqual([r["status"] for r in res], ["error", "error"])

    def test_pusta_strona_to_blad(self):
        self.assertEqual(d.check(SRC, self.dir, lambda u: "<html></html>")[0], "error")

    def test_raport_zmiany_na_gorze(self):
        r = d.report([{**SRC, "status": "unchanged", "detail": "a"}, {**SRC, "id": "z", "status": "changed", "detail": "b"}], "dziś")
        self.assertLess(r.index("changed"), r.index("unchanged"))

    def test_sources_poprawne(self):
        import json
        s = json.loads((Path(d.HERE) / "sources.json").read_text(encoding="utf-8"))["sources"]
        ids = [x["id"] for x in s]
        self.assertEqual(len(ids), len(set(ids)))
        banks = {b["bank_id"] for b in json.loads((d.HERE.parent / "banks.json").read_text(encoding="utf-8"))["banks"]}
        for x in s:
            self.assertIn(x["bank_id"], banks)
            self.assertTrue(x["url"].startswith("https://"))


if __name__ == "__main__":
    unittest.main()

"""Wykrywacz zmian stron ofert: pobiera strony z sources.json, porównuje tekst
z poprzednim dniem i zapisuje raport. Tylko biblioteka standardowa."""
import hashlib, json, re, sys, urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
SKIP = {"script", "style", "noscript", "svg", "head"}


class _Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts, self.depth = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in SKIP:
            self.depth += 1

    def handle_endtag(self, tag):
        if tag in SKIP and self.depth:
            self.depth -= 1

    def handle_data(self, data):
        if not self.depth and data.strip():
            self.parts.append(data.strip())


def html_to_text(html):
    p = _Text()
    p.feed(html)
    return re.sub(r"[ \t ]+", " ", "\n".join(p.parts))


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "lokaty-change-detector/1.0 (+https://github.com/atrianus/lokaty)"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode(r.headers.get_content_charset() or "utf-8", "replace")


def check(source, state_dir, fetcher=fetch):
    """Zwraca (status, szczegół). status: new | unchanged | changed | error."""
    path = state_dir / f"{source['id']}.txt"
    try:
        text = html_to_text(fetcher(source["url"]))
    except Exception as e:  # jedna strona nie może zatrzymać reszty
        return "error", f"{type(e).__name__}: {e}"
    if len(text) < 200:
        return "error", "za mało tekstu (strona wymaga skryptów lub zablokowała pobranie)"
    old = path.read_text(encoding="utf-8") if path.exists() else None
    path.write_text(text, encoding="utf-8")
    if old is None:
        return "new", hashlib.sha256(text.encode()).hexdigest()[:12]
    return ("unchanged" if old == text else "changed"), hashlib.sha256(text.encode()).hexdigest()[:12]


def run(sources, state_dir, fetcher=fetch):
    state_dir.mkdir(parents=True, exist_ok=True)
    results = [{**s, "status": st, "detail": d} for s in sources for st, d in [check(s, state_dir, fetcher)]]
    return results


def report(results, now=None):
    now = now or datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [f"# Raport zmian ofert ({now})", ""]
    order = {"changed": 0, "error": 1, "new": 2, "unchanged": 3}
    for r in sorted(results, key=lambda r: order[r["status"]]):
        lines.append(f"- **{r['status']}** `{r['id']}` ({r['bank_id']}): {r['detail']}")
    return "\n".join(lines) + "\n"


def main():
    sources = json.loads((HERE / "sources.json").read_text(encoding="utf-8"))["sources"]
    results = run(sources, HERE / "state")
    (HERE / "report.md").write_text(report(results), encoding="utf-8")
    print(report(results))
    return 0  # błędy pojedynczych stron nie psują przebiegu


if __name__ == "__main__":
    sys.exit(main())

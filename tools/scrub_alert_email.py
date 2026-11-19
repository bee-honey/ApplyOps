#!/usr/bin/env python3
"""Turn a raw alert email (from `make gmail-dump`) into a committable test fixture.

    python3 tools/scrub_alert_email.py data/alert-samples/<id>.html \
        orchestrator/src/test/resources/fixtures/email/<name>.html --redact "Your Name" ...

Keeps the HTML structure the parsers depend on and removes everything personal:
- job links keep only their public job id (tracking query strings dropped)
- every other link points to example.invalid; image sources are emptied
- <head>, <style>, comments and inline styles are removed
- each --redact string (your name, email, ...) becomes a neutral placeholder
- LinkedIn's "intended for <name> (<headline>)" footer is always replaced

Refuses to write the output if a redacted string, an email address that is not a
known no-reply sender, or a long tracking token survives.
"""

import argparse
import re
import sys

JOB_LINK = re.compile(
    r"https://(?:www\.linkedin\.com/(?:comm/)?jobs/view/\d+/?|jobright\.ai/jobs/info/[A-Za-z0-9]+)"
)
ALLOWED_EMAILS = {
    "jobalerts-noreply@linkedin.com",
    "noreply@jobright.ai",
    "user@example.com",
}
TOKEN = re.compile(
    r"[A-Za-z0-9_-]{32,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def scrub(html: str, redact: list[str]) -> str:
    html = re.sub(r"<!--.*?-->", "", html, flags=re.DOTALL)
    html = re.sub(r"<head\b.*?</head>", "", html, flags=re.DOTALL | re.IGNORECASE)
    html = re.sub(r"<style\b.*?</style>", "", html, flags=re.DOTALL | re.IGNORECASE)
    html = re.sub(r'\sstyle="[^"]*"', "", html)

    def href(m: re.Match) -> str:
        job = JOB_LINK.match(m.group(1))
        return f'href="{job.group(0) if job else "https://example.invalid/"}"'

    html = re.sub(r'href="([^"]*)"', href, html)
    html = re.sub(r'\ssrc="[^"]*"', ' src=""', html)
    html = re.sub(r'\ssrcset="[^"]*"', "", html)
    # LinkedIn footer: "This email was intended for <name> (<profile headline>)"
    html = re.sub(
        r"(intended for\s+)[^<]*",
        r"\1Alex Doe (Software Engineer)",
        html,
        flags=re.IGNORECASE,
    )
    for i, s in enumerate(redact):
        placeholder = (
            "user@example.com" if "@" in s else ("Alex Doe" if i == 0 else "REDACTED")
        )
        html = re.sub(re.escape(s), placeholder, html, flags=re.IGNORECASE)
    html = re.sub(r"[ \t]+\n", "\n", html)
    html = re.sub(r"\n{3,}", "\n\n", html)
    return html


def problems(html: str, redact: list[str]) -> list[str]:
    found = [
        f"redacted string survived: {s!r}" for s in redact if s.lower() in html.lower()
    ]
    found += [
        f"email: {e}"
        for e in set(EMAIL.findall(html))
        if e.lower() not in ALLOWED_EMAILS
    ]
    without_job_links = JOB_LINK.sub("", html)
    tokens = {
        t for t in TOKEN.findall(without_job_links) if sum(c.isdigit() for c in t) >= 6
    }
    found += [f"token: {t[:12]}..." for t in tokens]
    return found


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument(
        "--redact",
        action="append",
        default=[],
        help="personal string to remove (repeatable)",
    )
    ap.add_argument(
        "--note", default="", help="one-line description for the fixture header"
    )
    args = ap.parse_args()

    with open(args.src, encoding="utf-8") as f:
        out = scrub(f.read(), args.redact)
    bad = problems(out, args.redact)
    if bad:
        print(
            "Refusing to write fixture; personal data may remain:",
            *bad,
            sep="\n  ",
            file=sys.stderr,
        )
        return 1
    header = f"<!-- Real alert email, scrubbed by tools/scrub_alert_email.py. {args.note} -->\n"
    with open(args.dst, "w", encoding="utf-8") as f:
        f.write(header + out.strip() + "\n")
    print(f"Wrote {args.dst} ({len(out)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

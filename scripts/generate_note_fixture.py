#!/usr/bin/env python3
"""Generate ONE requested note at a time; does not allocate a whole large body."""
import argparse
from pathlib import Path

FIXTURES = {
    "links-1000": ("links", 1000), "links-3000": ("links", 3000),
    "links-5000": ("links", 5000), "links-10000": ("links", 10000),
    "characters-100000": ("plain", 100_000), "lines-50000": ("lines", 50_000),
    "plain-5mb": ("plain", 5_000_000), "links-5mb": ("link-bytes", 5_000_000),
    "plain-50mb": ("plain", 50_000_000), "links-50mb": ("link-bytes", 50_000_000),
    "plain-54mb": ("plain", 54_000_000), "plain-56mb": ("plain", 56_000_000),
}


def generate(kind, amount, output):
    if kind == "links":
        for index in range(1, amount + 1):
            if (index - 1) % 100 == 0:
                output.write(f"## SECTION {(index - 1) // 100 + 1}\n\n".encode())
            output.write(f"[Test link {index}](https://example.org/page/{index})\n\n".encode())
    elif kind == "lines":
        for _ in range(amount):
            output.write(b"Plain line without links.\n")
    else:
        fragment = (b"[Link](https://example.org/page)\n\n" if kind == "link-bytes"
                    else b"Plain Markdown text without links.\n")
        block = fragment * max(1, 16_384 // len(fragment))
        remaining = amount
        while remaining:
            part = block[:min(len(block), remaining)]
            output.write(part)
            remaining -= len(part)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", choices=FIXTURES)
    parser.add_argument("output", type=Path, help="New .md path; existing files are never overwritten")
    args = parser.parse_args()
    kind, amount = FIXTURES[args.fixture]
    with args.output.open("xb") as output:
        generate(kind, amount, output)
    print(f"Created {args.output}: {args.output.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()

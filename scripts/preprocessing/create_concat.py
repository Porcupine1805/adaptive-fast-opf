#!/usr/bin/env python3
"""Create deterministic repeated-series scale inputs from one numeric file."""

from __future__ import annotations

import argparse
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Whitespace-separated numeric input file.")
    parser.add_argument("output_dir", type=Path, help="Directory for generated files.")
    parser.add_argument("--max-scale", type=int, default=6, help="Largest repetition factor.")
    parser.add_argument("--line-width", type=int, default=20, help="Values written per line.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.max_scale < 1 or args.line_width < 1:
        raise ValueError("--max-scale and --line-width must be positive")

    tokens = args.input.read_text(encoding="utf-8").split()
    values = [float(token) for token in tokens]
    rendered = [format(value, ".15g") for value in values]
    args.output_dir.mkdir(parents=True, exist_ok=True)

    for scale in range(1, args.max_scale + 1):
        output = args.output_dir / f"{args.input.stem}_{scale}x.txt"
        with output.open("w", encoding="utf-8", newline="\n") as handle:
            index = 0
            for _ in range(scale):
                for value in rendered:
                    handle.write(value)
                    index += 1
                    handle.write("\n" if index % args.line_width == 0 else " ")
            if index % args.line_width:
                handle.write("\n")
        print(f"created: {output} ({len(values) * scale} values)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

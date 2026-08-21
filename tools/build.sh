#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/classes/benchmark"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/src/benchmark/java/OPF_Miner_Original.java" \
  "$ROOT/src/benchmark/java/FOMAblationFlags.java" \
  "$ROOT/src/benchmark/java/HJOPF.java"
echo "Build complete: $OUT"
echo "Entry points: OPF_Miner_Original | HJOPF (recommended) | FOMAblationFlags"

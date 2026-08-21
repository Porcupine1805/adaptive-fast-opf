# Changelog

## 2026-08-21 — Align repository with HJ-OPF paper

- Primary claim: **HJ-OPF** (`mode=hash_only`).
- Residual ablation (CPC / Gallop / adaptive) retained for controlled experiments only; not a primary claim.
- Renamed main class: `FOMAblationFlags` → `HJOPF`.
- Moved historical bitmap implementation to `legacy/FOM_bitmap_legacy.java`.
- Rewrote README and CITATION.cff to match paper title.
- Simplified `tools/build.ps1` / added `tools/build.sh`.
- Removed internal draft docs and outdated audits from the publication tree.

## Earlier

- Hash-indexed join ablation harness.
- Pilot scripts and DB1–DB8 benchmark data layout.

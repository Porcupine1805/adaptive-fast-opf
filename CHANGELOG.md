# Changelog

## 2026-08-14 — Align repo with Adaptive Fast OPF narrative

- Paper stages: **HJ → CPC → Sorted List / Gallop**; Adaptive staging after HJ.
- **CPC replaces WSB** in the research story (property names still `adaptiveWsb*` for compatibility).
- **Bitmap demoted**: `SPARSE=false`; default `bitmapPolicy=never`.
- Defaults when `-Dmode=adaptive`: CPC on, smart intersect on, `adaptiveGallopWithoutCpc` on, staged policy on.
- Default `mode` changed to `adaptive` (override with `-Dmode=hash_only` / `baseline` / `full`).
- Docs: README, ADAPTIVE_MECHANISM, HANDOFF, manuscript draft refreshed.

## Earlier

- Hash-indexed join ablation harness (`FOMAblationFlags`).
- Pilot scripts and DB1–DB8 benchmark data layout.

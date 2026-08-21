# Project structure

## Primary implementation (paper)

- `src/benchmark/java/OPF_Miner_Original.java` — reference OPF-Miner baseline.
- `src/benchmark/java/HJOPF.java` — HJ-OPF (primary) + residual ablation harness.

## Data

- `data/benchmark/DB1.txt` … `DB8.txt` — financial suite used in the paper.
- `data/electricity_scale/` — ELEC_01/05/10 concatenations used in the paper.
- `data/manifests/` — provenance and checksums.

## Supporting material

- `scripts/` — benchmark, validation, analysis, and preprocessing helpers.
- `tools/` — build and environment capture.
- `docs/` — reproducibility notes.
- `legacy/` — historical code retained only for provenance (not part of paper claims).

# Benchmark scripts (Adaptive Fast OPF manuscript)

## Primary (use this)

```powershell
# Pilot: DB4/5, σ∈{2,4}, V0–V3, warm-up 3 + median of 5 runs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark/run_manuscript_protocol.ps1 -Profile pilot

# Full DB1–8 matrix
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark/run_manuscript_protocol.ps1 -Profile db_full -MaximumHeap 8g

# Electricity scale
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark/run_manuscript_protocol.ps1 -Profile elec_scale -MaximumHeap 16g
```

Variants:

| ID | Class / flags |
|----|----------------|
| V0 | `OPF_Miner_Original` |
| V1 | `FOMAblationFlags` `mode=hash_only` |
| V2 | Adaptive, CPC gate C, Gallop off |
| V3 | Adaptive defaults (gate B + smart intersect) |

Outputs under `results/manuscript/<stamp>_<profile>/`.

## Supporting

- `run_benchmark.ps1` / `run_adaptive_pilot.ps1` — older single-purpose pilots
- `run_factorial_benchmark.ps1` — **legacy labels** (BM/WB); prefer manuscript protocol for paper tables
- `average.py` — optional CSV averaging helper

RQ6 / DB9 clustering scripts are **not** part of this manuscript and are not shipped in the supported path.

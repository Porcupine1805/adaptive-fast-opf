# Adaptive FastOPF pilot report

## Scope

This report records the mechanism-design pilot requested before any manuscript
claim is made. The tested workloads are DB1, DB5, and DB8 at minSup 2 and 4.
Each timed case uses three in-process case warmups and five measured JVM forks.
Configuration order is rotated across forks. Timing includes adaptive selection.

## Adaptive FastOPF v2

HJ is always enabled. Two independent controllers decide whether the other
strategies are worth their overhead:

1. BM workload gate: select compressed-bitmap fusion for the whole run when
   `2N <= 32768`; otherwise use the scalar HJ fusion kernel. The pilot selects
   BM for DB1 and DB8 and scalar fusion for DB5.
2. WB eligibility and online control: WB is initially ineligible when
   `minSup/N < 0.001`. An eligible level with enough estimated pairs probes up
   to eight real pairs. WB continues only when sampled saved fusion work is at
   least 1.25 times sampled bound-check work. Failed probes use exponential
   cooldown over subsequent levels.

The thresholds are calibration parameters and are not yet universal constants.
The full specification is in `docs/ADAPTIVE_MECHANISM.md`.

## Why earlier selectors were rejected

- v0 scanned every compatible pair to predict BM and repeatedly sampled WB.
  Decision overhead reached about 65 ms on DB5/minSup=2.
- v1 reduced BM estimation to an O(P) pass and added WB cooldown, but the O(P)
  pass still cost about 42 ms on DB5/minSup=2.
- v2 uses a constant-time workload gate and branch-free scalar/bitmap fast
  paths when WB is globally ineligible. Measured decision overhead is zero in
  these fast paths.

These failures are mechanism-design evidence, not publication benchmark data.

## Final pilot runtime

Median runtime over five measured forks, in milliseconds:

| Dataset | minSup | HJ only | BM only | WB only | Adaptive |
|---|---:|---:|---:|---:|---:|
| DB1 | 2 | **27.024** | 30.335 | 38.583 | 29.444 |
| DB1 | 4 | 16.547 | 19.069 | 22.140 | **14.476** |
| DB5 | 2 | **334.074** | 1910.223 | 1966.528 | 342.627 |
| DB5 | 4 | 161.206 | 460.400 | 457.761 | **158.984** |
| DB8 | 2 | **9.524** | 12.672 | 14.180 | 10.451 |
| DB8 | 4 | 7.729 | 7.750 | 8.641 | **7.497** |

Across the six cases, Adaptive is faster than BM-only in 6/6 cases with a
1.737x geometric-mean speedup, and faster than WB-only in 6/6 cases with a
1.931x geometric-mean speedup. Against HJ-only, Adaptive wins 3/6 cases; the
HJ/Adaptive geometric-mean ratio is 0.996, so they are effectively tied in
this small pilot and HJ is about 0.4% faster geometrically.

The direct BM-only and WB-only variants do not contain HJ. The supplementary
HJ-centered probe (`HJOnly`, `HashBitmap`, `Adaptive`) is retained separately
to distinguish standalone strategy contribution from marginal contribution on
top of HJ.

## Correctness

Canonical pattern-support outputs were compared against original OPF for all
six cases. Four files are exact raw SHA-256 matches. The two DB5 files are
normalized SHA-256 matches after support rounding to six decimals. There are
no pattern-set or support mismatches.

## Memory limitation

Runtime warmup intentionally retains compiled code and can leave objects in the
live heap. Therefore the memory columns from this pilot are not valid peak
memory comparisons. Memory must be measured in separate cold JVM forks with no
case warmup and a fixed heap policy.

## Decision

Adaptive FastOPF v2 is a valid, correctness-preserving prototype and clearly
dominates the BM-only and WB-only variants in this pilot. It does not yet
support the manuscript claim that Adaptive is faster than every fixed strategy.
The current evidence supports a narrower statement: workload gating removes
most harmful combination overhead and approaches the stronger HJ baseline.

Before a publication claim, the next experiment must:

1. calibrate `32768` and `0.001` using training folds only;
2. add WB-positive cases, for example DB1 with minSup 6-12 and proportionally
   higher minSup for DB5/DB8;
3. run leave-one-dataset-out evaluation on DB1-DB8;
4. report confidence intervals and paired effects, not only means;
5. measure memory in cold forks;
6. preserve OPF canonical equivalence for every evaluated case.

## Artifacts

- Runtime: `results/runs/adaptive_vs_three_single_v2/summary.csv`
- All measured rows: `results/runs/adaptive_vs_three_single_v2/all_runs.csv`
- Canonical check: `results/validation/adaptive_v2_pilot/opf_vs_adaptive.csv`
- Mechanism: `docs/ADAPTIVE_MECHANISM.md`
- Runner: `scripts/benchmark/run_adaptive_pilot.ps1`

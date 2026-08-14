# Small experiment: when can Full-FOM outperform HJ?

Date: 2026-08-12

## Scope and terminology

This experiment tests three proposed conditions:

1. Very low `minSup` leaves more candidates after hash-indexed joining.
2. Dense occurrence matrices make bitmap processing more efficient.
3. Long patterns provide enough repeated kernel work to amortize bitmap overhead.

The bitmap kernel in the current code computes a shifted occurrence intersection with bitwise `AND`, not `OR`:

`Shift(Occ(P), 1) AND Occ(Q)`

HJ is `mode=hash_only`. HJ+BM is `mode=hash_sparse`, with compressed bitmap always enabled. Full-static is `mode=full`, with compressed bitmap and WSB always enabled. Each reported comparison uses three independent JVM runs. Configuration order is rotated between runs.

## Experiment 1: minimum support

### Electricity, one client

| minSup | HJ (s) | HJ+BM (s) | Full-static (s) | HJ/Full | Frequent patterns |
|---:|---:|---:|---:|---:|---:|
| 2 | 0.5946 | 0.5684 | 0.5471 | 1.087x | 12,573 |
| 4 | 0.3629 | 0.3442 | 0.3657 | 0.992x | 6,293 |
| 8 | 0.1650 | 0.1780 | 0.1881 | 0.877x | 2,939 |
| 12 | 0.1511 | 0.1393 | 0.1542 | 0.980x | 2,035 |

At `minSup=2`, Full is 8.7% faster than HJ and HJ+BM is 4.6% faster. This is consistent with the proposed low-threshold mechanism, but the result is not monotonic across thresholds.

### Electricity, five clients

| minSup | HJ (s) | HJ+BM (s) | Full-static (s) | HJ/Full | Frequent patterns |
|---:|---:|---:|---:|---:|---:|
| 2 | 2.5591 | 3.2593 | 3.0179 | 0.848x | 120,333 |
| 12 | 1.3723 | 1.4808 | 1.4297 | 0.960x | 20,240 |

At the larger scale, low `minSup` does not make Full faster. At `minSup=2`, Full is 17.9% slower than HJ and HJ+BM is 27.4% slower. Full prunes 18,416 pairs, but still allocates about 14.5 million bitmap words and scans 23.0 million words. HJ performs 45.3 million scalar position comparisons without those allocations.

Verdict: **conditionally plausible, but not sufficient and not robustly supported as a standalone claim**. Lower `minSup` increases useful vector work and bitmap construction/allocation work at the same time. Density, fan-out, and bitmap reuse determine which effect dominates.

## Experiment 2: occurrence density

Four deterministic synthetic series of length 1,024 were generated. The density value is the mean generation-level ratio between occurrence count and covered position range.

| Dataset | Mean density | HJ (s) | HJ+BM (s) | Full-static (s) | HJ/BM | HJ/Full |
|---|---:|---:|---:|---:|---:|---:|
| Monotonic | 1.000 | 0.5213 | 0.4667 | 0.4632 | 1.117x | 1.126x |
| Alternating | 0.503 | 1.3786 | 1.2048 | 1.3638 | 1.144x | 1.011x |
| Sawtooth | 0.130 | 3.5452 | 3.7134 | 3.6655 | 0.955x | 0.967x |
| Random | 0.122 | 0.0057 | 0.0054 | 0.0058 | 1.052x | 0.992x |

The random workload is only about 5--6 ms and is too short for a strong timing conclusion. The other workloads show the expected density boundary:

- At density 1.0, BM is 11.7% faster and Full is 12.6% faster than HJ.
- At density about 0.5, BM is 14.4% faster, while WSB overhead reduces Full's gain to 1.1%.
- At density about 0.13, BM is 4.5% slower and Full is 3.3% slower.

Verdict: **supported for the tested dense workloads**. Density is the strongest of the three proposed conditions. Dense word containers replace many scalar comparisons with a small number of useful `AND` operations; sparse workloads spend more time building and walking bitmap metadata.

## Experiment 3: pattern length

Generation kernel times were grouped by current pattern length. The following table shows HJ/BM speedup.

| Length | Monotonic, density 1.0 | Alternating, density 0.50 | Sawtooth, density about 0.13 |
|---|---:|---:|---:|
| 2--15 | 0.895x | 1.411x | 0.633x |
| 16--63 | 0.758x | 1.201x | 0.744x |
| 64--255 | 0.885x | 1.557x | 0.987x |
| 256--511 | 1.001x | 1.372x | 1.047x |
| 512+ | 1.138x | 1.036x | 0.931x |

The monotonic workload displays the proposed amortization point: BM is slower below length 256, approximately equal at 256--511, and 13.8% faster for patterns of length 512 or more. However, the sparse sawtooth workload remains 6.9% slower at length 512+, despite having more than 1,000 generations.

Verdict: **conditional on occurrence density and representation reuse**. Pattern length alone is insufficient. Long patterns often have fewer occurrences, and the current implementation rebuilds bitmap containers for each generation. A long but sparse pattern can therefore remain slower than scalar HJ.

## Output correctness

Hash-only and Full-static canonical pattern-support outputs match by raw SHA-256 for all four synthetic datasets at `minSup=2`:

- `SYN_ALTERNATING_1024`: exact match
- `SYN_MONOTONIC_1024`: exact match
- `SYN_RANDOM_1024`: exact match
- `SYN_SAWTOOTH_1024`: exact match

All compared configurations also report identical frequent-pattern counts for every measured dataset and threshold.

## Final answer to the three statements

1. **Very low minSup:** revise to a conditional statement. It creates more vectorizable work, but can also increase bitmap construction and allocation faster than the saved scalar work.
2. **Dense occurrence matrix:** supported and the best predictor in this small experiment. Full exceeded HJ by 12.6% on the density-1.0 workload.
3. **Very long pattern:** partially supported only when occurrence density remains high. Length is an amortization opportunity, not an independent guarantee.

A scientifically defensible replacement claim is:

> Full-FOM can outperform HJ when the surviving compatible pairs retain dense occurrence sets over sufficiently many fusion generations, allowing bitmap construction and weighted-bound overhead to be amortized. Low minimum support and long patterns increase this opportunity, but neither condition is sufficient without adequate occurrence density and bitmap reuse.

## Reproducibility files

- `algorithm_benchmark/GenerateFOMHypothesisDatasets.java`
- `algorithm_benchmark/run_full_fom_optimization_probe.ps1`
- `algorithm_benchmark/summarize_full_fom_hypothesis_probe.ps1`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_hypothesis_summary/`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_hypothesis_canonical/canonical_equivalence.csv`


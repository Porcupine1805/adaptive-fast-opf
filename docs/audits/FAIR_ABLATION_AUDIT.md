# Fair ablation audit

## Conclusion

The earlier four-scenario comparison was not fully fair because:

- Full FastOPF timing came from `FOM.java`.
- Hash-only, Sparse-only, and WSB-only came from `FOMAblationFlags.java`.

Although all used the same input datasets, minSup values, and 10-run averaging, the implementation engine differed. That can bias wall-clock timing.

## Fair rerun

Full was rerun using the same configurable engine as the three option-only variants:

- Full: `FOMAblationFlags -Dmode=full`
- Hash-only: `FOMAblationFlags -Dmode=hash_only`
- Sparse-only: `FOMAblationFlags -Dmode=sparse_only`
- WSB-only: `FOMAblationFlags -Dmode=wsb_only`

All four scenarios use:

- Input: `../datasets`
- minSup: `2, 4, 6, 8, 10, 12`
- Configurations: 84
- Runs per configuration: 10
- Canonical output during timing: off
- Same summary aggregation script: `average.py`

## Fair outputs

- `algorithm_benchmark/results_full_20260811/benchmark/ablation_clean/FOMAblationFull_summary_avg.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/ablation_clean/FOMAblationHashOnly_summary_avg.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/ablation_clean/FOMAblationSparseOnly_summary_avg.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/ablation_clean/FOMAblationWSBOnly_summary_avg.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/ablation_clean/fair_ablation_4scenario_summary.csv`

## Fair mean metrics across 84 configurations

| Scenario | Mean Time (s) | Mean Memory (MB) | Mean PairChecks |
|---|---:|---:|---:|
| Full flags | 0.092141 | 41.82 | 8,496 |
| Hash-only | 0.060250 | 16.18 | 8,496 |
| Sparse-only | 0.172774 | 38.73 | 10,216,921 |
| WSB-only | 0.130036 | 15.04 | 10,216,921 |

## Counter equivalence

Compared with Full flags, all three option-only variants match on all 84 configurations for:

- `Candidates`
- `Fusions`
- `SupportOps`
- `FreqPatterns`

This means the variants preserve the mined result counts and final candidate/support behavior, while `PairChecks` exposes the cost difference of hash-indexed candidate retrieval.

## Winner counts

| Metric | Winner counts |
|---|---|
| Runtime | Hash-only 47, Full flags 15, Sparse-only 14, WSB-only 8 |
| Memory | WSB-only 44, Hash-only 27, Sparse-only 8, Full flags 5 |
| PairChecks | Full flags and Hash-only are tied in value; both use hash-indexed retrieval. |

## Interpretation

For the four-scenario ablation, use `FOMAblationFull_summary_avg.csv` as the Full result. Do not compare `FOMNoCanonical_summary_avg.csv` against the option-only files when making claims about option-level ablation, because that mixes two implementations.

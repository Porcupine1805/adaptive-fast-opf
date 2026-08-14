# Experiment with sub-unit minSup values

Date: 2026-08-12

## Meaning of minSup in this implementation

`minSup` is an absolute forgetting-weighted support threshold, not a percentage. With the default `k=1/n`, an occurrence weight lies approximately in `[exp(-1), 1]`, or `[0.368, 1]`. Therefore:

- `0 < minSup <= approximately 0.368`: any non-empty occurrence set is frequent.
- `minSup=0.5`: many one-occurrence patterns near the recent end of the series can be frequent.
- `minSup=1`: most patterns need multiple occurrences, except an occurrence with weight exactly 1 at the last position.
- `minSup=0`: invalid for this implementation. Empty occurrence sets have support 0 and would pass the test `support >= minSup`, allowing meaningless empty candidates to propagate.

The code now rejects non-finite or non-positive `minSup` values with an `IllegalArgumentException`.

## Electricity, one client

Three independent JVM runs were performed with rotated configuration order.

| minSup | Configuration | Mean time (s) | SD (s) | Mean memory (MB) | Frequent patterns | HJ/configuration |
|---:|---|---:|---:|---:|---:|---:|
| 1.0 | HJ | 0.6666 | 0.1341 | 50.36 | 26,627 | 1.000x |
| 1.0 | Full-static | 0.6075 | 0.0721 | 50.26 | 26,627 | 1.097x |
| 0.5 | HJ | 1.2881 | 0.2359 | 227.40 | 178,197 | 1.000x |
| 0.5 | Full-static | 1.1771 | 0.0317 | 213.84 | 178,197 | 1.094x |

Full-static was about 9.7% faster at `minSup=1` and 9.4% faster at `minSup=0.5`. However, reducing the threshold from 1 to 0.5 increased the frequent-pattern count by 6.69x and sampled memory by more than 4x. Compared with the earlier `minSup=2` result of 12,573 patterns, `minSup=0.5` produced 14.17x as many patterns.

The HJ and Full-static canonical outputs matched by raw SHA-256 at both thresholds.

## Controlled 128-point experiment

The random series shows the threshold transition most clearly:

| minSup | Frequent patterns | HJ mean (s) | Full mean (s) | HJ/Full |
|---:|---:|---:|---:|---:|
| 2.0 | 35 | 0.0018 | 0.0019 | 0.978x |
| 1.0 | 66 | 0.0023 | 0.0021 | 1.086x |
| 0.5 | 4,016 | 0.0748 | 0.0592 | 1.263x |
| 0.1 | 7,732 | 0.2582 | 0.1983 | 1.302x |

Even on only 128 points, lowering `minSup` from 1 to 0.5 increased the number of frequent patterns by approximately 60.8x. Full benefits because BM receives much more intersection work and WSB can remove some fusion pairs, but both methods still face an output-size explosion.

Canonical HJ and Full-static outputs matched by raw SHA-256 in all 12 combinations of three synthetic datasets and four positive thresholds.

## Conclusion

For positive `minSup < 1`, Full can outperform HJ because many more patterns and occurrence intersections survive. The experiment supports this effect at `minSup=0.5` and `0.1`. Nevertheless, this is not a free performance regime: the output and memory requirements grow sharply, and the mining task approaches enumeration of every pattern with at least one occurrence.

`minSup=0` must not be used. For experiments intended to express a relative threshold, define a ratio `rho` and convert it explicitly, for example `minSup = rho * sum_i w_i`, rather than passing `rho` directly as the absolute threshold.

## Reproducibility files

- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_subunit_minsup_electricity_summary.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_subunit_minsup_128/probe_summary.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_subunit_minsup_128/canonical_equivalence.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_subunit_minsup_electricity_canonical/canonical_equivalence.csv`


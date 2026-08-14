# Analysis scripts

Most scripts operate on the full local experiment archive, which is not
committed to Git. Place a preserved archive at:

```text
results/experiments/results_full_20260811/
```

`generate_benchmark_figures.py` accepts `--experiment-dir` and `--out-dir`.
Compact review evidence is split into current probes, historical results, and
RQ6 outputs under `results/reference/`. Read `docs/RESULTS_CATALOG.md` before
combining tables from those evidence tiers.

Generated figures and reports should remain outside Git unless they are
explicitly selected as release artifacts.

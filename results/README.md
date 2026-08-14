# Results

Only compact reference evidence is versioned.

```text
reference/current-probes/  Latest targeted BM/WB/Full mechanism probes
reference/historical/      Earlier OPF/HJ and legacy ablation summaries
reference/rq6/             Compact DB9 clustering outputs
runs/                      Generated repeated timing rows (ignored)
canonical/                 Generated pattern-support files (ignored)
comparisons/               Generated equivalence reports (ignored)
smoke/                     Generated CI output (ignored)
```

Each new experiment should use a new dated directory and include raw runs,
an aggregate table, `environment.txt`, command-line parameters, and a canonical
comparison generated outside the timed run.

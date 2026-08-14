# Manuscript experiment results layout

```text
results/manuscript/
  YYYYMMDD_HHMMSS_<profile>/
    protocol_meta.txt          # profile, regex, minsup, heap, variants
    environment.txt            # CPU/RAM/Java if capture script present
    warmup/                    # discarded JIT warm-up CSVs
    raw/                       # V*_run_XX.csv (measured)
    logs/                      # optional diagnostic logs
    summaries/
      all_runs.csv             # long format: Variant, Run, Dataset, minsup, metrics
      median_by_variant.csv    # median Time_s / MaxMem_MB per (Variant, Dataset, minsup)
```

**Profiles** (`run_manuscript_protocol.ps1`):

| Profile | Data | Default σ |
|---------|------|-----------|
| `pilot` | DB4, DB5 | 2, 4 |
| `db_full` | DB1–DB8 | 2,4,6,8,10,12 |
| `elec_scale` | `data/electricity_scale/ELEC_*` | 2,4,8 |

Do not mix profiles in one folder; one stamp directory = one protocol execution.

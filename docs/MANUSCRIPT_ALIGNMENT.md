# Manuscript alignment

| Manuscript claim | Code |
|------------------|------|
| HJ-OPF (primary) | `HJOPF` with `-Dmode=hash_only -DbitmapPolicy=never -DwsbPolicy=never` |
| Residual ablation (secondary) | `HJOPF` with residual flags / `-Dmode=adaptive` |
| Baseline | `OPF_Miner_Original` |
| No bitmap contribution | `SPARSE=false`, prefer `-DbitmapPolicy=never` |

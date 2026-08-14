# Manuscript alignment

This repository implements **Adaptive Fast OPF** as staged acceleration of OPF-Miner semantics.

| Manuscript claim | Code |
|------------------|------|
| Stage 1 HJ | `mode=hash_only` or Adaptive path `buildPrefixMap` |
| Stage 2 CPC | `adaptiveWsbCheapPrune` + `shouldPrune*` / CPC tiers |
| Stage 3 List/Gallop | `fuseScalar` / smart gallop when `adaptiveSmartIntersect` |
| Adaptive policy | `mode=adaptive`, staged CPC then Gallop decisions |
| No bitmap contribution | `SPARSE=false`, `bitmapPolicy=never` |

Do not cite sparse bitmap or static full-combine as the Adaptive result.

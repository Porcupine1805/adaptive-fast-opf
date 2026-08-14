# Adaptive Fast OPF — Handoff

**Updated:** 2026-08-14  
**Repo:** https://github.com/Porcupine1805/adaptive-fast-opf

## Research direction (locked)

1. Three optimization *stages* for OPF-Miner-style mining  
2. Static combine of all operators is **not** always optimal  
3. Analyse residual work and overhead after HJ  
4. **Adaptive Fast OPF** stages CPC / Gallop after HJ  

### Contributions (recommended count: 3)

| # | Content |
|---|---------|
| C1 | **HJ** — hash-indexed pair discovery (primary speedup) |
| C2 | Residual operators after HJ — **CPC** + optional **Gallop** (conditional) |
| C3 | **Adaptive** staging using problem-native signals only |

**Bitmap is not a contribution.** WSB classical narrative is replaced by **CPC**.

## Pipeline

```text
F_m
  → [1] HJ prefixMap          always
  → [2] CPC?                  residual / span / card [/ range]
  → [3] Sorted List | Gallop  Gallop iff skew
  → F_{m+1}
```

## Code entry

`src/benchmark/java/FOMAblationFlags.java`

| Concern | Property / mode |
|---------|-----------------|
| HJ-only | `-Dmode=hash_only -DwsbPolicy=never` |
| Adaptive | `-Dmode=adaptive -DwsbPolicy=cost` |
| CPC enable path | `adaptiveWsbCheapPrune` (default true if adaptive) |
| Gallop | `adaptiveSmartIntersect` (default true if adaptive) |
| CPC gate | `adaptiveCpcGate=B` (default) |
| Bitmap | disabled (`SPARSE=false`, default `bitmapPolicy=never`) |

## Empirical stance

- HJ carries most speedup vs OPF baseline.  
- CPC/Gallop gains are **conditional**; always-on can regress (range overhead, low prune rate).  
- Adaptive goal: protect HJ gains; enable residual operators when structure justifies them.  
- Correctness: same frequent OPF set (canonical dumps).

## Docs

- `README.md` — run instructions  
- `docs/ADAPTIVE_MECHANISM.md` — decision signals  
- `docs/Adaptive_FastOPF_Manuscript_Draft.md` — paper draft  

## Naming debt

System properties `adaptiveWsb*` control **CPC**. Metrics fields `wsbPrunes` etc. still used for CPC counts. Prefer documenting as CPC in paper tables.

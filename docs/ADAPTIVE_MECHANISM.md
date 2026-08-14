# Adaptive mechanism (current)

**Aligned with Adaptive Fast OPF paper direction (2026-08).**

## Pipeline

```text
Generation
  → HJ (prefixMap) always
  → Decide CPC?   (problem signals only)
  → Decide Gallop? (skew sample; may run without CPC)
  → Fuse survivors with Sorted List or Gallop
```

Bitmap fusion is **disabled** (`SPARSE = false`). Do not treat `bitmapPolicy=cost` as part of the Adaptive claim.

## CPC (Cheap-Prune Cascade)

Historical property prefix: `adaptiveWsb*`.

Tiers (cheap → expensive):

1. Residual weighted bound vs minSup  
2. Empty / non-overlapping span  
3. Card × weight  
4. Optional binary range-count (off by default via `adaptiveWsbUseRange=false` on the clean Adaptive path)

**Generation gate** (`adaptiveCpcGate`, default **B**):

| Gate | Meaning |
|------|---------|
| A | Fail-safe majority free O(1) prunes |
| **B** | Cost model: enable iff probe prune rate \(r > \alpha\) (`adaptiveCpcCheckFuseRatio`, default 0.08) |
| C | Structural floors only (N, pair count) |
| D | Legacy fixed prune-rate threshold (default 0.12) — demoted |

Floors: `adaptiveCpcMinN` (default 20000), `adaptiveCpcMinPairs`.

## Gallop

Enabled when `adaptiveSmartIntersect=true` and generation skew sample passes:

- `adaptiveGallopMinRatio`, `adaptiveGallopMinOcc`, `adaptiveGallopMinSkewFraction`
- `adaptiveGallopWithoutCpc=true` allows Gallop without CPC

Pairs that are not skewed still use classic two-pointer fusion.

## Signals allowed in decisions

- Series length \(N\), minSup, pair count after HJ  
- Residual / support tightness  
- Occurrence lengths and skew ratios  

**Not allowed:** wall-clock time as a control signal.

## Recommended commands

```text
-Dmode=hash_only -DbitmapPolicy=never -DwsbPolicy=never
-Dmode=adaptive  -DbitmapPolicy=never -DwsbPolicy=cost
```

With `mode=adaptive`, CPC and smart intersect default to on.

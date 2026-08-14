# Stage 3 Gallop, Adaptive Sampling, Ties & Decision Levels

**For manuscript Q&A / Methods.**  
**Code:** `fuseScalarSmart`, `gallopLowerBound`, `getShiftedOccurrences`, `decideUseCpcAfterHj`, `decideUseGallopAfterCpc`, length-2 initialization in `FOMAblationFlags`.

---

## 1. Galloping intersection (Stage 3)

### 1.1 Fusion geometry and the +1 shift

A superpattern occurrence ending at endpoint \(j\) requires:

\[
j-1 \in \mathrm{Occ}(p)
\quad\text{and}\quad
j \in \mathrm{Occ}(q).
\]

Implementation **does not** gallop on raw \(\mathrm{Occ}(p)\) against \(\mathrm{Occ}(q)\). Instead it builds once:

\[
\texttt{shiftedP}[i] = \mathrm{Occ}(p)[i] + 1
\]

(`getShiftedOccurrences`). Intersection is then ordinary equality of integers:

\[
\texttt{shiftedP}[i] = \mathrm{Occ}(q)[j]
\quad\Longleftrightarrow\quad
\text{aligned endpoint } j.
\]

**Consequence for galloping:** the +1 shift is applied **before** the intersection loop. Gallop / two-pointer only see two sorted integer arrays in the **same** coordinate system (suffix endpoint indices). There is no special “gallop with offset” arithmetic inside `gallopLowerBound`.

### 1.2 When a pair uses Gallop

Inside `fuseScalarSmart` (only if the **generation** enabled smart intersect):

\[
n_{\min}=\min(|\texttt{shiftedP}|,|\mathrm{Occ}(q)|),\quad
n_{\max}=\max(\ldots).
\]

Pair is **skewed** iff \(n_{\min}>0\), \(n_{\max}\ge L_{\min}\), and \(n_{\max}\ge \rho\cdot n_{\min}\).  
Otherwise → **classic two-pointer** on the same arrays.

### 1.3 Logic (pseudocode)

```text
Algorithm  GallopFuse(p, q)
  shiftedP ← Occ(p)[·] + 1          // prefix ends mapped to candidate j
  qPos     ← Occ(q)                 // suffix ends = j
  if not pairSkewed(shiftedP, qPos):
      return TwoPointerFuse(shiftedP, qPos)
  pIsLong ← (|shiftedP| ≥ |qPos|)
  i ← 0; j ← 0
  while i < |shiftedP| and j < |qPos|:
      pv ← shiftedP[i];  qv ← qPos[j]
      if pv = qv:
          // aligned endpoint qv; update weighted support / r,h cases as in SCF
          i ← i + 1;  j ← j + 1
      else if pv < qv:
          if pIsLong:  i ← gallopLowerBound(shiftedP, i, |shiftedP|, qv)  // jump on long side
          else:        i ← i + 1
      else:  # pv > qv
          if not pIsLong: j ← gallopLowerBound(qPos, j, |qPos|, pv)
          else:           j ← j + 1
```

```text
gallopLowerBound(values, lo, hi, target):
  // first index in [lo, hi) with values[idx] ≥ target
  exponential search (step = 1,2,4,...) while values[pos+step] < target
  then binary search in the last doubling window
```

Gallop advances **only the longer list** when that side is behind; the shorter side steps by one. Match set is identical to classic two-pointer.

### 1.4 After a match

Same as OPF-Miner SCF fusion: if \(p_1=q_m\), compare series values at window boundaries to assign candidate \(r\) vs \(h\); if equal boundaries, **neither** alternative is kept (strict model).

---

## 2. Sampling strategy for \(\hat r\) and \(\hat s\)

**Neither random nor “first \(K\) only”.** Both use **deterministic stride sampling** over the generation list (and up to 2 matching \(q\)’s per sampled \(p\) for skew).

### 2.1 CPC probe (\(\hat r\))

```text
stride = max(1, |F_m| / adaptiveCpcProbePairs)    // default probe budget ~24
for pi = 0, stride, 2·stride, ... while checks < budget:
    p = F_m[pi]
    if prefixSupport(p) < minsup: continue
    Q = prefixMap[suf(p)]
    if Q empty: continue
    q = Q[0]                    // one representative match
    if suffixSupport(q) < minsup: continue
    checks++
    if probeFreeO1Prune(p,q): freePrunes++   // residual/span/card only
r̂ = freePrunes / checks
require checks ≥ 4 else disable CPC
```

- **Not** uniform random.  
- **Not** only the first \(K\) patterns in list order without stride (unless \(|F_m|\) is tiny so stride = 1).  
- Maximal-support sort of \(F_m\) (when used) biases which patterns appear early; stride still spreads across the ordered list.

### 2.2 Skew probe (\(\hat s\))

```text
stride = max(1, |F_m| / adaptiveGallopSamplePairs)   // default budget ~48
for pi = 0, stride, ... while sampled < budget:
    p = F_m[pi]
    Q = prefixMap[suf(p)]
    for up to 2 entries q in Q:
        sample pair (p,q)
        if skewed by (ρ, L_min): skewed++
ŝ = skewed / sampled
```

Again: **stride over patterns + small fanout sample**, not RNG.

### 2.3 Manuscript wording

> Adaptive probes estimate free-prune rate and skew rate by deterministically striding through the current generation and inspecting a bounded number of hash-join matches per sampled pattern. No wall-clock measurement and no random seed enter the estimator.

---

## 3. Ties (equal values) under strict rank isomorphism

### 3.1 Model stance

Section 3.1 **strict** order isomorphism assumes pairwise **distinct** values in a window so \(\rho(x)\) is a permutation of \(\{1,\ldots,m\}\).

### 3.2 What the implementation does

| Stage | Tie handling |
|-------|----------------|
| **Length-2 seed scan** | For consecutive \(t_{i-1},t_i\): keep in (1,2) only if \(t_i>t_{i-1}\); in (2,1) only if \(t_i<t_{i-1}\). **If \(t_i=t_{i-1}\), the position is skipped** (not an occurrence of either pattern). |
| **Rank encoding `getOrder`** | Uses strict `<` to assign ranks; designed for distinct entries. Equal values are outside the intended model. |
| **Fusion boundary (\(p_1=q_m\))** | Compare first and last series values of the window: \(<\) → candidate \(r\), \(>\) → \(h\), **\(=\) → neither** (strict window rejected). |

There is **no** “shared rank” or “average rank” rule. Ties are **excluded** from occurrences / alternatives rather than assigned a tied rank.

### 3.3 Practical note

If the input series contains plateaus, those windows simply do not contribute OPF occurrences under this implementation—consistent with a strict permutation model, not dense ranks.

---

## 4. Stage-3 decision: generation-level vs pair-level

**Confirmed two-level logic:**

```text
Generation level:
  useGallopGen ← smartIntersect
                 ∧ (useCPC ∨ gallopWithoutCpc)
                 ∧ (ŝ ≥ s0)          // decideUseGallopAfterCpc

Pair level (only if useGallopGen):
  if pairSkewed(p,q):  fuseScalarSmart → Gallop path
  else:                fuseScalarClassic → two-pointer
```

| Question | Answer |
|----------|--------|
| Chỉ cần generation bật là **mọi** pair Gallop? | **Không.** |
| Generation bật + pair lệch? | **Gallop.** |
| Generation bật + pair cân? | **Classic two-pointer.** |
| Generation tắt? | Mọi pair classic (no smart path). |

Comment in code: *“Generation may be gallop-eligible overall, but this pair is balanced.”*

---

## 5. Quick reference table

| Topic | Mechanism |
|-------|-----------|
| +1 shift | Precompute `shiftedP = Occ(p)+1`; gallop on shifted vs `Occ(q)` |
| Gallop search | Exponential + binary lower_bound ≥ target on longer list |
| Sample \(\hat r,\hat s\) | Deterministic **stride**, capped count; not random |
| Ties | Skip equal consecutive values at length 2; reject equal boundaries at fusion |
| Gallop enable | Generation gate **and** per-pair skew test |

---

## 6. Code map

| Item | Location |
|------|----------|
| Shift | `PatternCandidate.getShiftedOccurrences` |
| Smart fuse | `fuseScalarSmart` |
| Gallop step | `gallopLowerBound` |
| Gen skew decision | `decideUseGallopAfterCpc` |
| CPC sample | `decideUseCpcAfterHj` + `probeFreeO1Prune` |
| Length-2 ties | scan `curr > prev` / `curr < prev` only |
| Boundary ties | `tFirst < tLast` / `>` / else drop |

---

*End of note.*

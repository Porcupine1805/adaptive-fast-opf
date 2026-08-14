# CPC Bounds & Adaptive Signals — Mathematical Detail for the Manuscript

**Purpose.** This note formalizes (1) the **span**, **cardinality–weight**, and **range** bounds used in the Cheap-Prune Cascade (CPC), and (2) how **Adaptive Fast OPF** quantifies *pair mass*, *residual structure*, and *length skew* to decide CPC / Gallop **without** wall-clock time.

**Alignment.** Definitions follow OPF-Miner forgetting support and the implementation in `FOMAblationFlags.java` (`shouldPruneAdaptivePair`, `rangeIntersectionUpperBound`, `decideUseCpcAfterHj`, `decideUseGallopAfterCpc`).

---

## 0. OPF notation (recap)

| Symbol | Meaning |
|--------|---------|
| \(t=(t_1,\ldots,t_n)\) | Input series, length \(N=n\) |
| \(k\in(0,1)\) | Forgetting factor (often \(k=1/n\)) |
| \(w_j = e^{-k(n-j)}\) | Weight of an occurrence ending at index \(j\) |
| \(\sigma\) | Minimum forgetting-aware support (`minsup`) |
| \(\mathrm{Occ}(p)\) | Sorted list of end positions of pattern \(p\) |
| \(fsup(p)=\sum_{j\in\mathrm{Occ}(p)} w_j\) | Forgetting-aware support |
| \(pre(p),\,suf(p)\) | Normalized order-preserving prefix / suffix |
| Compatible pair \((p,q)\) | \(\mathrm{suf}(p)=\mathrm{pre}(q)\) (after HJ) |

**Fusion geometry.** A length-\((m+1)\) superpattern occurrence ending at \(j\) requires:

- a prefix occurrence of \(p\) ending at \(j-1\), and  
- a suffix occurrence of \(q\) ending at \(j\).

Hence candidate endpoints live in the aligned set

\[
A(p,q)=\{j:\ j-1\in\mathrm{Occ}(p),\ j\in\mathrm{Occ}(q)\}.
\]

**Residual (weighted) bound (already from OPF theory).** If \(r\) is a superpattern of parents \(p,q\),

\[
fsup(r)\ \le\ e^{k}\,fsup(p),
\qquad
fsup(r)\ \le\ fsup(q).
\]

In code, residual fields `prefixSupport` / `suffixSupport` play the role of the remaining weighted mass associated with \(p\) as prefix and \(q\) as suffix. With \(\mathrm{expK}=e^{k}\),

\[
U_{\mathrm{res}}(p,q)
=
\min\bigl(\texttt{prefixSupport}(p)\cdot e^{k},\ \texttt{suffixSupport}(q)\bigr).
\]

If \(U_{\mathrm{res}}<\sigma\), CPC prunes **before any list scan** (residual tier).

The rest of this note details the three geometry-based tiers that use **occurrence positions**.

---

## 1. Span bound

### 1.1 Definition

Let the sorted occurrence lists be

\[
\mathrm{Occ}(p)=(p_1<\cdots<p_{n_p}),
\qquad
\mathrm{Occ}(q)=(q_1<\cdots<q_{n_q}).
\]

Any fused endpoint \(j\in A(p,q)\) must satisfy

\[
j-1\in\mathrm{Occ}(p)
\quad\Rightarrow\quad
p_1 \le j-1 \le p_{n_p}
\quad\Rightarrow\quad
p_1+1 \le j \le p_{n_p}+1,
\]

and

\[
j\in\mathrm{Occ}(q)
\quad\Rightarrow\quad
q_1 \le j \le q_{n_q}.
\]

Therefore every feasible \(j\) lies in the integer interval

\[
[\ell,h]
=
\bigl[\max(p_1+1,\ q_1),\ \min(p_{n_p}+1,\ q_{n_q})\bigr].
\]

**Span prune:** if \(\ell > h\), then \(A(p,q)=\emptyset\). No superpattern occurrence exists → \(fsup(r)=0<\sigma\) → prune.

### 1.2 Implementation (O(1))

```text
low  = max(pFirst + 1, qFirst)
high = min(pLast  + 1, qLast)
if low > high: prune  // cpcSpanPrunes
```

Empty lists are treated as an immediate span-style prune.

### 1.3 Intuition

Span asks: *do the two occurrence ranges even overlap after the mandatory +1 shift of the prefix?*  
It uses only the **first and last** end positions—no binary search, independent of list length beyond reading endpoints. It does **not** use \(k\) or \(\sigma\) except that an empty alignment implies support \(0<\sigma\).

---

## 2. Cardinality–weight bound

### 2.1 Definition

Even when \([\ell,h]\) is nonempty, the number of matches cannot exceed

\[
|A(p,q)|\ \le\ \min\bigl(|\mathrm{Occ}(p)|,\ |\mathrm{Occ}(q)|\bigr).
\]

Each match ending at some \(j\le h\) has weight \(w_j \le w_h\), because \(w_j=e^{-k(n-j)}\) is **increasing** in \(j\) (newer positions weigh more). Hence

\[
fsup(r)
\ \le\
\sum_{j\in A(p,q)} w_j
\ \le\
|A(p,q)|\cdot w_h
\ \le\
\min(n_p,n_q)\cdot w_h.
\]

**Cardinality–weight prune:** if

\[
\min\bigl(|\mathrm{Occ}(p)|,\ |\mathrm{Occ}(q)|\bigr)\cdot w_h \ <\ \sigma,
\]

then \(fsup(r)<\sigma\) → prune.

### 2.2 Role of \(k\) and positions

- \(w_h = e^{-k(n-h)}\) depends on the forgetting factor \(k\), series length \(n\), and the **rightmost feasible endpoint** \(h\) from the span interval.  
- Using \(w_h\) (not \(w_n=1\)) is tighter whenever the alignment cannot reach the series end.  
- \(\sigma\) (`minsup`) is the threshold in the comparison.

### 2.3 Implementation (O(1))

```text
wHigh = weight(high)           // e^{-k(n-high)}
minOcc = min(|Occ(p)|, |Occ(q)|)
if minOcc * wHigh < minsup: prune  // cpcCardPrunes
```

### 2.4 Intuition

Even if every occurrence of the shorter parent could align, the **total weighted mass** is still at most “count × heaviest feasible weight.” If that product is below \(\sigma\), fusion cannot create a frequent OPF. This bound is weak when lists are long and \(w_h\approx 1\), but it is essentially free.

---

## 3. Range bound

### 3.1 Motivation

Span and card–weight ignore *how many* positions of each list actually fall inside \([\ell,h]\). Range tightens the count by **binary search** on the sorted occurrence arrays.

### 3.2 Definition

Let

\[
\begin{align*}
c_p &= \bigl|\{ u\in\mathrm{Occ}(p):\ \ell-1 \le u \le h-1 \}\bigr|,\\
c_q &= \bigl|\{ v\in\mathrm{Occ}(q):\ \ell \le v \le h \}\bigr|.
\end{align*}
\]

(The \(\pm 1\) shift mirrors prefix end \(j-1\) vs suffix end \(j\).)

Then

\[
|A(p,q)|\ \le\ \min(c_p,c_q),
\]

and with the same weight argument as above,

\[
U_{\mathrm{range}}(p,q)
=
\min(c_p,c_q)\cdot w_h.
\]

**Range prune:** if \(U_{\mathrm{range}}<\sigma\), prune.

### 3.3 Implementation (\(O(\log n_p+\log n_q)\))

```text
low, high as in span
pCount = countInRange(Occ(p), low-1, high-1)  // binary lower/upper bound
qCount = countInRange(Occ(q), low,   high)
possibleMatches = min(pCount, qCount)
U_range = possibleMatches * weight(high)
if U_range < minsup: prune  // cpcRangePrunes
```

### 3.4 When range is invoked (cost control)

Range is **not** free. In the CPC cascade it runs only if:

1. Residual did not already prune;  
2. Span did not prune;  
3. Card–weight did not prune;  
4. Residual is **tight**: \(U_{\mathrm{res}} < \tau_{\mathrm{cpc}}\cdot\sigma\) (property `adaptiveWsbCpcTight`, default \(1.5\));  
5. Occurrence mass is moderate: \(n_p+n_q \le M\) (property `adaptiveWsbMaxOccForRange`).

**Intuition.** Binary search is worthwhile only when the residual is close to \(\sigma\) (otherwise cheaper tiers already decided) and lists are not so long that scanning for fusion would be comparable anyway.

### 3.5 Cascade order (safety)

```text
Residual O(1) → Span O(1) → Card×weight O(1) → [optional] Range O(log)
```

Each bound is a **valid upper bound** on \(fsup(r)\). Pruning never removes a pair that could still produce a frequent OPF under these inequalities; survivors still undergo exact fusion (list intersection + weighted support), same as OPF-Miner semantics.

---

## 4. Summary table of CPC tiers

| Tier | Inputs from OPF | Formula (sketch) | Cost | Uses \(k\)? | Uses \(\sigma\)? |
|------|-----------------|------------------|------|-------------|------------------|
| Residual | `prefixSupport`, `suffixSupport`, \(e^{k}\) | \(\min(pre\cdot e^{k}, suf)<\sigma\) | \(O(1)\) | yes (\(e^{k}\)) | yes |
| Span | first/last of \(\mathrm{Occ}(p),\mathrm{Occ}(q)\) | \(\ell>h\Rightarrow\) empty \(A(p,q)\) | \(O(1)\) | no | only via empty support |
| Card×weight | list lengths, \(w_h\), \(\sigma\) | \(\min(n_p,n_q)\,w_h<\sigma\) | \(O(1)\) | yes (via \(w_h\)) | yes |
| Range | counts in \([\ell,h]\), \(w_h\), \(\sigma\) | \(\min(c_p,c_q)\,w_h<\sigma\) | \(O(\log)\) | yes | yes |

---

## 5. Adaptive signals (no wall-clock control)

Adaptive Fast OPF **always** runs HJ. CPC and Gallop are optional. Decisions use only quantities available after HJ / from pattern structure.

### 5.1 Pair mass \(P\) (and series length \(N\))

**Definition.**

\[
P
=
\sum_{p\in F_m}
\bigl|\mathrm{prefixMap}[\mathrm{suf}(p)]\bigr|
=
|\mathrm{Pairs}_m|
\quad\text{(number of HJ-compatible pairs).}
\]

**Use.**

- If \(N < N_0\) (`adaptiveCpcMinN`) or \(P < P_0\) (`adaptiveCpcMinPairs`) → **do not enable CPC** for this generation (fail-safe HJ-only fuse).  
- Rationale: when few pairs remain, check overhead cannot pay; pair mass is a **workload proxy** measured in *counts*, not seconds.

### 5.2 Residual structure (for CPC enablement)

**Probe.** Sample up to \(m\) HJ pairs (stride over generation, default `adaptiveCpcProbePairs`). For each sample pair, evaluate **O(1) free prunes only** (residual, empty, span, card×weight)—**not** range.

\[
\hat r
=
\frac{\#\{\text{sample pairs killed by O(1) bounds}\}}{\#\{\text{sample pairs checked}\}}.
\]

**Gate B (default, cost-model style in problem units):**

\[
\text{enable CPC}
\iff
N\ge N_0
\ \wedge\
P\ge P_0
\ \wedge\
\hat r > \alpha,
\quad
\alpha=\texttt{adaptiveCpcCheckFuseRatio}\ (\text{default }0.08).
\]

Interpretation in **work units**, not wall-clock:

- One O(1) check costs a small constant \(c_0\) “ops”.  
- One fuse costs work proportional to \(|\mathrm{Occ}(p)|+|\mathrm{Occ}(q)|\).  
- \(\alpha\) stands for a fixed ratio \(c_0 / \overline{W}_{\mathrm{fuse}}\) used as a threshold on the **fraction** of pairs expected to die for free.  
- **Implementation does not measure runtime** to set \(\alpha\); \(\hat r\) is a pure count ratio.

**Other gates (ablation):**

| Gate | Rule |
|------|------|
| A | \(\hat r \ge 1/2\) (majority free prune) |
| C | only \(N,P\) floors (always O(1) CPC when eligible) |
| D | \(\hat r \ge 0.12\) (legacy fixed rate; demoted in narrative) |

**Residual tightness for range (pair-level, not generation gate):**  
range runs only if \(U_{\mathrm{res}} < \tau_{\mathrm{cpc}}\sigma\) and \(n_p+n_q\le M\). That is a **local residual-structure** test on the same \(U_{\mathrm{res}}\) used in tier 1.

### 5.3 Length skew (for Gallop)

**Pair skew indicator.** For a compatible pair \((p,q)\),

\[
n_{\min}=\min(|\mathrm{Occ}(p)|,|\mathrm{Occ}(q)|),\quad
n_{\max}=\max(|\mathrm{Occ}(p)|,|\mathrm{Occ}(q)|).
\]

Pair is **skewed** if

\[
n_{\max} \ge L_{\min}
\quad\text{and}\quad
n_{\max} \ge \rho\cdot n_{\min},
\]

with defaults \(\rho=\) `adaptiveGallopMinRatio` (e.g. 8), \(L_{\min}=\) `adaptiveGallopMinOcc` (e.g. 256).

**Generation skew rate.** Sample up to \(S\) real HJ pairs (`adaptiveGallopSamplePairs`):

\[
\hat s
=
\frac{\#\{\text{sampled pairs that are skewed}\}}{\#\{\text{sampled pairs}\}}.
\]

**Enable Gallop for the generation** if

\[
\hat s \ge s_0
\quad\text{(`adaptiveGallopMinSkewFraction`, default }0.35\text{)}.
\]

When Gallop is enabled at generation level, **each** pair still uses Gallop only if that pair is skewed; balanced pairs keep two-pointer fusion.

**Independence from CPC.** With `adaptiveGallopWithoutCpc=true`, Gallop may run even if CPC is off—skew is a fusion-shape signal, not a support-prune signal.

### 5.4 What is *not* used as a signal

| Forbidden in control flow | Allowed only in experimental reporting |
|---------------------------|----------------------------------------|
| Wall-clock time, `System.nanoTime` as enable rule | `Time_s`, median speedup tables |
| Peak memory as enable rule | Memory columns in results CSV |
| JIT / GC metrics | Environment appendix |

(Decision code may *record* nanoseconds for diagnostics; it must not branch on them for CPC/Gallop enablement. Probe logic uses only structural counts and bounds above.)

---

## 6. Decision flow (one generation)

```text
HJ: build prefixMap, form Pairs (mass P)

if N < N0 or P < P0:
    useCPC = false
else:
    compute r̂ on O(1) probe
    useCPC = Gate(r̂, α)          // default Gate B

compute ŝ on skew sample of HJ pairs
useGallop = (ŝ ≥ s0) ∧ smartIntersect
            ∧ (useCPC ∨ gallopWithoutCpc)

for each (p,q) in Pairs:
    if useCPC and CPC-cascade(p,q) prunes: skip fuse
    else fuse with Gallop if (useGallop ∧ pairSkewed) else two-pointer
```

---

## 7. Manuscript-oriented wording (English)

**CPC bounds.**  
*After hash-indexed retrieval of join-compatible parents \((p,q)\), Adaptive Fast OPF applies a cascade of upper bounds on the forgetting-aware support of any fused superpattern. The residual bound scales the prefix support by \(e^{k}\) and takes the minimum with the suffix support. The span bound tests emptiness of the shifted endpoint interval derived from the first and last occurrence positions. The cardinality–weight bound multiplies the shorter occurrence count by the forgetting weight at the rightmost feasible endpoint. When the residual is tight, a range bound replaces the crude count by the number of endpoints lying inside that interval, obtained by binary search on the sorted occurrence lists. All comparisons are against the minimum support threshold \(\sigma\).*

**Adaptive signals.**  
*Generation-level activation of CPC and galloping intersection depends only on problem quantities available after the hash join: the number of compatible pairs (pair mass), a probe estimate of the fraction of pairs eliminated by constant-time bounds (residual structure), and the fraction of sampled pairs whose occurrence lengths satisfy a prescribed skew ratio (length skew). No wall-clock measurement enters the enablement rule.*

---

## 8. Code map

| Concept | Function / property |
|---------|---------------------|
| Residual / span / card / range cascade | `shouldPruneAdaptivePair` |
| Range formula | `rangeIntersectionUpperBound` |
| O(1) probe for gate | `probeFreeO1Prune`, `decideUseCpcAfterHj` |
| Skew decision | `decideUseGallopAfterCpc` |
| CPC gate family | `adaptiveCpcGate` = A\|B\|C\|D |
| \(\alpha\) | `adaptiveCpcCheckFuseRatio` |
| Skew \(\rho, L_{\min}, s_0\) | `adaptiveGallopMinRatio`, `MinOcc`, `MinSkewFraction` |

---

*End of note. Suitable for Methods / Appendix of the Adaptive Fast OPF manuscript.*

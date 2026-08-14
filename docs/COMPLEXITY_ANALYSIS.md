# Complexity Analysis — OPF-Miner vs Adaptive Fast OPF

**Manuscript note (Methods / Complexity).**  
Symbols match the Adaptive Fast OPF pipeline: Stage 1 HJ, Stage 2 CPC, Stage 3 List/Gallop, plus Adaptive decision overhead.

---

## 0. Notation

| Symbol | Meaning |
|--------|---------|
| \(n\) | Length of the time series |
| \(m\) | Current pattern length |
| \(L = \|F_m\|\) | Number of frequent patterns of length \(m\) |
| \(J = \|\mathrm{Pairs}_m\|\) | Number of join-compatible pairs after HJ: \(\mathrm{suf}(p)=\mathrm{pre}(q)\) |
| \(G\) | Number of generations until \(F_m=\emptyset\) (typically \(G \le n-1\), often \(\ll n\)) |
| \(n_p=\|\mathrm{Occ}(p)\|\), \(n_q=\|\mathrm{Occ}(q)\|\) | Occurrence-list lengths |
| \(W_{\mathrm{fuse}}(p,q)\) | Work of intersecting one pair (comparisons / scans) |
| \(K\) | Number of distinct normalized prefix keys in \(F_m\) |
| \(s_{\mathrm{cpc}}, s_{\mathrm{gal}}\) | Adaptive sample budgets (constants, e.g. 24 and 48) |

**Output-sensitive principle.** Any algorithm that *materializes* all compatible pairs must spend \(\Omega(J)\) work on those pairs. HJ matches this lower order for discovery; residual stages charge only survivors (or all pairs if CPC is on).

---

## 1. Baseline: OPF-Miner-style pairing (within a generation)

### 1.1 Naive / enumeration-style

Without group restrictions, testing all ordered pairs:

\[
T_{\mathrm{pair}}^{\mathrm{naive}} = \Theta(L^2 \cdot c_{\mathrm{key}}),
\]

where \(c_{\mathrm{key}}\) is the cost of comparing normalized suffix/prefix arrays (length \(m-1\)).

### 1.2 GP-Fusion (OPF-Miner)

Patterns are partitioned into four groups; each pattern only attempts fusion against **two** admissible groups. Let \(L_a,L_b\) be sizes of an admissible group pair. Summing over rules:

\[
T_{\mathrm{pair}}^{\mathrm{GP}} = \Theta\!\left(\sum_{\mathrm{rules}} L_a L_b\right)
= \Theta(\alpha L^2 \cdot c_{\mathrm{key}}),
\quad \alpha \approx \tfrac12
\]

in the balanced case (OPF-Miner: “\(L^2/2\) rather than \(L^2\)”).

**Important:** GP-Fusion reduces the *constant* in front of \(L^2\); the dependence on \(L\) remains **quadratic** whenever admissible groups are \(\Theta(L)\). Failed \(\mathrm{suf}(p)\neq\mathrm{pre}(q)\) tests are still paid inside those groups.

### 1.3 Support / fusion (both systems, per emitted pair)

For each pair that proceeds to occurrence fusion (two-pointer):

\[
W_{\mathrm{fuse}}(p,q) = \Theta(n_p + n_q)
\]

comparisons in the worst case (and typical case for classic merge). Gallop is \(O(n_p+n_q)\) worst-case as well, but can use fewer comparisons under skew (see Stage 3).

### 1.4 Per-generation baseline (compact)

\[
\boxed{
T_{\mathrm{OPF\text{-}gen}}
=
\underbrace{O(L^2\,c_{\mathrm{key}})}_{\text{GP-Fusion pair tests}}
+
\underbrace{\sum_{(p,q)\in \mathcal{P}_{\mathrm{GP}}} W_{\mathrm{fuse}}(p,q)}_{\text{fuse attempted pairs}}
}
\]

Here \(\mathcal{P}_{\mathrm{GP}}\) is the set of pairs that pass the key test after group scans; \(|\mathcal{P}_{\mathrm{GP}}|=J\) when the key test is exact, but the **pairing work** is still \(O(L^2)\), not \(O(J)\).

---

## 2. Stage 1 — Hash-Indexed Join (HJ)

### 2.1 Build

One pass over \(F_m\): insert each pattern under key \(\mathrm{pre}(q)\).

\[
T_{\mathrm{build}} = \Theta(L \cdot c_{\mathrm{hash}}),
\]

with \(c_{\mathrm{hash}}\) the cost of hashing/equality on an \((m-1)\)-integer key (amortized \(O(m)\) worst-case for equality; expected \(O(1)\) per op under standard hashing assumptions if keys are already stored).

### 2.2 Probe

For each \(p\), lookup \(\mathrm{suf}(p)\) and iterate the bucket:

\[
T_{\mathrm{probe}} = \Theta\!\left(L \cdot c_{\mathrm{lookup}} + J \cdot c_{\mathrm{emit}}\right).
\]

### 2.3 Stage-1 total

\[
\boxed{
T_{\mathrm{HJ}} = \Theta(L + J)
}
\]

(in unit-cost RAM model counting map ops and pair emissions; multiply by \(O(m)\) if each key compare scans \(m-1\) ranks).

### 2.4 Contrast with GP-Fusion

| | GP-Fusion | HJ |
|--|-----------|-----|
| Dominant term | \(O(L^2)\) key tests | \(\Theta(L+J)\) |
| Failed matches | Paid as array equality | Empty / non-matching buckets not scanned elementwise |
| When \(J=\Theta(L^2)\) | Same order as emitting all pairs | Still \(\Theta(L+J)=\Theta(L^2)\) — **optimal output-sensitively** |
| When \(J=o(L^2)\) | Still \(O(L^2)\) tests | **Strictly cheaper** discovery |

**Highlight for the paper:** Adaptive Fast OPF does not claim sublinear work in \(J\). It claims **pair discovery is output-sensitive**, whereas OPF-Miner’s GP-Fusion remains quadratic in \(L\) even when few pairs are compatible.

---

## 3. Stage 2 — Cheap-Prune Cascade (CPC)

CPC is applied to pairs already produced by HJ (or to the same \(J\) stream).

### 3.1 Per-pair costs

| Tier | Cost |
|------|------|
| Residual | \(O(1)\) |
| Span | \(O(1)\) |
| Cardinality × weight | \(O(1)\) |
| Range (optional) | \(O(\log n_p + \log n_q)\) |

Let \(J_{\mathrm{range}}\subseteq J\) be pairs that reach the range tier.

\[
T_{\mathrm{CPC}}
=
O(J) + O\!\left(\sum_{(p,q)\in J_{\mathrm{range}}} (\log n_p + \log n_q)\right).
\]

If range is disabled or rarely triggered, \(T_{\mathrm{CPC}}=O(J)\).

### 3.2 Effect on fusion work

Let \(J_{\mathrm{surv}}\) be pairs not pruned. Fuse work becomes

\[
\sum_{(p,q)\in J_{\mathrm{surv}}} W_{\mathrm{fuse}}(p,q)
\ \le\
\sum_{(p,q)\in J} W_{\mathrm{fuse}}(p,q).
\]

CPC is beneficial when the saved fuse mass exceeds \(T_{\mathrm{CPC}}\) (data-dependent; not always true — see Adaptive gates).

### 3.3 Stage-2 box

\[
\boxed{
T_{\mathrm{CPC}} = O(J) + O(J_{\mathrm{range}}\log \bar n_{\mathrm{occ}})
}
\]

---

## 4. Stage 3 — Occurrence fusion (Sorted List / Gallop)

### 4.1 Classic two-pointer

\[
W_{\mathrm{TP}}(p,q) = \Theta(n_p + n_q).
\]

### 4.2 Gallop

Worst case remains \(O(n_p + n_q)\). Under skew (\(n_{\max}\gg n_{\min}\)), comparison count can drop toward

\[
O\!\bigl(n_{\min}\log (n_{\max}/n_{\min})\bigr)
\]

on idealized random / adversarial-skip patterns (standard galloping intersection analysis); constants depend on implementation (gallop only on the longer side).

### 4.3 Stage-3 total

\[
\boxed{
T_{\mathrm{fuse}}
=
\sum_{(p,q)\in J_{\mathrm{surv}}} W_{\ast}(p,q),
\quad
W_{\ast}\in\{W_{\mathrm{TP}}, W_{\mathrm{Gallop}}\}
}
\]

Gallop does **not** change asymptotic worst-case of the mining pipeline; it is a comparison-reduction refinement under skew.

---

## 5. Adaptive decision overhead

### 5.1 CPC enablement probe

At most \(s_{\mathrm{cpc}}\) pairs (constant budget), each \(O(1)\) bounds:

\[
T_{\mathrm{decide\text{-}CPC}} = O(s_{\mathrm{cpc}}) = O(1)
\quad\text{per generation (w.r.t. }L,J\text{)}.
\]

Pair-mass count (optional full pass to compute \(J\)) is \(O(L+J)\) if implemented as a full scan; can be merged with the probe loop. Floors on \(N\) and \(J\) are \(O(1)\) tests.

### 5.2 Gallop enablement probe

\[
T_{\mathrm{decide\text{-}Gallop}} = O(s_{\mathrm{gal}}) = O(1)
\quad\text{per generation}.
\]

### 5.3 Box

\[
\boxed{T_{\mathrm{AdaptiveDecision}} = O(1)\ \text{per generation (fixed sample budgets)}}
\]

Decisions use **counts and bounds only**, not wall-clock.

---

## 6. Per-generation and end-to-end totals

### 6.1 One generation — Adaptive Fast OPF (HJ path)

\[
\begin{align*}
T_{\mathrm{ADP\text{-}gen}}
&=
T_{\mathrm{HJ}}
+ T_{\mathrm{AdaptiveDecision}}
+ \mathbf{1}_{\mathrm{CPC}}\,T_{\mathrm{CPC}}
+ T_{\mathrm{fuse}} \\[4pt]
&=
\Theta(L+J)
+ O(1)
+ O(J + J_{\mathrm{range}}\log \bar n)
+ \sum_{J_{\mathrm{surv}}} W_{\ast}(p,q).
\end{align*}
\]

### 6.2 One generation — OPF-Miner (GP-Fusion path)

\[
T_{\mathrm{OPF\text{-}gen}}
=
O(L^2\,c_{\mathrm{key}})
+ \sum_{\mathcal{P}_{\mathrm{GP}}} W_{\mathrm{TP}}(p,q).
\]

### 6.3 Highlighted difference (discovery only)

\[
\boxed{
\begin{array}{c}
\text{Pair discovery:}
\quad
T_{\mathrm{OPF}}^{\mathrm{discover}} = O(L^2)
\quad\text{vs}\quad
T_{\mathrm{HJ}}^{\mathrm{discover}} = \Theta(L+J)
\end{array}
}
\]

When \(J = o(L^2)\), HJ asymptotically dominates GP-Fusion on the discovery term. When \(J=\Theta(L^2)\), discovery costs meet at the output-sensitive lower bound, and runtime is dominated by fusion \(\sum (n_p+n_q)\).

### 6.4 Full mining run

Sum over generations \(m=2,3,\ldots\) until empty:

\[
T_{\mathrm{full}}
=
\sum_{m} T_{\mathrm{gen}}(F_m)
+
T_{\mathrm{seed}}(n),
\]

where seed length-2 scan is \(\Theta(n)\). Let \(L_m=\|F_m\|\), \(J_m=\|\mathrm{Pairs}_m\|\). Then

\[
T_{\mathrm{ADP}}
=
\Theta(n) + \sum_m \Theta(L_m + J_m) + \sum_m T_{\mathrm{fuse}}^{(m)},
\]

\[
T_{\mathrm{OPF}}
=
\Theta(n) + \sum_m O(L_m^2) + \sum_m T_{\mathrm{fuse}}^{(m)}.
\]

Fusion sums differ if CPC/Gallop change \(J_{\mathrm{surv}}\) or comparison counts; the **structural** gap emphasized in the paper is \(\sum_m O(L_m^2)\) vs \(\sum_m \Theta(L_m+J_m)\).

---

## 7. Space complexity (brief)

| Component | Space |
|-----------|--------|
| Series + forgetting weights | \(\Theta(n)\) |
| \(F_m\) patterns + occurrence lists | \(\Theta\bigl(\sum_{p\in F_m} (m + n_p)\bigr)\) |
| `prefixMap` | \(\Theta(L)\) keys + references (lists share pattern objects) |
| Gallop / CPC | \(O(1)\) extra beyond lists (no full bitmap matrix) |

Adaptive Fast OPF does **not** require an \(O(n)\) dense bitvector per pattern in the default design.

---

## 8. Summary table (camera-ready)

| Stage | OPF-Miner (typical) | Adaptive Fast OPF |
|-------|---------------------|-------------------|
| Pair discovery | \(O(L^2)\) (GP-Fusion \(\sim L^2/2\)) | \(\Theta(L+J)\) (HJ) |
| Pre-fusion filter | Prefix/suffix prune *during* SCF | CPC \(O(J)\) (+ optional \(O(J_{\mathrm{range}}\log n)\)) *before* fuse |
| Occurrence fuse | Two-pointer \(\sum (n_p+n_q)\) | Same, optional Gallop under skew |
| Policy | Fixed pipeline | \(O(1)\) sample decisions / generation |
| Dominant improvement | — | Discovery \(O(L^2)\to\Theta(L+J)\) when \(J\ll L^2\) |

---

## 9. Suggested manuscript paragraph (English)

> Within a generation of \(L\) frequent patterns, OPF-Miner relies on GP-Fusion to restrict which groups interact, but still performs a quadratic number of suffix–prefix compatibility tests inside admissible groups. Adaptive Fast OPF replaces this search by a hash-indexed join on normalized length-\((m-1)\) keys. Building and probing the multimap costs \(\Theta(L+J)\) operations, where \(J\) is the number of structurally compatible pairs—the output-sensitive cost of enumerating the join. Subsequent CPC checks add \(O(J)\) (or \(O(J\log n)\) when range bounds are invoked), and occurrence fusion retains the standard \(\Theta(n_p+n_q)\) two-pointer bound, optionally reduced in comparisons by galloping under skew. End-to-end, the asymptotic gap relative to OPF-Miner is concentrated in pair discovery: \(\sum_m O(L_m^2)\) versus \(\sum_m \Theta(L_m+J_m)\).

---

*End of complexity analysis note.*

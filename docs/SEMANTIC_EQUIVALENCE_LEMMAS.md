# Semantic Equivalence: Completeness & Soundness Lemmas

**Manuscript use:** Methods / Correctness subsection.  
**Claim:** Adaptive Fast OPF (HJ + optional CPC + optional Gallop) returns **exactly** the same set of frequent order-preserving patterns (with the same forgetting-aware supports) as OPF-Miner under identical inputs \((t,\sigma,k)\).

---

## 0. Setup and notation

Fix a time series \(t=(t_1,\ldots,t_n)\), minimum support \(\sigma>0\), and forgetting factor \(k\in(0,1)\).  
Weights \(w_j=e^{-k(n-j)}\). For an OPF \(p\) with occurrence end-set \(\mathrm{Occ}(p)\),

\[
fsup(p)=\sum_{j\in\mathrm{Occ}(p)} w_j.
\]

Let \(F_m^{\mathrm{OPF}}\) (resp. \(F_m^{\mathrm{ADP}}\)) be the set of length-\(m\) patterns with \(fsup\ge\sigma\) produced by OPF-Miner (resp. Adaptive Fast OPF) at generation \(m\).

**Normalized join keys** (identical to OPF-Miner):

\[
\mathrm{pre}(p)=\mathrm{norm}(p_1,\ldots,p_{m-1}),\qquad
\mathrm{suf}(p)=\mathrm{norm}(p_2,\ldots,p_m),
\]

where \(\mathrm{norm}\) is the unique rank-encoding of a sequence of distinct values into a permutation of \(\{1,\ldots,m-1\}\).

**Compatible pairs:**

\[
\mathrm{Comp}(F_m)=\bigl\{(p,q)\in F_m\times F_m:\ \mathrm{suf}(p)=\mathrm{pre}(q)\bigr\}.
\]

OPF-Miner enumerates a (group-restricted) superset of pairs and retains those in \(\mathrm{Comp}(F_m)\). Adaptive Fast OPF enumerates \(\mathrm{Comp}(F_m)\) via HJ, optionally drops pairs by CPC, and fuses survivors by two-pointer or Gallop.

---

## 1. Lemma (Unique join key)

**Lemma 1 (Normalized prefix–suffix key).**  
For any length-\(m\) OPF \(p\) arising under the strict order-isomorphism model, the arrays \(\mathrm{pre}(p)\) and \(\mathrm{suf}(p)\) are uniquely determined by \(p\), and for any \(p,q\),

\[
\mathrm{suf}(p)=\mathrm{pre}(q)
\quad\Longleftrightarrow\quad
\text{the length-\((m-1)\) overlap of \(p\) and \(q\) is order-isomorphic}.
\]

**Proof sketch.**  
\(\mathrm{norm}\) is a bijection between order-isomorphism classes of length-\((m-1)\) windows and permutations of \(\{1,\ldots,m-1\}\). Equality of normalized keys is therefore necessary and sufficient for the OPF-Miner join predicate used in SCF / GP-Fusion. \(\square\)

**Corollary.** Hashing \(\mathrm{pre}(\cdot)\) with value-based equality on the integer array is a **faithful** index for the join attribute: no two inequivalent overlaps share a key, and no equivalent overlap is split across keys.

---

## 2. Completeness & soundness of HJ (Stage 1)

### 2.1 Relation to GP-Fusion

GP-Fusion partitions \(F_m\) into four groups and only scans **admissible** group pairs. Every pair with \(\mathrm{suf}(p)=\mathrm{pre}(q)\) lies in some admissible group combination (OPF-Miner group theorems). Conversely, GP-Fusion may **test** pairs with \(\mathrm{suf}(p)\neq\mathrm{pre}(q)\); those fail the equality check and never enter fusion.

Thus the set of pairs that OPF-Miner **fuses** is exactly \(\mathrm{Comp}(F_m)\).

### 2.2 Lemma (HJ enumerates exactly \(\mathrm{Comp}(F_m)\))

**Lemma 2 (HJ completeness & soundness).**  
Let \(\mathrm{prefixMap}[\kappa]=\{q\in F_m:\mathrm{pre}(q)=\kappa\}\). The HJ procedure

\[
\bigcup_{p\in F_m}\ \{p\}\times \mathrm{prefixMap}[\mathrm{suf}(p)]
\]

emits **exactly** the set \(\mathrm{Comp}(F_m)\).

**Proof.**

*Soundness.* If \(q\in\mathrm{prefixMap}[\mathrm{suf}(p)]\), then \(\mathrm{pre}(q)=\mathrm{suf}(p)\), hence \((p,q)\in\mathrm{Comp}(F_m)\).

*Completeness.* If \((p,q)\in\mathrm{Comp}(F_m)\), then \(\mathrm{pre}(q)=\mathrm{suf}(p)=\kappa\). During the build phase \(q\) is inserted into \(\mathrm{prefixMap}[\kappa]\). The probe of \(p\) retrieves that bucket and emits \((p,q)\).

*Independence of group labels.* Group rules are not required for correctness of the join set; they only reduce the number of failed tests in OPF-Miner. By Lemma 1, the key already encodes the full join predicate. \(\square\)

**Proposition (HJ vs GP-Fusion pair set).**  
\[
\{\text{pairs fused by OPF-Miner on }F_m\}
=
\mathrm{Comp}(F_m)
=
\{\text{pairs emitted by HJ on }F_m\}.
\]

Therefore Stage 1 does not drop any join-feasible parent pair and does not introduce any join-infeasible pair.

---

## 3. Safety of CPC (Stage 2)

CPC removes a pair \((p,q)\) only if a numeric **upper bound** on the forgetting support of every possible fused superpattern \(r\) satisfies \(U(p,q)<\sigma\).

### 3.1 Residual bound

**Lemma 3 (Residual upper bound).**  
Let \(r\) be any superpattern obtained by fusing parents \(p,q\) under OPF-Miner fusion rules. Then

\[
fsup(r)\ \le\ e^{k}\,fsup(p)
\quad\text{and}\quad
fsup(r)\ \le\ fsup(q).
\]

Hence

\[
U_{\mathrm{res}}(p,q)=\min\bigl(e^{k}\,fsup(p),\ fsup(q)\bigr)
\ \ge\ fsup(r).
\]

*(Matches OPF-Miner / FastOPF residual inequalities; in code, residual fields play the role of the remaining weighted mass attributed to \(p\) as prefix and \(q\) as suffix.)*

If \(U_{\mathrm{res}}(p,q)<\sigma\), then necessarily \(fsup(r)<\sigma\): residual pruning is **safe**.

### 3.2 Span bound

**Lemma 4 (Span upper bound).**  
Every aligned endpoint \(j\) of a fusion of \(p\) and \(q\) satisfies

\[
j\in[\ell,h]=\bigl[\max(p_1+1,q_1),\ \min(p_{\mathrm{last}}+1,q_{\mathrm{last}})\bigr],
\]

where \(p_1,p_{\mathrm{last}}\) (resp. \(q\)) are the first and last elements of \(\mathrm{Occ}(p)\) (resp. \(\mathrm{Occ}(q)\)).  
If \(\ell>h\), the aligned set is empty, so \(fsup(r)=0<\sigma\).

### 3.3 Cardinality–weight bound

**Lemma 5 (Card–weight upper bound).**  
Let \(A(p,q)\) be the set of aligned endpoints. Then \(|A(p,q)|\le\min(n_p,n_q)\).  
Since \(w_j\) is nondecreasing in \(j\) and every \(j\in A(p,q)\) satisfies \(j\le h\),

\[
fsup(r)=\sum_{j\in A(p,q)} w_j
\ \le\
|A(p,q)|\,w_h
\ \le\
\min(n_p,n_q)\,w_h =: U_{\mathrm{card}}(p,q).
\]

### 3.4 Range bound

**Lemma 6 (Range upper bound).**  
Let \(c_p=\lvert\mathrm{Occ}(p)\cap[\ell-1,h-1]\rvert\) and \(c_q=\lvert\mathrm{Occ}(q)\cap[\ell,h]\rvert\). Then \(|A(p,q)|\le\min(c_p,c_q)\), and

\[
fsup(r)\ \le\ \min(c_p,c_q)\,w_h =: U_{\mathrm{range}}(p,q).
\]

### 3.5 CPC safety theorem

**Theorem 1 (CPC soundness / no false discard of frequent patterns).**  
If CPC reports “prune \((p,q)\)”, then for every superpattern \(r\) that OPF-Miner could form from \((p,q)\),

\[
fsup(r)<\sigma.
\]

**Proof.**  
CPC prunes only when at least one of \(U_{\mathrm{res}},U_{\mathrm{span}},U_{\mathrm{card}},U_{\mathrm{range}}\) is \(<\sigma\).  
Lemmas 3–6 show each \(U\) is a valid upper bound on \(fsup(r)\). Hence \(fsup(r)<\sigma\). \(\square\)

**Remark (non-increasing / valid bound language).**  
In the sense required for pruning safety, each \(U\) is **never smaller than the true** \(fsup(r)\) (i.e. \(U\ge fsup(r)\)). Bounds may be loose (\(U\gg fsup(r)\)), which only causes **missed prunes**, not incorrect removals of frequent patterns.

**Completeness relative to OPF-Miner outputs.**  
CPC may keep pairs that later fuse to \(fsup(r)<\sigma\) (bound not tight). Exact fusion + threshold then discard them, as in OPF-Miner. No frequent \(r\) is lost at Stage 2.

---

## 4. Preservation under Gallop (Stage 3)

**Lemma 7 (Gallop = two-pointer intersection).**  
Let \(A,B\) be strictly increasing integer arrays (here \(A=\mathrm{Occ}(p)+1\), \(B=\mathrm{Occ}(q)\)). Let \(I_{\mathrm{TP}}\) be the multiset of values reported by the classic two-pointer scan, and \(I_{\mathrm{G}}\) the multiset reported by the galloping procedure that advances the longer list with exponential + binary `lower_bound` and the shorter list by unit steps (or symmetric). Then

\[
I_{\mathrm{G}}=I_{\mathrm{TP}}.
\]

**Proof idea.**  
Both algorithms maintain indices \((i,j)\) and only emit when \(A[i]=B[j]\). When \(A[i]<B[j]\), any correct algorithm must advance \(i\) to the first position with \(A[i']\ge B[j]\) (and symmetrically). Gallop computes exactly that next index; two-pointer advances one-by-one to the same index. Equality cases coincide. Termination conditions coincide. \(\square\)

**Corollary.** Weighted supports accumulated from aligned endpoints are identical under Gallop and two-pointer. Rank-construction cases \(r/h\) of OPF-Miner are unchanged because they depend only on the endpoint set and series values, not on the search schedule.

---

## 5. Semantic Equivalence Lemma (main)

**Lemma 8 (Semantic Equivalence).**  
Under the same series \(t\), threshold \(\sigma\), forgetting factor \(k\), and strict order-isomorphism model,

\[
\bigcup_{m\ge 2} F_m^{\mathrm{ADP}}
=
\bigcup_{m\ge 2} F_m^{\mathrm{OPF}}
\]

as sets of rank sequences; moreover, for each pattern the computed \(fsup\) values coincide.

**Proof (induction on pattern length).**

*Base \(m=2\).*  
Both systems scan consecutive pairs \((t_{i-1},t_i)\) with the same strict inequalities \(>\) / \(<\); ties contribute to neither pattern. Supports use the same weights \(w_i\). Thus \(F_2^{\mathrm{ADP}}=F_2^{\mathrm{OPF}}\).

*Inductive step.*  
Assume \(F_m^{\mathrm{ADP}}=F_m^{\mathrm{OPF}}=:F_m\).

1. **Pairs:** Lemma 2 ⇒ HJ emits exactly \(\mathrm{Comp}(F_m)\), the same pairs OPF-Miner fuses.  
2. **Pruning:** Theorem 1 ⇒ every pair CPC removes cannot produce any \(r\) with \(fsup(r)\ge\sigma\). Pairs retained may still fail after exact fusion, as in OPF-Miner.  
3. **Fusion:** Lemma 7 ⇒ endpoint sets (hence \(fsup\) and rank outcomes) match two-pointer / OPF-Miner SCF for every fused pair.  
4. Therefore the multiset of candidates with \(fsup\ge\sigma\) at length \(m+1\) coincides: \(F_{m+1}^{\mathrm{ADP}}=F_{m+1}^{\mathrm{OPF}}\).

By induction, all generations match. \(\square\)

---

## 6. What equivalence does *not* claim

| Not claimed | Reason |
|-------------|--------|
| Same intermediate pair *tests* | HJ avoids failed key tests |
| Same runtime | Goal is acceleration |
| CPC prunes every infrequent pair | Bounds may be loose |
| Gallop fewer comparisons always | Only under skew; worst-case still linear |

Equivalence is **output** equivalence (frequent pattern set + supports), not trace equivalence.

---

## 7. One-paragraph manuscript version (English)

> **Semantic Equivalence.** Adaptive Fast OPF preserves OPF-Miner semantics. Normalized prefix and suffix keys are unique identifiers of the length-\((m-1)\) order-isomorphism class; the hash-indexed join therefore emits exactly the set of suffix–prefix compatible pairs that OPF-Miner would fuse after GP-Fusion filtering (Lemma 2). CPC discards a pair only when a residual, span, cardinality–weight, or range quantity is a valid upper bound on the forgetting-aware support of every fusion child and lies strictly below \(\sigma\) (Theorem 1), so no frequent pattern is removed. Galloping intersection returns the same aligned endpoint set as the classical two-pointer scan on the shifted occurrence lists (Lemma 7). By induction on pattern length, the frequent sets and supports coincide with OPF-Miner.

---

## 8. Proof-obligation checklist (reviewer-facing)

| Obligation | Result |
|------------|--------|
| HJ completeness (no missing compatible pair) | Lemma 2 |
| HJ soundness (no incompatible pair) | Lemma 2 |
| Key uniqueness / faithfulness | Lemma 1 |
| CPC never drops frequent \(r\) | Theorem 1 + Lemmas 3–6 |
| Gallop same intersection | Lemma 7 |
| Full pipeline ≡ OPF-Miner | Lemma 8 |

---

*End of correctness note.*

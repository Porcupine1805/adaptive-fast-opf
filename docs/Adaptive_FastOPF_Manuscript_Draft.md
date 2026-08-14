# Adaptive Fast OPF: Staged Acceleration of Order-Preserving Pattern Mining with Forgetting Mechanism

**Manuscript draft (detailed, v2)**  
**Semantic baseline:** Li et al., *OPF-Miner*, IEEE TKDE, 2024 (DOI: 10.1109/TKDE.2024.3438274).  
**Systems ancestry (reference only):** preliminary FastOPF-Miner draft (hash-indexed join, residual pruning, systems notes)—repurposed selectively; bitmap / allocation-centric claims are **not** core contributions of this Adaptive paper.  
**Status:** Working draft. Experimental numbers remain pilot-oriented until full-matrix medians are locked.

---

## Abstract

Order-preserving pattern (OPP) mining with an exponential forgetting mechanism (OPF) discovers frequent relative-order trends in non-stationary time series by assigning higher weights to recent observations. The reference algorithm OPF-Miner generates candidates by group pattern fusion (GP-Fusion) and evaluates forgetting-aware support by prefix–suffix occurrence fusion (SCF). Both stages remain expensive: compatible pairs are still enumerated densely within admissible groups, and many pairs enter linear occurrence-list fusion even when weighted-support upper bounds already fall below the threshold.

We reorganize OPF mining into three pipeline stages and propose **Adaptive Fast OPF**:

1. **Hash-indexed join (HJ)** — an output-sensitive prefix–suffix join that retrieves only structurally compatible pairs;  
2. **Cheap-prune cascade (CPC)** — residual, span, and cardinality–weight bounds (optional range bound) that skip fusion of pairs proven sub-threshold;  
3. **Skew-aware fusion** — classical two-pointer intersection, optionally replaced by **galloping** intersection when occurrence lengths are highly unbalanced.

Because enabling Stages 2–3 *statically* is not uniformly beneficial after a strong Stage 1, Adaptive Fast OPF decides *after HJ*, using only problem quantities (series length, minSup, pair mass, residual structure, length skew)—never wall-clock measurements inside the miner—whether to apply CPC and whether to fuse with sorted lists or gallop.

Pilots on financial-style and multi-client electricity series show that HJ yields the primary speedup at strict output equivalence with OPF semantics; CPC and gallop address residual work and help only conditionally. Adaptive staging protects Stage-1 gains against harmful always-on secondary operators.

**Index Terms**—order-preserving patterns, forgetting mechanism, time series, hash-indexed join, support pruning, galloping intersection, adaptive algorithms

---

## I. Introduction

### A. Motivation

Time-series data arise continuously in finance, healthcare, industrial monitoring, environmental sensing, and transportation. In many applications the *structural* evolution of a signal—rising–falling shapes, local reversals, recurrent trends—is more informative than absolute magnitude. Value-based matching is fragile under amplitude scaling, baseline shift, and noise. Order-preserving pattern (OPP) mining represents each window by the relative ranks of its elements, capturing internal shape while remaining invariant under strictly increasing transformations of amplitude [1], [2].

OPP-Miner established systematic frequent OPP mining directly on numerical series via relative-order patterns, fusion, and verification [2]. EFO-Miner / OPR-Miner reuse prefix–suffix occurrence structure to avoid rescanning the full series for every candidate and further form order-preserving rules [3]. These models, however, weight every occurrence equally. In non-stationary regimes—and under concept drift—recent observations often characterize the current state better than distant ones. **OPF-Miner** introduces exponential forgetting into OPP mining so that an occurrence ending at position \(j\) contributes \(e^{-k(n-j)}\) to support [4]. Support is no longer a pure count; candidate processing must respect position-dependent weights.

### B. Computational bottlenecks of OPF-Miner

OPF-Miner retains relative-order semantics and combines (i) maximal-support-priority ordering, (ii) GP-Fusion over four pattern groups, and (iii) SCF with prefix/suffix pruning [4]. From a systems viewpoint, two costs dominate:

1. **Candidate pairing.** GP-Fusion restricts *which groups* interact, roughly halving naive \(L^2\) checks, but within admissible groups the implementation still scans for matching normalized suffix/prefix keys.  
2. **Occurrence fusion.** Compatible pairs proceed to list intersection whose work is proportional to occurrence lengths; many pairs are already doomed by weighted-support upper bounds yet still pay linear fusion cost.  
3. **Skew.** Two-pointer fusion always examines both lists fully; under strong length imbalance, many comparisons on the long list are avoidable.

Index structures such as the order-preserving suffix tree (OPST) achieve strong bounds for *unweighted* maximal/closed OPP mining [5], [8], but they do not natively encode position-dependent exponential sums required by OPF support. Adapting OPST to exhaustive OPF output is non-trivial and orthogonal to accelerating the OPF-Miner fusion pipeline.

### C. This work: staged acceleration, not a new mining semantics

We keep the OPF *problem* fixed—same patterns, same exponential weights, same minimum weighted support—and accelerate the *pipeline*:

| Stage | Bottleneck | Technique |
|-------|------------|-----------|
| 1 | Dense compatible-pair discovery | **Output-sensitive hash-indexed join (HJ)** |
| 2 | Fusion of pairs that cannot meet minSup | **Cheap-prune cascade (CPC)** (from residual-support ideas) |
| 3 | Expensive list intersection under skew | **Two-pointer or gallop** |
| Policy | Static Stage 2–3 enablement is unstable | **Adaptive Fast OPF** |

**Central empirical message.** Stage 1 (HJ) accounts for most practical speedup versus an OPF-Miner-style baseline. Stages 2–3 operate on *residual* work; always-on CPC (especially range bounds) or always-on gallop is not uniformly faster than HJ-only. Adaptive staging is required if secondary operators must not erase Stage-1 gains.

A preliminary FastOPF design explored hash joins, sparse bitsets, fused bit-parallel kernels, and residual pruning. After experimental scrutiny we **demote bitmap-centric fusion** from the Adaptive story: after HJ (and optional CPC), remaining pair mass and density often fail to amortize bitmap conversion. This manuscript therefore centers **HJ + CPC + skew-aware list fusion + adaptive staging**, and treats bit-parallel occurrence encodings as optional systems machinery rather than a primary claim.

### D. Contributions

1. **Output-sensitive hash-indexed prefix–suffix join for OPF.** We formalize HJ on normalized prefix keys, retrieve candidates by suffix probes, and explicitly acknowledge \(\Omega(J)\) work in the number \(J\) of compatible pairs (output-sensitive join complexity).  
2. **Residual work after HJ.** We connect weighted residual-support bounds (prefix scaled by \(e^{k}\), suffix unscaled) to a **cheap-prune cascade** before fusion, and optionally apply **galloping intersection** under occurrence-length skew. We show the two address different residual questions (*whether* to fuse vs. *how* to fuse) and that static enablement is conditional.  
3. **Adaptive Fast OPF.** A staged policy that always runs HJ and enables CPC / gallop only from problem-native signals—protecting HJ gains without using benchmark timers inside the miner—while preserving strict output equivalence with OPF-Miner semantics.

---

## II. Related Work

### A. Order-preserving and forgetting-aware mining

Order-preserving matching equates sequences that induce the same relative ordering [1]. OPP-Miner mines frequent OPPs without mandatory symbolization [2]. EFO-Miner reuses subpattern occurrences for superpatterns; OPR-Miner builds rules [3]. OPF-Miner adds exponential forgetting and GP-Fusion / SCF [4].

Orthogonal extensions change the *objective* or *match relation*: AOP-Miner allows approximate order isomorphism [5]; COPP-Miner seeks contrast patterns for classification [6]; COP-Miner targets co-occurrence with a prescribed prefix [7]. They are not interchangeable with exact exhaustive OPF mining under weighted minSup.

### B. Scalable indexing (OPST)

OPST supports linear-time extraction of maximal/closed *unweighted* OPPs after index construction [5], [8]. Adaptive Fast OPF returns every pattern meeting *exponentially weighted* support; node occurrence counts alone do not store the position-dependent sum. The approaches are complementary. A fully indexed exhaustive OPF solver remains future work.

### C. Joins, bitmaps, and runtime systems

Vertical bitmaps (e.g., SPAM) and compressed bitmaps (e.g., Roaring) motivate word-parallel set operations [9], [10]. Main-memory join research emphasizes data layout and materialization costs [11]. JVM object headers and allocation traffic affect managed runtimes [12], [13].

**Positioning.** Hash indexing of compatible OPF pairs is output-sensitive: if \(J\) pairs match, exact enumeration costs \(\Omega(J)\). Sparse bitsets can accelerate *dense* local intersections but require conversion cost; in our Adaptive design they are **not** a default Stage-3 operator. Memory claims, when made, should be limited to measured peak heap.

### D. Sorted-set intersection

Two-pointer merge is standard; galloping (exponential + binary search) reduces comparisons under skew. We use it as optional Stage-3 fusion for OPF occurrence endpoints.

---

## III. Preliminaries and Problem Definition

### A. Strict relative-order patterns

**Definition 1 (Time series).** \(t=(t_1,\ldots,t_n)\), \(t_i\in\mathbb{R}\).

**Definition 2 (Strict order isomorphism).** Windows with pairwise distinct values are order-isomorphic iff all pairwise order relations coincide.

**Definition 3 (Strict rank encoding).**  
\[\rho(x)_i = 1 + |\{h:x_h < x_i\}|.\]  
For distinct values, \(\rho(x)\) is a permutation of \(\{1,\ldots,m\}\).

**Definition 4 (Strict OPP).** A pattern \(p\) is a permutation of \(\{1,\ldots,m\}\).

**Tie policy.** Windows with equal values are excluded under the strict model.

**Definition 5 (Occurrence).**  
\[\mathrm{Occ}(p,t)=\{j\in\{m,\ldots,n\}:\rho(t[j-m+1:j])=p\}.\]  
Occurrences may overlap.

### B. Exponential forgetting support

**Definition 6 (Weight).** \(w_j=\exp[-k(n-j)]\), \(0<k<1\), with \(w_n=1\) and \(w_j=e^{k}w_{j-1}\).

**Definition 7 (Forgetting-aware support).**  
\[fsup(p,t)=\sum_{j\in\mathrm{Occ}(p,t)}w_j.\]  
Always \(0\le fsup(p,t)\le |\mathrm{Occ}(p,t)|\).

**Definition 8 (Frequent OPF).** \(p\) is frequent if \(fsup(p,t)\ge \sigma\) (denoted \(minsup\) in experiments). \(F_m=\{p:|p|=m,\,fsup(p,t)\ge\sigma\}\).

### C. Prefix–suffix fusion

**Definition 9–10 (Normalized prefix/suffix).**  
\(\mathrm{pre}(p)=\mathrm{norm}(p_1,\ldots,p_{m-1})\), \(\mathrm{suf}(p)=\mathrm{norm}(p_2,\ldots,p_m)\).

**Definition 11 (Join compatibility).** \(p\) and \(q\) are compatible iff \(\mathrm{suf}(p)=\mathrm{pre}(q)\).

**Definition 12 (Fusion \(p\oplus q\)).** As in OPF-Miner: one or two length-\((m+1)\) permutations depending on \(p_1\) vs \(q_m\).

**Definition 13 (Aligned endpoints).**  
\[A(p,q)=\{j: j-1\in\mathrm{Occ}(p),\, j\in\mathrm{Occ}(q)\}.\]

### D. Residual support bounds (foundation for CPC)

**Proposition (Prefix/suffix weighted bounds).** Let \(r\) be a length-\((m+1)\) pattern with normalized prefix \(p\) and suffix \(q\). Every endpoint \(j\in\mathrm{Occ}(r)\) satisfies \(j-1\in\mathrm{Occ}(p)\) and \(j\in\mathrm{Occ}(q)\). Hence

\[
fsup(r,t)\le e^{k}\,fsup(p,t), \qquad fsup(r,t)\le fsup(q,t).
\]

The factor \(e^{k}\) is required because the superpattern endpoint is one step to the right of the prefix endpoint. These inequalities yield *safe* residual upper bounds for CPC; unrestricted anti-monotonicity without the scale factor does **not** hold for weighted support.

### E. Problem statement (unchanged)

Given \(t\), forgetting factor \(k\), and threshold \(\sigma\), return \(F=\bigcup_m F_m\) exactly as defined by OPF-Miner.

---

## IV. Pipeline Bottlenecks

Let \(L=|F_m|\) and \(\mathrm{Pairs}_m=\{(p,q):\mathrm{suf}(p)=\mathrm{pre}(q)\}\), \(J=|\mathrm{Pairs}_m|\).

- GP-Fusion reduces group-level scans but does not index identical prefix keys.  
- SCF-style fusion costs \(\sum_{(p,q)}\Theta(|\mathrm{Occ}(p)|+|\mathrm{Occ}(q)|)\) when all pairs are fused.  
- Prefix/suffix pruning inside SCF helps during fusion; it does not skip fusion when residual bound is already \(<\sigma\).

```
F_m → [HJ] Pairs_m → [CPC?] survivors → [List/Gallop fusion] → F_{m+1}
```

---

## V. Stage 1: Output-Sensitive Hash-Indexed Join (HJ)

Build multimap \(\mathrm{prefixMap}:\mathrm{pre}(q)\mapsto\{q\in F_m\}\).  
For each \(p\), probe \(Q(p)=\mathrm{prefixMap}[\mathrm{suf}(p)]\).

**Output sensitivity.** Any exact algorithm that materializes all compatible pairs requires \(\Omega(J)\) work. HJ does not claim sublinear cost independent of output size.

**Completeness.** Every compatible pair is retrieved. Maximal-support ordering of \(F_m\) can be retained before probing.

**Role.** HJ is the **primary** accelerator relative to dense OPF-Miner-style pairing.

---

## VI. Stage 2: Cheap-Prune Cascade (CPC)

### From residual theory to practice

If \(U_{\mathrm{res}}(p,q)=\min(e^{k}\cdot fsup(p), fsup(q))<\sigma\), prune.

**Cascade:** residual \(O(1)\) → span \(O(1)\) → card×weight \(O(1)\) → optional range \(O(\log n)\).

**Empirical lesson.** Range often produces most prunes *and* most overhead. Prefer **thin CPC** (O(1) tiers; range rare) unless occurrence mass is large.

CPC is not a substitute for HJ, SCF dynamic pruning, or gallop.

---

## VII. Stage 3: Sorted List and Galloping Fusion

Aligned endpoints via shifted \(\mathrm{Occ}(p)\) ∩ \(\mathrm{Occ}(q)\).

- **Two-pointer:** \(\Theta(n+m)\) comparisons.  
- **Gallop:** exponential + binary on the longer list under skew; **same matches**.

CPC = *whether* to fuse; Gallop = *how* under skew. Bitmap kernels may implement intersection but are optional systems realizations—not default Adaptive claims.

---

## VIII. Adaptive Fast OPF

**Why.** HJ-only is often near-optimal after Stage 1; always-on CPC can regress; gallop helps only under skew; static full combine does not dominate.

**Principles.** Always HJ; fail-safe HJ-only; signals = \(n,\sigma,J\), residual tightness, occurrence lengths, skew; no wall-clock in control flow; thin CPC; conditional gallop.

**Schedule.** Build HJ → DecideCPC → DecideGallop → for each pair: optional CPC prune → List or Gallop fuse → retain frequent OPFs.

---

## IX. Correctness Position

Semantic identity with OPF-Miner on: join-compatible candidates; occurrence endpoints under strict fusion; exponential weighted support. CPC only drops pairs with safe upper bound \(<\sigma\). Gallop preserves two-pointer matches.

---

## X. Experiments

**Protocol.** Correctness (canonical dumps) separate from timing (median \(R\ge 5\), fixed heap). Modes: OPF-style, HJ-only, CPC variants, Gallop-only, Adaptive, static full. Data: DB1–8, ELEC_01/05/10.

**RQs.** (1) HJ vs baseline; (2) CPC help/hurt (range on/off); (3) Gallop vs list, CPC off; (4) Adaptive vs HJ vs static full.

**Pilot trends.** Equal frequent sets; CPC conditional; ELEC_10 gallop reduced tens of millions of comparisons with mild time win at some minsup; **HJ carries most gains**.

---

## XI. Discussion

Stage 1 dominates because residual pair mass shrinks after exact join. Systems honesty: no sublinear-in-\(J\) join claims; no GC claims from allocation counts alone; inherited OPF semantics are not new theory; bitmap not required for Adaptive claims.

**Limitations.** Conditional secondary gains; policy thresholds need sensitivity analysis; static single-run series; limited domains.

---

## XII. Conclusion

Adaptive Fast OPF accelerates OPF-Miner’s pipeline via output-sensitive HJ, cheap pre-fusion pruning, and skew-aware list fusion under an adaptive policy that enables residual operators only when post-HJ structure justifies them—preserving OPF semantics and prioritizing HJ as the main lever.

**Future work.** Thin-CPC defaults; work-based CPC enablement; broader domains; weighted OPF indexing; downstream contrast/prediction tasks.

---

## Appendix A — Claim checklist

| Claim | Allowed |
|-------|---------|
| Output equivalence with OPF-Miner | Yes |
| HJ primary speedup; join \(\Omega(J)\) | Yes |
| CPC from residual weighted bounds | Yes |
| Always-on CPC uniformly faster than HJ | **No** |
| Gallop under skew | Yes |
| Adaptive never slower on all data | Target only |
| Sparse bitmap as core Adaptive claim | **No** |
| Wall-clock inside policy | **No** |

## Appendix B — Reuse from preliminary FastOPF draft

| Reused | Not core Adaptive claim |
|--------|-------------------------|
| Output-sensitive HJ framing | Sparse bitset as primary contribution |
| Residual \(e^{k}\) prefix/suffix bounds | Fused bit-parallel kernel as main result |
| OPST / AOP / COPP positioning | Extreme speedup multipliers without locked matrix |
| Managed-runtime caution | “Allocation-free entire miner” rhetoric |
| Formal strict rank / fusion cases | Bitmap default Stage-3 |

## Appendix C — Suggested figures

1. OPF forgetting support  
2. Three-stage Adaptive pipeline vs OPF-Miner  
3. HJ prefixMap  
4. CPC cascade  
5. Two-pointer vs gallop  
6. Runtime comparison bars  
7. Post-HJ work: pair mass vs avoided fuse mass  

---

*End of manuscript draft v2.*

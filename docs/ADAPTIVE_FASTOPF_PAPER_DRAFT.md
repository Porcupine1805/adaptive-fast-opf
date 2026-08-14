# Adaptive Fast OPF: Draft Outline & Narrative

**Working title (EN):**  
Adaptive Fast OPF: Staged Optimization of Order-Preserving Pattern Mining with Forgetting

**Working title (VI):**  
Adaptive Fast OPF: Tối ưu theo tầng cho khai thác mẫu bảo toàn thứ tự có cơ chế quên

**Status:** Research draft — aligned with current experimental evidence (HJ primary; CPC/Gallop conditional; Adaptive as staging policy).  
**Do not claim:** Adaptive always dominates HJ on all datasets; CPC/Gallop match HJ’s absolute speedup.

---

## 0. One-paragraph pitch

Order-preserving frequent pattern mining with a forgetting mechanism (OPF-Miner) is expensive mainly because of naive pair enumeration and repeated occurrence-list fusion. We structure the work into three pipeline stages—(1) pair discovery, (2) pre-fusion pruning, (3) list fusion—and show that a **hash-indexed join (HJ)** removes most of the baseline cost. Residual work after HJ is smaller; **cheap-prune cascade (CPC)** and **galloping intersection** address different residual bottlenecks but are not uniformly beneficial when enabled statically. **Adaptive Fast OPF** decides after HJ, using only problem quantities (series length, minSup, pair mass, occurrence structure, skew), whether to apply CPC and whether to fuse with sorted lists or gallop—preserving correctness while avoiding systematic slowdowns from always-on secondary optimizations.

---

## 1. Problem & motivation

### 1.1 Background
- OPF / OPF-Miner: order-preserving patterns on time series; support with forgetting factor.
- Level-wise generation: length-\(k\) patterns from length-\((k-1)\).
- Cost drivers:
  - Pair enumeration across candidates in a generation.
  - Fusion of sorted occurrence lists to compute supports and form longer patterns.

### 1.2 Limitations of the baseline
- Naive pairing scales poorly with generation size.
- Every compatible pair proceeds to fusion even when simple bounds already imply support \(< \minSup\).
- Fusion is two-pointer on occurrence lists: work \(\propto |Occ_p|+|Occ_q|\); skewed lengths waste comparisons.

### 1.3 Goal of this work
Optimize OPF **by pipeline stage**, with evidence that:
- Stage 1 (HJ) is the primary speedup.
- Stages 2–3 yield **smaller, conditional** gains.
- Static “enable everything” is not optimal → staged Adaptive policy.

---

## 2. Related work (sketch)

| Area | Placement |
|------|-----------|
| Order-preserving / time-series pattern mining | OPF-Miner and variants |
| Join / candidate generation in pattern mining | Prefix–suffix style joins; hash-based pairing |
| Support upper bounds / pruning | Concise bounds before expensive verification |
| Sorted-set intersection | Two-pointer; galloping / exponential search |
| Adaptive algorithm selection | Cost-aware operators (without wall-clock inside the miner) |
| Aho–Corasick / multi-pattern matching | Related only at high level (avoid repeated scans); **different problem** (fixed dictionary vs level-wise OP frequent mining) |

---

## 3. Pipeline view (core framing)

```text
Generation ℓ
    → [Stage 1] Pair discovery          → HJ (prefixMap)
    → [Stage 2] Pre-fusion filter       → CPC (optional)
    → [Stage 3] Occurrence fusion       → Sorted List or Gallop (optional)
    → Patterns ≥ minSup → Generation ℓ+1
```

**Important claim:** After strong Stage 1, remaining work is reduced; Stage 2–3 must not destroy Stage 1 gains.

---

## 4. Contributions (proposed count: 3 or 4)

### Option A — Three contributions (safer)
1. **HJ for OPF-style mining** — hash-indexed pair discovery; output-equivalent to baseline OPF.
2. **Residual bottlenecks after HJ** — formalize wasted fusion; CPC (cheap bounds) and skew-aware gallop as *conditional* operators; show static enablement is not uniformly beneficial.
3. **Adaptive Fast OPF** — stage decisions after HJ using problem-native quantities only (not benchmark timers).

### Option B — Four contributions (if Gallop ablation is strong)
1. HJ  
2. CPC  
3. Gallop under occurrence skew  
4. Adaptive staging of (2) and (3)

**Recommendation:** Start with **Option A** in the draft; promote Gallop to C3 only after a clean HJ+List vs HJ+Gallop table (CPC off).

---

## 5. Methods

### 5.1 Stage 1 — Hash-Indexed Join (HJ)
- Index patterns by prefix; probe with suffix of \(p\) (order-preserving join condition).
- Complexity intuition: avoid full \(O(L^2)\) candidate pairing within a generation.
- Correctness: same frequent pattern set as OPF under the same minSup / forgetting parameters.

### 5.2 Stage 2 — Cheap-Prune Cascade (CPC)
**Problem:** After HJ, pairs still enter fusion when support is already impossible.

**Cascade (problem quantities only):**
1. Residual: \(\min(pre(p)\cdot e^{k}, suf(q)) < \minSup\)
2. Span: occurrence ranges do not meet
3. Card × weight: \(\min(|Occ_p|,|Occ_q|)\cdot w(high) < \minSup\)
4. Range (optional, more expensive): binary-count upper bound on intersections

**Design stance (from experiments):**
- O(1) tiers are the “cheap” part of CPC.
- Range is often the dominant CPC overhead; always-on range can slow down small/medium instances.
- Benefit when avoided fusion work \(\sum (|Occ_p|+|Occ_q|)\) exceeds check cost—not when prune *count* is merely large.

### 5.3 Stage 3 — Sorted List vs Gallop
**Problem:** Fusion cost \(\propto |Occ_p|+|Occ_q|\); under length skew, two-pointer wastes comparisons on the long list.

**Gallop:** exponential + binary advance on the longer list; **same matches** as classic two-pointer.

**Not interchangeable with CPC:** CPC decides *whether* to fuse; Gallop decides *how* to intersect when fusing.

### 5.4 Adaptive Fast OPF
**Principle:** Default path should match HJ-only when secondary signals are weak.

**Inputs to decisions (allowed):** \(N\), minSup, pair count \(P\), supports/residuals, occurrence lengths, skew ratios.  
**Forbidden inside the miner:** wall-clock time, benchmark counters as control signals.

**Staging:**
1. Always HJ.
2. CPC only when residual structure suggests enough *cheap* prunes / avoided fuse mass (policy details in implementation; avoid magic “12% runtime ROI” narrative).
3. Gallop only when sampled HJ pairs show sufficient length skew.

**Preferred engineering direction:** “Thin CPC” (O(1) always or lightly gated; range rare) + fail-safe toward HJ.

---

## 6. Experimental design

### 6.1 Protocols
- **Correctness phase:** canonical pattern–support dump; OPF ≡ HJ ≡ Adaptive (CPC/Gallop variants). No timing claims from this phase.
- **Timing phase:** no canonical I/O; fixed heap; \(R \ge 5\) runs; report **median** time and peak memory.
- Modes: OPF baseline, HJ-only, CPC variants, Gallop-only, Adaptive, optional static full.

### 6.2 Data
- Financial-style series: DB1–DB8 (and subsets for pilots).
- Electricity scale: ELEC_01 / ELEC_05 / ELEC_10 (\(|T|\) up to \(\sim 1.4\times 10^6\) for 10 clients).

### 6.3 Research questions
- **RQ1:** How much does HJ improve over OPF, with equal outputs?
- **RQ2:** When does CPC reduce work after HJ, and when does it add overhead (especially range)?
- **RQ3:** When does gallop reduce position comparisons / time vs sorted list (CPC off)?
- **RQ4:** Does Adaptive avoid the worst static configurations while matching or improving HJ on median time?

### 6.4 Evidence already observed (pilot summary — update after full runs)
- Correctness: matching FreqPatterns across OPF/HJ/Adaptive on tested sets.
- CPC always-on: wins are **conditional** (e.g., some ELEC/DB5 cells); can lose on DB4 and on ELEC_10 @ minSup=8.
- Range often dominates CPC prunes and CPC overhead.
- Gallop on ELEC_10 @ minSup=4: measurable drop in position comparisons and slight time win vs HJ; not uniform at higher minSup.
- Primary narrative: **HJ carries most gains; secondary stages need care.**

---

## 7. Paper structure (section outline)

1. **Introduction**  
   Cost of OPF; three-stage view; contributions; result teaser (HJ primary; Adaptive staging).

2. **Preliminaries**  
   OPF definitions; forgetting support; level-wise generation; complexity of naive pairing and fusion.

3. **Stage 1: Hash-Indexed Join**  
   Algorithm; complexity discussion; equivalence argument.

4. **Stage 2: CPC**  
   Bounds; cascade order; overhead analysis; when pruning helps vs hurts.

5. **Stage 3: Galloping fusion**  
   Skew; algorithm; relation to classical set intersection; difference from CPC.

6. **Adaptive Fast OPF**  
   Decision principles; problem-native signals; fail-safe HJ; thin CPC recommendation.

7. **Experiments**  
   Setup; correctness; RQ1–RQ4 tables/figures; ablation (range on/off; gallop on/off).

8. **Discussion**  
   Why residual stages are smaller after HJ; limitations; threats to validity.

9. **Related work**

10. **Conclusion**

---

## 8. Figures & tables (planned)

| ID | Content |
|----|---------|
| Fig.1 | Overall Adaptive / three-stage pipeline |
| Fig.2 | HJ prefixMap join |
| Fig.3 | CPC cascade (O(1) vs range) |
| Fig.4 | Sorted list vs gallop under skew |
| Tab.1 | Correctness equivalence |
| Tab.2 | OPF vs HJ (time, memory) |
| Tab.3 | CPC variants vs HJ (incl. range off) |
| Tab.4 | Gallop vs list (CPC off) |
| Tab.5 | Adaptive vs HJ vs static full |
| Tab.6 | Case study ELEC_10: prunes, smart fusions, comparisons |

---

## 9. Claims checklist (editorial)

| Claim | Allowed now? |
|-------|----------------|
| HJ is the main practical speedup vs OPF | Yes (with full tables) |
| CPC addresses wasted post-HJ fusion | Yes |
| CPC always-on is uniformly faster than HJ | **No** |
| Gallop addresses skewed fusion | Yes (mechanism) |
| Gallop always faster than list | **No** |
| Adaptive never slower than HJ | Target design; verify empirically |
| Algorithm decisions use only problem quantities | Yes (principle) |
| Bitmap is a core contribution | **No** (removed) |

---

## 10. Implementation notes (reproducibility)

- Main entry: `FOMAblationFlags` (`mode=hash_only` / `adaptive` / `baseline`).
- Repo: `https://github.com/Porcupine1805/adaptive-fast-opf`
- Data: `data/benchmark` (DB1–8, ELEC_01/05/10).
- Canonical: `-Dcanonical=...` for equivalence; disable during timing.
- Preferred story for CPC in camera-ready: thin O(1) cascade; range conditional.

---

## 11. Next steps (to harden the draft)

1. Full median timing matrix: OPF, HJ, Adaptive (thin CPC), DB1–8 + ELEC.  
2. Gallop-only ablation on ELEC_10 / ELEC_05 (CPC off).  
3. CPC range-on vs range-off on same cells.  
4. Lock contribution count (3 vs 4) after Tab.4 strength.  
5. Write Introduction + Section 3–4 in paper English from this outline.

---

## 12. Abstract sketch (EN, ~150 words)

Order-preserving pattern mining with forgetting (OPF) repeatedly enumerates candidate pairs and fuses occurrence lists, which dominates runtime. We organize optimization into three stages: hash-indexed join (HJ) for pair discovery, a cheap-prune cascade (CPC) that drops pairs already proven to miss the support threshold before fusion, and optional galloping intersection for skewed occurrence lists. Experiments show that HJ delivers the primary speedup while preserving the baseline pattern set. CPC and gallop target residual work after HJ; enabling them statically is not uniformly beneficial—range checks in particular can outweigh avoided fusions. Adaptive Fast OPF therefore applies secondary operators only when post-HJ signals (series length, pair mass, bounds relative to minSup, and length skew) justify them, using problem quantities rather than benchmark timers. The result is a staged, correctness-preserving accelerator for OPF-style mining that prioritizes HJ gains and treats CPC and gallop as conditional refinements.

---

*End of draft outline. Update Section 6.4 and contribution option after the next full experiment matrix.*

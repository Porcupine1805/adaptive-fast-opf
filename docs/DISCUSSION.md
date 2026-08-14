# Discussion — When Each Stage Pays Off

**Manuscript placement:** before Conclusion.  
**Tone:** evidence-aligned; distinguish structural guarantees from data-dependent gains.

---

## Discussion

Adaptive Fast OPF decomposes generation into three stages—hash-indexed join (HJ), cheap-prune cascade (CPC), and list fusion with optional galloping—and activates the latter two only when problem-native signals suggest a net benefit. The stages are not interchangeable accelerators: each targets a different cost regime. Below we discuss when HJ is the dominant (and sometimes sole) win, when CPC actually reduces work, and when Gallop can become a net burden.

### When HJ is the primary—and often only—lifeline

Pair discovery in OPF-Miner remains quadratic in the size \(L=|F_m|\) of a generation even after GP-Fusion: admissible groups still induce \(\Theta(L^2)\) suffix–prefix equality tests. HJ replaces that search by an output-sensitive enumeration of cost \(\Theta(L+J)\), where \(J\) is the number of structurally compatible pairs.

**HJ dominates when \(L\) is large and key diversity keeps \(J \ll L^2\).**  
This is exactly the “many frequent patterns” regime: low-to-moderate \(\sigma\), long series, or rich local order structure so that \(F_m\) grows before support decay empties later generations. In that regime the discovery term \(\sum_m O(L_m^2)\) becomes the bottleneck of the baseline, while fusion work \(\sum (n_p+n_q)\) over the *true* join cannot be avoided by any correct algorithm. Experiments on denser synthetic and multi-user electricity traces show that speedups versus OPF-Miner track the reduction from dense group scans to HJ probes; when \(J\) is already close to the GP-Fusion test count (few distinct keys), the HJ advantage shrinks toward the output-sensitive lower bound, as expected.

**Importantly, HJ does not depend on \(\sigma\) being “tight.”**  
Even when almost every compatible pair later fuses to a frequent child, discovery must still *find* those pairs. CPC and Gallop cannot repair a quadratic enumerator. Thus in the large-\(L\) limit, HJ is not merely the first stage—it is the only stage whose asymptotic improvement is structural rather than heuristic.

A practical corollary for Adaptive control: if \(L\) is small (high \(\sigma\), short series), HJ’s absolute saving is modest because \(L^2\) itself is small; wall-clock then shifts to fusion and to any overhead of optional stages.

### When CPC actually pays off

CPC spends \(O(1)\) (or \(O(\log n_{\mathrm{occ}})\) for range) per HJ pair to avoid a full list intersection. Net gain requires that a non-trivial fraction of pairs be *provably* hopeless *before* scanning occurrences, and that the avoided fuse work exceed the check cost.

**CPC helps when \(\sigma\) sits near the upper bounds—especially residual and range.**  
Recall \(U_{\mathrm{res}}=\min(e^{k}fsup(p),fsup(q))\). For frequent parents, full residual mass is typically \(\ge\sigma\), so residual alone rarely kills a pair at the beginning of a generation. CPC becomes effective when:

1. **High \(\sigma\)** relative to typical fused mass: many compatible parents exist, but aligned endpoints are few or lie early in the series (small \(w_h\)), so \(U_{\mathrm{card}}\) or \(U_{\mathrm{range}}\) falls below \(\sigma\) while parents themselves remain frequent;  
2. **Tight range windows:** long occurrence lists with only partial overlap—card–weight still sees \(\min(n_p,n_q)\cdot w_h\ge\sigma\), but binary range counts \(c_p,c_q\) expose \(U_{\mathrm{range}}<\sigma\) (as in the running example at \(\sigma=2.05\));  
3. **Residual drain across many pairs:** after sequential fusion consumes prefix/suffix mass, later pairs see reduced residual fields and residual-tier prunes appear even at moderate \(\sigma\).

**CPC does *not* help when almost every HJ pair is “obviously” worth fusing.**  
At low \(\sigma\), upper bounds stay above the threshold, \(J_{\mathrm{surv}}\approx J\), and the algorithm pays \(O(J)\) checks for almost no avoided intersections—the pattern observed when always-on CPC underperformed HJ-only on several electricity settings with low prune rates. Gate design (pair-mass floors + free-prune rate \(\hat r>\alpha\)) exists precisely to skip CPC in that regime; a too-small \(\alpha\) re-introduces the overhead the gate was meant to prevent.

In short: CPC is a **threshold-sensitive filter**, not a general substitute for HJ. Its best operating region is “many compatible pairs, few children that can clear \(\sigma\)”—the opposite of the low-threshold explosion where HJ carries the run.

### When Gallop becomes a burden

Galloping intersection is comparison-efficient when one list is much longer than the other: exponential search jumps over large gaps on the long side. Worst-case work remains \(O(n_p+n_q)\), and each jump pays branch-heavy binary search.

**Gallop is counterproductive on short or balanced lists.**  
If \(\min(n_p,n_q)\) is small, or \(n_{\max}/n_{\min}\) is near one, two-pointer already finishes in a handful of iterations. Gallop then adds:

- extra branches and `lower_bound` logic per mismatch;  
- poorer locality than a tight two-pointer loop;  
- generation-level sampling and pair-level skew tests when Adaptive is enabled.

Empirical gates therefore require both a **generation skew rate** \(\hat s\ge s_0\) and a **per-pair** test (\(n_{\max}\ge L_{\min}\) and \(n_{\max}\ge\rho\,n_{\min}\)). Balanced pairs keep two-pointer even inside a Gallop-enabled generation. When lists are short everywhere (high \(\sigma\), aggressive forgetting, or sparse motifs), the correct Adaptive decision is often “never Gallop”: the Stage-3 default remains classical merge.

Gallop also does **not** reduce asymptotic mining complexity. It is a constant-factor optimization for skewed fuse-heavy workloads after HJ has already made discovery cheap. Claiming Gallop as a primary contribution on short-list datasets would be misleading; its role is conditional and secondary to HJ.

### Joint reading of the three regimes

| Regime | Dominant cost | Stage that helps | Stage that may hurt |
|--------|---------------|------------------|---------------------|
| Large \(L\), diverse keys, moderate/low \(\sigma\) | Pair discovery \(O(L^2)\) | **HJ** | CPC/Gallop if always on with low prune/skew |
| Moderate \(L\), high \(\sigma\), bounds near threshold | Futile fuses | **CPC** (esp. range) | Gallop on short Occ |
| Large Occ lists, strong length skew | Fuse comparisons | **Gallop** (pair-local) | Gallop forced on balanced pairs |
| Tiny \(L\) and tiny Occ | Everything small | None critical | Any extra decision/check overhead |

Adaptive Fast OPF is therefore best viewed as **HJ-first architecture** with two optional, gated refinements. Completeness and soundness do not depend on CPC or Gallop firing; performance does. The experimental tables should be read in that light: large speedups versus OPF-Miner are primarily HJ; CPC and Gallop explain residual gaps only in the niches above.

### Limitations

The discussion assumes the strict order-isomorphism model and the same fusion cases as OPF-Miner. Bound tightness of CPC depends on occurrence geometry and on residual accounting under sequential pair order; different pair orderings change *when* residual prunes appear, not final frequent sets. Gallop’s benefit is hardware- and language-sensitive (branch prediction, interpreter vs JIT). Finally, Adaptive thresholds (\(\alpha\), skew ratio, sample budgets) are engineering defaults: they are not claimed as instance-optimal, and fail-safe behaviour (prefer HJ-only when signals are weak) is intentional.

---

## Suggested closing bridge to Conclusion

> These regime-specific roles motivate the Adaptive policy: always apply HJ; enable CPC only when free-prune mass is expected to offset check cost; enable Gallop only when occurrence lengths are demonstrably skewed. The design prioritizes never being slower than a pure HJ baseline in unfavourable data, while retaining the ability to exploit high-threshold pruning and long-list skew when they arise.

---

*End of Discussion draft.*

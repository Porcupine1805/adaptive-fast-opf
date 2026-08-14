# Q1 checklist data availability audit

Source checklist: `D:\4. CAND T05\5. Research\FastOPF\ver1\fastopf_q1_checklist.md`

## Current available artifacts

- OPF benchmark summary: `algorithm_benchmark/results_full_20260811/benchmark/opf/OPF_Miner_Original_summary_avg.csv` with 84 rows.
- FOM benchmark summary: `algorithm_benchmark/results_full_20260811/benchmark/fom/FOM_summary_avg.csv` with 84 rows.
- OPF canonical outputs: `algorithm_benchmark/results_full_20260811/canonical/opf/` with 84 CSV files.
- FOM canonical outputs: `algorithm_benchmark/results_full_20260811/canonical/fom/` with 84 CSV files.
- SHA-256 equivalence report: `algorithm_benchmark/results_full_20260811/comparisons/sha256_equivalence.csv`.
- Ablation summaries from previous run: `algorithm_benchmark/results/` for `FOMNoHash`, `FOMNoVector`, `FOMNoWSB`, and `FOM`.
- RQ6 clustering outputs: `rq6_output/rq6_full_results_final.csv`, feature matrices, timing files, and LaTeX tables.
- Dataset files: DB1-DB8 and replicated DB8_1x-DB8_6x are present in `datasets/`.

## Checklist coverage

| Checklist item | Status | Evidence / gap |
|---|---|---|
| Fig. 1 runtime DB1-DB8, OPF vs FastOPF | Data available, figure missing | Use benchmark summaries at `minsup=4`; no `.png` or `.pdf` figure exists yet. |
| Fig. 2 runtime vs minSup on DB5 | Data available, figure missing | Use 6 DB5 rows from OPF/FOM summaries; no figure exists yet. |
| Fig. 3 runtime vs replicated DB8 length | Data available, figure missing | Use DB8_1x-DB8_6x rows from OPF/FOM summaries; no figure exists yet. |
| OPF vs FastOPF runtime/memory/pattern table | Available | Both OPF and FOM summaries have `Time_s`, `MaxMem_MB`, `FreqPatterns`, and operation counters. |
| Output equivalence / reproducibility by SHA-256 | Available | 84 comparisons: 77 raw SHA-256 matches, 6 normalized SHA-256 matches, 1 tolerance match, 0 failures. |
| Ablation variants | Partially available | Old summaries exist for `FOMNoHash`, `FOMNoVector`, `FOMNoWSB`; they were not rerun in the new `results_full_20260811` layout. |
| RQ6 clustering / application results | Available, needs interpretation | `rq6_full_results_final.csv` includes FOM-Clustering, Euclidean k-means, SAX k-means, and DTW k-means metrics. |
| Multi-method benchmark for mining algorithms | Missing | No runnable/result artifacts found for OPP-Miner, OPR-Miner, EFO-Miner, SAX-based SPM, or sliding-window OPP mining baseline. |
| Non-finance datasets | Missing | Current DB1-DB9 assets are financial/stock-oriented; no UCR, PAMAP2, weather, or electricity datasets are present. |
| Forgetting factor `k` sensitivity | Missing | Current benchmark varies `minsup`, not `k`; code may need a configurable `k` parameter and runner. |
| Pattern distribution / compatibility ratio | Missing | Current summaries have aggregate `Candidates`, `Fusions`, `SupportOps`; they do not record per-level `L_m`, `J_m`, or `J_m / L_m^2`. |
| SWA bitset sparsity analysis | Missing | Current code/results do not report non-zero word counts or sparsity percentiles. |
| Streaming scenario | Missing | No chunked/online simulation outputs or regime-label evaluation artifacts found. |
| Related Work references | Not determined from code folder | Requires manuscript/bibliography inspection and literature search, not benchmark data. |
| Discussion text | Not data-blocked | Can be drafted from available benchmark + SHA data, but some requested explanations need C4/C5 instrumentation. |
| GitHub reproducibility statement | Partially ready | README, `.gitignore`, run scripts, logs, and SHA outputs exist; repo publishing and URL still missing. |

## Bottom line

The current artifacts are enough to fill the core OPF vs FastOPF benchmark tables, reproducibility/SHA-256 validation, and to generate the three requested benchmark figures. They are not enough to fully satisfy the stronger Q1 checklist items C1-C6 without additional code, datasets, or experiments.

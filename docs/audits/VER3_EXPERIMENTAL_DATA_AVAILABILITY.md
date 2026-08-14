# Ver3 experimental data availability audit

Source requirement file: `D:\4. CAND T05\5. Research\FastOPF\ver3\experimental_data_requirements.md`

## Decision

Do not generate the final PDF report yet. The current workspace has enough data for the core OPF vs FastOPF performance tables and figures, but it is still missing or only partially satisfies several requested data groups.

## What is already available

| Requirement group | Status | Evidence |
|---|---|---|
| Pattern counts `|P|` for DB1-DB8 and minSup 2,4,6,8,10,12 | Available | `algorithm_benchmark/results_full_20260811/benchmark/opf/OPF_Miner_Original_summary_avg.csv` and `benchmark/fom/FOM_summary_avg.csv`; both have DB1-DB8 x 6 minSup = 48 rows. |
| Mean runtime and standard deviation after at least 5 runs | Available | OPF and FOM summaries include `Time_s`, `Time_std`, and `Runs=10`. |
| Memory peak | Available | OPF and FOM summaries include `MaxMem_MB` and `MaxMem_std`. |
| Pair-check / candidate-comparison proxy | Available, needs naming confirmation | Summary files include `Candidates` and `Fusions`. `Fusions` is likely the closest OPF pair-check/work-count field; confirm against code before using the term "pair-checks" in the manuscript. |
| Support operations | Available | Summary files include `SupportOps`. |
| DB5 minSup sensitivity | Available | DB5 rows exist for minSup 2,4,6,8,10,12 for OPF and FOM. Figure already generated: `fig_runtime_vs_minsup_db5`. |
| DB8 scalability 1x-6x | Available | DB8_1x to DB8_6x rows exist for OPF and FOM. Figure already generated: `fig_runtime_vs_db8_scale_minsup4`. |
| Full FastOPF ablation baseline | Available | `algorithm_benchmark/results/FOM_summary_avg.csv`. |
| Ablation when disabling one component | Partially available | Old summaries exist for `FOMNoHash`, `FOMNoVector`, `FOMNoWSB`; they are "component removed" variants, not exactly "only using one component" variants. |
| Figures from available data | Available | Eight PNG/PDF figures exist under `algorithm_benchmark/results_full_20260811/figures/`. |
| Java/JDK version | Available | OpenJDK Temurin 25.0.3 LTS. |

## Missing or not yet sufficient

| Requirement | Missing detail | Needed action |
|---|---|---|
| SHA-256 after normalization with 8 decimal places | Current default validated report uses 6 decimals. A round-8 check was generated but has 1 support mismatch: `DB1_minsup_2p0.csv`, max diff `2.17600018004e-07` at pattern `[1, 2]`. | Either change the requirement to 6 decimal places, or rerun/modify canonical support formatting so both algorithms emit exactly comparable support to 8 decimals. |
| Hardware environment | CPU family/logical processor count can be inferred from env vars, but exact CPU model name and RAM query were blocked by Windows permissions. | Run an approved hardware info command, or manually provide CPU model, RAM, OS, and JVM heap flags used. |
| Generality on non-finance datasets | No UCR ECG, sensor, weather, electricity, or other non-finance benchmark outputs were found. | Add preprocessing and run OPF/FOM on at least 2-4 non-finance datasets if this requirement is mandatory. |
| Exact ablation modes requested | Available for the three option-only variants. | New results are in `algorithm_benchmark/results_full_20260811/benchmark/option_only/`: `FOMOptionHashOnly_summary_avg.csv`, `FOMOptionSparseOnly_summary_avg.csv`, `FOMOptionWSBOnly_summary_avg.csv`, plus `option_only_summary_comparison.csv`. |
| Pair-check semantics | Current metrics have `Candidates` and `Fusions`; the exact mapping to "pair-checks" needs code-level confirmation. | Confirm definitions in `FOM.java` and `OPF_Miner_Original.java`, then label the table accordingly. |

## Recommended next experiments

1. Rerun or re-export canonical outputs with support rounded consistently to 8 decimals, or officially use 6-decimal normalized SHA-256 in the manuscript.
2. Capture environment metadata: CPU model, RAM, OS, Java version, JVM heap settings, and run date.
3. Run non-finance datasets only if the final manuscript keeps the "Generality" requirement as mandatory.

## Current bottom line

Core benchmark report: possible.

Full PDF satisfying `experimental_data_requirements.md`: not yet recommended, because SHA-8 and hardware metadata are still not fully satisfied. Non-finance generality is intentionally out of scope if the current dataset set is retained.

# Source migration map

The release directory is a clean staging copy. The original experimental
workspace remains unchanged so historical result paths continue to resolve.

| Original workspace | GitHub staging location |
|---|---|
| `algorithm_benchmark/OPF_Miner_Original.java` | `src/benchmark/java/OPF_Miner_Original.java` |
| `algorithm_benchmark/FOMAblationFlags.java` | `src/benchmark/java/FOMAblationFlags.java` |
| `algorithm_benchmark/FOM.java` | `src/benchmark/java/FOM.java` |
| `algorithm_benchmark/FOMNo*.java` | `src/benchmark/java/legacy/` |
| `algorithm_benchmark/average.py` | `scripts/benchmark/average.py` |
| `algorithm_benchmark/verify_sha256_equivalence.py` | `scripts/validation/verify_sha256_equivalence.py` |
| `algorithm_benchmark/generate_*.py` | `scripts/analysis/` |
| `datasets/create_concat.py` | `scripts/preprocessing/create_concat.py` |
| `datasets/preprocess_electricity_load_diagrams.py` | `scripts/preprocessing/preprocess_electricity_load_diagrams.py` |
| `rq6_clustering_pipeline/*.java` | `src/rq6/java/` |
| current RQ6 Python pipeline | `scripts/rq6/` |
| old RQ6 repair/coordinator utilities | `legacy/rq6/` |
| audit Markdown files | `docs/audits/` |
| selected summary CSV files | `results/reference/` |

New root-aware build, benchmark, canonical-validation, and smoke-test wrappers
are provided in `tools/`, `scripts/benchmark/`, and `scripts/validation/`.

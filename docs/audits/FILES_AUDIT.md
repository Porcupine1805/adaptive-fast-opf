# FastOPF/FOM file audit

This file separates source code from generated artifacts so the project can be cleaned or pushed to GitHub safely.

## Keep as source code

- `algorithm_benchmark/FOM.java`: FastOPF/FOM implementation and benchmark runner.
- `algorithm_benchmark/OPF_Miner_Original.java`: original OPF baseline implementation.
- `algorithm_benchmark/FOMNoHash.java`: ablation variant without hash optimization.
- `algorithm_benchmark/FOMNoVector.java`: ablation variant without vector optimization.
- `algorithm_benchmark/FOMNoWSB.java`: ablation variant without WSB optimization.
- `algorithm_benchmark/average.py`: aggregates benchmark CSV outputs.
- `algorithm_benchmark/verify_sha256_equivalence.py`: compares OPF and FOM canonical outputs using SHA-256 plus tolerant numeric comparison.
- `algorithm_benchmark/run_opf_canonical.ps1`: OPF canonical-output runner.
- `algorithm_benchmark/run_fom_benchmark_canonical.ps1`: FOM benchmark and canonical-output runner.
- `algorithm_benchmark/run_sha256_verify.ps1`: wrapper for SHA-256 equivalence verification.
- `rq6_clustering_pipeline/FOM_Clustering.java`: FOM implementation for RQ6 clustering workflow.
- `rq6_clustering_pipeline/OPF_Miner_Clustering.java`: OPF implementation for RQ6 clustering workflow.
- `rq6_clustering_pipeline/rq6_coordinator.py`: RQ6 experiment coordinator.
- `rq6_clustering_pipeline/rq6_clustering.py`: RQ6 clustering analysis.
- `rq6_clustering_pipeline/rq6_reproduce_original_metrics.py`: reproduces RQ6 metrics.
- `rq6_clustering_pipeline/generate_sector_labels.py`: creates sector labels for RQ6.
- `rq6_clustering_pipeline/sector.py`: sector helper data.
- `datasets/create_concat.py`: dataset construction helper.
- `README.txt` and `requirements.txt`: project documentation and Python dependencies.

## Keep locally, but do not push to GitHub by default

- `datasets/`: full benchmark datasets. Keep locally for reproduction; consider Git LFS or an external archive if these must be shared.
- `algorithm_benchmark/results_full_20260811/`: current full OPF/FOM benchmark, canonical outputs, logs, and SHA-256 comparison results.
- `algorithm_benchmark/results/`: older FOM benchmark outputs. Keep as legacy results until the new run is fully accepted.
- `rq6_output/` and `datasets/RQ6_output/`: generated RQ6 outputs.
- `benchmark_tables_figures_audit.xlsx`: generated report-support workbook.

## Safe cleanup candidates after confirmation

- `*.class`: Java compilation outputs; can be regenerated with `javac`.
- `__pycache__/` and `*.pyc`: Python cache files; can be regenerated.
- `*.log`: runtime logs; keep only if needed for experiment provenance.
- `tmp_opf_smoke_input/`: temporary smoke-test dataset copy.
- `rq6_clustering_pipeline/main.py`: empty file, likely unused.

## Needs manual decision before delete or rename

- `rq6_clustering_pipeline/compile_and_run.bat`: file extension says BAT, but the content is Python code. It should be renamed to `.py` or moved to a `legacy/` folder after checking whether it duplicates `rq6_coordinator.py`.
- `algorithm_benchmark/results/`: old results may still be useful as provenance or for comparison with runs from another machine.
- `algorithm_benchmark/results_full_20260811/`: valuable current results; archive it, do not delete casually.

## Suggested future structure

```text
FOM-main/
  README.txt
  requirements.txt
  .gitignore
  algorithm_benchmark/
    src/
    scripts/
    experiments/
      20260811_full/
  rq6_clustering_pipeline/
    src/
    scripts/
  datasets/
  docs/
```

The current folder names are already understandable enough for use. The highest-value cleanup is to ignore generated artifacts in Git, then move source files into `src/` and runners into `scripts/` only after the benchmark workflow is stable.

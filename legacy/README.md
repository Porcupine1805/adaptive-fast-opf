# Legacy files

These files are retained for provenance but are not part of the supported
workflow.

- `rq6/coordinator_v2_legacy.py` was previously named `compile_and_run.bat`,
  although its content is Python. It represents an older global-matrix RQ6
  pipeline and has been renamed to prevent accidental execution as a batch file.
- `rq6/quick_fix_scaled.py` is a one-off migration utility for old feature
  matrices and still uses the historical output layout.
- `scripts/` contains one-off diagnostic runners with the original project
  paths preserved. They are reference material, not supported entry points.

Use `scripts/rq6/` for current experiments.

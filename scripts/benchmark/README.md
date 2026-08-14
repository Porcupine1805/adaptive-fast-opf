# Benchmark scripts

- `run_factorial_benchmark.ps1`: publication runner for OPF, HJ, BM, WB, and
  Full. It rotates execution order and keeps canonical output disabled.
- `run_benchmark.ps1`: convenience runner for one configuration.
- `average.py`: aggregates repeated CSV files produced by the convenience
  runner.

Use canonical validation under `scripts/validation/` as a separate command.
Do not compare rows produced with different source revisions, input files, JVM
flags, or machines as if they were one controlled experiment.

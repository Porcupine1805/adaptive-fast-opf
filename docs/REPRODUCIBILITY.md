# Reproducibility protocol

1. Record the environment using `tools/capture_environment.ps1`.
2. Build once with `tools/build.ps1 -Clean`.
3. Use one JVM process per configuration/run with identical heap flags.
4. Rotate configuration order across repeated runs.
5. Keep canonical serialization disabled during timing.
6. Report mean, standard deviation, median, minimum, and all raw rows.
7. Validate every optimized mode against OPF by canonical pattern-support
   content using the same dataset and threshold list.
8. Keep sampled JVM heap separate from RSS or allocation-profile claims.

Suggested publication command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/capture_environment.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark/run_factorial_benchmark.ps1 -InputDir data/benchmark -Runs 10 -WarmupRuns 1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validation/run_canonical_equivalence.ps1 -InputDir data/benchmark
```

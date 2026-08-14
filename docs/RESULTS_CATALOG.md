# Reference result catalog

## Current probes

- `full_fom_optimization_probe_10clients_rotated`: five schedules run in
  rotated order on the 10-client Electricity input.
- `full_fom_hypothesis_summary`: minSup, bitmap density, and pattern-length
  hypothesis tables.
- `full_fom_subunit_minsup_electricity_0p5`: sub-unit minSup timing probe.
- `full_fom_subunit_minsup_electricity_canonical`: matching canonical check.
- `electricity_opf_vs_4options`: OPF and four FastOPF configurations on the
  Electricity scale inputs.

## Historical evidence

The historical folder contains accepted OPF/HJ ten-run summaries, canonical
comparisons, and earlier diagnostic tables. Its SparseOnly/WSBOnly rows must
not be presented as current BM/WB results.

## RQ6

The RQ6 folder contains timing, clustering metrics, labels, and LaTeX tables
for DB9. It does not contain independent current BM-only and WB-only benchmark
matrices.

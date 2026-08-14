# Contributing

Use a focused branch and include a test or reproducible command for behavioral
changes. Do not commit generated classes, logs, full canonical outputs, or raw
archives. New benchmark claims must include environment metadata, repeated raw
runs, an aggregate table, and canonical equivalence against OPF.

Run before submitting changes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify_repository.ps1
```

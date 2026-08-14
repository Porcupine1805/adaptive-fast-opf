# GitHub release checklist

- [ ] Replace `LICENSE_PENDING.md` with the authors' selected `LICENSE`.
- [ ] Confirm dataset redistribution terms in `data/manifests/datasets.csv`.
- [ ] Add the final repository URL and manuscript DOI to `CITATION.cff`.
- [ ] Replace any remaining manuscript affiliation/email placeholders.
- [ ] Run `tools/verify_repository.ps1` on a clean clone.
- [ ] Run the full DB1-DB8 factorial benchmark on the publication machine.
- [ ] Run canonical equivalence for HJ, BM, WB, and Full.
- [ ] Add CPU, RAM, OS, JVM build, heap flags, and power policy to each run.
- [ ] Verify that no raw archives, secrets, `.class` files, or logs are tracked.
- [ ] Tag the exact commit used for manuscript tables and figures.

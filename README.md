# HJ-OPF

**Output-Sensitive Hash-Indexed Join for Order-Preserving Pattern Mining with Forgetting Mechanism**

This repository contains the reference implementation and experimental artifact for the paper:

> Nguyen, K.-C., Bui, N.-M., Tran, C.-P.  
> *HJ-OPF: Output-Sensitive Hash-Indexed Join for Order-Preserving Pattern Mining with Forgetting Mechanism*

HJ-OPF replaces the quadratic group scan of OPF-Miner with an output-sensitive hash-indexed join on the same normalized prefix/suffix keys. Compatible pairs are emitted in expected Θ(L + J) time; the original fusion and forgetting-aware support are then applied unchanged. Semantic equivalence follows from the completeness of GP-Fusion together with soundness and completeness of the join.

Residual operators (cheap pre-fusion bounds / CPC, galloping intersection, adaptive enablement) are implemented for controlled ablation only. Relative to pure HJ-OPF they change the matrix mean by only a few percent and are **not** a primary claim of the paper.

---

## Requirements

- JDK 11 or later (`java -version`, `javac -version`)

---

## Quick start

```bash
git clone https://github.com/Porcupine1805/HJ-OPF.git
cd HJ-OPF

# Compile
mkdir -p build/classes/benchmark
javac -encoding UTF-8 -d build/classes/benchmark \
  src/benchmark/java/OPF_Miner_Original.java \
  src/benchmark/java/HJOPF.java
```

### 1. OPF-Miner baseline

```bash
java -Xmx2g \
  -Dinput=data/benchmark \
  -DfileRegex='DB4\.txt' \
  -DminsupList=2,4 \
  -Doutput=results/out_opf.csv \
  -cp build/classes/benchmark OPF_Miner_Original
```

### 2. HJ-OPF only (primary claim)

```bash
java -Xmx2g \
  -Dinput=data/benchmark \
  -DfileRegex='DB4\.txt' \
  -DminsupList=2,4 \
  -Dmode=hash_only \
  -DbitmapPolicy=never \
  -DwsbPolicy=never \
  -Doutput=results/out_hj.csv \
  -cp build/classes/benchmark HJOPF
```

### 3. Residual ablation (not a primary claim)

```bash
java -Xmx2g \
  -Dinput=data/benchmark \
  -DfileRegex='DB4\.txt' \
  -DminsupList=2,4 \
  -Dmode=adaptive \
  -DbitmapPolicy=never \
  -DwsbPolicy=cost \
  -Doutput=results/out_residual.csv \
  -cp build/classes/benchmark HJOPF
```

---

## Repository layout

```text
src/benchmark/java/
  OPF_Miner_Original.java   # baseline OPF-Miner
  HJOPF.java                # HJ-OPF + residual ablation harness
data/benchmark/             # DB1.txt … DB8.txt (financial suite)
data/electricity_scale/     # ELEC_01/05/10 concatenations used in the paper
scripts/                    # benchmark, validation, analysis helpers
tools/                      # build and environment capture
docs/                       # reproducibility notes
```

Electricity raw archive is not shipped; the pre-concatenated scale files used in the manuscript are included under `data/electricity_scale/`.

---

## Correctness

Add `-Dcanonical=results/canonical_run` and compare pattern-support dumps across baseline / `hash_only` / residual modes. They must be identical.

---

## Citation

See `CITATION.cff`. When the paper is published, please cite the journal version; until then cite this repository.

## License

See `LICENSE_PENDING.md` (authors will replace with a chosen open-source license before final release).

# Adaptive Fast OPF

Exact acceleration of **OPF-Miner** (order-preserving patterns with exponential forgetting) via a **three-stage** pipeline:

| Stage | Technique | Role |
|-------|-----------|------|
| **1** | **HJ** — Hash-Indexed Join | Output-sensitive compatible pair discovery |
| **2** | **CPC** — Cheap-Prune Cascade | Pre-fusion residual / span / card bounds (optional range) |
| **3** | **Sorted List or Gallop** | Occurrence fusion; Gallop only under length skew |

**Adaptive Fast OPF** always runs HJ, then enables CPC / Gallop only from **problem quantities** (series length, minSup, pair mass, residual structure, skew)—not wall-clock timers.

> **Bitmap is not a paper contribution.** The fusion path is list-based (`SPARSE=false`). Prefer `-DbitmapPolicy=never`.

Property names still use the historical prefix `adaptiveWsb*` for CPC (WSB was replaced by CPC).

Repo: https://github.com/Porcupine1805/adaptive-fast-opf

---

## Requirements

- JDK 11+ (`java -version`, `javac -version`)
- Windows: PowerShell in VS Code / Terminal
- Linux/macOS: bash

---

## Clone & open

```powershell
git clone https://github.com/Porcupine1805/adaptive-fast-opf.git
cd adaptive-fast-opf
```

VS Code: **File → Open Folder** → `adaptive-fast-opf`.

---

## Compile

```powershell
New-Item -ItemType Directory -Force -Path build\classes\benchmark | Out-Null
javac -encoding UTF-8 -d build\classes\benchmark src\benchmark\java\FOMAblationFlags.java
```

Or: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1`

---

## Main configurations

Data: `data\benchmark\DB1.txt` … `DB8.txt`.

```powershell
New-Item -ItemType Directory -Force -Path results | Out-Null
```

### 1) OPF-Miner baseline (V0)

Compile baseline + Adaptive sources:

```powershell
javac -encoding UTF-8 -d build\classes\benchmark `
  src\benchmark\java\OPF_Miner_Original.java `
  src\benchmark\java\FOMAblationFlags.java
```

```powershell
java -Xmx2g `
  -Dinput=data/benchmark `
  "-DfileRegex=DB4\.txt" `
  -DminsupList=2,4 `
  -Doutput=results/out_opf.csv `
  -cp build/classes/benchmark OPF_Miner_Original
```

Source: `src/benchmark/java/OPF_Miner_Original.java` (original OPF-Miner algorithm + timing CSV).

### 2) HJ-only

```powershell
java -Xmx2g `
  -Dinput=data/benchmark `
  "-DfileRegex=DB4\.txt" `
  -DminsupList=2,4 `
  -Dmode=hash_only `
  -DbitmapPolicy=never `
  -DwsbPolicy=never `
  -Doutput=results/out_hj.csv `
  -cp build/classes/benchmark FOMAblationFlags
```

### 3) Adaptive Fast OPF (HJ → CPC? → Gallop?)

When `-Dmode=adaptive`, defaults enable CPC, smart intersect (Gallop eligible), staged policy, and `bitmapPolicy=never`.

```powershell
java -Xmx2g `
  -Dinput=data/benchmark `
  "-DfileRegex=DB4\.txt" `
  -DminsupList=2,4 `
  -Dmode=adaptive `
  -DbitmapPolicy=never `
  -DwsbPolicy=cost `
  -Doutput=results/out_adaptive.csv `
  -cp build/classes/benchmark FOMAblationFlags
```

Optional knobs:

```text
-DadaptiveWsbCheapPrune=true
-DadaptiveSmartIntersect=true
-DadaptiveStagedPolicy=true
-DadaptiveGallopWithoutCpc=true
-DadaptiveCpcGate=B
-DadaptiveGallopMinRatio=2
-DadaptiveGallopMinOcc=64
-DadaptiveGallopMinSkewFraction=0.08
```

---

## Modes (ablation)

| Name | How to run | Notes |
|------|------------|--------|
| **V0 OPF-Miner** | class `OPF_Miner_Original` | Official baseline (no HJ) |
| V1 HJ-only | `-Dmode=hash_only` on `FOMAblationFlags` | Stage 1 only |
| V2 HJ+CPC | `-Dmode=adaptive` + gate C, Gallop off | See manuscript script |
| V3 Adaptive | `-Dmode=adaptive` (defaults) | Staged HJ + CPC + Gallop |
| Full (legacy) | `-Dmode=full` | Static enable — **not** the paper claim |

---

## Correctness check

Add `-Dcanonical=results/canonical_run` and compare dumps across baseline / hash_only / adaptive.

---

## Repo layout

```text
src/benchmark/java/FOMAblationFlags.java
data/benchmark/DB1.txt … DB8.txt
docs/ADAPTIVE_FASTOPF_HANDOFF.md
docs/Adaptive_FastOPF_Manuscript_Draft.md
docs/ADAPTIVE_MECHANISM.md
README.md
tools/build.ps1
```

Electricity (ELEC_*) is not shipped; set `-Dinput=` locally.

## License

See `LICENSE_PENDING.md`.

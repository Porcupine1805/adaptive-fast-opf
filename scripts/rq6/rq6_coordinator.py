"""
rq6_coordinator_v3.py  — Pipeline đúng theo bài gốc OPF-Miner
=================================================================
Khác biệt cốt lõi so với v2:

  v2 (SAI):  global_minsup=50 trên 400 series → chỉ 39 patterns
             Feature matrix 400×39 → SC=0.13

  v3 (ĐÚNG): local_minsup=50 per-series → mỗi series có patterns riêng
             Union toàn bộ → feature dictionary lớn (200-500 patterns)
             Feature matrix 400×M với M >> 39 → SC ≈ bài gốc

Bài gốc OPF-Miner_Original.java:
  for (File file : files)          // mỗi series riêng lẻ
    processFile(file, ms=50, ...)  // fsup(p, s_i) ≥ 50 IN series i
  → patterns_i = {p : fsup(p,s_i) ≥ 50}  (local, per-series)
  → X[i,j] = fsup(p_j, s_i) if p_j ∈ patterns_i else 0

Chạy:
  python rq6_coordinator_v3.py
"""

import os, sys, csv, time, logging, subprocess
from pathlib import Path
from concurrent.futures import ProcessPoolExecutor, as_completed
from multiprocessing import cpu_count

import numpy as np
import pandas as pd

# ═══════════════════════════════════════════════════════════════════════
BASE_DIR   = Path(__file__).resolve().parents[2]
WORK_DIR   = BASE_DIR / "build" / "classes" / "rq6"
DB9_DIR    = BASE_DIR / "data" / "DB9"
OUTPUT_DIR = BASE_DIR / "results" / "rq6"
LOG_FILE   = OUTPUT_DIR / "coordinator_v3.log"

CLASS_FOM  = "FOM_Clustering"
CLASS_OPF  = "OPF_Miner_Clustering"

# ── THAM SỐ THEN CHỐT ──────────────────────────────────────────────────
# LOCAL minsup: fsup(p, s_i) ≥ MINSUP_LOCAL trong riêng series i
# Giống bài gốc Section 5.6: minsup=50 per-series
MINSUP_LOCAL = 50.0

JVM_XMS    = "256m"
JVM_XMX    = "2g"
N_WORKERS  = min(4, max(1, cpu_count() - 1))

# Đầu ra
MATRIX_FOM_RAW    = OUTPUT_DIR / "Feature_Matrix_FOM_RAW.csv"     # fsup thô
MATRIX_OPF_RAW    = OUTPUT_DIR / "Feature_Matrix_OPF_RAW.csv"
MATRIX_FOM_SCALED = OUTPUT_DIR / "Feature_Matrix_FOM_SCALED.csv"  # MinMaxScaled
TIMING_CSV         = OUTPUT_DIR / "phase1_timing_v3.csv"
ERROR_CSV          = OUTPUT_DIR / "error_files_v3.csv"
# ═══════════════════════════════════════════════════════════════════════


def setup_logging():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    fmt = "%(asctime)s  %(levelname)-7s  %(message)s"
    logging.basicConfig(level=logging.INFO, format=fmt, handlers=[
        logging.FileHandler(LOG_FILE, encoding="utf-8"),
        logging.StreamHandler(sys.stdout)
    ])


def scan_files():
    files = sorted(DB9_DIR.glob("*.txt"))
    if not files:
        logging.error(f"Không tìm thấy .txt trong {DB9_DIR}")
        sys.exit(1)
    logging.info(f"[SCAN] {len(files)} file DB9")
    return files


# ── Worker: chạy Java, trả về per-series patterns ───────────────────────────
def _worker(args):
    """
    Trả về patterns của RIÊNG series này với local minsup.
    Không lọc global — giữ tất cả patterns có fsup ≥ MINSUP_LOCAL.
    """
    filepath, class_name, minsup, xms, xmx, work_dir = args
    ticker = Path(filepath).stem
    try:
        raw    = Path(filepath).read_text(encoding="utf-8").strip()
        tokens = [t for t in raw.replace(",", " ").split() if t]
        n      = len(tokens)
        k_val  = round(1.0 / n, 10) if n > 1 else 0.001
    except Exception as ex:
        return ticker, class_name, {}, None, None, f"READ:{ex}"

    cmd = ["java", f"-Xms{xms}", f"-Xmx{xmx}",
           "-cp", work_dir, class_name,
           filepath, str(minsup), str(k_val)]

    t0 = time.perf_counter()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        ms   = round((time.perf_counter() - t0) * 1000, 1)
    except subprocess.TimeoutExpired:
        return ticker, class_name, {}, None, None, "TIMEOUT"
    except FileNotFoundError:
        return ticker, class_name, {}, None, None, "JAVA_NOT_FOUND"

    if proc.returncode != 0:
        return ticker, class_name, {}, None, ms, f"ERR:{proc.stderr[:200]}"

    # Parse: giữ TẤT CẢ patterns có fsup ≥ minsup (đã được Java lọc)
    pats, mem = {}, None
    for line in proc.stdout.splitlines():
        line = line.strip()
        if line.startswith("MEMORY_MB:"):
            try: mem = float(line.split(":", 1)[1])
            except: pass
        elif line.startswith("PATTERN:"):
            parts = line.split(":")
            if len(parts) >= 3:
                try: pats[parts[1]] = float(parts[2])
                except: pass

    return ticker, class_name, pats, mem, ms, "OK" if pats else "NO_PATTERNS"


def run_phase1(files, class_name, label):
    """Phase 1: Mining với LOCAL minsup per-series."""
    logging.info(f"\n{'='*60}")
    logging.info(f"[PHASE1] {label} | {len(files)} files | local_minsup={MINSUP_LOCAL}")

    cf = WORK_DIR / f"{class_name}.class"
    if not cf.exists():
        logging.error(f"[LỖI] Không thấy {cf}")
        return {}, [], [(fp.stem, "CLASS_NOT_FOUND") for fp in files]

    args_list = [(str(fp), class_name, MINSUP_LOCAL, JVM_XMS, JVM_XMX, str(WORK_DIR))
                 for fp in files]

    all_pats, timing, errors = {}, [], []
    done, t0 = 0, time.perf_counter()

    with ProcessPoolExecutor(max_workers=N_WORKERS) as pool:
        futs = {pool.submit(_worker, a): a[0] for a in args_list}
        for fut in as_completed(futs):
            try:
                ticker, algo, pats, mem, ms, status = fut.result()
            except Exception as ex:
                errors.append((futs[fut], str(ex))); continue

            done += 1
            if status in ("OK", "NO_PATTERNS"):
                # Giữ TOÀN BỘ per-series patterns (không lọc global)
                all_pats[ticker] = pats or {}
                timing.append({
                    "ticker": ticker, "algo": label,
                    "elapsed_ms": ms or 0,
                    "max_mem_mb": round(mem, 2) if mem else 0,
                    "n_patterns_local": len(pats or {}),
                    "status": status
                })
            else:
                errors.append((ticker, status))

            if done % 50 == 0 or done == len(files):
                logging.info(f"  {label}: {done}/{len(files)} | {time.perf_counter()-t0:.0f}s")

    total = time.perf_counter() - t0
    logging.info(f"  {label}: {len(all_pats)} OK, {len(errors)} lỗi | {total:.1f}s")
    return all_pats, timing, errors


def build_feature_matrix(all_pats, label):
    """
    Build feature matrix theo đúng pipeline bài gốc:
    - Global dictionary = UNION của tất cả per-series patterns
    - X[i,j] = fsup(p_j, s_i) nếu p_j ∈ patterns_of_s_i
    - KHÔNG lọc global minsup
    - Trả về cả raw fsup và MinMaxScaled
    """
    logging.info(f"\n[PHASE2] {label}: Build feature matrix (per-series union)")

    # Global dictionary: union tất cả patterns
    global_pats = set()
    for pats in all_pats.values():
        global_pats.update(pats.keys())

    pat_list = sorted(global_pats)
    tickers  = sorted(all_pats.keys())
    M, S_    = len(pat_list), len(tickers)

    logging.info(f"  Per-series patterns: total union = {M} unique patterns")
    logging.info(f"  ({S_} series × avg {sum(len(v) for v in all_pats.values())/S_:.1f} patterns/series)")

    if M == 0:
        logging.warning("  Không có pattern nào!")
        return None, None

    # Build raw fsup matrix
    p2i = {p: i for i, p in enumerate(pat_list)}
    mat  = np.zeros((S_, M), dtype=np.float32)
    for i, t in enumerate(tickers):
        for p, fsup in all_pats.get(t, {}).items():
            if p in p2i:
                mat[i, p2i[p]] = fsup

    density = np.count_nonzero(mat) / mat.size * 100
    logging.info(f"  Raw matrix {S_}×{M}, density={density:.1f}%")

    # Raw (giống bài gốc)
    df_raw = pd.DataFrame(mat, index=tickers, columns=pat_list)

    # MinMaxScaled (cho metrics bổ sung)
    col_min = mat.min(axis=0); col_max = mat.max(axis=0)
    rng = col_max - col_min;   rng[rng == 0] = 1.0
    df_scaled = pd.DataFrame((mat - col_min) / rng,
                             index=tickers, columns=pat_list)

    return df_raw, df_scaled


def verify_isomorphism(df_f, df_o):
    logging.info("\n[VERIFY] Isomorphism (per-series union)")
    common   = sorted(set(df_f.columns) & set(df_o.columns))
    only_fom = set(df_f.columns) - set(df_o.columns)
    only_opf = set(df_o.columns) - set(df_f.columns)
    logging.info(f"  Common:{len(common)}  OnlyFOM:{len(only_fom)}  OnlyOPF:{len(only_opf)}")

    if common:
        diff = np.abs(df_f[common].sort_index().values -
                      df_o[common].sort_index().values)
        n_bad = int((diff > 1e-4).sum())
        logging.info(f"  Max|diff|={diff.max():.6f}  Bad>{n_bad}")
        if len(only_fom)==0 and len(only_opf)==0 and n_bad==0:
            logging.info("  ✅ ISOMORPHISM CONFIRMED")
        else:
            logging.warning("  ⚠️  Sai lệch phát hiện")


def save_timing(rows_fom, rows_opf):
    all_rows = rows_fom + rows_opf
    if not all_rows: return
    fields = ["ticker","algo","elapsed_ms","max_mem_mb","n_patterns_local","status"]
    with open(TIMING_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fields); w.writeheader(); w.writerows(all_rows)

    df = pd.DataFrame(all_rows)
    logging.info("\n" + "="*55 + "\n  HIỆU NĂNG PHASE 1 (v3)\n" + "="*55)
    for algo in ["FOM", "OPFMiner"]:
        sub = df[df["algo"]==algo]
        if sub.empty: continue
        logging.info(
            f"  {algo:<10}: total={sub['elapsed_ms'].sum()/1000:.1f}s  "
            f"mean={sub['elapsed_ms'].mean()/1000:.3f}s/file  "
            f"peak={sub['max_mem_mb'].max():.1f}MB  "
            f"patterns/series: mean={sub['n_patterns_local'].mean():.1f}  "
            f"ok={(sub['status']=='OK').sum()}/{len(sub)}"
        )
    grp = df.groupby("algo")["elapsed_ms"].sum()
    if "OPFMiner" in grp and "FOM" in grp and grp["FOM"]>0:
        logging.info(f"  Speedup: {grp['OPFMiner']/grp['FOM']:.2f}×")


def main():
    setup_logging()
    logging.info("="*60)
    logging.info("  RQ6 COORDINATOR v3 — Per-series local minsup")
    logging.info("  (đúng theo pipeline bài gốc OPF-Miner Section 5.6)")
    logging.info("="*60)

    files = scan_files()

    # Phase 1
    pats_fom, t_fom, e_fom = run_phase1(files, CLASS_FOM, "FOM")
    pats_opf, t_opf, e_opf = run_phase1(files, CLASS_OPF, "OPFMiner")
    save_timing(t_fom, t_opf)

    # Phase 2: build per-series union matrix
    df_fom_raw = df_fom_sc = None
    df_opf_raw = df_opf_sc = None

    if pats_fom:
        df_fom_raw, df_fom_sc = build_feature_matrix(pats_fom, "FOM")
        if df_fom_raw is not None:
            df_fom_raw.to_csv(MATRIX_FOM_RAW)
            df_fom_sc.to_csv(MATRIX_FOM_SCALED)
            logging.info(f"[SAVE] Feature_Matrix_FOM_RAW.csv    {df_fom_raw.shape}")
            logging.info(f"[SAVE] Feature_Matrix_FOM_SCALED.csv {df_fom_sc.shape}")

    if pats_opf:
        df_opf_raw, df_opf_sc = build_feature_matrix(pats_opf, "OPFMiner")
        if df_opf_raw is not None:
            df_opf_raw.to_csv(MATRIX_OPF_RAW)
            logging.info(f"[SAVE] Feature_Matrix_OPF_RAW.csv    {df_opf_raw.shape}")

    if df_fom_raw is not None and df_opf_raw is not None:
        verify_isomorphism(df_fom_raw, df_opf_raw)

    # Errors
    all_err = [(t,"FOM",e) for t,e in e_fom]+[(t,"OPF",e) for t,e in e_opf]
    if all_err:
        with open(ERROR_CSV, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["ticker","algo","error"])
            w.writerows(all_err)

    logging.info("\n[DONE] Chạy tiếp: python rq6_clustering.py")
    logging.info(f"       Dùng {MATRIX_FOM_RAW.name} để tính SC/CHI (raw, như bài gốc)")
    logging.info(f"       Dùng {MATRIX_FOM_SCALED.name} cho ARI/NMI/Silhouette/DB")


if __name__ == "__main__":
    main()

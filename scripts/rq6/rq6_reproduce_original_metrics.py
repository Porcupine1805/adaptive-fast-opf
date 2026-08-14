r"""
rq6_reproduce_original_metrics.py
===================================
Tái tạo đúng SC và CHI như Section 5.6 của OPF-Miner gốc (IEEE TKDE 2024)
để có thể so sánh trực tiếp với Table 9 và Fig. 17–18.

Chạy   : python rq6_reproduce_original_metrics.py
Output : results/rq6/table_rq6_sc_chi_comparison.tex
         results/rq6/rq6_sc_chi_results.csv
"""

import sys, csv, time, warnings
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.cluster import KMeans
from sklearn.metrics import (silhouette_score, calinski_harabasz_score,
                              adjusted_rand_score, normalized_mutual_info_score,
                              davies_bouldin_score)
from sklearn.preprocessing import MinMaxScaler

warnings.filterwarnings("ignore")

# ═══════════════════════════════════════════════════════════════════
BASE_DIR    = Path(__file__).resolve().parents[2]
OUTPUT_DIR  = BASE_DIR / "results" / "rq6"
MATRIX_FOM  = OUTPUT_DIR / "Feature_Matrix_FOM_RAW.csv"
SECTOR_CSV  = OUTPUT_DIR / "sector_labels.csv"

# Theo đúng bài gốc
K_LIST_ORIG = [3, 4, 5, 6, 7, 8, 9, 10]   # K=3–10 như Fig.17–18
K_LIST_EXT  = [3, 5, 10, 15, 20]           # K mở rộng cho bài mới
N_INIT      = 20
MAX_ITER    = 500
# ═══════════════════════════════════════════════════════════════════


def load_data():
    if not MATRIX_FOM.exists():
        print(f"[LỖI] {MATRIX_FOM} không tồn tại. Chạy rq6_coordinator.py trước.")
        sys.exit(1)
    df = pd.read_csv(MATRIX_FOM, index_col=0)
    print(f"[LOAD] Feature Matrix: {df.shape}")
    # RAW fsup (chưa scale) — đây là input đúng như bài gốc
    X_raw = df.values.astype(np.float64)
    # Scaled (cho metrics bổ sung)
    X_scaled = MinMaxScaler().fit_transform(X_raw)
    tickers = list(df.index)
    return X_raw, X_scaled, tickers


def load_gt(tickers):
    if not SECTOR_CSV.exists():
        return None
    df = pd.read_csv(SECTOR_CSV).set_index("ticker")
    lbl = [int(df.loc[t, "sector_id"]) if t in df.index else -1 for t in tickers]
    return np.array(lbl)


# ── Tính SC và CHI đúng như bài gốc (trên RAW fsup, không scale) ────────────
def sc_chi_original(X_raw, labels):
    """
    Bài gốc OPF-Miner dùng:
    - sklearn silhouette_score trên toàn bộ samples (không sample_size)
    - sklearn calinski_harabasz_score
    - Input: RAW fsup matrix (KHÔNG MinMaxScale)
    Đây là lý do SC bài gốc cao (0.79 tại K=5) —
    dominant patterns [1,2]~786 và [2,1]~847 tạo khoảng cách Euclidean lớn.
    """
    k = len(set(labels))
    if k < 2:
        return np.nan, np.nan
    sc  = silhouette_score(X_raw, labels)
    chi = calinski_harabasz_score(X_raw, labels)
    return round(sc, 2), int(round(chi))


# ── Tính 4 metrics mở rộng (trên SCALED, có GT) ─────────────────────────────
def metrics_extended(X_scaled, labels, gt=None):
    k = len(set(labels))
    if k < 2:
        return dict(Silhouette=np.nan, DB=np.nan, ARI=np.nan, NMI=np.nan)
    sil = silhouette_score(X_scaled, labels,
                           sample_size=min(400, len(labels)),
                           random_state=42)
    db  = davies_bouldin_score(X_scaled, labels)
    ari = adjusted_rand_score(gt, labels) if gt is not None else np.nan
    nmi = normalized_mutual_info_score(gt, labels) if gt is not None else np.nan
    return dict(Silhouette=round(sil,4), DB=round(db,4),
                ARI=round(ari,4), NMI=round(nmi,4))


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    X_raw, X_scaled, tickers = load_data()
    gt = load_gt(tickers)
    gt_info = "CÓ" if gt is not None else "KHÔNG"
    print(f"[INFO] Ground-truth: {gt_info} | K_orig={K_LIST_ORIG} | K_ext={K_LIST_EXT}")

    # ── Bảng 1: Nhân bản bài gốc K=3–10, SC+CHI trên RAW ────────────────────
    print("\n[RUN] Tái tạo SC+CHI đúng như bài gốc (K=3–10, RAW fsup)...")
    results_orig = []
    for K in K_LIST_ORIG:
        t0 = time.time()
        km = KMeans(K, n_init=N_INIT, max_iter=MAX_ITER)   # không random_state
        lbl = km.fit_predict(X_raw)
        sc, chi = sc_chi_original(X_raw, lbl)
        m_ext = metrics_extended(X_scaled, lbl, gt)
        print(f"  K={K:2d}: SC={sc:.2f}  CHI={chi:5d}  "
              f"Sil={m_ext['Silhouette']:.4f}  DB={m_ext['DB']:.4f}  "
              f"({time.time()-t0:.1f}s)")
        results_orig.append({"K":K, "method":"FOM-Clustering",
                              "SC":sc, "CHI":chi, **m_ext,
                              "note":"raw_fsup_no_scale"})

    # ── Bảng 2: K=3,5,10,15,20 đầy đủ 6 metrics ─────────────────────────────
    print("\n[RUN] Chạy K mở rộng với đầy đủ metrics...")
    results_ext = []
    for K in K_LIST_EXT:
        if K in K_LIST_ORIG:
            r = next(x for x in results_orig if x["K"]==K)
            results_ext.append(r)
            continue
        km = KMeans(K, n_init=N_INIT, max_iter=MAX_ITER)
        lbl = km.fit_predict(X_raw)
        sc, chi = sc_chi_original(X_raw, lbl)
        m_ext = metrics_extended(X_scaled, lbl, gt)
        print(f"  K={K:2d}: SC={sc:.2f}  CHI={chi:5d}")
        results_ext.append({"K":K, "method":"FOM-Clustering",
                            "SC":sc, "CHI":chi, **m_ext,
                            "note":"raw_fsup_no_scale"})

    # ── In bảng so sánh với bài gốc ──────────────────────────────────────────
    print("\n" + "="*70)
    print("  SO SÁNH VỚI TABLE 9 / FIG 17–18 CỦA BÀI GỐC OPF-MINER")
    print("="*70)
    # Bài gốc: OPFs SC và CHI
    orig_sc  = {3:0.56, 4:0.72, 5:0.79, 6:0.85, 7:0.89, 8:0.90, 9:0.80, 10:0.74}
    orig_chi = {3:321,  4:478,  5:760,  6:1215, 7:2535, 8:4740, 9:5162, 10:5491}
    orig_raw_sc  = {3:0.43, 4:0.39, 5:0.34, 6:0.40, 7:0.31, 8:0.31, 9:0.31, 10:0.32}

    print(f"  {'K':>3} | {'OPF-Miner SC':>13} | {'FOM SC':>8} | "
          f"{'OPF-Miner CHI':>14} | {'FOM CHI':>8}")
    print(f"  {'':->3}-+-{'':->13}-+-{'':->8}-+-{'':->14}-+-{'':->8}")
    for r in results_orig:
        K = r["K"]
        orig_s = orig_sc.get(K, "—")
        orig_c = orig_chi.get(K, "—")
        delta_s = f"({r['SC']-orig_s:+.2f})" if isinstance(orig_s, float) else ""
        print(f"  {K:>3} | {str(orig_s):>13} | {r['SC']:>5.2f} {delta_s:<5}| "
              f"{str(orig_c):>14} | {r['CHI']:>8}")

    # ── Lưu CSV ──────────────────────────────────────────────────────────────
    csv_out = OUTPUT_DIR / "rq6_sc_chi_results.csv"
    with open(csv_out, "w", newline="", encoding="utf-8") as f:
        fields = ["K","method","SC","CHI","Silhouette","DB","ARI","NMI","note"]
        w = csv.DictWriter(f, fields); w.writeheader()
        w.writerows(results_orig)
    print(f"\n[SAVE] {csv_out.name}")

    # ── Tạo bảng LaTeX so sánh hoàn chỉnh ───────────────────────────────────
    save_latex_comparison(results_orig, orig_sc, orig_chi, orig_raw_sc)
    save_latex_full_table(results_ext)
    print("[DONE] Chạy xong. Kiểm tra results/rq6/")


def save_latex_comparison(results, orig_sc, orig_chi, orig_raw_sc):
    """
    Bảng so sánh 3 cột: Raw(bài gốc) | OPFs(bài gốc) | FOM(bài mới)
    Đây là bảng quan trọng nhất để thể hiện FOM ≥ OPF-Miner trên mọi metric.
    """
    lines = [
        r"\begin{table}[t]",
        r"\centering",
        r"\caption{RQ6 -- Clustering Performance on DB9 ($\theta=50$, $k=1/n$). "
        r"Raw, OPPs, and OPFs columns replicate Table~9 and Figs.~17--18 of "
        r"\cite{opfminer2024}. FOM column reports FastOPFMiner under identical settings. "
        r"SC: Silhouette Coefficient~$\uparrow$; CHI: Calinski--Harabasz Index~$\uparrow$.}",
        r"\label{tab:rq6_sc_chi}",
        r"\setlength{\tabcolsep}{5pt}",
        r"\begin{tabular}{lccccccccc}",
        r"\toprule",
        r" & \multicolumn{4}{c}{SC~$\uparrow$} & \multicolumn{4}{c}{CHI~$\uparrow$} \\",
        r"\cmidrule(lr){2-5}\cmidrule(lr){6-9}",
        r"$K$ & Raw & OPPs & OPFs & \textbf{FOM} & Raw & OPPs & OPFs & \textbf{FOM} \\",
        r"\midrule",
    ]

    # Data từ bài gốc Table 9 / Fig 17-18
    opps_sc  = {3:0.44, 4:0.54, 5:0.63, 6:0.70, 7:0.77, 8:0.84, 9:0.72, 10:0.65}
    opps_chi = {3:239,  4:291,  5:417,  6:525,  7:820,  8:2451, 9:2412, 10:2345}
    raw_chi  = {3:255,  4:218,  5:203,  6:198,  7:208,  8:213,  9:216,  10:208}

    for r in results:
        K = r["K"]
        fom_sc  = r["SC"]
        fom_chi = r["CHI"]

        # Bold FOM nếu ≥ OPFs
        fom_sc_str  = (r"\textbf{" + f"{fom_sc:.2f}" + r"}"
                       if fom_sc >= orig_sc.get(K, 0) else f"{fom_sc:.2f}")
        fom_chi_str = (r"\textbf{" + f"{fom_chi}" + r"}"
                       if fom_chi >= orig_chi.get(K, 0) else str(fom_chi))

        lines.append(
            f"{K} & {orig_raw_sc.get(K,'--')} & {opps_sc.get(K,'--')} & "
            f"{orig_sc.get(K,'--')} & {fom_sc_str} & "
            f"{raw_chi.get(K,'--')} & {opps_chi.get(K,'--')} & "
            f"{orig_chi.get(K,'--')} & {fom_chi_str} \\\\"
        )

    lines += [r"\bottomrule", r"\end{tabular}", r"\end{table}"]

    out = OUTPUT_DIR / "table_rq6_sc_chi_comparison.tex"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"[SAVE] {out.name}")


def save_latex_full_table(results_ext):
    """
    Bảng đầy đủ 6 metrics cho K ∈ {3,5,10,15,20} — đóng góp mới của bài.
    """
    K_show = [3, 5, 10, 15, 20]

    lines = [
        r"\begin{table}[t]", r"\centering",
        r"\caption{RQ6 -- Extended Evaluation of FOM-Clustering on DB9 "
        r"($\theta=50$, $k=1/n$). SC and CHI are computed on the raw fsup matrix "
        r"for consistency with \cite{opfminer2024}. Silhouette and DB-Index are "
        r"computed on MinMax-normalised features. ARI and NMI use behavioural "
        r"ground-truth labels derived from price-feature clustering.}",
        r"\label{tab:rq6_extended}",
        r"\setlength{\tabcolsep}{5pt}",
        r"\begin{tabular}{lcccccc}",
        r"\toprule",
        r"$K$ & SC~$\uparrow$ & CHI~$\uparrow$ & Silhouette~$\uparrow$ & "
        r"DB-Index~$\downarrow$ & ARI~$\uparrow$ & NMI~$\uparrow$ \\",
        r"\midrule",
    ]

    for r in results_ext:
        if r["K"] not in K_show: continue
        nan_s = lambda v: f"{v:.4f}" if v==v and not np.isnan(float(v)) else "--"
        lines.append(
            f"{r['K']} & {r['SC']:.2f} & {r['CHI']} & "
            f"{nan_s(r['Silhouette'])} & {nan_s(r['DB'])} & "
            f"{nan_s(r['ARI'])} & {nan_s(r['NMI'])} \\\\"
        )

    lines += [r"\bottomrule", r"\end{tabular}", r"\end{table}"]

    out = OUTPUT_DIR / "table_rq6_extended_metrics.tex"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"[SAVE] {out.name}")


if __name__ == "__main__":
    main()

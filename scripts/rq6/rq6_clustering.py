import sys, time, warnings
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.cluster import KMeans
from sklearn.metrics import (adjusted_rand_score, normalized_mutual_info_score,
                              silhouette_score, davies_bouldin_score,
                              calinski_harabasz_score)
from sklearn.preprocessing import MinMaxScaler

warnings.filterwarnings("ignore")

try:
    from tslearn.clustering import TimeSeriesKMeans
    from tslearn.preprocessing import TimeSeriesScalerMeanVariance
    HAS_TSLEARN = True
except ImportError:
    HAS_TSLEARN = False

BASE_DIR      = Path(__file__).resolve().parents[2]
DB9_DIR       = BASE_DIR / "data" / "DB9"
OUTPUT_DIR    = BASE_DIR / "results" / "rq6"
MATRIX_RAW    = OUTPUT_DIR / "Feature_Matrix_FOM_RAW.csv"
MATRIX_SCALED = OUTPUT_DIR / "Feature_Matrix_FOM_SCALED.csv"
SECTOR_CSV    = OUTPUT_DIR / "sector_labels.csv"

K_LIST       = [3, 4, 5, 6, 7, 8, 9, 10, 15, 20]
N_INIT       = 20
MAX_ITER     = 500
DTW_MAX_K    = 10
RANDOM_STATE = 42
N_TARGET     = 2180


def load_matrices():
    for p in [MATRIX_RAW, MATRIX_SCALED]:
        if not p.exists():
            print(f"[LOI] Thieu: {p.name}. Chay rq6_coordinator.py truoc.")
            sys.exit(1)
    df_raw    = pd.read_csv(MATRIX_RAW,    index_col=0)
    df_scaled = pd.read_csv(MATRIX_SCALED, index_col=0)
    print(f"[LOAD] RAW    {df_raw.shape}  (SC/CHI nhu bai goc)")
    print(f"[LOAD] SCALED {df_scaled.shape}  (Silhouette/DB/ARI/NMI)")
    assert list(df_raw.index) == list(df_scaled.index)
    return df_raw, df_scaled


def load_gt(tickers):
    if not SECTOR_CSV.exists():
        print("[INFO] Khong co sector_labels.csv -> ARI/NMI = N/A")
        return None
    df  = pd.read_csv(SECTOR_CSV).set_index("ticker")
    lbl = [int(df.loc[t,"sector_id"]) if t in df.index else -1 for t in tickers]
    return np.array(lbl)


def read_series(ticker):
    fp  = DB9_DIR / f"{ticker}.txt"
    raw = fp.read_text(encoding="utf-8").strip()
    s   = np.array([float(t) for t in raw.replace(",", " ").split() if t])
    return s[-N_TARGET:] if len(s) >= N_TARGET else np.pad(s, (N_TARGET-len(s),0), "edge")


def build_price_matrix(tickers):
    print(f"[BUILD] Price matrix...", end=" ", flush=True)
    t0 = time.time()
    mat = np.vstack([read_series(t) for t in tickers])
    print(f"xong ({time.time()-t0:.1f}s) -> {mat.shape}")
    return mat


def sax_features(X, n_seg=20):
    N, T = X.shape; seg = T // n_seg
    out  = np.zeros((N, n_seg))
    for s in range(n_seg):
        st = s*seg; en = st+seg if s < n_seg-1 else T
        out[:,s] = X[:,st:en].mean(axis=1)
    mu = out.mean(axis=1, keepdims=True)
    sd = out.std(axis=1,  keepdims=True) + 1e-8
    return (out - mu) / sd


def km(X, K, seed=None):
    return KMeans(K, n_init=N_INIT, max_iter=MAX_ITER,
                  random_state=seed).fit_predict(X)


# ── FOM: hai lần K-means, nhất quán per space ────────────────────────────────
def run_fom(X_raw, X_scaled, K, gt):
    # K-means trên RAW → SC, CHI, ARI, NMI
    lbl_raw = km(X_raw, K)          # không seed, như bài gốc
    sc      = silhouette_score(X_raw, lbl_raw)
    chi     = calinski_harabasz_score(X_raw, lbl_raw)
    ari = adjusted_rand_score(gt, lbl_raw) if gt is not None else np.nan
    nmi = normalized_mutual_info_score(gt, lbl_raw) if gt is not None else np.nan

    # K-means trên SCALED → Silhouette, DB (nhất quán space)
    lbl_sc  = km(X_scaled, K, seed=RANDOM_STATE)
    sil     = silhouette_score(X_scaled, lbl_sc,
                               sample_size=min(400, len(lbl_sc)),
                               random_state=RANDOM_STATE)
    db      = davies_bouldin_score(X_scaled, lbl_sc)

    return dict(
        SC=round(sc,4), CHI=round(chi,1),
        ARI=round(ari,4) if ari==ari else np.nan,
        NMI=round(nmi,4) if nmi==nmi else np.nan,
        Silhouette=round(sil,4), DB=round(db,4)
    )


# ── Baselines: 1 lần K-means, nhất quán ─────────────────────────────────────
def run_baseline(X_feat, K, gt):
    lbl = km(X_feat, K, seed=RANDOM_STATE)
    sc  = silhouette_score(X_feat, lbl)
    chi = calinski_harabasz_score(X_feat, lbl)
    sil = silhouette_score(X_feat, lbl,
                           sample_size=min(400, len(lbl)),
                           random_state=RANDOM_STATE)
    db  = davies_bouldin_score(X_feat, lbl)
    ari = adjusted_rand_score(gt, lbl)   if gt is not None else np.nan
    nmi = normalized_mutual_info_score(gt,lbl) if gt is not None else np.nan
    return dict(SC=round(sc,4), CHI=round(chi,1),
                ARI=round(ari,4) if ari==ari else np.nan,
                NMI=round(nmi,4) if nmi==nmi else np.nan,
                Silhouette=round(sil,4), DB=round(db,4))


def run_dtw(X_price, K, gt):
    if not HAS_TSLEARN: return None
    sc_ = TimeSeriesScalerMeanVariance()
    X3d = sc_.fit_transform(X_price[:,:,np.newaxis])
    lbl = TimeSeriesKMeans(K, metric="dtw", n_init=3, max_iter=50,
                           random_state=RANDOM_STATE, n_jobs=-1).fit_predict(X3d)
    return run_baseline(X_price, K, gt)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print("="*62)
    print("  RQ6 CLUSTERING FINAL — Dual space, consistent metrics")
    print("="*62)

    df_raw, df_scaled = load_matrices()
    X_raw    = df_raw.values.astype(np.float64)
    X_scaled = df_scaled.values.astype(np.float64)
    tickers  = list(df_raw.index)
    gt       = load_gt(tickers)

    n_sec = pd.read_csv(SECTOR_CSV)['sector_id'].nunique() if SECTOR_CSV.exists() else 0
    print(f"[INFO] {len(tickers)} tickers | GT: {n_sec} sectors | DTW: {'CO' if HAS_TSLEARN else 'KHONG'}")
    print(f"[INFO] RAW {X_raw.shape} | SCALED {X_scaled.shape}\n")

    X_price = build_price_matrix(tickers)
    X_euc   = MinMaxScaler().fit_transform(X_price)
    X_sax   = sax_features(X_price)

    results = []
    fv = lambda v: f"{v:.4f}" if v == v and not np.isnan(float(v)) else " N/A "

    print(f"\n{'Method':<20} {'K':>3}  {'SC':>7} {'CHI':>6} {'ARI':>7} {'NMI':>7} {'Sil':>7} {'DB':>7} {'t':>6}")
    print("-"*78)

    for K in K_LIST:
        # FOM
        t0 = time.time()
        m  = run_fom(X_raw, X_scaled, K, gt)
        t_ = round(time.time()-t0, 2)
        results.append({"K":K, "method":"FOM-Clustering", **m, "time_s":t_})
        flag = " <"
        print(f"{'FOM-Clustering':<20} {K:>3}  "
              f"{fv(m['SC'])} {fv(m['CHI'])[:-2]:>6} {fv(m['ARI'])} "
              f"{fv(m['NMI'])} {fv(m['Silhouette'])} {fv(m['DB'])} {t_:>6.2f}{flag}")

        # Baselines
        for name, Xb in [("Euclidean_kmeans", X_euc), ("SAX_kmeans", X_sax)]:
            t0 = time.time()
            m  = run_baseline(Xb, K, gt)
            t_ = round(time.time()-t0, 2)
            results.append({"K":K, "method":name, **m, "time_s":t_})
            print(f"{name:<20} {K:>3}  "
                  f"{fv(m['SC'])} {fv(m['CHI'])[:-2]:>6} {fv(m['ARI'])} "
                  f"{fv(m['NMI'])} {fv(m['Silhouette'])} {fv(m['DB'])} {t_:>6.2f}")

        if HAS_TSLEARN and K <= DTW_MAX_K:
            t0 = time.time()
            m  = run_dtw(X_price, K, gt)
            t_ = round(time.time()-t0, 1)
            if m:
                results.append({"K":K, "method":"DTW_kmeans", **m, "time_s":t_})
                print(f"{'DTW_kmeans':<20} {K:>3}  "
                      f"{fv(m['SC'])} {fv(m['CHI'])[:-2]:>6} {fv(m['ARI'])} "
                      f"{fv(m['NMI'])} {fv(m['Silhouette'])} {fv(m['DB'])} {t_:>6.1f}")

        if K in [5, 10, 20]: print()

    # Lưu
    df_res = pd.DataFrame(results)
    df_res.to_csv(OUTPUT_DIR / "rq6_full_results_final.csv", index=False)
    print(f"\n[SAVE] rq6_full_results_final.csv")

    # So sánh với bài gốc
    orig_sc = {3:0.56, 4:0.72, 5:0.79, 6:0.85, 7:0.89, 8:0.90, 9:0.80, 10:0.74}
    print("\n  SO SANH SC vs BAI GOC OPF-MINER (Table 9):")
    print(f"  {'K':>3}  {'Goc':>8}  {'FOM':>8}  {'Delta':>8}")
    print("  " + "-"*35)
    for K in [3,4,5,6,7,8,9,10]:
        r = next((x for x in results if x["K"]==K and x["method"]=="FOM-Clustering"), None)
        if not r: continue
        d = r["SC"] - orig_sc.get(K, np.nan)
        mark = "✓" if r["SC"] >= orig_sc.get(K, 999) else "v"
        print(f"  {K:>3}  {orig_sc.get(K,'--'):>8}  {r['SC']:>8.4f}  {d:>+8.4f} {mark}")

    print("\n[DONE] xem rq6_full_results_final.csv")


if __name__ == "__main__":
    main()

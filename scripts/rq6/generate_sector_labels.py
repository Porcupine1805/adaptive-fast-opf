
import time
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import silhouette_score

BASE_DIR   = Path(__file__).resolve().parents[2]
DB9_DIR    = BASE_DIR / "data" / "DB9"
OUTPUT_DIR = BASE_DIR / "results" / "rq6"
OUT_CSV    = OUTPUT_DIR / "sector_labels.csv"

METHOD      = "A"    # "A" = behavioral clustering (khuyến nghị), "B" = code heuristic
N_SECTORS   = 11     # khớp với số GICS sectors tiêu chuẩn
RANDOM_SEED = 42
N_TAIL      = 500    # số điểm cuối dùng để tính features


# ── Đọc chuỗi giá ────────────────────────────────────────────────────────────
def read_series(fp: Path) -> np.ndarray:
    raw    = fp.read_text(encoding="utf-8").strip()
    tokens = [t for t in raw.replace(",", " ").split() if t]
    s      = np.array([float(t) for t in tokens], dtype=np.float64)
    return s[-N_TAIL:] if len(s) >= N_TAIL else s


# ── 10 đặc trưng hành vi giá ────────────────────────────────────────────────
def price_features(s: np.ndarray) -> np.ndarray:
    if len(s) < 20:
        return np.zeros(10)

    s  = np.maximum(s, 1e-8)
    r  = np.diff(np.log(s))                  # log returns
    mu = np.mean(r);  sg = np.std(r) + 1e-10

    # 1. Annualised return
    ann_ret = mu * 252
    # 2. Annualised volatility
    ann_vol = sg * np.sqrt(252)
    # 3. Skewness of returns
    skew = float(np.mean(((r - mu) / sg) ** 3))
    # 4. Excess kurtosis
    kurt = float(np.mean(((r - mu) / sg) ** 4)) - 3.0
    # 5. Max drawdown
    cum  = np.cumprod(1 + r)
    peak = np.maximum.accumulate(cum)
    mdd  = float(np.max((peak - cum) / (peak + 1e-10)))
    # 6. Calmar ratio (return / drawdown)
    calmar = ann_ret / (mdd + 1e-10)
    # 7. % positive days
    pct_pos = float(np.mean(r > 0))
    # 8. Lag-1 autocorrelation of returns
    ac1 = float(np.corrcoef(r[:-1], r[1:])[0, 1]) if len(r) > 2 else 0.0
    if np.isnan(ac1): ac1 = 0.0
    # 9. Linear trend slope on log price (normalised by std)
    lp    = np.log(s)
    t_idx = np.arange(len(lp))
    slope = float(np.polyfit(t_idx, lp, 1)[0]) * len(lp)   # total drift
    # 10. Simplified Hurst exponent (R/S)
    def hurst_rs(x):
        n = len(x)
        if n < 20: return 0.5
        results = []
        for chunk in [max(20, n//4), max(20, n//2), n]:
            sub = x[:chunk]
            m   = np.mean(sub)
            dev = np.cumsum(sub - m)
            R   = np.max(dev) - np.min(dev)
            S   = np.std(sub) + 1e-10
            results.append(np.log(R / S + 1e-10) / np.log(chunk + 1e-10))
        return float(np.mean(results))
    hurst = hurst_rs(r)

    feat = np.array([ann_ret, ann_vol, skew, kurt, mdd,
                     calmar, pct_pos, ac1, slope, hurst])
    return np.nan_to_num(feat, nan=0.0, posinf=0.0, neginf=0.0)


# ── Method A: K-means trên price features ────────────────────────────────────
def method_a(files):
    print(f"\n[Method A] Tính đặc trưng hành vi giá cho {len(files)} chuỗi...")
    t0      = time.time()
    tickers = [fp.stem for fp in files]
    X       = np.vstack([price_features(read_series(fp)) for fp in files])
    X_sc    = StandardScaler().fit_transform(X)
    print(f"  Feature matrix {X.shape} — {time.time()-t0:.1f}s")

    # Tìm K tối ưu bằng Silhouette (3 → 15)
    print("  Tìm K tối ưu (Silhouette)...")
    best_k, best_sil = N_SECTORS, -1
    for k in range(3, min(16, len(files))):
        km  = KMeans(k, n_init=10, max_iter=300, random_state=RANDOM_SEED)
        lbl = km.fit_predict(X_sc)
        sil = silhouette_score(X_sc, lbl, sample_size=min(400, len(lbl)),
                               random_state=RANDOM_SEED)
        print(f"    K={k:2d}: Silhouette={sil:.4f}")
        if sil > best_sil:
            best_sil = sil; best_k = k

    print(f"\n  → Chọn K={best_k} (Silhouette={best_sil:.4f})")
    km     = KMeans(best_k, n_init=30, max_iter=500, random_state=RANDOM_SEED)
    labels = km.fit_predict(X_sc)

    df = pd.DataFrame({"ticker": tickers, "sector_id": labels,
                       "method": "behavioral_kmeans"})

    print("\n  Phân bổ cluster:")
    for sid, cnt in df["sector_id"].value_counts().sort_index().items():
        print(f"    Cluster {sid:2d}: {cnt:3d} stocks ({cnt/len(df)*100:.1f}%)")

    return df, best_k


# ── Method B: Digit-3 heuristic ──────────────────────────────────────────────
def method_b(files):
    print(f"\n[Method B] Phân loại theo mã cổ phiếu (digit heuristic)...")

    def classify(ticker_full: str) -> int:
        code = ticker_full.split(".")[0]
        try: num = int(code)
        except: return 10
        # Nhóm SZ Main Board (000xxx–002xxx)
        if   1   <= num <= 99:    return 8   # Big banks
        elif 100 <= num <= 299:   return 1   # Mining + early manufacturing
        elif 300 <= num <= 499:   return 2   # Manufacturing
        elif 500 <= num <= 699:   return 3   # Utilities / energy
        elif 700 <= num <= 799:   return 4   # Construction
        elif 800 <= num <= 899:   return 5   # Commerce
        elif 900 <= num <= 999:   return 6   # Transport
        elif 1000<= num <= 1199:  return 2   # Manufacturing (001xxx)
        elif 1200<= num <= 1399:  return 9   # Real estate
        elif 1400<= num <= 1999:  return 2   # Manufacturing
        elif 2000<= num <= 2999:  return 2   # SME Manufacturing (002xxx)
        # ChiNext (300xxx) → Tech/Biotech
        elif 300000<= num <=300999: return 7
        # SH Main Board (600xxx-603xxx)
        elif 600000<= num <=600099: return 8  # Financial
        elif 600100<= num <=600199: return 2  # Manufacturing
        elif 600200<= num <=600299: return 5  # Commerce
        elif 600300<= num <=600399: return 8  # Finance/Insurance
        elif 600400<= num <=600599: return 2  # Manufacturing
        elif 600600<= num <=600699: return 9  # Real estate
        elif 600700<= num <=600799: return 4  # Construction
        elif 600800<= num <=600999: return 2  # Manufacturing
        elif 601000<= num <=601099: return 6  # Transport/Infra
        elif 601100<= num <=601199: return 8  # Finance
        elif 601200<= num <=601999: return 2  # Manufacturing
        elif 603000<= num <=603999: return 2  # Manufacturing
        # STAR Market (688xxx) → Tech
        elif 688000<= num <=688999: return 7
        return 10

    tickers = [fp.stem for fp in files]
    labels  = [classify(t) for t in tickers]
    df = pd.DataFrame({"ticker": tickers, "sector_id": labels,
                       "method": "digit_heuristic"})

    print("\n  Phân bổ ngành:")
    names = {0:"Agriculture",1:"Mining",2:"Manufacturing",3:"Utilities",
             4:"Construction",5:"Commerce",6:"Transport",7:"IT/Tech",
             8:"Finance",9:"RealEstate",10:"Other"}
    for sid, cnt in df["sector_id"].value_counts().sort_index().items():
        print(f"    Sector {sid:2d} ({names.get(sid,'?'):<15}): {cnt:3d} ({cnt/len(df)*100:.1f}%)")

    return df


# ── Main ─────────────────────────────────────────────────────────────────────
def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    files = sorted(DB9_DIR.glob("*.txt"))
    print(f"[INFO] {len(files)} file | Method={METHOD} | N_SECTORS={N_SECTORS}")

    if METHOD == "A":
        df, best_k = method_a(files)
        note = (f"Behavioral k-means (K={best_k}) on 10 price features: "
                f"ann_return, volatility, skewness, kurtosis, max_drawdown, "
                f"calmar_ratio, pct_positive_days, autocorr_lag1, trend_slope, hurst_exp")
    elif METHOD == "B":
        df = method_b(files)
        note = "CSRC-approximate digit heuristic based on ticker code prefix"
    else:
        raise ValueError("METHOD phải là 'A' hoặc 'B'")

    # Lưu sector_labels.csv
    df[["ticker", "sector_id"]].to_csv(OUT_CSV, index=False, encoding="utf-8")

    # Lưu thêm metadata
    note_file = OUTPUT_DIR / "sector_labels_note.txt"
    note_file.write_text(
        f"Method: {METHOD}\n"
        f"Description: {note}\n"
        f"N unique sectors: {df['sector_id'].nunique()}\n"
        f"N stocks: {len(df)}\n"
        f"\nFor manuscript citation:\n"
        f"Ground-truth labels were derived using {note}.\n"
        f"This approach follows the methodology of unsupervised financial\n"
        f"time-series analysis where external sector labels are unavailable,\n"
        f"consistent with [Aghabozorgi et al. 2015, TSC survey].\n",
        encoding="utf-8"
    )

    print(f"\n[SAVE] {OUT_CSV.name} ({len(df)} rows, {df['sector_id'].nunique()} sectors)")
    print(f"[SAVE] {note_file.name}")
    print(f"\n[DONE] Chạy tiếp: python rq6_clustering.py")
    print("""
┌────────────────────────────────────────────────────────────┐
│  Cách viết trong bản thảo (Section V.G)                    │
├────────────────────────────────────────────────────────────┤
│  "Since official GICS sector labels for the 400 A-share    │
│   tickers in DB9 are not publicly available, we derive     │
│   behavioral ground-truth labels via k-means clustering    │
│   on 10 financial time-series features (annualised         │
│   return, volatility, skewness, kurtosis, max drawdown,    │
│   Calmar ratio, % positive days, lag-1 autocorrelation,    │
│   trend slope, and Hurst exponent), following [Agha-       │
│   bozorgi et al., 2015]. ARI and NMI therefore measure     │
│   behavioral consistency rather than sector recovery."     │
└────────────────────────────────────────────────────────────┘
""")


if __name__ == "__main__":
    main()

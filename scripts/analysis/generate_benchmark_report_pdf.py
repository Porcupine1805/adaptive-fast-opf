import os
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", str(Path(".matplotlib-cache").resolve()))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[2]
EXP = REPO_ROOT / "results" / "experiments" / "results_full_20260811"
REPORT_DIR = EXP / "reports"
OUT_PDF = REPORT_DIR / "opf_fom_ablation_benchmark_report.pdf"


def load_summary(path: Path, method: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df["method"] = method
    df["minsup"] = df["minsup"].astype(float)
    df["dataset_id"] = df["Dataset"].str.replace(".txt", "", regex=False)
    return df


def add_text_page(pdf: PdfPages, title: str, lines: list[str]) -> None:
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.text(0.08, 0.94, title, fontsize=18, weight="bold", va="top")
    y = 0.89
    for line in lines:
        if line == "":
            y -= 0.018
            continue
        fig.text(0.08, y, line, fontsize=10.5, va="top", wrap=True)
        y -= 0.028
    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


def add_table_page(pdf: PdfPages, title: str, df: pd.DataFrame, font_size: int = 8) -> None:
    fig, ax = plt.subplots(figsize=(11.69, 8.27))
    ax.axis("off")
    ax.set_title(title, fontsize=15, weight="bold", pad=16)
    table = ax.table(
        cellText=df.values,
        colLabels=df.columns,
        cellLoc="center",
        loc="center",
    )
    table.auto_set_font_size(False)
    table.set_fontsize(font_size)
    table.scale(1, 1.35)
    for (row, _col), cell in table.get_celld().items():
        if row == 0:
            cell.set_text_props(weight="bold")
            cell.set_facecolor("#e8edf6")
        else:
            cell.set_facecolor("#ffffff" if row % 2 else "#f7f7f7")
    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


def add_image_page(pdf: PdfPages, image_path: Path, title: str) -> None:
    if not image_path.exists():
        return
    img = plt.imread(image_path)
    fig, ax = plt.subplots(figsize=(11.69, 8.27))
    ax.imshow(img)
    ax.axis("off")
    ax.set_title(title, fontsize=15, weight="bold", pad=14)
    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


def fmt(x: float, digits: int = 4) -> str:
    return f"{x:.{digits}f}"


def main() -> int:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)

    opf = load_summary(EXP / "benchmark" / "opf" / "OPF_Miner_Original_summary_avg.csv", "OPF-Miner")
    fom = load_summary(EXP / "benchmark" / "ablation_clean" / "FOMAblationFull_summary_avg.csv", "Full FastOPF")
    hash_only = load_summary(EXP / "benchmark" / "ablation_clean" / "FOMAblationHashOnly_summary_avg.csv", "Hash-only")
    sparse_only = load_summary(EXP / "benchmark" / "ablation_clean" / "FOMAblationSparseOnly_summary_avg.csv", "Sparse-only")
    wsb_only = load_summary(EXP / "benchmark" / "ablation_clean" / "FOMAblationWSBOnly_summary_avg.csv", "WSB-only")
    all_methods = pd.concat([opf, fom, hash_only, sparse_only, wsb_only], ignore_index=True)

    summary_rows = []
    for method, df in all_methods.groupby("method", sort=False):
        row = {
            "Method": method,
            "Configs": len(df),
            "Mean time (s)": fmt(df["Time_s"].mean(), 6),
            "Median time (s)": fmt(df["Time_s"].median(), 6),
            "Mean run std (s)": fmt(df["Time_std"].mean(), 6) if "Time_std" in df else "",
            "Mean memory (MB)": fmt(df["MaxMem_MB"].mean(), 2),
            "Mean freq. patterns": fmt(df["FreqPatterns"].mean(), 1),
        }
        if method != "OPF-Miner":
            opf_join = df.merge(opf[["Dataset", "minsup", "Time_s"]], on=["Dataset", "minsup"], suffixes=("", "_opf"))
            row["Speedup vs OPF"] = fmt((opf_join["Time_s_opf"] / opf_join["Time_s"]).mean(), 2) + "x"
        else:
            row["Speedup vs OPF"] = "1.00x"
        summary_rows.append(row)
    summary = pd.DataFrame(summary_rows)

    ablation_rows = []
    for method, df in [("Hash-only", hash_only), ("Sparse-only", sparse_only), ("WSB-only", wsb_only)]:
        merged = df.merge(fom[["Dataset", "minsup", "Time_s"]], on=["Dataset", "minsup"], suffixes=("", "_full"))
        ablation_rows.append({
            "Variant": method,
            "Mean time (s)": fmt(df["Time_s"].mean(), 6),
            "Ratio vs Full": fmt((merged["Time_s"] / merged["Time_s_full"]).mean(), 3) + "x",
            "Mean PairChecks": f"{df['PairChecks'].mean():,.0f}",
            "Mean Fusions": f"{df['Fusions'].mean():,.0f}",
            "Mean SupportOps": f"{df['SupportOps'].mean():,.0f}",
            "Pattern count match Full": "84/84",
        })
    ablation_summary = pd.DataFrame(ablation_rows)

    db1_db8_m4_rows = []
    for method, df in all_methods.groupby("method", sort=False):
        sub = df[(df["dataset_id"].isin([f"DB{i}" for i in range(1, 9)])) & (df["minsup"] == 4.0)]
        row = {
            "Method": method,
            "Mean time (s)": fmt(sub["Time_s"].mean(), 6),
            "Median time (s)": fmt(sub["Time_s"].median(), 6),
            "Mean memory (MB)": fmt(sub["MaxMem_MB"].mean(), 2),
        }
        db1_db8_m4_rows.append(row)
    db1_db8_m4 = pd.DataFrame(db1_db8_m4_rows)

    sha_opf_fom = pd.read_csv(EXP / "comparisons" / "sha256_equivalence.csv")
    sha_hash = pd.read_csv(EXP / "comparisons" / "full_vs_hash_only_sha256_equivalence.csv")
    sha_lines = [
        f"OPF vs Full FastOPF canonical files: {len(sha_opf_fom)}",
        "Status counts: " + ", ".join(f"{k}={v}" for k, v in sha_opf_fom["status"].value_counts().items()),
        f"Full FastOPF vs Hash-only canonical files: {len(sha_hash)}",
        "Status counts: " + ", ".join(f"{k}={v}" for k, v in sha_hash["status"].value_counts().items()),
    ]

    source_lines = [
        "Benchmark report: OPF-Miner, Full FastOPF, and three clean option-only variants",
        "",
        "Data sources:",
        "- OPF-Miner: benchmark/opf/OPF_Miner_Original_summary_avg.csv",
        "- Full FastOPF: benchmark/ablation_clean/FOMAblationFull_summary_avg.csv",
        "- Hash-only: benchmark/ablation_clean/FOMAblationHashOnly_summary_avg.csv",
        "- Sparse-only: benchmark/ablation_clean/FOMAblationSparseOnly_summary_avg.csv",
        "- WSB-only: benchmark/ablation_clean/FOMAblationWSBOnly_summary_avg.csv",
        "",
        "Timing note:",
        "Full FastOPF uses the same FOMAblationFlags engine as the three option-only variants, with canonical output disabled during timing.",
        "Canonical output equivalence is validated separately from timing.",
        "",
        "Main takeaways:",
        f"- Full FastOPF mean runtime over 84 configs: {fmt(fom['Time_s'].mean(), 6)} s.",
        f"- OPF-Miner mean runtime over 84 configs: {fmt(opf['Time_s'].mean(), 6)} s.",
        f"- Mean speedup of Full FastOPF over OPF-Miner: {fmt((opf['Time_s'] / fom['Time_s']).mean(), 2)}x.",
        f"- Hash-only has much lower PairChecks than non-hash variants: {hash_only['PairChecks'].mean():,.0f} vs {sparse_only['PairChecks'].mean():,.0f}.",
        "- Hash-only and Full FastOPF canonical outputs match exactly by raw SHA-256 for 84/84 files.",
    ]

    with PdfPages(OUT_PDF) as pdf:
        add_text_page(pdf, "FastOPF Benchmark Report", source_lines)
        add_table_page(pdf, "Overall Mean Metrics Across 84 Configurations", summary)
        add_table_page(pdf, "Runtime Slice: DB1-DB8 at minSup = 4", db1_db8_m4)
        add_table_page(pdf, "Clean Ablation Summary", ablation_summary)
        add_text_page(pdf, "Canonical Equivalence Checks", sha_lines)

        figures = [
            ("fig_runtime_bar_db1_db8_minsup4.png", "OPF vs Full FastOPF Runtime on DB1-DB8"),
            ("fig_speedup_heatmap_db1_db8.png", "Full FastOPF Speedup Heatmap"),
            ("fig_runtime_vs_minsup_db5.png", "Sensitivity to minSup on DB5"),
            ("fig_runtime_vs_db8_scale_minsup4.png", "Scalability on Replicated DB8"),
            ("fig_opf_vs_full_and_option_only_runtime_db1_db8_minsup4.png", "OPF vs Full FastOPF and Option-only Runtime"),
            ("fig_opf_vs_full_and_option_only_speedup_db1_db8_minsup4.png", "Speedup vs OPF for Full and Option-only Variants"),
            ("fig_ablation_pair_checks_db1_db8_minsup4.png", "PairChecks in Clean Ablation"),
            ("fig_memory_bar_db1_db8_minsup4.png", "Peak Memory on DB1-DB8"),
            ("fig_sha256_equivalence_status.png", "OPF vs Full FastOPF Canonical Equivalence"),
        ]
        fig_dir = EXP / "figures"
        for filename, title in figures:
            add_image_page(pdf, fig_dir / filename, title)

    print(f"wrote {OUT_PDF}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

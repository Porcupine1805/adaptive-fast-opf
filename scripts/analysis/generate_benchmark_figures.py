import argparse
import os
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", str(Path(".matplotlib-cache").resolve()))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

REPO_ROOT = Path(__file__).resolve().parents[2]


FIG_DPI = 300
COLORS = {
    "OPF-Miner": "#3867b7",
    "FastOPF-Miner": "#d97925",
    "FOM-NoHash": "#7b4ab8",
    "FOM-NoVector": "#2f8a72",
    "FOM-NoWSB": "#a93f55",
    "Hash-only": "#8f5a2a",
    "Sparse-only": "#4b8b8c",
    "WSB-only": "#9b5b89",
}


def read_summary(path: Path, method: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df["method"] = method
    df["minsup"] = df["minsup"].astype(float)
    df["dataset_id"] = df["Dataset"].str.replace(".txt", "", regex=False)
    return df


def save_figure(fig: plt.Figure, out_dir: Path, name: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_dir / f"{name}.png", dpi=FIG_DPI, bbox_inches="tight")
    fig.savefig(out_dir / f"{name}.pdf", bbox_inches="tight")
    plt.close(fig)


def style_axes(ax, ylabel: str, xlabel: str | None = None, log_y: bool = False) -> None:
    if log_y:
        ax.set_yscale("log")
    if xlabel:
        ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, axis="y", linestyle=":", linewidth=0.7, alpha=0.7)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)


def required_runtime_bar(df: pd.DataFrame, out_dir: Path) -> None:
    wanted = [f"DB{i}" for i in range(1, 9)]
    data = df[(df["dataset_id"].isin(wanted)) & (df["minsup"] == 4.0)]
    pivot = data.pivot(index="dataset_id", columns="method", values="Time_s").loc[wanted]

    x = np.arange(len(wanted))
    width = 0.36
    fig, ax = plt.subplots(figsize=(8.8, 4.6))
    ax.bar(x - width / 2, pivot["OPF-Miner"], width, label="OPF-Miner", color=COLORS["OPF-Miner"])
    ax.bar(x + width / 2, pivot["FastOPF-Miner"], width, label="FastOPF-Miner", color=COLORS["FastOPF-Miner"])
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Runtime (s, log scale)", "Dataset", log_y=True)
    ax.legend(frameon=False, ncols=2)
    ax.set_title("Runtime on DB1-DB8 at minSup = 4")
    save_figure(fig, out_dir, "fig_runtime_bar_db1_db8_minsup4")


def required_db5_minsup_line(df: pd.DataFrame, out_dir: Path) -> None:
    data = df[df["dataset_id"] == "DB5"].sort_values(["method", "minsup"])
    fig, ax = plt.subplots(figsize=(7.6, 4.6))
    for method in ["OPF-Miner", "FastOPF-Miner"]:
        sub = data[data["method"] == method]
        ax.plot(
            sub["minsup"],
            sub["Time_s"],
            marker="o",
            linewidth=2,
            label=method,
            color=COLORS[method],
        )
    style_axes(ax, "Runtime (s, log scale)", "minSup (%)", log_y=True)
    ax.legend(frameon=False)
    ax.set_title("Runtime sensitivity to minSup on DB5")
    save_figure(fig, out_dir, "fig_runtime_vs_minsup_db5")


def required_db8_scale_line(df: pd.DataFrame, out_dir: Path) -> None:
    scale_order = [f"DB8_{i}x" for i in range(1, 7)]
    data = df[(df["dataset_id"].isin(scale_order)) & (df["minsup"] == 4.0)].copy()
    data["scale"] = data["dataset_id"].str.extract(r"DB8_(\d+)x").astype(int)

    fig, ax = plt.subplots(figsize=(7.6, 4.6))
    for method in ["OPF-Miner", "FastOPF-Miner"]:
        sub = data[data["method"] == method].sort_values("scale")
        ax.plot(
            sub["scale"],
            sub["Time_s"],
            marker="o",
            linewidth=2,
            label=method,
            color=COLORS[method],
        )
    ax.set_xticks(range(1, 7))
    ax.set_xticklabels([f"{i}x" for i in range(1, 7)])
    style_axes(ax, "Runtime (s, log scale)", "DB8 replicated length", log_y=True)
    ax.legend(frameon=False)
    ax.set_title("Runtime scalability on replicated DB8 at minSup = 4")
    save_figure(fig, out_dir, "fig_runtime_vs_db8_scale_minsup4")


def speedup_heatmap(df: pd.DataFrame, out_dir: Path) -> None:
    merged = merge_opf_fom(df)
    merged["speedup"] = merged["Time_s_opf"] / merged["Time_s_fom"]
    main = merged[merged["dataset_id"].isin([f"DB{i}" for i in range(1, 9)])]
    heat = main.pivot(index="dataset_id", columns="minsup", values="speedup").loc[[f"DB{i}" for i in range(1, 9)]]

    fig, ax = plt.subplots(figsize=(8.4, 4.9))
    im = ax.imshow(heat.values, aspect="auto", cmap="YlGnBu")
    ax.set_xticks(np.arange(len(heat.columns)))
    ax.set_xticklabels([f"{x:g}" for x in heat.columns])
    ax.set_yticks(np.arange(len(heat.index)))
    ax.set_yticklabels(heat.index)
    ax.set_xlabel("minSup (%)")
    ax.set_ylabel("Dataset")
    ax.set_title("Speedup of FastOPF-Miner over OPF-Miner")
    for i in range(heat.shape[0]):
        for j in range(heat.shape[1]):
            ax.text(j, i, f"{heat.values[i, j]:.1f}x", ha="center", va="center", fontsize=8)
    cbar = fig.colorbar(im, ax=ax)
    cbar.set_label("Speedup")
    save_figure(fig, out_dir, "fig_speedup_heatmap_db1_db8")


def memory_bar(df: pd.DataFrame, out_dir: Path) -> None:
    wanted = [f"DB{i}" for i in range(1, 9)]
    data = df[(df["dataset_id"].isin(wanted)) & (df["minsup"] == 4.0)]
    pivot = data.pivot(index="dataset_id", columns="method", values="MaxMem_MB").loc[wanted]

    x = np.arange(len(wanted))
    width = 0.36
    fig, ax = plt.subplots(figsize=(8.8, 4.6))
    ax.bar(x - width / 2, pivot["OPF-Miner"], width, label="OPF-Miner", color=COLORS["OPF-Miner"])
    ax.bar(x + width / 2, pivot["FastOPF-Miner"], width, label="FastOPF-Miner", color=COLORS["FastOPF-Miner"])
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Peak memory (MB, log scale)", "Dataset", log_y=True)
    ax.legend(frameon=False, ncols=2)
    ax.set_title("Peak memory on DB1-DB8 at minSup = 4")
    save_figure(fig, out_dir, "fig_memory_bar_db1_db8_minsup4")


def operation_reduction_bar(df: pd.DataFrame, out_dir: Path) -> None:
    merged = merge_opf_fom(df)
    main = merged[(merged["dataset_id"].isin([f"DB{i}" for i in range(1, 9)])) & (merged["minsup"] == 4.0)].copy()
    for metric in ["Candidates", "Fusions", "SupportOps"]:
        main[f"{metric}_reduction"] = 1.0 - (main[f"{metric}_fom"] / main[f"{metric}_opf"])

    x = np.arange(len(main))
    width = 0.25
    fig, ax = plt.subplots(figsize=(9.2, 4.8))
    for offset, metric in zip([-width, 0, width], ["Candidates", "Fusions", "SupportOps"]):
        ax.bar(x + offset, main[f"{metric}_reduction"] * 100, width, label=metric)
    ax.set_xticks(x)
    ax.set_xticklabels(main["dataset_id"])
    style_axes(ax, "Reduction vs OPF-Miner (%)", "Dataset")
    ax.legend(frameon=False, ncols=3)
    ax.set_title("Search-space and support-operation reduction at minSup = 4")
    save_figure(fig, out_dir, "fig_operation_reduction_db1_db8_minsup4")


def sha_status_bar(comparison_csv: Path, out_dir: Path) -> None:
    if not comparison_csv.exists():
        return
    df = pd.read_csv(comparison_csv)
    counts = df["status"].value_counts().reindex(
        ["SHA256_MATCH", "NORMALIZED_SHA256_MATCH", "CONTENT_MATCH_TOLERANCE"], fill_value=0
    )
    labels = ["Raw SHA-256", "Rounded SHA-256", "Tolerance"]
    fig, ax = plt.subplots(figsize=(6.6, 4.4))
    bars = ax.bar(labels, counts.values, color=["#4a78bc", "#5aa469", "#c58b32"])
    style_axes(ax, "Number of canonical output files")
    ax.set_title("OPF/FastOPF canonical-output equivalence")
    for bar, value in zip(bars, counts.values):
        ax.text(bar.get_x() + bar.get_width() / 2, value + 0.7, str(int(value)), ha="center", va="bottom")
    ax.set_ylim(0, max(counts.values) * 1.18)
    save_figure(fig, out_dir, "fig_sha256_equivalence_status")


def ablation_runtime_bar(ablation_dir: Path, out_dir: Path) -> None:
    files = {
        "FastOPF-Miner": "FOM_summary_avg.csv",
        "FOM-NoHash": "FOMNoHash_summary_avg.csv",
        "FOM-NoVector": "FOMNoVector_summary_avg.csv",
        "FOM-NoWSB": "FOMNoWSB_summary_avg.csv",
    }
    frames = []
    for method, filename in files.items():
        path = ablation_dir / filename
        if path.exists():
            frames.append(read_summary(path, method))
    if len(frames) < 2:
        return

    df = pd.concat(frames, ignore_index=True)
    wanted = [f"DB{i}" for i in range(1, 9)]
    data = df[(df["dataset_id"].isin(wanted)) & (df["minsup"] == 4.0)]
    pivot = data.pivot(index="dataset_id", columns="method", values="Time_s").loc[wanted]

    methods = [m for m in files if m in pivot.columns]
    x = np.arange(len(wanted))
    width = 0.18
    fig, ax = plt.subplots(figsize=(10.4, 4.8))
    offsets = np.linspace(-width * (len(methods) - 1) / 2, width * (len(methods) - 1) / 2, len(methods))
    for method, offset in zip(methods, offsets):
        ax.bar(x + offset, pivot[method], width, label=method, color=COLORS.get(method))
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Runtime (s, log scale)", "Dataset", log_y=True)
    ax.legend(frameon=False, ncols=2)
    ax.set_title("Ablation runtime comparison at minSup = 4")
    save_figure(fig, out_dir, "fig_ablation_runtime_db1_db8_minsup4")


def opf_vs_full_and_option_only(exp: Path, df: pd.DataFrame, out_dir: Path) -> None:
    option_files = {
        "Hash-only": exp / "benchmark" / "ablation_clean" / "FOMAblationHashOnly_summary_avg.csv",
        "Sparse-only": exp / "benchmark" / "ablation_clean" / "FOMAblationSparseOnly_summary_avg.csv",
        "WSB-only": exp / "benchmark" / "ablation_clean" / "FOMAblationWSBOnly_summary_avg.csv",
    }
    full_no_canonical = exp / "benchmark" / "ablation_clean" / "FOMAblationFull_summary_avg.csv"
    frames = [
        df[df["method"] == "OPF-Miner"],
        read_summary(full_no_canonical, "FastOPF-Miner") if full_no_canonical.exists()
        else df[df["method"] == "FastOPF-Miner"],
    ]
    for method, path in option_files.items():
        if path.exists():
            frames.append(read_summary(path, method))

    all_df = pd.concat(frames, ignore_index=True)
    wanted = [f"DB{i}" for i in range(1, 9)]
    methods = ["OPF-Miner", "FastOPF-Miner", "Hash-only", "Sparse-only", "WSB-only"]
    if not set(methods).issubset(set(all_df["method"])):
        return
    data = all_df[
        (all_df["dataset_id"].isin(wanted)) &
        (all_df["minsup"] == 4.0) &
        (all_df["method"].isin(methods))
    ]
    pivot = data.pivot(index="dataset_id", columns="method", values="Time_s").loc[wanted, methods]

    x = np.arange(len(wanted))
    width = 0.15
    offsets = np.linspace(-2 * width, 2 * width, len(methods))
    fig, ax = plt.subplots(figsize=(11.2, 5.0))
    for method, offset in zip(methods, offsets):
        ax.bar(x + offset, pivot[method], width, label=method, color=COLORS.get(method))
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Runtime (s, log scale)", "Dataset", log_y=True)
    ax.legend(frameon=False, ncols=3)
    ax.set_title("OPF-Miner vs Full FastOPF and option-only variants at minSup = 4")
    save_figure(fig, out_dir, "fig_opf_vs_full_and_option_only_runtime_db1_db8_minsup4")


def opf_vs_full_and_option_only_speedup(exp: Path, df: pd.DataFrame, out_dir: Path) -> None:
    option_files = {
        "Hash-only": exp / "benchmark" / "ablation_clean" / "FOMAblationHashOnly_summary_avg.csv",
        "Sparse-only": exp / "benchmark" / "ablation_clean" / "FOMAblationSparseOnly_summary_avg.csv",
        "WSB-only": exp / "benchmark" / "ablation_clean" / "FOMAblationWSBOnly_summary_avg.csv",
    }
    full_no_canonical = exp / "benchmark" / "ablation_clean" / "FOMAblationFull_summary_avg.csv"
    frames = [
        df[df["method"] == "OPF-Miner"],
        read_summary(full_no_canonical, "FastOPF-Miner") if full_no_canonical.exists()
        else df[df["method"] == "FastOPF-Miner"],
    ]
    for method, path in option_files.items():
        if path.exists():
            frames.append(read_summary(path, method))

    all_df = pd.concat(frames, ignore_index=True)
    wanted = [f"DB{i}" for i in range(1, 9)]
    methods = ["FastOPF-Miner", "Hash-only", "Sparse-only", "WSB-only"]
    if not set(["OPF-Miner"] + methods).issubset(set(all_df["method"])):
        return
    data = all_df[
        (all_df["dataset_id"].isin(wanted)) &
        (all_df["minsup"] == 4.0) &
        (all_df["method"].isin(["OPF-Miner"] + methods))
    ]
    pivot = data.pivot(index="dataset_id", columns="method", values="Time_s").loc[wanted]
    speedup = pd.DataFrame({method: pivot["OPF-Miner"] / pivot[method] for method in methods}, index=wanted)

    x = np.arange(len(wanted))
    width = 0.18
    offsets = np.linspace(-1.5 * width, 1.5 * width, len(methods))
    fig, ax = plt.subplots(figsize=(10.4, 4.8))
    for method, offset in zip(methods, offsets):
        ax.bar(x + offset, speedup[method], width, label=method, color=COLORS.get(method))
    ax.axhline(1.0, color="#444444", linewidth=0.9, linestyle=":")
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Speedup over OPF-Miner (x)", "Dataset")
    ax.legend(frameon=False, ncols=2)
    ax.set_title("Speedup of Full FastOPF and option-only variants at minSup = 4")
    save_figure(fig, out_dir, "fig_opf_vs_full_and_option_only_speedup_db1_db8_minsup4")


def pair_checks_option_only(exp: Path, out_dir: Path) -> None:
    option_files = {
        "Hash-only": exp / "benchmark" / "ablation_clean" / "FOMAblationHashOnly_summary_avg.csv",
        "Sparse-only": exp / "benchmark" / "ablation_clean" / "FOMAblationSparseOnly_summary_avg.csv",
        "WSB-only": exp / "benchmark" / "ablation_clean" / "FOMAblationWSBOnly_summary_avg.csv",
    }
    frames = []
    for method, path in option_files.items():
        if path.exists():
            frames.append(read_summary(path, method))
    if len(frames) != 3:
        return

    all_df = pd.concat(frames, ignore_index=True)
    wanted = [f"DB{i}" for i in range(1, 9)]
    methods = ["Hash-only", "Sparse-only", "WSB-only"]
    data = all_df[
        (all_df["dataset_id"].isin(wanted)) &
        (all_df["minsup"] == 4.0) &
        (all_df["method"].isin(methods))
    ]
    if "PairChecks" not in data.columns:
        return
    pivot = data.pivot(index="dataset_id", columns="method", values="PairChecks").loc[wanted, methods]

    x = np.arange(len(wanted))
    width = 0.22
    offsets = np.linspace(-width, width, len(methods))
    fig, ax = plt.subplots(figsize=(9.6, 4.8))
    for method, offset in zip(methods, offsets):
        ax.bar(x + offset, pivot[method], width, label=method, color=COLORS.get(method))
    ax.set_xticks(x)
    ax.set_xticklabels(wanted)
    style_axes(ax, "Pair checks (log scale)", "Dataset", log_y=True)
    ax.legend(frameon=False, ncols=3)
    ax.set_title("Pair-check reduction from Hash-indexed join at minSup = 4")
    save_figure(fig, out_dir, "fig_ablation_pair_checks_db1_db8_minsup4")


def load_all_comparison_methods(exp: Path, df: pd.DataFrame) -> pd.DataFrame:
    full_no_canonical = exp / "benchmark" / "ablation_clean" / "FOMAblationFull_summary_avg.csv"
    option_files = {
        "Hash-only": exp / "benchmark" / "ablation_clean" / "FOMAblationHashOnly_summary_avg.csv",
        "Sparse-only": exp / "benchmark" / "ablation_clean" / "FOMAblationSparseOnly_summary_avg.csv",
        "WSB-only": exp / "benchmark" / "ablation_clean" / "FOMAblationWSBOnly_summary_avg.csv",
    }
    frames = [
        df[df["method"] == "OPF-Miner"],
        read_summary(full_no_canonical, "FastOPF-Miner") if full_no_canonical.exists()
        else df[df["method"] == "FastOPF-Miner"],
    ]
    for method, path in option_files.items():
        if path.exists():
            frames.append(read_summary(path, method))
    return pd.concat(frames, ignore_index=True)


def all_config_runtime_distribution(exp: Path, df: pd.DataFrame, out_dir: Path) -> None:
    all_df = load_all_comparison_methods(exp, df)
    methods = ["OPF-Miner", "FastOPF-Miner", "Hash-only", "Sparse-only", "WSB-only"]
    if not set(methods).issubset(set(all_df["method"])):
        return

    values = [all_df[all_df["method"] == method]["Time_s"].to_numpy() for method in methods]
    fig, ax = plt.subplots(figsize=(8.8, 4.8))
    bp = ax.boxplot(values, patch_artist=True, showfliers=True)
    for patch, method in zip(bp["boxes"], methods):
        patch.set_facecolor(COLORS.get(method, "#cccccc"))
        patch.set_alpha(0.78)
    means = [v.mean() for v in values]
    ax.scatter(range(1, len(methods) + 1), means, color="#111111", s=28, zorder=3, label="Mean")
    ax.set_xticks(range(1, len(methods) + 1))
    ax.set_xticklabels(methods)
    ax.set_yscale("log")
    ax.tick_params(axis="x", labelrotation=20)
    style_axes(ax, "Runtime across all 84 configurations (s, log scale)")
    ax.legend(frameon=False)
    ax.set_title("Runtime Distribution Across All Benchmark Configurations")
    save_figure(fig, out_dir, "fig_all_config_runtime_distribution")


def all_config_memory_distribution(exp: Path, df: pd.DataFrame, out_dir: Path) -> None:
    all_df = load_all_comparison_methods(exp, df)
    methods = ["OPF-Miner", "FastOPF-Miner", "Hash-only", "Sparse-only", "WSB-only"]
    if not set(methods).issubset(set(all_df["method"])):
        return

    values = [all_df[all_df["method"] == method]["MaxMem_MB"].to_numpy() for method in methods]
    fig, ax = plt.subplots(figsize=(8.8, 4.8))
    bp = ax.boxplot(values, patch_artist=True, showfliers=True)
    for patch, method in zip(bp["boxes"], methods):
        patch.set_facecolor(COLORS.get(method, "#cccccc"))
        patch.set_alpha(0.78)
    means = [v.mean() for v in values]
    ax.scatter(range(1, len(methods) + 1), means, color="#111111", s=28, zorder=3, label="Mean")
    ax.set_xticks(range(1, len(methods) + 1))
    ax.set_xticklabels(methods)
    ax.set_yscale("log")
    ax.tick_params(axis="x", labelrotation=20)
    style_axes(ax, "Peak memory across all 84 configurations (MB, log scale)")
    ax.legend(frameon=False)
    ax.set_title("Memory Distribution Across All Benchmark Configurations")
    save_figure(fig, out_dir, "fig_all_config_memory_distribution")


def merge_opf_fom(df: pd.DataFrame) -> pd.DataFrame:
    opf = df[df["method"] == "OPF-Miner"]
    fom = df[df["method"] == "FastOPF-Miner"]
    return opf.merge(
        fom,
        on=["Dataset", "dataset_id", "minsup"],
        suffixes=("_opf", "_fom"),
        validate="one_to_one",
    )


def write_manifest(out_dir: Path, names: list[str]) -> None:
    rows = []
    for name in names:
        rows.append({
            "figure": name,
            "png": str(out_dir / f"{name}.png"),
            "pdf": str(out_dir / f"{name}.pdf"),
        })
    pd.DataFrame(rows).to_csv(out_dir / "figures_manifest.csv", index=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate benchmark figures for the FastOPF/FOM manuscript.")
    parser.add_argument(
        "--experiment-dir",
        type=Path,
        default=REPO_ROOT / "results" / "experiments" / "results_full_20260811",
    )
    parser.add_argument(
        "--ablation-dir",
        type=Path,
        default=REPO_ROOT / "results" / "experiments" / "legacy_results",
    )
    parser.add_argument("--out-dir", type=Path, default=None)
    args = parser.parse_args()

    exp = args.experiment_dir
    out_dir = args.out_dir or (exp / "figures")
    opf = read_summary(exp / "benchmark" / "opf" / "OPF_Miner_Original_summary_avg.csv", "OPF-Miner")
    fom = read_summary(exp / "benchmark" / "fom" / "FOM_summary_avg.csv", "FastOPF-Miner")
    df = pd.concat([opf, fom], ignore_index=True)

    required_runtime_bar(df, out_dir)
    required_db5_minsup_line(df, out_dir)
    required_db8_scale_line(df, out_dir)
    speedup_heatmap(df, out_dir)
    memory_bar(df, out_dir)
    operation_reduction_bar(df, out_dir)
    sha_status_bar(exp / "comparisons" / "sha256_equivalence.csv", out_dir)
    ablation_runtime_bar(args.ablation_dir, out_dir)
    opf_vs_full_and_option_only(exp, df, out_dir)
    opf_vs_full_and_option_only_speedup(exp, df, out_dir)
    pair_checks_option_only(exp, out_dir)
    all_config_runtime_distribution(exp, df, out_dir)
    all_config_memory_distribution(exp, df, out_dir)

    names = sorted(p.stem for p in out_dir.glob("*.png"))
    write_manifest(out_dir, names)
    print(f"Generated {len(names)} figures in {out_dir}")
    for name in names:
        print(f"- {name}.png / {name}.pdf")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

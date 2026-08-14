import os
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", str(Path(".matplotlib-cache").resolve()))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[2]
EXPERIMENT_DIR = REPO_ROOT / "results" / "experiments" / "results_full_20260811"
BASE_DIR = EXPERIMENT_DIR / "benchmark" / "electricity_ablation_scale_probe"
INPUT_CSV = BASE_DIR / "electricity_ablation_4scenario_long.csv"
OUT_DIR = EXPERIMENT_DIR / "figures" / "electricity_ablation_scale"
FIG_DPI = 300

CONFIG_ORDER = ["HashOnly", "Full", "WSBOnly", "SparseOnly"]
CONFIG_LABELS = {
    "HashOnly": "Hash-only",
    "Full": "Full FOM",
    "WSBOnly": "WSB-only",
    "SparseOnly": "Sparse-only",
}
COLORS = {
    "HashOnly": "#8a5a2b",
    "Full": "#d97925",
    "WSBOnly": "#8f4c72",
    "SparseOnly": "#3c7f80",
}
DATASET_LABELS = {
    "ELEC_01clients_concat.txt": "1 client",
    "ELEC_05clients_concat.txt": "5 clients",
    "ELEC_10clients_concat.txt": "10 clients",
}
DATASET_ORDER = list(DATASET_LABELS.keys())


def load_data() -> pd.DataFrame:
    df = pd.read_csv(INPUT_CSV)
    df["minsup"] = df["minsup"].astype(float)
    df["clients"] = df["Dataset"].map(DATASET_LABELS)
    df["ConfigLabel"] = df["Config"].map(CONFIG_LABELS)
    return df


def save_figure(fig: plt.Figure, name: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    fig.savefig(OUT_DIR / f"{name}.png", dpi=FIG_DPI, bbox_inches="tight")
    fig.savefig(OUT_DIR / f"{name}.pdf", bbox_inches="tight")
    plt.close(fig)


def style_axis(ax, ylabel: str, xlabel: str | None = None, log_y: bool = True) -> None:
    if log_y:
        ax.set_yscale("log")
    if xlabel:
        ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, axis="y", linestyle=":", linewidth=0.7, alpha=0.7)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)


def grouped_mean_bar(df: pd.DataFrame, metric: str, ylabel: str, title: str, name: str) -> None:
    agg = (
        df.groupby(["Dataset", "Config"], as_index=False)[metric]
        .mean()
        .pivot(index="Dataset", columns="Config", values=metric)
        .loc[DATASET_ORDER, CONFIG_ORDER]
    )

    x = np.arange(len(DATASET_ORDER))
    width = 0.19
    fig, ax = plt.subplots(figsize=(8.6, 4.8))
    for idx, config in enumerate(CONFIG_ORDER):
        offset = (idx - 1.5) * width
        ax.bar(
            x + offset,
            agg[config].to_numpy(),
            width,
            label=CONFIG_LABELS[config],
            color=COLORS[config],
        )
    ax.set_xticks(x)
    ax.set_xticklabels([DATASET_LABELS[d] for d in DATASET_ORDER])
    style_axis(ax, ylabel, "Electricity dataset scale", log_y=True)
    ax.set_title(title)
    ax.legend(frameon=False, ncols=4, loc="upper left")
    save_figure(fig, name)


def runtime_by_minsup(df: pd.DataFrame) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(12.6, 4.2), sharey=True)
    for ax, dataset in zip(axes, DATASET_ORDER):
        sub = df[df["Dataset"] == dataset]
        for config in CONFIG_ORDER:
            cur = sub[sub["Config"] == config].sort_values("minsup")
            ax.plot(
                cur["minsup"],
                cur["Time_s"],
                marker="o",
                linewidth=2,
                markersize=4,
                label=CONFIG_LABELS[config],
                color=COLORS[config],
            )
        ax.set_title(DATASET_LABELS[dataset])
        ax.set_xticks([2, 4, 6, 8, 10, 12])
        style_axis(ax, "Runtime (s, log scale)", "minSup", log_y=True)
    axes[0].legend(frameon=False, ncols=2, loc="upper right")
    fig.suptitle("Runtime by minSup on ElectricityLoadDiagrams")
    save_figure(fig, "fig_electricity_runtime_by_minsup")


def speedup_vs_hashonly(df: pd.DataFrame) -> None:
    rows = []
    for dataset in DATASET_ORDER:
        for minsup in sorted(df["minsup"].unique()):
            base = df[
                (df["Dataset"] == dataset)
                & (df["minsup"] == minsup)
                & (df["Config"] == "HashOnly")
            ]["Time_s"].iloc[0]
            for config in ["Full", "WSBOnly", "SparseOnly"]:
                cur = df[
                    (df["Dataset"] == dataset)
                    & (df["minsup"] == minsup)
                    & (df["Config"] == config)
                ]["Time_s"].iloc[0]
                rows.append(
                    {
                        "Dataset": dataset,
                        "clients": DATASET_LABELS[dataset],
                        "Config": config,
                        "relative": cur / base,
                    }
                )
    rel = pd.DataFrame(rows)
    agg = (
        rel.groupby(["Dataset", "Config"], as_index=False)["relative"]
        .mean()
        .pivot(index="Dataset", columns="Config", values="relative")
        .loc[DATASET_ORDER, ["Full", "WSBOnly", "SparseOnly"]]
    )

    x = np.arange(len(DATASET_ORDER))
    width = 0.24
    fig, ax = plt.subplots(figsize=(8.2, 4.8))
    for idx, config in enumerate(["Full", "WSBOnly", "SparseOnly"]):
        ax.bar(
            x + (idx - 1) * width,
            agg[config].to_numpy(),
            width,
            label=CONFIG_LABELS[config],
            color=COLORS[config],
        )
    ax.axhline(1.0, color="#333333", linewidth=1.0, linestyle="--")
    ax.set_xticks(x)
    ax.set_xticklabels([DATASET_LABELS[d] for d in DATASET_ORDER])
    style_axis(ax, "Runtime / Hash-only runtime (log scale)", "Electricity dataset scale", log_y=True)
    ax.set_title("Runtime overhead relative to Hash-only")
    ax.legend(frameon=False, ncols=3)
    save_figure(fig, "fig_electricity_runtime_relative_to_hashonly")


def write_manifest(names: list[str]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = OUT_DIR / "figures_manifest.csv"
    with manifest.open("w", encoding="utf-8", newline="") as f:
        f.write("figure,png,pdf\n")
        for name in names:
            f.write(f"{name},{name}.png,{name}.pdf\n")


def main() -> None:
    df = load_data()
    names = [
        "fig_electricity_mean_runtime_by_scale",
        "fig_electricity_mean_memory_by_scale",
        "fig_electricity_mean_pairchecks_by_scale",
        "fig_electricity_runtime_by_minsup",
        "fig_electricity_runtime_relative_to_hashonly",
    ]
    grouped_mean_bar(
        df,
        "Time_s",
        "Mean runtime over minSup values (s, log scale)",
        "Mean runtime across electricity scales",
        names[0],
    )
    grouped_mean_bar(
        df,
        "MaxMem_MB",
        "Mean peak memory over minSup values (MB, log scale)",
        "Mean peak memory across electricity scales",
        names[1],
    )
    grouped_mean_bar(
        df,
        "PairChecks",
        "Mean pair checks over minSup values (log scale)",
        "Pair-check workload across electricity scales",
        names[2],
    )
    runtime_by_minsup(df)
    speedup_vs_hashonly(df)
    write_manifest(names)
    print(f"Wrote {len(names)} figures to {OUT_DIR}")


if __name__ == "__main__":
    main()

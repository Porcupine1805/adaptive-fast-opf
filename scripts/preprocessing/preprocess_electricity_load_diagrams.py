#!/usr/bin/env python3
"""
Preprocess UCI ElectricityLoadDiagrams20112014 for OPF/FOM benchmarks.

The raw file uses semicolon separators, quoted timestamps/client names, and
comma decimal separators. This script creates numeric-only .txt files that the
Java benchmark programs can read with Scanner/Double parsing.
"""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_CLIENTS = [
    "MT_330",
    "MT_329",
    "MT_327",
    "MT_326",
    "MT_324",
    "MT_323",
    "MT_321",
    "MT_320",
    "MT_319",
    "MT_318",
]


@dataclass
class ClientStats:
    name: str
    rows: int = 0
    nonzero: int = 0
    first_nonzero_row: int = 0
    max_value: float = 0.0

    def observe(self, value: float) -> None:
        self.rows += 1
        if value != 0.0:
            self.nonzero += 1
            if self.first_nonzero_row == 0:
                self.first_nonzero_row = self.rows
        if value > self.max_value:
            self.max_value = value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert ElectricityLoadDiagrams20112014 to OPF/FOM numeric txt files."
    )
    parser.add_argument(
        "--input",
        default="data/electricity_raw/LD2011_2014.txt",
        help="Raw LD2011_2014.txt path.",
    )
    parser.add_argument(
        "--output-dir",
        default="data/electricity_clean",
        help="Directory for cleaned numeric files.",
    )
    parser.add_argument(
        "--clients",
        default=",".join(DEFAULT_CLIENTS),
        help="Comma-separated client names to export, or 'all'.",
    )
    parser.add_argument(
        "--line-width",
        type=int,
        default=20,
        help="Number of numeric values per output line.",
    )
    parser.add_argument(
        "--concat-sizes",
        default="1,5,10",
        help="Comma-separated prefix sizes to concatenate from selected clients. Use '' to skip.",
    )
    return parser.parse_args()


def write_value(handle, value: str, item_index: int, line_width: int) -> None:
    handle.write(value)
    if (item_index + 1) % line_width == 0:
        handle.write("\n")
    else:
        handle.write(" ")


def parse_client_list(raw: str, available: list[str]) -> list[str]:
    if raw.strip().lower() == "all":
        return available
    requested = [item.strip() for item in raw.split(",") if item.strip()]
    missing = [name for name in requested if name not in available]
    if missing:
        raise SystemExit(f"Unknown client(s): {', '.join(missing)}")
    return requested


def parse_concat_sizes(raw: str, selected_count: int) -> list[int]:
    if not raw.strip():
        return []
    sizes = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    invalid = [size for size in sizes if size < 1 or size > selected_count]
    if invalid:
        raise SystemExit(
            f"Invalid concat size(s) {invalid}; selected client count is {selected_count}."
        )
    return sizes


def open_outputs(output_dir: Path, clients: Iterable[str]):
    handles = {}
    for client in clients:
        handles[client] = (output_dir / f"ELEC_{client}.txt").open("w", encoding="utf-8")
    return handles


def close_outputs(handles) -> None:
    for handle in handles.values():
        handle.close()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    with input_path.open("r", encoding="utf-8", newline="") as raw:
        reader = csv.reader(raw, delimiter=";")
        header = next(reader)
        available_clients = [name.strip('"') for name in header[1:]]

    selected_clients = parse_client_list(args.clients, available_clients)
    concat_sizes = parse_concat_sizes(args.concat_sizes, len(selected_clients))
    selected_indices = [available_clients.index(name) + 1 for name in selected_clients]
    stats = {name: ClientStats(name) for name in selected_clients}

    output_handles = open_outputs(output_dir, selected_clients)
    concat_handles = {
        size: (output_dir / f"ELEC_{size:02d}clients_concat.txt").open("w", encoding="utf-8")
        for size in concat_sizes
    }
    output_counts = {name: 0 for name in selected_clients}
    concat_counts = {size: 0 for size in concat_sizes}

    try:
        with input_path.open("r", encoding="utf-8", newline="") as raw:
            reader = csv.reader(raw, delimiter=";")
            next(reader)
            for row in reader:
                values_for_concat = []
                for client, column_index in zip(selected_clients, selected_indices):
                    value_text = row[column_index].replace(",", ".")
                    value = float(value_text)
                    stats[client].observe(value)

                    write_value(
                        output_handles[client],
                        value_text,
                        output_counts[client],
                        args.line_width,
                    )
                    output_counts[client] += 1
                    values_for_concat.append(value_text)

                for size, handle in concat_handles.items():
                    for value_text in values_for_concat[:size]:
                        write_value(handle, value_text, concat_counts[size], args.line_width)
                        concat_counts[size] += 1
    finally:
        close_outputs(output_handles)
        close_outputs(concat_handles)

    report_path = output_dir / "electricity_preprocessing_report.csv"
    with report_path.open("w", encoding="utf-8", newline="") as report:
        writer = csv.writer(report)
        writer.writerow(
            [
                "Client",
                "Rows",
                "NonZero",
                "FirstNonZeroRow",
                "MaxValue",
                "OutputFile",
            ]
        )
        for client in selected_clients:
            stat = stats[client]
            writer.writerow(
                [
                    stat.name,
                    stat.rows,
                    stat.nonzero,
                    stat.first_nonzero_row,
                    f"{stat.max_value:.12g}",
                    f"ELEC_{client}.txt",
                ]
            )

    manifest_path = output_dir / "README.txt"
    with manifest_path.open("w", encoding="utf-8") as manifest:
        manifest.write("Cleaned ElectricityLoadDiagrams20112014 data for OPF/FOM.\n")
        manifest.write(f"Raw source: {input_path}\n")
        manifest.write("Format: whitespace-separated numeric values, decimal dot, no timestamp.\n")
        manifest.write(f"Selected clients: {', '.join(selected_clients)}\n")
        manifest.write(f"Rows per selected client: {next(iter(stats.values())).rows}\n")
        manifest.write(f"Concat datasets: {', '.join(str(size) for size in concat_sizes) or 'none'}\n")
        manifest.write("Do not point benchmarks at the raw LD2011_2014.txt file.\n")

    print(f"Clean data written to: {output_dir}")
    print(f"Selected clients: {len(selected_clients)}")
    print(f"Rows per client: {next(iter(stats.values())).rows}")
    print(f"Report: {report_path}")


if __name__ == "__main__":
    main()

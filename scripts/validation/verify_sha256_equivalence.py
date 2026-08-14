import argparse
import csv
import hashlib
from pathlib import Path


def format_support(value: float, decimals: int) -> str:
    return f"{value:.{decimals}f}"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_normalized(path: Path, decimals: int) -> str:
    rows = read_patterns(path)
    h = hashlib.sha256()
    h.update(b"pattern,support\n")
    for pattern in sorted(rows):
        line = f"{pattern},{format_support(rows[pattern], decimals)}\n"
        h.update(line.encode("utf-8"))
    return h.hexdigest()


def read_patterns(path: Path) -> dict[str, float]:
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return {row["pattern"]: float(row["support"]) for row in reader}


def compare_content(opf_path: Path, fom_path: Path, tolerance: float) -> tuple[str, str, float]:
    opf = read_patterns(opf_path)
    fom = read_patterns(fom_path)
    if set(opf) != set(fom):
        only_opf = sorted(set(opf) - set(fom))
        only_fom = sorted(set(fom) - set(opf))
        detail = []
        if only_opf:
            detail.append(f"only_opf={only_opf[:5]}")
        if only_fom:
            detail.append(f"only_fom={only_fom[:5]}")
        return "PATTERN_SET_MISMATCH", "; ".join(detail), 0.0

    max_diff = 0.0
    worst = ""
    for pattern in sorted(opf):
        diff = abs(opf[pattern] - fom[pattern])
        if diff > max_diff:
            max_diff = diff
            worst = pattern
    if max_diff > tolerance:
        return "SUPPORT_MISMATCH", f"max_diff={max_diff:.12g}; pattern={worst}", max_diff
    return "CONTENT_MATCH_TOLERANCE", f"max_diff={max_diff:.12g}", max_diff


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare canonical OPF/FOM pattern-support CSV outputs using SHA-256."
    )
    parser.add_argument("--opf", required=True, type=Path, help="Directory with OPF canonical CSV files.")
    parser.add_argument("--fom", required=True, type=Path, help="Directory with FOM canonical CSV files.")
    parser.add_argument("--out", required=True, type=Path, help="Output comparison CSV path.")
    parser.add_argument("--tolerance", type=float, default=1e-6, help="Support tolerance if hashes differ.")
    parser.add_argument(
        "--round-decimals",
        type=int,
        default=6,
        help="Decimal places used before computing normalized SHA-256.",
    )
    args = parser.parse_args()

    opf_files = {p.name: p for p in args.opf.glob("*.csv")}
    fom_files = {p.name: p for p in args.fom.glob("*.csv")}
    names = sorted(set(opf_files) | set(fom_files))
    args.out.parent.mkdir(parents=True, exist_ok=True)

    if not names:
        print("error: no canonical CSV files found in either input directory")
        return 2

    rows = []
    ok = True
    for name in names:
        opf_path = opf_files.get(name)
        fom_path = fom_files.get(name)
        if opf_path is None or fom_path is None:
            status = "MISSING_FILE"
            detail = f"opf_exists={opf_path is not None}; fom_exists={fom_path is not None}"
            opf_hash = sha256_file(opf_path) if opf_path else ""
            fom_hash = sha256_file(fom_path) if fom_path else ""
            opf_normalized_hash = sha256_normalized(opf_path, args.round_decimals) if opf_path else ""
            fom_normalized_hash = sha256_normalized(fom_path, args.round_decimals) if fom_path else ""
            max_diff = ""
            ok = False
        else:
            opf_hash = sha256_file(opf_path)
            fom_hash = sha256_file(fom_path)
            opf_normalized_hash = sha256_normalized(opf_path, args.round_decimals)
            fom_normalized_hash = sha256_normalized(fom_path, args.round_decimals)
            if opf_hash == fom_hash:
                status = "SHA256_MATCH"
                detail = ""
                max_diff = 0.0
            elif opf_normalized_hash == fom_normalized_hash:
                status = "NORMALIZED_SHA256_MATCH"
                detail = f"round_decimals={args.round_decimals}"
                max_diff = ""
            else:
                status, detail, max_diff = compare_content(opf_path, fom_path, args.tolerance)
                if status != "CONTENT_MATCH_TOLERANCE":
                    ok = False

        rows.append({
            "file": name,
            "status": status,
            "detail": detail,
            "max_diff": max_diff,
            "round_decimals": args.round_decimals,
            "opf_raw_sha256": opf_hash,
            "fom_raw_sha256": fom_hash,
            "opf_normalized_sha256": opf_normalized_hash,
            "fom_normalized_sha256": fom_normalized_hash,
        })

    with args.out.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "file",
            "status",
            "detail",
            "max_diff",
            "round_decimals",
            "opf_raw_sha256",
            "fom_raw_sha256",
            "opf_normalized_sha256",
            "fom_normalized_sha256",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    total = len(rows)
    exact = sum(1 for r in rows if r["status"] == "SHA256_MATCH")
    normalized = sum(1 for r in rows if r["status"] == "NORMALIZED_SHA256_MATCH")
    tolerance = sum(1 for r in rows if r["status"] == "CONTENT_MATCH_TOLERANCE")
    failed = total - exact - normalized - tolerance
    print(f"wrote: {args.out}")
    print(
        f"total={total} sha256_match={exact} "
        f"normalized_sha256_match={normalized} content_match_tolerance={tolerance} failed={failed}"
    )
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

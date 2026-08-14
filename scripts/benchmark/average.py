"""
average.py  —  Tính trung bình Time(s) và MaxMem(MB) qua N lần chạy.

Cách dùng (gọi từ run_all.bat):
    python average.py <result_dir> <algo_name> <num_runs>

Ví dụ:
    python average.py results FOM 10

Input:  results/FOM_run_01.csv ... results/FOM_run_10.csv
Output: results/FOM_summary_avg.csv

Các cột Time(s) và MaxMem(MB) được lấy trung bình.
Candidates, Fusions, SupportOps, FreqPatterns là deterministic —
lấy từ run_01 làm đại diện, kèm assert kiểm tra nhất quán.
"""

import sys
import csv
import os
from collections import defaultdict

# ------------------------------------------------------------------
# Cột được trung bình hóa
AVG_COLS = {"Time(s)", "MaxMem(MB)"}

# Cột dùng để định danh mỗi dòng (key)
KEY_COLS = {"Dataset", "minsup"}

# Cột deterministic — lấy từ run 1, kiểm tra nhất quán qua các run
DET_COLS = {"PairChecks", "Candidates", "Fusions", "SupportOps", "FreqPatterns"}
# ------------------------------------------------------------------


def load_csv(path):
    """Đọc CSV, trả về list of dict."""
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader), reader.fieldnames


def make_key(row):
    return (row["Dataset"].strip(), row["minsup"].strip())


def main():
    if len(sys.argv) != 4:
        print("Usage: python average.py <result_dir> <algo_name> <num_runs>")
        sys.exit(1)

    result_dir = sys.argv[1]
    algo = sys.argv[2]
    num_runs = int(sys.argv[3])

    # ------------------------------------------------------------------
    # Đọc tất cả file run
    # ------------------------------------------------------------------
    all_runs = []
    fieldnames = None

    for r in range(1, num_runs + 1):
        run_label = f"{r:02d}"
        path = os.path.join(result_dir, f"{algo}_run_{run_label}.csv")

        if not os.path.exists(path):
            print(f"[CANH BAO] Khong tim thay: {path} — bo qua lan chay nay.")
            continue

        rows, fn = load_csv(path)
        if fieldnames is None:
            fieldnames = fn
        all_runs.append((run_label, rows))

    if not all_runs:
        print(f"[LOI] Khong co file CSV nao cho {algo}.")
        sys.exit(1)

    valid_runs = len(all_runs)
    print(f"  -> Doc duoc {valid_runs}/{num_runs} file CSV cho {algo}.")

    # Tìm tên cột Time và MaxMem thực tế từ fieldnames đã đọc
    first_fieldnames = list(fieldnames) if fieldnames else []
    actual_time_col = next((c for c in first_fieldnames if "Time" in c), "Time(s)")
    actual_mem_col  = next((c for c in first_fieldnames if "Mem"  in c), "MaxMem(MB)")
    print(f"  -> Ten cot thuc te: time='{actual_time_col}', mem='{actual_mem_col}'")

    # ------------------------------------------------------------------
    # Gom dữ liệu theo key (Dataset, minsup)
    # ------------------------------------------------------------------
    # avg_data[key][col] = list of float values across runs
    avg_data = defaultdict(lambda: defaultdict(list))
    # det_data[key][col] = value from run_01 (reference)
    det_data = {}
    # Giữ thứ tự xuất hiện của các key
    key_order = []

    for run_label, rows in all_runs:
        for row in rows:
            key = make_key(row)
            if key not in avg_data:
                key_order.append(key)

            # Thu thập các giá trị cần trung bình (dùng tên cột thực tế)
            for col in (actual_time_col, actual_mem_col):
                if col in row:
                    try:
                        avg_data[key][col].append(float(row[col]))
                    except ValueError:
                        pass  # bỏ qua giá trị rỗng hoặc lỗi

            # Lưu giá trị deterministic từ run đầu tiên
            if run_label == all_runs[0][0] and key not in det_data:
                det_data[key] = {col: row.get(col, "").strip() for col in DET_COLS}

    # ------------------------------------------------------------------
    # Kiểm tra nhất quán deterministic (cảnh báo nếu lệch)
    # ------------------------------------------------------------------
    inconsistencies = 0
    for run_label, rows in all_runs[1:]:
        for row in rows:
            key = make_key(row)
            for col in DET_COLS:
                ref = det_data.get(key, {}).get(col, "")
                cur = row.get(col, "").strip()
                if ref and cur and ref != cur:
                    print(f"  [CANH BAO] {algo} | {key} | {col}: "
                          f"run_01={ref}, run_{run_label}={cur}")
                    inconsistencies += 1

    if inconsistencies == 0:
        print(f"  -> Kiem tra nhat quan: OK (tat ca {len(DET_COLS)} cot det. khop).")
    else:
        print(f"  -> [CHU Y] {inconsistencies} bat nhat quan duoc phat hien.")

    # ------------------------------------------------------------------
    # Tính trung bình và ghi output
    # ------------------------------------------------------------------
    out_path = os.path.join(result_dir, f"{algo}_summary_avg.csv")

    # Output fieldnames: giữ đúng tên gốc từ CSV + thêm 3 cột mới ở cuối
    base_fieldnames = list(fieldnames) if fieldnames else [
        "Dataset", "minsup", actual_time_col, actual_mem_col,
        "Candidates", "Fusions", "SupportOps", "FreqPatterns"
    ]
    avg_fieldnames = base_fieldnames + ["Time_std", "MaxMem_std", "Runs"]

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=avg_fieldnames, extrasaction="ignore")
        writer.writeheader()

        for key in key_order:
            dataset, minsup = key
            out_row = {}

            # Điền tất cả cột gốc với giá trị rỗng trước (tránh missing key)
            for col in base_fieldnames:
                out_row[col] = ""

            out_row["Dataset"] = dataset
            out_row["minsup"] = minsup

            # Trung bình và độ lệch chuẩn
            time_vals = avg_data[key].get(actual_time_col, [])
            mem_vals  = avg_data[key].get(actual_mem_col, [])

            out_row[actual_time_col] = f"{_mean(time_vals):.6f}" if time_vals else ""
            out_row[actual_mem_col]  = f"{_mean(mem_vals):.2f}"  if mem_vals  else ""
            out_row["Time_std"]      = f"{_std(time_vals):.6f}"  if len(time_vals) > 1 else "0.000000"
            out_row["MaxMem_std"]    = f"{_std(mem_vals):.2f}"   if len(mem_vals)  > 1 else "0.00"
            out_row["Runs"]          = len(time_vals)

            # Giá trị deterministic từ run_01
            det = det_data.get(key, {})
            for col in DET_COLS:
                # Tìm cột tương ứng trong fieldnames gốc (tên có thể hơi khác)
                matched = next((f for f in base_fieldnames if col in f), col)
                out_row[matched] = det.get(col, "")

            writer.writerow(out_row)

    print(f"  -> Ghi xong: {out_path}  ({len(key_order)} dong, {valid_runs} runs).")


def _mean(vals):
    return sum(vals) / len(vals) if vals else 0.0


def _std(vals):
    if len(vals) < 2:
        return 0.0
    m = _mean(vals)
    variance = sum((v - m) ** 2 for v in vals) / (len(vals) - 1)
    return variance ** 0.5


if __name__ == "__main__":
    main()

# Phản biện: α, Range bound, Pair mass sau HJ — Báo cáo trung thực

**Ngày:** 2026-08-14  
**Mục đích:** Trả lời các câu hỏi phản biện khi viết manuscript; phân biệt rõ *mô hình chi phí*, *mặc định kỹ thuật*, và *bằng chứng thực nghiệm đã có / chưa có*.

---

## Tóm tắt điều hành

| Câu hỏi | Trả lời ngắn |
|---------|----------------|
| α = 0.08 có quá thấp? | **Có thể thấp** nếu mỗi lần check O(1) không “rẻ hơn fuse ~12×”. α là **ngưỡng mô hình**, chưa được calibrate bằng full sensitivity net-gain. |
| Có data khoảng α tối ưu? | **Chưa có** sweep α đầy đủ → net gain. Pilot cho thấy always-on CPC / prune rate thấp thường **không** lãi so với HJ-only. |
| Range phụ thuộc \(U_{res}<1.5\sigma\)? | **Đúng hướng bạn hiểu:** residual đã \(<\sigma\) thì chết ở tier 1; range chỉ khi **chưa chết** nhưng \(U_{res}\) vẫn **gần** \(\sigma\) (vùng tight). |
| \(J\) sau HJ so với \(L^2\)? | HJ chỉ sinh pair **tương thích** \(\mathrm{suf}(p)=\mathrm{pre}(q)\). Tỷ lệ \(J/L^2\) **phụ thuộc generation**, thường \(\ll 1\); code ghi **\(J\) tuyệt đối** (`PairChecks`), chưa log \(L^2\) từng tầng. |
| Logic chọn 0.08 + data pair mass? | 0.08 \(=\ C_{check}/C_{fuse}\) giả định; pair mass đo được trên pilot (DB4/DB5) — bảng bên dưới. |

---

## 1. Về ngưỡng \(\alpha = 0.08\) (residual structure / Gate B)

### 1.1 Logic chọn \(\alpha\) (không phải “8% magic từ benchmark time”)

Gate B trong code dùng mô hình **đơn vị công việc** (ops), không đo wall-clock để bật/tắt:

\[
\begin{align*}
\mathrm{cost}_{HJ} &\approx P \cdot C_{fuse}, \\
\mathrm{cost}_{CPC} &\approx P \cdot C_{check} + (1-r)\,P \cdot C_{fuse}.
\end{align*}
\]

Bật CPC khi \(\mathrm{cost}_{CPC} < \mathrm{cost}_{HJ}\), tức

\[
r > \frac{C_{check}}{C_{fuse}} =: \alpha.
\]

Mặc định \(\alpha = 0.08\) tương đương giả định

\[
C_{fuse} \approx \frac{1}{0.08}\,C_{check} = 12.5\,C_{check},
\]

tức: **một lần fuse “đắt” hơn một lần check O(1) khoảng một bậc độ lớn**.  
\(\hat r\) trên sample là **tỷ lệ pair bị O(1) prune**, không phải phần trăm thời gian.

### 1.2 \(\alpha = 0.08\) có quá thấp không?

**Phản biện của bạn đúng về mặt rủi ro:**

- Nếu bật CPC, thuật toán trả \(C_{check}\) trên **gần như 100%** pair còn lại (mọi pair HJ đều đi qua cascade O(1) khi CPC on).
- Lợi ích chỉ nằm ở phần \(r\) pair **không fuse**.
- Với \(\alpha=0.08\), mô hình cho phép bật khi chỉ kỳ vọng **~8%** pair chết miễn phí — điều kiện **lỏng**.
- Nếu trên data thật \(C_{fuse}/C_{check} \ll 12.5\) (Occ ngắn, fuse rẻ), thì ngay cả \(r=0.08\) cũng **không** đủ → overhead check đội lên, đúng lo ngại của bạn.

**Bằng chứng pilot (đã quan sát, không phải sweep α):**

| Hiện tượng | Ý nghĩa với α |
|------------|----------------|
| CPC always-on / prune rate thấp (vd. ELEC_10 minsup=8: ~1% prune) → chậm hơn HJ | Gate cần **không** bật khi \(r\) thấp; α quá nhỏ dễ bật nhầm |
| O(1)-only gần HJ hơn full+Range trên DB4 | Phần lớn overhead không chỉ α mà còn **Range** |
| Gate A (\(r\ge 0.5\)) an toàn hơn, bật ít hơn | Trade-off: ít false enable, bỏ lỡ case lãi nhẹ |

**Kết luận manuscript-safe:**

> \(\alpha=0.08\) is a **default cost-ratio constant**, not an empirically optimized operating point. We do **not** claim that 8% free-prune rate guarantees net gain on all datasets. Safer alternatives for the paper narrative: report Gate A (\(r\ge 1/2\)) as fail-safe, treat B as ablation, and/or add an α-sensitivity table in the final experiments.

### 1.3 Có data “α nên nằm khoảng nào”?

**Chưa có** bảng systematic:

\[
\alpha \in \{0.05, 0.08, 0.12, 0.20, 0.35, 0.50\} \times \text{datasets} \times \text{median time vs HJ}.
\]

Việc cần làm trước camera-ready nếu giữ Gate B:

1. Sweep α trên DB4/DB5/ELEC_01/ELEC_10.  
2. Vẽ median(Time_CPC gate) / median(Time_HJ).  
3. Chọn α **tối thiểu** sao cho hầu hết ô không chậm hơn HJ (fail-safe), không phải α tối đa hóa speedup trung bình.

**Gợi ý khoảng kỳ vọng (giả thuyết, chưa khóa):**

| \(\alpha\) | Tính chất |
|------------|-----------|
| \(0.05\)–\(0.08\) | Dễ bật CPC — rủi ro overhead cao |
| \(0.12\)–\(0.20\) | Trung gian (gần legacy gate D 0.12) |
| \(0.50\) (Gate A) | Chỉ bật khi đa số sample chết O(1) — an toàn, ít case lãi |

---

## 2. Về Range Bound và điều kiện \(U_{res} < 1.5\sigma\)

### 2.1 Bạn hiểu đúng

Thứ tự cascade:

1. Nếu \(U_{res} < \sigma\) → **đã prune ở tier Residual** (không bao giờ tới Range).  
2. Nếu span / card×weight prune → cũng không tới Range.  
3. Range chỉ chạy khi pair **còn sống** sau tier 1–3 **và**:
   - \(U_{res} < \tau\cdot\sigma\) với \(\tau=\) `adaptiveWsbCpcTight` (mặc định **1.5**),
   - và \(n_p+n_q \le M\) (`adaptiveWsbMaxOccForRange`).

Vậy miền residual kích hoạt Range là:

\[
\sigma \ \le\ U_{res}\ <\ 1.5\,\sigma
\]

(cộng điều kiện list không quá dài).

### 2.2 Ý nghĩa

- \(U_{res}\) **chưa** đủ nhỏ để kết luận \(<\sigma\), nhưng **đủ gần** ngưỡng nên có hy vọng đếm thật trong cửa sổ \([\ell,h]\) (range) sẽ kéo upper bound xuống dưới \(\sigma\).
- Nếu \(U_{res} \gg \sigma\) (vd. \(3\sigma\)), range gần như **không** cứu được prune → tránh trả \(O(\log n)\).
- \(\tau=1.5\) là **heuristic engineering**, cùng tinh thần với α: cần sensitivity trong appendix nếu reviewer hỏi.

### 2.3 Công thức Range (nhắc lại)

\[
U_{range}=\min(c_p,c_q)\cdot w_h,
\quad
w_h=e^{-k(n-h)},
\]

\(c_p,c_q\): số occurrence nằm trong cửa sổ căn chỉnh (binary search). Prune nếu \(U_{range}<\sigma\).

---

## 3. Độ phức tạp / quy mô pair sau HJ

### 3.1 Trước và sau HJ (lý thuyết)

| Cách | Số lần xét pair (một generation, \(|F_m|=L\)) |
|------|-----------------------------------------------|
| Ngây thơ / không index | \(\Theta(L^2)\) (hoặc \(\sim L^2/2\) với GP-Fusion group) |
| **HJ** | \(\Theta(L + J)\), \(J=\lvert\{(p,q):\mathrm{suf}(p)=\mathrm{pre}(q)\}\rvert\) |

\[
\frac{J}{L^2} = \text{phụ thuộc phân bố key prefix/suffix trong } F_m.
\]

- Key trùng nhiều → bucket lớn → \(J\) lớn.  
- Key thưa → \(J \ll L^2\).  
Không có một % cố định cho mọi generation / mọi minsup.

### 3.2 Số liệu thực tế đo được (pilot, HJ-only)

`PairChecks` trong CSV là **tổng** số pair HJ được xét qua **mọi generation** (không phải một tầng). Không log \(L^2\) từng tầng trong bản hiện tại.

| Dataset | minsup | PairChecks \(J_{tot}\) | FreqPatterns (toàn run) | Time (s) |
|---------|-------:|----------------------:|------------------------:|---------:|
| DB4 | 2 | 14 401 | 4 836 | 0.29 |
| DB4 | 4 | 7 072 | 2 259 | 0.06 |
| DB5 | 2 | 79 863 | 68 695 | 0.95 |
| DB5 | 4 | 35 492 | 28 201 | 0.14 |

**Đọc đúng:** đây là **khối lượng pair sau HJ** (output-sensitive), đủ để thấy minsup tăng → \(J_{tot}\) giảm mạnh; series lớn (DB5) → \(J_{tot}\) lớn hơn DB4 cùng minsup.

**Chưa có:** cột \(J/L^2\) trung bình theo generation. Muốn có trong paper cần thêm instrumentation:

```text
mỗi generation: L = |F_m|, J = pairCount, ratio = J / max(L*L, 1)
```

### 3.3 Câu manuscript-safe về “bao nhiêu %”

> HJ replaces dense pairing with an output-sensitive enumeration of structurally compatible pairs. The absolute number of such pairs (\(J\)) is data- and threshold-dependent; across pilot runs on DB4/DB5 it ranges from on the order of \(10^3\)–\(10^5\) total pair checks per mining run (summed over generations). We do not claim a universal percentage of \(L^2\); GP-Fusion already restricts groups, and HJ further restricts to equal prefix–suffix keys within those constraints.

---

## 4. Tổng hợp: logic α = 0.08 và pair mass

### 4.1 α = 0.08

| Khía cạnh | Nội dung |
|-----------|----------|
| Nguồn | Cost-model Gate B: \(\alpha \approx C_{check}/C_{fuse}\) |
| Không phải | ROI % đo từ wall-clock trên training set |
| Rủi ro | 8% free-prune có thể **không** đủ net gain → khớp pilot CPC thua HJ |
| Việc paper nên làm | Sensitivity α; hoặc nhấn Gate A / fail-safe HJ; không oversell 0.08 |

### 4.2 Pair mass sau HJ

| Khía cạnh | Nội dung |
|-----------|----------|
| Định nghĩa | \(P=J=\sum_p \lvert\mathrm{prefixMap}[\mathrm{suf}(p)]\rvert\) |
| Dùng trong Adaptive | Floor \(P \ge P_0\), \(N \ge N_0\) trước khi xét \(\hat r\) |
| Data pilot | Bảng PairChecks DB4/DB5 ở §3.2 |
| Thiếu | Tỷ lệ \(J/L^2\) per generation — cần log thêm |

---

## 5. Đề xuất chỉnh manuscript / code (sau phản biện)

1. **Viết rõ:** \(\alpha\) is a cost-ratio default, not a fitted optimum.  
2. **Thêm experiment:** α-sweep + bảng “when CPC beats HJ”.  
3. **Range:** một câu đúng như bạn diễn giải (\(\sigma \le U_{res} < \tau\sigma\)).  
4. **HJ:** report \(J\) (PairChecks) + optional \(J/L^2\) nếu instrument thêm; tránh claim “giảm X% cố định”.  
5. **Default an toàn hơn cho camera-ready:** cân nhắc Gate A hoặc \(\alpha \in [0.2,0.5]\) nếu ưu tiên “không chậm hơn HJ”.

---

## 6. Trả lời từng câu (sẵn gửi reviewer / co-author)

**Q1 — α = 0.08 quá thấp?**  
Có rủi ro đó. Mô hình cho phép bật CPC khi chỉ ~8% pair chết O(1), trong khi 100% pair trả check. Net gain chỉ đúng nếu fuse thật sự đắt hơn check ~12×. Pilot cho thấy nhiều ô CPC không thắng HJ khi prune rate thấp; do đó 0.08 **không** được coi là đã chứng minh tối ưu.

**Q2 — Range và \(U_{res}<1.5\sigma\)?**  
Đúng: residual \(<\sigma\) đã loại ở tier 1. Range chỉ khi pair sống và residual còn trong vùng “gần ngưỡng” \([\sigma, 1.5\sigma)\), kèm giới hạn độ dài list.

**Q3 — \(J\) so với cặp tiềm năng?**  
HJ chỉ đếm pair cùng key prefix–suffix; \(J/L^2\) biến thiên theo generation. Thực tế pilot: tổng PairChecks ~7k–80k trên DB4/DB5 (minsup 2–4). Chưa công bố % \(L^2\) trung bình vì chưa log \(L\) theo tầng.

**Q4 — Logic α và data pair mass?**  
α từ bất đẳng thức cost \(r > C_{check}/C_{fuse}\). Pair mass = PairChecks / \(P\) sau HJ; số liệu §3.2; chưa có full matrix α–net-gain.

---

*File này phản ánh trạng thái evidence hiện tại của Adaptive Fast OPF (2026-08-14), không thay cho full experimental section.*

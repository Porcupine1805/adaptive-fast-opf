# Running Example — HJ, CPC, and Fusion (with tables)

**Purpose.** A fully computed walk-through for the manuscript: one short series, explicit weights, two scenarios (CPC prune vs fusion).  
All indices are **1-based** end positions (as in OPF-Miner).

---

## 1. Input series and parameters

**Time series** (\(n=10\)):

\[
t = (15,\ 32,\ 29,\ 27,\ 34,\ 33,\ 25,\ 20,\ 28,\ 23).
\]

| Position \(j\) | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|----------------|---|---|---|---|---|---|---|---|---|-----|
| \(t_j\) | 15 | 32 | 29 | 27 | 34 | 33 | 25 | 20 | 28 | 23 |

**Forgetting factor:** \(k = 0.1\) (same order as OPF-Miner’s illustrative example).

**Weights** \(w_j = e^{-k(n-j)}\):

| \(j\) | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|-------|------|------|------|------|------|------|------|------|------|------|
| \(w_j\) | 0.4066 | 0.4493 | 0.4966 | 0.5488 | 0.6065 | 0.6703 | 0.7408 | 0.8187 | 0.9048 | 1.0000 |

We use **two thresholds** to separate the pedagogical cases:

| Scenario | \(\sigma\) | What we show |
|----------|------------|--------------|
| **Case 1** | \(2.05\) | Compatible pair **pruned by Range** (after residual/span/card pass) |
| **Case 2** | \(1.50\) | Same pair **survives CPC** and enters **fusion** |

*(Case 1 needs a slightly higher \(\sigma\) so that \(U_{\mathrm{range}}<\sigma\le fsup(p),fsup(q)\). Case 2 matches the classical OPF-Miner narrative threshold.)*

---

## 2. Length-2 seed (both cases)

Scan consecutive pairs \((t_{j-1},t_j)\):

| End \(j\) | \(t_{j-1}\) | \(t_j\) | Pattern |
|-----------|-------------|---------|---------|
| 2 | 15 | 32 | (1,2) |
| 3 | 32 | 29 | (2,1) |
| 4 | 29 | 27 | (2,1) |
| 5 | 27 | 34 | (1,2) |
| 6 | 34 | 33 | (2,1) |
| 7 | 33 | 25 | (2,1) |
| 8 | 25 | 20 | (2,1) |
| 9 | 20 | 28 | (1,2) |
| 10 | 28 | 23 | (2,1) |

\[
\begin{align*}
\mathrm{Occ}((1,2)) &= \{2,5,9\}, &
fsup &= w_2+w_5+w_9 = 1.9607,\\
\mathrm{Occ}((2,1)) &= \{3,4,6,7,8,10\}, &
fsup &= 4.2753.
\end{align*}
\]

Both are frequent for \(\sigma\in\{1.5,\,2.05\}\).

**HJ keys at length 2:** \(\mathrm{pre}=\mathrm{suf}=(1)\) for every length-2 pattern (single-element normalization).  
**Skew (for Stage-3 intuition):** \(|\mathrm{Occ}((2,1))|=6\) vs \(|\mathrm{Occ}((1,2))|=3\) (ratio \(2\))—mild imbalance; larger \(m\) lists in real data amplify Gallop’s effect.

---

## 3. Length-3 patterns used in both cases

Windows of length 3 and their rank patterns:

| End \(j\) | Window values | Rank pattern |
|-----------|---------------|--------------|
| 3 | (15,32,29) | **(1,3,2)** |
| 4 | (32,29,27) | **(3,2,1)** |
| 5 | (29,27,34) | (2,1,3) |
| 6 | (27,34,33) | **(1,3,2)** |
| 7 | (34,33,25) | **(3,2,1)** |
| 8 | (33,25,20) | **(3,2,1)** |
| 9 | (25,20,28) | (2,1,3) |
| 10 | (20,28,23) | **(1,3,2)** |

Focus parents:

| Pattern | \(\mathrm{Occ}\) | \(fsup\) | \(\mathrm{pre}\) | \(\mathrm{suf}\) |
|---------|------------------|----------|------------------|------------------|
| \(p=(1,3,2)\) | \(\{3,6,10\}\) | **2.1669** | (1,2) | **(2,1)** |
| \(q=(3,2,1)\) | \(\{4,7,8\}\) | **2.1084** | **(2,1)** | (2,1) |
| \((2,1,3)\) | \(\{5,9\}\) | 1.5114 | (2,1) | (1,2) |

**HJ join:** \(\mathrm{suf}(p)=(2,1)=\mathrm{pre}(q)\) ⇒ \((p,q)\) is emitted by the prefix map (one probe of key \((2,1)\)).  
GP-Fusion would still *test* other group members whose keys do not match; HJ does not.

---

## 4. Case 1 — HJ + CPC prune (\(\sigma = 2.05\))

**Parents are frequent:** \(fsup(p)=2.1669\ge 2.05\), \(fsup(q)=2.1084\ge 2.05\).

### 4.1 Residual

\[
e^{k}=e^{0.1}\approx 1.1052,\quad
U_{\mathrm{res}}=\min(1.1052\cdot 2.1669,\ 2.1084)=\min(2.3949,\ 2.1084)=2.1084.
\]

\(U_{\mathrm{res}}=2.1084 \ge 2.05\) → **not pruned** by residual.

### 4.2 Span

\[
\ell=\max(3+1,4)=4,\quad
h=\min(10+1,8)=8.
\]

\(\ell\le h\) → alignment window \([4,8]\) nonempty → **not pruned**.

### 4.3 Cardinality–weight

\[
\min(n_p,n_q)=\min(3,3)=3,\quad
w_h=w_8=0.8187,\quad
U_{\mathrm{card}}=3\times 0.8187=2.4562 \ge 2.05.
\]

**Not pruned.**

### 4.4 Range (tightens the count)

Occurrences of \(p\) with end in \([\ell-1,h-1]=[3,7]\): \(\{3,6\}\) → \(c_p=2\)  
(10 is outside).  

Occurrences of \(q\) with end in \([4,8]\): \(\{4,7,8\}\) → \(c_q=3\).

\[
U_{\mathrm{range}}=\min(2,3)\times w_8=2\times 0.8187=\mathbf{1.6375} < 2.05.
\]

**CPC prunes \((p,q)\).** No fusion is executed.

True aligned set is only \(A(p,q)=\{4,7\}\) (size 2), so the range bound matches the true cardinality of \(A\); card–weight was loose because it counted the non-feasible end \(10\in\mathrm{Occ}(p)\).

| Tier | Value | vs \(\sigma=2.05\) | Action |
|------|-------|---------------------|--------|
| Residual | 2.1084 | \(\ge\) | continue |
| Span | \([4,8]\) | nonempty | continue |
| Card×weight | 2.4562 | \(\ge\) | continue |
| **Range** | **1.6375** | **\( <\)** | **prune** |

---

## 5. Case 2 — HJ + CPC pass + fusion (\(\sigma = 1.5\))

Same pair \((p,q)\). All CPC upper bounds exceed \(1.5\):

| Tier | Value | vs \(\sigma=1.5\) |
|------|-------|-------------------|
| Residual | 2.1084 | pass |
| Span | \([4,8]\) | pass |
| Card×weight | 2.4562 | pass |
| Range | 1.6375 | pass |

### 5.1 Aligned endpoints (exact fusion geometry)

Require \(j-1\in\mathrm{Occ}(p)\) and \(j\in\mathrm{Occ}(q)\):

| \(j\in\mathrm{Occ}(q)\) | \(j-1\) | In \(\mathrm{Occ}(p)\)? | Aligned |
|-------------------------|---------|-------------------------|---------|
| 4 | 3 | yes | **yes** |
| 7 | 6 | yes | **yes** |
| 8 | 7 | no | no |

\[
A(p,q)=\{4,7\}.
\]

**Shifted list for Gallop/two-pointer:**

\[
\mathrm{Occ}(p)+1 = \{4,7,11\},
\quad
\mathrm{Occ}(q)=\{4,7,8\}.
\]

Intersection \(\{4,7\}\) — same result whether two-pointer or gallop is used.

### 5.2 Boundary tests (\(p_1=q_m=1\)) → two alternatives

| \(j\) | Window \(t_{j-3:j}\) | \(t_{j-3}\) | \(t_j\) | Relation | Child |
|-------|----------------------|-------------|--------|----------|-------|
| 4 | (15,32,29,27) | 15 | 27 | \(<\) | **\(r\)** |
| 7 | (27,34,33,25) | 27 | 25 | \(>\) | **\(h\)** |

Rank outcomes (OPF fusion rules):

- \(r=(1,4,3,2)\) with \(\mathrm{Occ}(r)=\{4\}\), \(fsup(r)=w_4=0.5488\)  
- \(h=(2,4,3,1)\) with \(\mathrm{Occ}(h)=\{7\}\), \(fsup(h)=w_7=0.7408\)

Under \(\sigma=1.5\), **neither** length-4 child is frequent (supports \(<1.5\)).  
The example still shows: **CPC did not prune**, fusion ran, supports match exact enumeration — and children correctly fail the threshold (same as OPF-Miner).

### 5.3 Galloping vs two-pointer on this pair

List lengths \(3\) and \(3\) (ratio \(1\)) → **not skewed** under typical gates (\(\rho\ge 2\) or \(8\)).  
Adaptive would use **classic two-pointer** for this pair even if the generation were Gallop-enabled.

**Skew illustration (same series, length-2 lists):**  
\(\mathrm{Occ}((2,1))\) length 6 vs \(\mathrm{Occ}((1,2))\) length 3. After \(+1\) shift of the longer parent, gallop would skip over gaps when the long cursor lags—**identical match set**, fewer index steps on the long side when imbalance grows (large electricity series in experiments).

---

## 6. Side-by-side summary for reviewers

| Step | Case 1 (\(\sigma=2.05\)) | Case 2 (\(\sigma=1.5\)) |
|------|-------------------------|-------------------------|
| HJ | Emit \((p,q)\) via key \((2,1)\) | Same |
| Residual | pass | pass |
| Span | pass | pass |
| Card×weight | pass | pass |
| Range | **prune** (\(1.64<2.05\)) | pass (\(1.64>1.5\)) |
| Fusion | skipped | \(A=\{4,7\}\) → \(r,h\) |
| Frequent length-4 | — | none (supports \(0.55,\ 0.74\)) |

**Takeaways.**

1. HJ finds the pair with one key lookup.  
2. CPC tiers are **ordered**; Range can prune when Card is still above \(\sigma\).  
3. Surviving pairs use the same alignment semantics as OPF-Miner; Gallop only changes *search*, not the intersection.  
4. Raising \(\sigma\) can turn a “fuse” instance into a “prune” instance without changing \(t\) or \(k\).

---

## 7. Optional figure captions

- **Fig. R1.** Series \(t\) with occurrence marks of \(p=(1,3,2)\) and \(q=(3,2,1)\).  
- **Fig. R2.** Cascade of bounds for Case 1 (arrow stops at Range).  
- **Fig. R3.** Shifted lists \(\mathrm{Occ}(p)+1\) and \(\mathrm{Occ}(q)\) with matches at 4 and 7.

---

## 8. Numerical appendix (full weight vector)

\[
\begin{align*}
w &= (e^{-0.9},\ e^{-0.8},\ e^{-0.7},\ e^{-0.6},\ e^{-0.5},\\
&\quad e^{-0.4},\ e^{-0.3},\ e^{-0.2},\ e^{-0.1},\ e^{0})\\
&\approx (0.4066,\ 0.4493,\ 0.4966,\ 0.5488,\ 0.6065,\\
&\quad 0.6703,\ 0.7408,\ 0.8187,\ 0.9048,\ 1.0000).
\end{align*}
\]

\[
\begin{align*}
fsup(p) &= w_3+w_6+w_{10} = 0.4966+0.6703+1.0000 = 2.1669,\\
fsup(q) &= w_4+w_7+w_8 = 0.5488+0.7408+0.8187 = 2.1084.
\end{align*}
\]

---

*End of running example. All figures rounded to 4 decimals; inequalities are exact under the printed rounding.*

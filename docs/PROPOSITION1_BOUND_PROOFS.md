# Proofs for Proposition 1 — Safe Upper Bounds (CPC)

**Manuscript use:** formal justification that residual, cardinality–weight, and range bounds are valid upper bounds on \(fsup(r,t)\), so pruning never discards a frequent pattern.  
**Span** is included for completeness (empty alignment \(\Rightarrow fsup=0\)).

---

## 0. Setup

Time series \(t=(t_1,\ldots,t_n)\), forgetting factor \(k\in(0,1)\),

\[
w_j = e^{-k(n-j)}, \qquad j=1,\ldots,n.
\]

Properties used below:

1. \(w_j > 0\) for all \(j\).  
2. \(w_j\) is **strictly increasing** in \(j\): \(j < j' \Rightarrow w_j < w_{j'}\) (because \(n-j > n-j'\) and the exponent \(-k(n-j)\) increases).  
3. In particular, on any nonempty index set \(S\subseteq\{1,\ldots,n\}\) with \(h=\max S\),

\[
\forall j\in S:\quad w_j \le w_h.
\]

For a pattern \(x\), \(\mathrm{Occ}(x)\subseteq\{1,\ldots,n\}\) is the set of occurrence **end** positions, and

\[
fsup(x,t)=\sum_{j\in\mathrm{Occ}(x)} w_j.
\]

Let \(p,q\) be join-compatible length-\(m\) parents and \(r\) any length-\((m+1)\) superpattern produced by OPF fusion of \((p,q)\). Write \(n_p=|\mathrm{Occ}(p)|\), \(n_q=|\mathrm{Occ}(q)|\).

**Aligned endpoints.** Every \(j\in\mathrm{Occ}(r)\) satisfies

\[
j-1\in\mathrm{Occ}(p), \qquad j\in\mathrm{Occ}(q).
\]

Hence

\[
\mathrm{Occ}(r)\ \subseteq\ A(p,q)
:=
\bigl\{j:\ j-1\in\mathrm{Occ}(p),\ j\in\mathrm{Occ}(q)\bigr\}
\ \subseteq\ \mathrm{Occ}(q).
\]

---

## Proposition 1 (Safe upper bounds)

For every fusion child \(r\) of \((p,q)\),

\[
\begin{align}
fsup(r,t)
&\ \le\
U_{\mathrm{res}}(p,q)
:=\min\bigl(e^{k}\,fsup(p,t),\ fsup(q,t)\bigr),
\tag{R}\\[4pt]
fsup(r,t)
&\ \le\
U_{\mathrm{card}}(p,q)
:=\min(n_p,n_q)\,w_h
\quad\text{when }A(p,q)\neq\emptyset\text{ and }h=\max A(p,q),
\tag{C}\\[4pt]
fsup(r,t)
&\ \le\
U_{\mathrm{range}}(p,q)
:=\min(c_p,c_q)\,w_h,
\tag{Rg}
\end{align}
\]

with \(c_p,c_q,h\) defined in §3.  
If the span interval is empty, \(A(p,q)=\emptyset\) and \(fsup(r,t)=0\).

Consequently, if any of these upper bounds is \(<\sigma\), then \(fsup(r,t)<\sigma\): pruning is **safe**.

---

## 1. Residual bound \(U_{\mathrm{res}}\)

### 1.1 Suffix side (no scale factor)

**Claim.** \(fsup(r,t)\le fsup(q,t)\).

**Proof.**  
From the alignment geometry, \(\mathrm{Occ}(r)\subseteq\mathrm{Occ}(q)\).  
Weights are positive, therefore summing a subset cannot exceed summing the whole set:

\[
fsup(r,t)
=
\sum_{j\in\mathrm{Occ}(r)} w_j
\ \le\
\sum_{j\in\mathrm{Occ}(q)} w_j
=
fsup(q,t).
\]

(The same holds with \(\mathrm{Occ}(r)\subseteq A(p,q)\subseteq\mathrm{Occ}(q)\).) \(\square\)

### 1.2 Prefix side (scale \(e^{k}\))

**Claim.** \(fsup(r,t)\le e^{k}\,fsup(p,t)\).

**Proof.**  
If \(j\in\mathrm{Occ}(r)\), then \(j-1\in\mathrm{Occ}(p)\). The map \(j\mapsto j-1\) is injective from \(\mathrm{Occ}(r)\) into \(\mathrm{Occ}(p)\).  
Weight recurrence:

\[
w_j = e^{-k(n-j)} = e^{k}\,e^{-k(n-(j-1))} = e^{k}\,w_{j-1}.
\]

Therefore

\[
\begin{align*}
fsup(r,t)
&=
\sum_{j\in\mathrm{Occ}(r)} w_j
=
\sum_{j\in\mathrm{Occ}(r)} e^{k}\,w_{j-1}
=
e^{k}\sum_{j\in\mathrm{Occ}(r)} w_{j-1}
\\
&\le
e^{k}\sum_{u\in\mathrm{Occ}(p)} w_u
=
e^{k}\,fsup(p,t),
\end{align*}
\]

where the inequality uses \(\{j-1:j\in\mathrm{Occ}(r)\}\subseteq\mathrm{Occ}(p)\) and \(w_u>0\). \(\square\)

### 1.3 Combined residual

\[
fsup(r,t)
\ \le\
\min\bigl(e^{k}\,fsup(p,t),\ fsup(q,t)\bigr)
=:
U_{\mathrm{res}}(p,q).
\]

**Implementation note.** Code may maintain residual masses `prefixSupport` / `suffixSupport` that shrink as occurrences are consumed; those values are \(\le\) the original \(fsup(p)\), \(fsup(q)\), so replacing \(fsup(p)\) by `prefixSupport` in \(U_{\mathrm{res}}\) remains a valid (and often tighter) upper bound.

---

## 2. Cardinality–weight bound \(U_{\mathrm{card}}\)

### 2.1 Feasible endpoint window

Let

\[
\begin{align*}
\ell
&=
\max\bigl(\min\mathrm{Occ}(p)+1,\ \min\mathrm{Occ}(q)\bigr),
\\
h
&=
\min\bigl(\max\mathrm{Occ}(p)+1,\ \max\mathrm{Occ}(q)\bigr).
\end{align*}
\]

Then \(A(p,q)\subseteq[\ell,h]\cap\mathbb{Z}\). If \(\ell>h\), \(A(p,q)=\emptyset\) and \(fsup(r)=0\) (**span prune**).

Assume henceforth \(A(p,q)\neq\emptyset\), so \(h=\max A(p,q)\) is well-defined and \(h\ge\ell\).

### 2.2 Cardinality

Each aligned endpoint uses **one** distinct end from \(\mathrm{Occ}(p)\) (namely \(j-1\)) and **one** from \(\mathrm{Occ}(q)\) (namely \(j\)). Hence

\[
|A(p,q)|\ \le\ n_p, \qquad |A(p,q)|\ \le\ n_q,
\quad\Rightarrow\quad
|A(p,q)|\ \le\ \min(n_p,n_q).
\]

### 2.3 Why \(\min(n_p,n_q)\cdot w_h\) is safe

**Step A — weights maximized at \(h\).**  
For every \(j\in A(p,q)\) we have \(j\le h\). By monotonicity of \(w_j\),

\[
w_j \le w_h.
\]

**Step B — sum of at most \(M\) positive weights.**  
Let \(M=\min(n_p,n_q)\). Even in the most favourable case where \(|A(p,q)|=M\) and every weight equals the maximum feasible weight \(w_h\),

\[
fsup(r,t)
=
\sum_{j\in\mathrm{Occ}(r)} w_j
\ \le\
\sum_{j\in A(p,q)} w_j
\ \le\
\sum_{j\in A(p,q)} w_h
=
|A(p,q)|\,w_h
\ \le\
M\,w_h.
\]

**Conclusion.**

\[
fsup(r,t)\ \le\ \min(n_p,n_q)\,w_h =: U_{\mathrm{card}}(p,q).
\]

**Intuition (as in the review question).**  
Under exponential forgetting, the heaviest contribution any single occurrence can make inside the feasible window is \(w_h\). Any sum of \(M\) such contributions is at most \(M\cdot w_h\). Using \(w_n=1\) would also be safe but **looser** whenever \(h<n\).

---

## 3. Range bound \(U_{\mathrm{range}}\)

### 3.1 Refined counts inside \([\ell,h]\)

Not every occurrence of \(p\) or \(q\) can participate in an alignment: only those whose end index falls in the window forced by both lists.

Define

\[
\begin{align*}
c_p
&=
\bigl|\{\,u\in\mathrm{Occ}(p):\ \ell-1 \le u \le h-1\,\}\bigr|,
\\
c_q
&=
\bigl|\{\,v\in\mathrm{Occ}(q):\ \ell \le v \le h\,\}\bigr|.
\end{align*}
\]

(The \(\pm 1\) on the \(p\)-side matches \(u=j-1\) when \(j\) is the fused endpoint.)

Then every aligned \(j\) consumes one such \(u\) and one such \(v\), so

\[
|A(p,q)|\ \le\ \min(c_p,c_q).
\]

Clearly \(c_p\le n_p\), \(c_q\le n_q\), and the inequality is **strictly tighter** whenever some occurrences lie outside \([\ell,h]\) (common when lists are long but only a sub-range overlaps).

### 3.2 Bound

Same weight argument as §2.3 with \(M\) replaced by \(\min(c_p,c_q)\):

\[
fsup(r,t)
\ \le\
\min(c_p,c_q)\,w_h
=:
U_{\mathrm{range}}(p,q).
\]

### 3.3 Computing \(c_p,c_q\) by binary search

\(\mathrm{Occ}(p)\) and \(\mathrm{Occ}(q)\) are stored **sorted**. For a sorted array \(Z\) and integer bounds \(a\le b\),

\[
\bigl|\{z\in Z:\ a\le z\le b\}\bigr|
=
\mathrm{upper\_bound}(Z,b)-\mathrm{lower\_bound}(Z,a),
\]

each bound in \(O(\log |Z|)\) comparisons. Thus

\[
c_p,c_q\ \text{in}\ O(\log n_p+\log n_q).
\]

This is the only CPC tier that is not \(O(1)\); it is invoked only under additional “tight residual / moderate list length” guards in the implementation, which does not affect **correctness**—only when the cost is paid.

### 3.4 Why range is tighter than card–weight

\[
\min(c_p,c_q)\ \le\ \min(n_p,n_q)
\quad\Rightarrow\quad
U_{\mathrm{range}}\ \le\ U_{\mathrm{card}},
\]

with equality iff all occurrences of both parents already lie in the feasible windows. Range never invalidates safety; it only reduces false retention of hopeless pairs.

---

## 4. Span bound (empty alignment)

**Proposition (span).**  
If \(\ell>h\), then \(A(p,q)=\emptyset\), hence \(\mathrm{Occ}(r)=\emptyset\) and \(fsup(r,t)=0<\sigma\).

**Proof.**  
Any aligned \(j\) would need \(j\ge\ell\) and \(j\le h\) simultaneously. \(\square\)

---

## 5. Safety corollary for pruning

**Corollary (no loss of frequent patterns).**  
If CPC discards \((p,q)\) because \(U_{\mathrm{res}}<\sigma\) or \(U_{\mathrm{card}}<\sigma\) or \(U_{\mathrm{range}}<\sigma\) or the span interval is empty, then every fusion child \(r\) satisfies \(fsup(r,t)<\sigma\).  
Therefore the frequent set of OPF-Miner is unchanged by Stage-2 pruning.

**Note on looseness.**  
Bounds may satisfy \(U\ge\sigma\) while true \(fsup(r)<\sigma\). Those pairs survive CPC and are filtered by exact fusion—the same final test as OPF-Miner. Safety concerns **false negatives** (dropping a frequent pattern), not false positives (keeping a useless pair).

---

## 6. Compact proof block for the paper (English)

> **Proposition 1.** Let \(r\) be any OPF fused from join-compatible parents \(p,q\). Then \(fsup(r,t)\le\min(e^{k}fsup(p,t),fsup(q,t))\). Indeed \(\mathrm{Occ}(r)\subseteq\mathrm{Occ}(q)\) yields the suffix inequality, while \(j\in\mathrm{Occ}(r)\) implies \(j-1\in\mathrm{Occ}(p)\) and \(w_j=e^{k}w_{j-1}\), hence the scaled prefix inequality. Moreover, writing \(h\) for the rightmost feasible aligned endpoint, monotonicity of \(w_j\) gives \(fsup(r,t)\le |A(p,q)|\,w_h\le\min(n_p,n_q)\,w_h\). Restricting counts to occurrences inside the feasible index window produces the tighter range bound \(\min(c_p,c_q)\,w_h\), with \(c_p,c_q\) obtainable by binary search on the sorted occurrence lists. Each quantity is therefore a valid upper bound on \(fsup(r,t)\); pruning when a bound is less than \(\sigma\) cannot eliminate a frequent pattern.

---

## 7. Proof checklist

| Bound | Key inequality | Uses \(k\)? |
|-------|----------------|-------------|
| Residual (suffix) | \(\mathrm{Occ}(r)\subseteq\mathrm{Occ}(q)\) | no |
| Residual (prefix) | \(w_j=e^{k}w_{j-1}\), injectivity of \(j\mapsto j-1\) | yes |
| Span | empty \(A(p,q)\) | no |
| Card–weight | \(\|A\|\le\min(n_p,n_q)\), \(w_j\le w_h\) | yes (via \(w_h\)) |
| Range | \(\|A\|\le\min(c_p,c_q)\le\min(n_p,n_q)\), \(w_j\le w_h\) | yes |

---

*End of Proposition 1 proofs.*

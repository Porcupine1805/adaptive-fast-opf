# HJ (Stage 1): Key Normalization, Data Structure, Pseudocode & GP-Fusion Contrast

**For manuscript Section 4.1 / Introduction motivation.**  
**Implementation reference:** `FOMAblationFlags.OrderPreservingUtils`, `IntArrayKey`, `buildPrefixMap` / HJ probe loop.

---

## 1. Join attribute: normalized prefix and suffix

### 1.1 Why normalize?

A length-\(m\) OPF is a permutation \(p=(p_1,\ldots,p_m)\) of \(\{1,\ldots,m\}\).  
The overlap of a length-\((m+1)\) window uses the first \(m-1\) ranks of one parent and the last \(m-1\) ranks of the other. Those subsequences are **not** necessarily permutations of \(\{1,\ldots,m-1\}\) (labels may have gaps). OPF-Miner therefore applies **rank normalization** (relative order of the subsequence):

\[
\mathrm{norm}(z)_i = 1 + |\{h:\ z_h < z_i\}|.
\]

**Normalized prefix / suffix** (same as OPF-Miner / FastOPF formalization):

\[
\begin{align*}
\mathrm{pre}(p) &= \mathrm{norm}(p_1,\ldots,p_{m-1}), \\
\mathrm{suf}(p) &= \mathrm{norm}(p_2,\ldots,p_m).
\end{align*}
\]

**Join compatibility:** \(p\) and \(q\) may fuse iff

\[
\mathrm{suf}(p) = \mathrm{pre}(q).
\]

### 1.2 Implementation of \(\mathrm{norm}\) / \(\mathrm{pre}\) / \(\mathrm{suf}\)

In code (`OrderPreservingUtils`):

```text
getOrder(seq):          // rank-encode distinct values → permutation of 1..|seq|
  for rank = 1..|seq|:
      assign rank to the still-unmarked minimum element

getPrefix(p):  getOrder(p[0 .. m-2])     // first m-1 entries
getSuffix(p):  getOrder(p[1 .. m-1])     // last  m-1 entries
```

Each `PatternCandidate` stores `int[] prefix` and `int[] suffix` computed once at construction.

**Example.** \(p=(1,4,3,2)\):

- \(\mathrm{pre}(p)=\mathrm{norm}(1,4,3)=(1,3,2)\)
- \(\mathrm{suf}(p)=\mathrm{norm}(4,3,2)=(3,2,1)\)

### 1.3 Hash key structure

| Concept | Java realization |
|---------|------------------|
| Map | `HashMap<IntArrayKey, List<PatternCandidate>> prefixMap` |
| Key | `IntArrayKey` wraps `int[]` with `Arrays.hashCode` / `Arrays.equals` |
| Value | All patterns \(q\) in \(F_m\) with \(\mathrm{pre}(q)=\textit{key}\) |

So the logical type is:

```text
prefixMap : normalized_prefix_key → list of patterns sharing that pre(·)
```

**Not** a raw `HashMap<int[], List<...>>` (arrays use identity equality); the explicit key wrapper is required for value-based hashing of rank tuples.

---

## 2. HJ procedure (Stage 1)

### 2.1 Two phases

1. **Build:** one pass over \(F_m\); insert each pattern under key \(\mathrm{pre}(q)\).  
2. **Probe:** for each \(p\in F_m\), retrieve \(Q(p)=\mathrm{prefixMap}[\mathrm{suf}(p)]\); every \(q\in Q(p)\) is join-compatible.

Complexity (generation size \(L=|F_m|\), pair count \(J\)):

- Build: \(\Theta(L)\) map operations (plus \(O(m)\) already paid to form keys).  
- Probe: \(\Theta(L+J)\) — **output-sensitive** in the number of compatible pairs.  
- Contrast: scanning admissible GP-Fusion groups costs \(\Theta(\sum_p |G_{\mathrm{adm}}(p)|)\) **equality tests**, including many failed \(\mathrm{suf}(p)\neq\mathrm{pre}(q)\).

### 2.2 Pseudocode (Section 4.1 style)

```text
Algorithm 1  Hash-Indexed Join (HJ) for generation F_m
Input:  F_m  // frequent OPFs of length m, each with pre(·), suf(·)
Output: stream of compatible pairs (p, q)

1:  prefixMap ← empty hash map          // key: IntArrayKey(pre), value: list of patterns
2:  for each pattern q in F_m do
3:      k ← IntArrayKey(pre(q))
4:      prefixMap[k].append(q)
5:  end for
6:  for each pattern p in F_m do
7:      k ← IntArrayKey(suf(p))
8:      Q ← prefixMap.lookup(k)        // empty list if absent
9:      for each pattern q in Q do
10:         emit (p, q)                 // suf(p) = pre(q) guaranteed
11:     end for
12: end for
```

Optional (compatible with OPF-Miner heuristics):

- Sort \(F_m\) by decreasing \(fsup\) before the probe loop (maximal-support priority).  
- Filter \((p,q)\) by GP-Fusion group rules *inside* the inner loop if group labels are maintained (HJ already ensures the join key; groups only further restrict).

### 2.3 Correctness sketch

- **Completeness:** every pair with \(\mathrm{suf}(p)=\mathrm{pre}(q)\) has \(q\) stored under that prefix key, so the probe of \(p\) retrieves it.  
- **Soundness:** lookup returns only patterns with that exact key → only compatible pairs.  
- **Independence from fusion cases:** HJ only decides *which pairs* enter fusion; Cases \(p_1=q_m\) / \(</>\) of OPF-Miner still apply when materializing superpatterns.

---

## 3. Why GP-Fusion still pays redundant comparisons

### 3.1 What GP-Fusion optimizes

OPF-Miner partitions \(F_m\) into **four groups** by the first two and last two ranks (Theorems on group structure). Each pattern only attempts fusion with patterns in **two** admissible groups → about **half** of the \(L^2\) pairs are skipped at the *group* level.

GP-Fusion does **not** index the full length-\((m-1)\) normalized key. Within an admissible group pair \((G_a,G_b)\), the implementation still walks candidates and tests

\[
\mathrm{suf}(p)\ \stackrel{?}{=}\ \mathrm{pre}(q)
\]

on each \((p,q)\). For \(m\ge 4\), many patterns share the same group label but **differ** in \(\mathrm{pre}/\mathrm{suf}\) of length \(m-1\).

### 3.2 Concrete example (redundant GP-Fusion scans vs HJ)

Consider length \(m=4\) and focus on patterns whose **group** allows mutual fusion under GP-Fusion Rule 1 (Group 1 with Groups 1 and 2). Illustrative frequent set (all treated as frequent for the join argument):

| ID | Pattern \(p\) | Group (sketch) | \(\mathrm{pre}(p)=\mathrm{norm}(p_1,p_2,p_3)\) | \(\mathrm{suf}(p)=\mathrm{norm}(p_2,p_3,p_4)\) |
|----|---------------|------------------|-----------------------------------------------|-----------------------------------------------|
| A | (1,2,3,4) | 1 | (1,2,3) | (1,2,3) |
| B | (1,2,4,3) | 1 | (1,2,3) | (1,3,2) |
| C | (1,3,2,4) | 1 | (1,3,2) | (2,1,3) |
| D | (1,3,4,2) | 1 | (1,2,3) | (2,3,1) |
| E | (1,4,2,3) | 2 | (1,3,2) | (3,1,2) |
| F | (1,4,3,2) | 2 | (1,3,2) | (3,2,1) |

**GP-Fusion behaviour (admissible pairs only):**  
Each of \(\{A,B,C,D\}\) (Group 1) is checked against all of \(\{A,B,C,D,E,F\}\) (Groups 1∪2), and each of \(\{E,F\}\) against the groups allowed by its rule. Even restricting to “Group 1 probes Groups 1∪2”:

- Number of **key equality tests** ≥ \(4\times 6 = 24\) for these six patterns alone in that rule slice.  
- Compatible outcomes for \(\mathrm{suf}(p)=\mathrm{pre}(q)\):

| \(p\) | \(\mathrm{suf}(p)\) | Matching \(q\) (same \(\mathrm{pre}\)) |
|-------|---------------------|----------------------------------------|
| A | (1,2,3) | A, B, D |
| B | (1,3,2) | C, E, F |
| C | (2,1,3) | *(none in this toy set)* |
| D | (2,3,1) | *(none)* |
| … | … | … |

Only a **handful** of pairs are true join hits; the rest are **failed** \(\mathrm{suf}=\mathrm{pre}\) tests still performed by GP-Fusion.

**HJ behaviour on the same set:**

1. Build `prefixMap`:

```text
(1,2,3) → [A, B, D]
(1,3,2) → [C, E, F]
(2,1,3) → []
...
```

2. Probe:

```text
A probes (1,2,3) → {A,B,D}     // 3 hits, 0 failed equality scans
B probes (1,3,2) → {C,E,F}     // 3 hits
C probes (2,1,3) → {}          // 1 O(1) miss, no linear scan of group
```

Failed matches are **empty bucket lookups**, not repeated array compares against every group member.

### 3.3 Scaling the same phenomenon

Suppose Group 1 ∪ Group 2 contains \(L\) patterns, split across \(K\) distinct prefix keys with roughly even buckets (\(L/K\) each). Assume suffixes are similarly diverse.

| Method | Work (order of magnitude) |
|--------|---------------------------|
| GP-Fusion within admissible groups | \(\Theta(L^2)\) key comparisons (still quadratic *inside* the allowed groups) |
| HJ | \(\Theta(L + J)\) with \(J \approx L\cdot (L/K)/c = O(L^2/K)\) emitted pairs, **without** paying failed compares |

When \(K\) is large (diverse length-\((m-1)\) overlaps—common as \(m\) grows), GP-Fusion still “touches” almost every admissible pair, while HJ only materializes true hits.

**One-sentence motivation for the paper:**

> GP-Fusion reduces which *groups* interact, but within those groups it still performs a dense suffix–prefix equality search; HJ replaces that search by a hash multimap on the full normalized length-\((m-1)\) key, so incompatible keys never enter the inner fusion loop.

---

## 4. Minimal LaTeX-friendly pseudocode block

```latex
\begin{algorithm}
\caption{Hash-Indexed Join for OPF candidate pairs}
\begin{algorithmic}[1]
\Require Frequent set $F_m$ with $\mathrm{pre}(\cdot)$, $\mathrm{suf}(\cdot)$
\Ensure All pairs $(p,q)$ with $\mathrm{suf}(p)=\mathrm{pre}(q)$
\State $\textit{prefixMap} \gets \emptyset$
\ForAll{$q \in F_m$}
  \State append $q$ to $\textit{prefixMap}[\mathrm{pre}(q)]$
\EndFor
\ForAll{$p \in F_m$}
  \ForAll{$q \in \textit{prefixMap}[\mathrm{suf}(p)]$}
    \State \textbf{emit} $(p,q)$
  \EndFor
\EndFor
\end{algorithmic}
\end{algorithm}
```

---

## 5. Code map

| Manuscript item | Code |
|-----------------|------|
| \(\mathrm{norm}\) | `OrderPreservingUtils.getOrder` |
| \(\mathrm{pre},\mathrm{suf}\) | `getPrefix` / `getSuffix` |
| Key wrapper | `IntArrayKey` |
| Build map | `prefixMap.computeIfAbsent(new IntArrayKey(c.prefix), ...)` |
| Probe | `prefixMap.get(new IntArrayKey(p.suffix))` |
| Non-HJ baseline compare | `Arrays.equals(p.suffix, q.prefix)` inside nested loops |

---

## 6. Claims checklist (editorial)

| Claim | OK? |
|-------|-----|
| HJ uses normalized length-\((m-1)\) keys | Yes |
| Structure is hash multimap key → list of patterns | Yes |
| Output-sensitive in \(J\) | Yes |
| GP-Fusion alone removes all redundant key tests | **No** — only group-level |
| Toy example counts are schematic for motivation | Yes — scale with real \(L,K\) in experiments via `PairChecks` |

---

*End of note. Suitable for Section 4.1 (HJ) and a short Introduction paragraph contrasting GP-Fusion.*

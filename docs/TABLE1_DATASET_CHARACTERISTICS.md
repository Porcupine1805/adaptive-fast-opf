# Table 1 — Dataset Characteristics

**Manuscript use:** Experimental Setup / Data.  
**Sources:** bundled `data/benchmark/DB1–DB8.txt`; electricity scale inputs from UCI *ElectricityLoadDiagrams20112014* (concatenated client streams); provenance in `data/manifests/datasets.csv` and OPF-Miner release  
`https://github.com/wuc567/Pattern-Mining/tree/master/OPF-Miner`.

---

## Table 1. Characteristics of the evaluation datasets

| ID | Description / domain | Length \(n\) | \(\sigma\) (minSup) used | \(k\) (forgetting) |
|----|----------------------|-------------:|--------------------------|--------------------|
| DB1 | Financial time series (OPF-Miner suite) | 5 842 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default); sensitivity \(\{0.01,0.1\}\) optional |
| DB2 | Financial time series (OPF-Miner suite) | 8 141 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB3 | Financial time series (OPF-Miner suite) | 12 279 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB4 | Financial time series (OPF-Miner suite) | 23 046 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB5 | Financial time series (OPF-Miner suite; longest DB) | 60 000 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB6 | Financial time series (OPF-Miner suite) | 10 305 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB7 | Financial time series (OPF-Miner suite) | 12 075 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| DB8 | Financial time series (OPF-Miner suite) | 14 058 | \{2, 4, 6, 8, 10, 12\} | \(k=1/n\) (default) |
| ELEC\_01 | UCI electricity load — 1 client, concatenated | 140 256 | \{2, 4, 8\} (scale pilots); extendable \{2,…,12\} | \(k=1/n\) (default) |
| ELEC\_05 | UCI electricity load — 5 clients, concatenated | 701 280 | \{2, 4, 8\} (scale pilots) | \(k=1/n\) (default) |
| ELEC\_10 | UCI electricity load — 10 clients, concatenated | 1 402 560 | \{2, 4, 8\} (scale / CPC–Gallop isolation) | \(k=1/n\) (default) |

*Notes.*  
(1) \(n\) is the number of numeric samples in the whitespace-separated `.txt` file consumed by the miner.  
(2) \(\sigma\) is an **absolute** forgetting-weighted support threshold (not a relative frequency). With \(k=1/n\), single-occurrence weights lie in approximately \([e^{-1},1]\approx[0.37,1]\).  
(3) Default \(k=1/n\) matches the implementation when the `-Dk` property is omitted; the running example in the text uses \(k=0.1\) for readability only.  
(4) DB1–DB8 are the financial series distributed with the OPF-Miner study; exact ticker-level naming follows that upstream package.  
(5) Electricity series are derived from UCI *ElectricityLoadDiagrams20112014* by client selection and temporal concatenation (1 / 5 / 10 clients).

---

## Compact LaTeX (booktabs)

```latex
\begin{table}[t]
\centering
\caption{Characteristics of the evaluation datasets.
Length $n$ is the number of time points.
The minimum support $\sigma$ is an absolute forgetting-weighted threshold;
the forgetting factor defaults to $k=1/n$.}
\label{tab:datasets}
\begin{tabular}{@{}llrll@{}}
\toprule
ID & Description & $n$ & $\sigma$ (minSup) & $k$ \\
\midrule
DB1 & Financial series (OPF-Miner) & 5\,842 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB2 & Financial series (OPF-Miner) & 8\,141 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB3 & Financial series (OPF-Miner) & 12\,279 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB4 & Financial series (OPF-Miner) & 23\,046 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB5 & Financial series (OPF-Miner) & 60\,000 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB6 & Financial series (OPF-Miner) & 10\,305 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB7 & Financial series (OPF-Miner) & 12\,075 & $\{2,4,\ldots,12\}$ & $1/n$ \\
DB8 & Financial series (OPF-Miner) & 14\,058 & $\{2,4,\ldots,12\}$ & $1/n$ \\
ELEC\_01 & UCI electricity (1 client) & 140\,256 & $\{2,4,8\}$ & $1/n$ \\
ELEC\_05 & UCI electricity (5 clients) & 701\,280 & $\{2,4,8\}$ & $1/n$ \\
ELEC\_10 & UCI electricity (10 clients) & 1\,402\,560 & $\{2,4,8\}$ & $1/n$ \\
\bottomrule
\end{tabular}
\end{table}
```

---

## Optional expanded columns (if space allows)

| ID | \(n\) | Domain | Scale role in this paper |
|----|------:|--------|---------------------------|
| DB1–DB4 | 5.8k–23k | Finance | Core correctness & median timing |
| DB5 | 60k | Finance | Largest single financial series |
| DB6–DB8 | 10k–14k | Finance | Cross-series robustness |
| ELEC\_01 | 140k | Smart-meter load | Medium-scale Adaptive / CPC |
| ELEC\_05 | 701k | Smart-meter load | Multi-client stress |
| ELEC\_10 | 1.40M | Smart-meter load | Largest scale; CPC/Gallop isolation |

---

## Parameter protocol (recommended Experimental Setup paragraph)

> Unless otherwise stated, the forgetting factor is set to \(k=1/n\).  
> On DB1–DB8 we report results for \(\sigma\in\{2,4,6,8,10,12\}\).  
> On electricity scale inputs we emphasize \(\sigma\in\{2,4,8\}\) for wall-clock feasibility at \(n\sim 10^5\)–\(10^6\), with selected higher thresholds for ablation.  
> Each configuration is repeated multiple times; we report the **median** runtime and peak heap where applicable.  
> All compared algorithms (OPF-Miner baseline, HJ-only, Adaptive Fast OPF) use identical \((t,\sigma,k)\) and produce matching frequent-pattern sets under the semantic-equivalence lemma.

---

## Measurement notes for \(n\)

| File | Measured \(n\) (token count) |
|------|-----------------------------:|
| `data/benchmark/DB1.txt` | 5 842 |
| `data/benchmark/DB2.txt` | 8 141 |
| `data/benchmark/DB3.txt` | 12 279 |
| `data/benchmark/DB4.txt` | 23 046 |
| `data/benchmark/DB5.txt` | 60 000 |
| `data/benchmark/DB6.txt` | 10 305 |
| `data/benchmark/DB7.txt` | 12 075 |
| `data/benchmark/DB8.txt` | 14 058 |
| `ELEC_01clients_concat.txt` | 140 256 |
| `ELEC_05clients_concat.txt` | 701 280 |
| `ELEC_10clients_concat.txt` | 1 402 560 |

---

*If the camera-ready version requires ticker-level names (e.g. specific equity symbols), replace the Description column using the OPF-Miner upstream labels without changing \(n\).*

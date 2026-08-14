# Data

| Path | Use | Tracked |
|---|---|---|
| `benchmark/DB1.txt` ... `DB8.txt` | Main OPF/HJ benchmark | Yes |
| `DB9/` | RQ6, 400 financial series | Yes |
| `synthetic/fom_hypothesis_probe/` | BM/WB mechanism probes | Yes |
| `smoke/DB1.txt` | Fast CI fixture | Yes |
| `electricity_raw/` | UCI raw download | No |
| `electricity_clean/` | Preprocessor output | No |
| `electricity_scale/` | Generated 1/5/10-client inputs | No |

The Java miners consume whitespace-separated numeric `.txt` files. Use
`-DfileRegex` or keep unrelated text outside an input directory.

DB1-DB8 are the financial series used by the OPF-Miner study. The manuscript
identifies the upstream location as:

`https://github.com/wuc567/Pattern-Mining/tree/master/OPF-Miner`

Dataset provenance and redistribution review are recorded in
`manifests/datasets.csv`; exact bundled-file hashes are in
`manifests/checksums.sha256`.

The large ElectricityLoadDiagrams20112014 archive is intentionally excluded.
Prepare it with:

```powershell
python scripts/preprocessing/preprocess_electricity_load_diagrams.py `
  --input data/electricity_raw/LD2011_2014.txt `
  --output-dir data/electricity_clean
```

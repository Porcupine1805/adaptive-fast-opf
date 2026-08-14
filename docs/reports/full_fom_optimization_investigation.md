# Bao cao dieu tra va toi uu Full-FOM

Ngay thuc hien: 2026-08-12

## 1. Ket luan chinh

Van de khong nam o Hash-Indexed Join. Hai diem nghen la bitmap gia-sparse va WSB dat sai vi tri. Bitmap cu cap phat mot `long[]` lien tuc tu block dau den block cuoi, sao chep bitmap cua `q`, tao bitmap dich cua `p`, va quet ca cac word bang 0. WSB cu duoc kiem tra sau khi `p.prefixSupport` va `q.suffixSupport` deu da lon hon hoac bang `minSup`; do `exp(k) > 1`, can `min(pSupport * exp(k), qSupport)` hau nhu khong the nho hon `minSup`.

Khong nen dinh nghia Full-FOM la "ep ca ba kernel chay tren moi candidate". Cau hinh do van co overhead cong don. Huong giai quyet co co so thuc nghiem la mot composite thich nghi:

- Hash join luon bat de loai cap pattern khong tuong thich.
- WSB duoc lay mau theo generation va chi tiep tuc neu ty le prune du lon.
- Bitmap duoc chon theo chi phi uoc luong tu occurrence count, density va join fan-out; neu khong dat diem hoa von thi dung scalar two-pointer.

Day van la mot Full-FOM co ca ba chien luoc trong cung engine, nhung no toi uu lich thuc thi thay vi mac dinh rang loi ich cua ba chien luoc la cong tinh.

## 2. Sua doi da thuc hien

Tep `algorithm_benchmark/FOMAblationFlags.java` da duoc sua nhu sau:

1. Bitmap luu `blockIds[]` va `words[]` chi cho non-zero word; khong con mang lien tuc tu `minBlock` den `maxBlock`.
2. Bitmap dich cua `p` va bitmap suffix cua `q` duoc tao truc tiep tu occurrence, khong tao bitmap goc roi `copy()`/shift them mot mang lon.
3. Giao bitmap dung hai con tro tren block ID va chi quet block hien huu.
4. WSB duoc chuyen sau group-compatibility test.
5. WSB bo sung can an toan theo mien giao chi so: so match toi da nhan trong so lon nhat trong mien. Can nay chi over-estimate, nen prune khong lam mat frequent pattern.
6. Full-adaptive lay mau toi da 64 pair/pattern de quyet dinh WSB va bitmap, tranh quet lai toan bo generation chi de chon kernel.
7. Bo sung counter `BitmapFusionPairs`, `ScalarFusionPairs`, `RangeBoundPrunes` va tham so `fileRegex`, `minsupList` de probe co lap.

Script tai lap: `algorithm_benchmark/run_full_fom_optimization_probe.ps1`. Script chay theo tung round va xoay thu tu cau hinh de giam thien lech do nhiet do/tan so CPU.

## 3. Kiem chung co che

Tren ElectricityLoadDiagrams, 1 client:

- WSB moi prune 2,831 pair tai `minSup=2` va 470 pair tai `minSup=4`; WSB cu prune 0.
- Canonical output giua Hash-only va Full-adaptive khop SHA-256 tuyet doi tai ca hai nguong.
- Bitmap nen giam luong word cap phat tu quy mo hang ty word cua implementation cu xuong quy mo khoang 0.6--1.1 trieu word trong probe 1 client khi ep bitmap.

Tren 10 clients, `minSup=4`, probe xoay thu tu ba lan:

| Configuration | Mean time (s) | Mean memory (MB) | WSB prunes | Bitmap words allocated |
|---|---:|---:|---:|---:|
| Hash-only | 4.133 | 539.65 | 0 | 0 |
| Hash+WSB | 4.191 | 521.95 | 5,467 | 0 |
| Hash+compressed bitmap | 4.430 | 687.66 | 0 | 42,536,715 |
| Full-static (bat ca ba) | 4.670 | 657.97 | 5,467 | 42,488,598 |
| Full-adaptive | 4.370 | 549.51 | 5 | 0 |

Bang nay xac nhan viec bitmap va WSB da hoat dong dung ve co che, nhung loi ich khong cong tinh tren workload nay. Full-static giam rat lon so voi code cu, song van chua vuot Hash-only.

## 4. Probe cuoi sau khi giam overhead dispatcher

Probe cuoi chi so sanh Hash-only va Full-adaptive, chay nam JVM doc lap, xoay thu tu, cung `-Xms512m -Xmx16g`, cung 10 clients va `minSup=4`:

| Configuration | Runs | Mean (s) | SD (s) | Min--max (s) | Mean memory (MB) |
|---|---:|---:|---:|---:|---:|
| Hash-only | 5 | 4.356 | 0.136 | 4.212--4.530 | 525.42 |
| Full-adaptive | 5 | 4.211 | 0.201 | 3.944--4.452 | 535.63 |

Full-adaptive nhanh hon trung binh 3.34% va dung nhieu bo nho hon 1.94%. Chenh lech runtime con nho so voi do dao dong cua nam lan chay, vi vay day la bang chung so bo ve diem hoa von, chua du de claim mot cach co y nghia thong ke rang Full luon nhanh hon Hash-only.

Trong probe nay dispatcher da kiem tra WSB 1,172 lan, prune 5 pair, va chon scalar cho tat ca 258,560 fusion pair. Bitmap khong duoc kich hoat vi khong vuot nguong chi phi. Day la hanh vi dung cua composite thich nghi: mot chien luoc co san nhung khong bi ep chay khi du bao se lam cham workload.

Canonical Hash-only va Full-adaptive khop raw SHA-256:

`7014DBE313081A68BE135F2C01450577688468E08E0855C3862DD95E84874C13`

So voi Full-FOM cu tren cung dataset/nguong (40.377941 s, 8,836 MB), Full-adaptive moi dat 4.211287 s va 535.632 MB: nhanh hon 9.59 lan va giam sampled memory 16.50 lan. Frequent-pattern count giu nguyen 168,603.

## 5. Cach trinh bay khoa hoc trong manuscript

Khong nen viet "Full-FOM always activates all three optimizations" hoac "the three gains are additive". Cach viet phu hop voi code va thuc nghiem moi la:

> Full-FOM is an adaptive composite. Hash-indexed enumeration is always active, whereas weighted-bound checks and compressed-word bitmap fusion are dispatched per generation using sampled cost indicators. This policy preserves exact output while avoiding optimization overhead when candidate fan-out or occurrence density is insufficient to amortize it.

Claim an toan hien tai:

- Bitmap da duoc sua dung thanh non-zero-word representation va loai bo hien tuong bo nho hang GB.
- WSB moi co pruning that va bao toan output.
- Adaptive composition co the dat ngang hoac nhinh hon Hash-only o workload lon, nhung can nhieu lan lap hon va nhieu che do density/fan-out hon de ket luan ve toc do.
- Dong gop manh nhat van la hash-indexed join; dong gop cua Full-FOM la cost-aware composition va phan tich diem hoa von, khong phai loi hua "bat nhieu option hon luon nhanh hon".

## 6. Viec con lai truoc khi thay ket qua chinh cua bai bao

1. Chay lai DB1--DB8, it nhat 10 lan/configuration, voi thu tu random/rotated va warm-up tach rieng.
2. Chay Electricity 1/5/10 clients tai tat ca `minSup`, bao cao mean, median, SD va confidence interval.
3. Bo sung workload co occurrence density va join fan-out cao de xac dinh vung bitmap duoc dispatcher kich hoat va co loi.
4. Neu muon bitmap thang ro hon, chuyen occurrence tu `List<Integer>` sang primitive `int[]` va dung hybrid container: dense contiguous words cho block day, non-zero-word list cho block thua.
5. Chi thay cac bang/figure manuscript sau khi canonical cua toan bo cau hinh moi da duoc xac minh.

## 7. Tep ket qua

- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_optimization_probe_10clients_final/probe_summary.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_optimization_probe_10clients_final/canonical_equivalence.csv`
- `algorithm_benchmark/results_full_20260811/benchmark/full_fom_optimization_probe_10clients_rotated/probe_summary.csv`
- `algorithm_benchmark/run_full_fom_optimization_probe.ps1`


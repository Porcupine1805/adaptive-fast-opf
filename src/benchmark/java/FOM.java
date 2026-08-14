import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class FOM {

    private static final String INPUT_DIR = System.getProperty("input", "data/benchmark");
    private static final String OUTPUT_SUMMARY_FILE = System.getProperty("output", "results/FOM_summary.csv");
    private static final String LOG_FILE = System.getProperty("log", "FOM.log");
    private static final String CANONICAL_DIR = System.getProperty("canonical", "");
    private static final double[] MIN_SUP_ARRAY = {2.0, 4.0, 6.0, 8.0, 10.0, 12.0};

    public static final class MetricsTracker {
        public static long startTimeNano = 0, endTimeNano = 0;
        public static double maxMemoryMB = 0.0;
        public static long candidatePatternsCount = 0, patternFusionsCount = 0;
        public static long supportCalculationsCount = 0, frequentPatternsCount = 0;

        public static void reset() {
            candidatePatternsCount = patternFusionsCount = supportCalculationsCount = frequentPatternsCount = 0;
            maxMemoryMB = 0.0;
            endTimeNano = 0;
            startTimeNano = System.nanoTime();
        }

        public static void stopTimer() { endTimeNano = System.nanoTime(); }

        public static void checkMemory() {
            double usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0;
            if (usedMemory > maxMemoryMB) maxMemoryMB = usedMemory;
        }

        public static double getExecutionTimeSeconds() {
            return (endTimeNano - startTimeNano) / 1_000_000_000.0;
        }
    }

    public static final class SparseBitmap {
        public final int minBlock; public final int maxBlock; public final long[] blocks;

        public SparseBitmap(int minBlock, int maxBlock, long[] blocks) {
            this.minBlock = minBlock; this.maxBlock = maxBlock; this.blocks = blocks;
        }

        public static SparseBitmap create(List<Integer> positions) {
            if (positions.isEmpty()) return new SparseBitmap(0, -1, new long[0]);
            int minB = positions.get(0) >>> 6, maxB = positions.get(positions.size() - 1) >>> 6;
            long[] blks = new long[maxB - minB + 1];
            for (int pos : positions) blks[(pos >>> 6) - minB] |= (1L << (pos & 63));
            return new SparseBitmap(minB, maxB, blks);
        }

        public SparseBitmap copy() {
            long[] nb = new long[blocks.length]; System.arraycopy(blocks, 0, nb, 0, blocks.length);
            return new SparseBitmap(minBlock, maxBlock, nb);
        }

        public void clearBit(int index) {
            int b = index >>> 6;
            if (b >= minBlock && b <= maxBlock) blocks[b - minBlock] &= ~(1L << (index & 63));
        }

        public SparseBitmap shiftForwardOneStep() {
            if (blocks.length == 0) return this;
            boolean spill = (blocks[blocks.length - 1] >>> 63) != 0;
            int newMaxBlock = spill ? maxBlock + 1 : maxBlock;
            long[] shifted = new long[newMaxBlock - minBlock + 1];
            long ghostBit = 0L;
            for (int i = 0; i < blocks.length; i++) {
                shifted[i] = (blocks[i] << 1) | ghostBit;
                ghostBit = blocks[i] >>> 63;
            }
            if (spill) shifted[shifted.length - 1] = ghostBit;
            return new SparseBitmap(minBlock, newMaxBlock, shifted);
        }
    }

    public static final class ForgettingMechanism {
        private final double[] decayWeights;
        public ForgettingMechanism(int n, double k) {
            this.decayWeights = new double[n];
            for (int j = 0; j < n; j++) this.decayWeights[j] = Math.exp(-k * (n - (j + 1)));
        }
        public double getWeight(int index) { return decayWeights[index]; }
    }

    public static final class PatternResult {
        public final String signature; public final double support;
        public PatternResult(int[] rank, double support) { this.signature = Arrays.toString(rank); this.support = support; }
    }

    public static final class IntArrayKey {
        private final int[] array; private final int hash;
        public IntArrayKey(int[] array) { this.array = array; this.hash = Arrays.hashCode(array); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Arrays.equals(array, ((IntArrayKey) o).array);
        }
        @Override public int hashCode() { return hash; }
    }

    public static final class PatternCandidate {
        private final int[] rankSequence; private final double support; private final int group;
        public final int[] prefix; public final int[] suffix;
        
        public double prefixSupport;
        public double suffixSupport;
        
        public final SparseBitmap occurrences; public SparseBitmap sufqBitmap;

        public PatternCandidate(int[] rankSequence, SparseBitmap occurrences, double support, int group) {
            this.rankSequence = rankSequence; this.support = support; this.group = group;
            this.prefix = OrderPreservingUtils.getPrefix(rankSequence);
            this.suffix = OrderPreservingUtils.getSuffix(rankSequence);
            this.occurrences = occurrences;
            this.prefixSupport = support;
            this.suffixSupport = support;
        }

        public int[] getRankSequence() { return rankSequence; }
        public double getSupport() { return support; }
        public int getGroup() { return group; }
        public void clearBitmaps() { this.sufqBitmap = null; }
    }

    public static final class OrderPreservingUtils {
        public static int[] getOrder(int[] origSeq) {
            int[] sq = Arrays.copyOf(origSeq, origSeq.length), o = new int[sq.length], t = Arrays.copyOf(sq, sq.length);
            Arrays.sort(t); int mx = t[t.length - 1] + 1;
            for (int j = 0; j < sq.length; j++) {
                int mn = mx, idx = -1;
                for (int i = 0; i < sq.length; i++) { if (sq[i] < mn && sq[i] != Integer.MIN_VALUE) { mn = sq[i]; idx = i; } }
                sq[idx] = Integer.MIN_VALUE; o[idx] = j + 1;
            }
            return o;
        }
        public static int[] getPrefix(int[] p) { return getOrder(Arrays.copyOfRange(p, 0, p.length - 1)); }
        public static int[] getSuffix(int[] p) { return getOrder(Arrays.copyOfRange(p, 1, p.length)); }
    }

    public static final class FusionEngine {
        private final ForgettingMechanism forgettingMechanism;
        private final List<Double> timeSeries;
        private final double minSup;
        private final double kVal;

        public FusionEngine(ForgettingMechanism forgettingMechanism, List<Double> timeSeries, double minSup, double kVal) {
            this.forgettingMechanism = forgettingMechanism; this.timeSeries = timeSeries;
            this.minSup = minSup; this.kVal = kVal;
        }

        public List<PatternCandidate> generateNextGeneration(List<PatternCandidate> currentGen) {
            currentGen.sort((a, b) -> Double.compare(b.getSupport(), a.getSupport()));

            Map<IntArrayKey, List<PatternCandidate>> prefixMap = new HashMap<>();
            for (PatternCandidate c : currentGen) {
                c.prefixSupport = c.getSupport();
                c.suffixSupport = c.getSupport();
                c.sufqBitmap = null;
                prefixMap.computeIfAbsent(new IntArrayKey(c.prefix), k -> new ArrayList<>()).add(c);
            }

            List<PatternCandidate> nextGen = new ArrayList<>();
            double expK = Math.exp(kVal);
            double expMinusK = Math.exp(-kVal);
            
            for (PatternCandidate p : currentGen) {
                if (p.prefixSupport < minSup) continue; 

                int m = p.getRankSequence().length;
                List<PatternCandidate> matchingQs = prefixMap.getOrDefault(new IntArrayKey(p.suffix), Collections.emptyList());

                // consumedMaskP tracks which bits of p.occurrences have been consumed
                // across Q iterations — replicates shiftedP.clearBit(j) from the original code
                // without allocating a new SparseBitmap object per P.
                // Size matches p.occurrences.blocks exactly. Initialized to zero (no bits consumed).
                long[] consumedMaskP = (matchingQs.isEmpty()) ? null
                        : new long[p.occurrences.blocks.length];

                for (PatternCandidate q : matchingQs) {
                    if (p.prefixSupport < minSup) break;
                    if (q.suffixSupport < minSup) continue;

                    double upperBound = Math.min(p.prefixSupport * expK, q.suffixSupport);
                    if (upperBound < minSup) continue;

                    if (m > 2) {
                        int pG = p.getGroup(), qG = q.getGroup();
                        boolean valid = ((pG == 1 || pG == 3) && (qG == 1 || qG == 2)) || ((pG == 2 || pG == 4) && (qG == 3 || qG == 4));
                        if (!valid) continue;
                    }

                    MetricsTracker.patternFusionsCount++;
                    MetricsTracker.candidatePatternsCount += (p.getRankSequence()[0] == q.getRankSequence()[m - 1]) ? 2 : 1;

                    if (q.sufqBitmap == null) q.sufqBitmap = q.occurrences.copy();

                    fuseAndEvaluate(p, q, consumedMaskP, nextGen, expMinusK);
                }
            }
            for (PatternCandidate c : currentGen) c.clearBitmaps();
            return nextGen;
        }

        private void fuseAndEvaluate(PatternCandidate pCand, PatternCandidate qCand,
                                      long[] consumedMaskP,
                                      List<PatternCandidate> nextGen, double expMinusK) {
            int[] pRank = pCand.getRankSequence(), qRank = qCand.getRankSequence();
            int m = pRank.length;
            SparseBitmap P = pCand.occurrences;
            SparseBitmap Q = qCand.sufqBitmap;

            // After shift by +1, P spans blocks [P.minBlock, P.maxBlock+1].
            int overlapMin = Math.max(P.minBlock, Q.minBlock);
            int overlapMax = Math.min(P.maxBlock + 1, Q.maxBlock);

            if (overlapMin > overlapMax) return;

            List<Integer> rPositions = new ArrayList<>(), hPositions = new ArrayList<>();
            double rSupport = 0.0, hSupport = 0.0;
            double totalSupportRemovedP = 0.0, totalSupportRemovedQ = 0.0;

            // ghostBit: MSB spill from the block just before the overlap window.
            // consumedMaskP is indexed by (b - P.minBlock), same layout as P.blocks.
            long ghostBit = 0L;
            if (overlapMin > P.minBlock) {
                int prevIdx = overlapMin - 1 - P.minBlock;
                long rawPrev = P.blocks[prevIdx] & ~consumedMaskP[prevIdx];
                ghostBit = rawPrev >>> 63;
            }

            for (int b = overlapMin; b <= overlapMax; b++) {
                // Compute shifted P word: apply consumed mask to exclude already-used bits.
                long rawP = 0L;
                long shiftedPWord;
                if (b <= P.maxBlock) {
                    int idx = b - P.minBlock;
                    rawP = P.blocks[idx] & ~consumedMaskP[idx];
                }
                shiftedPWord = (rawP << 1) | ghostBit;
                ghostBit = rawP >>> 63;

                if (b < Q.minBlock || b > Q.maxBlock) continue;
                long qWord = Q.blocks[b - Q.minBlock];
                long word = shiftedPWord & qWord;

                while (word != 0) {
                    MetricsTracker.supportCalculationsCount++;
                    long tBit = word & -word;
                    int j = (b << 6) + Long.numberOfTrailingZeros(tBit);
                    double weightQ = forgettingMechanism.getWeight(j);
                    double weightP = weightQ * expMinusK;

                    if (pRank[0] == qRank[m - 1]) {
                        double t_first = timeSeries.get(j - m), t_last = timeSeries.get(j);
                        if (t_first < t_last) { rPositions.add(j); rSupport += weightQ; }
                        else if (t_first > t_last) { hPositions.add(j); hSupport += weightQ; }
                    } else { rPositions.add(j); rSupport += weightQ; }

                    // Mark bit j-1 as consumed in P (equivalent to shiftedP.clearBit(j) in original).
                    // consumedMaskP is shared across Q iterations for this P — correct proactive
                    // consumption: a position used by one Q cannot be reused by the next Q.
                    int pBitPos = j - 1;
                    int pBlockIdx = (pBitPos >>> 6) - P.minBlock;
                    if (pBlockIdx >= 0 && pBlockIdx < consumedMaskP.length)
                        consumedMaskP[pBlockIdx] |= (1L << (pBitPos & 63));

                    Q.clearBit(j);
                    totalSupportRemovedP += weightP;
                    totalSupportRemovedQ += weightQ;
                    word ^= tBit;
                }
            }

            pCand.prefixSupport -= totalSupportRemovedP;
            qCand.suffixSupport -= totalSupportRemovedQ;

            int newGroup = (m == 2) ? ((pRank[0]<pRank[1] && qRank[0]<qRank[1]) ? 1 : (pRank[0]<pRank[1] && qRank[0]>qRank[1]) ? 2 : (pRank[0]>pRank[1] && qRank[0]<qRank[1]) ? 3 : 4) : pCand.getGroup();

            if (pRank[0] == qRank[m - 1]) {
                if (rSupport >= minSup) {
                    int[] rRank = new int[m + 1]; rRank[0] = pRank[0]; rRank[m] = qRank[m - 1] + 1;
                    for (int i = 1; i < m; i++) rRank[i] = (qRank[i - 1] < pRank[0]) ? qRank[i - 1] : qRank[i - 1] + 1;
                    nextGen.add(new PatternCandidate(rRank, SparseBitmap.create(rPositions), rSupport, newGroup));
                    MetricsTracker.frequentPatternsCount++;
                }
                if (hSupport >= minSup) {
                    int[] hRank = new int[m + 1]; hRank[0] = pRank[0] + 1; hRank[m] = qRank[m - 1];
                    for (int i = 1; i < m; i++) hRank[i] = (pRank[i] < qRank[m - 1]) ? pRank[i] : pRank[i] + 1;
                    nextGen.add(new PatternCandidate(hRank, SparseBitmap.create(hPositions), hSupport, newGroup));
                    MetricsTracker.frequentPatternsCount++;
                }
            } else {
                if (rSupport >= minSup) {
                    int[] rRank = new int[m + 1];
                    if (pRank[0] < qRank[m - 1]) {
                        rRank[0] = pRank[0]; rRank[m] = qRank[m - 1] + 1;
                        for (int i = 1; i < m; i++) rRank[i] = (qRank[i - 1] < pRank[0]) ? qRank[i - 1] : qRank[i - 1] + 1;
                    } else {
                        rRank[0] = pRank[0] + 1; rRank[m] = qRank[m - 1];
                        for (int i = 1; i < m; i++) rRank[i] = (pRank[i] < qRank[m - 1]) ? pRank[i] : pRank[i] + 1;
                    }
                    nextGen.add(new PatternCandidate(rRank, SparseBitmap.create(rPositions), rSupport, newGroup));
                    MetricsTracker.frequentPatternsCount++;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(INPUT_DIR);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        File outFile = new File(OUTPUT_SUMMARY_FILE);
        File outParent = outFile.getParentFile();
        if (outParent != null) outParent.mkdirs();

        try (BufferedWriter csvWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(OUTPUT_SUMMARY_FILE), StandardCharsets.UTF_8));
             PrintWriter logWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                     new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8)))) {

            // Ghi header CSV
            csvWriter.write("Dataset,minsup,Time_s,MaxMem_MB,Candidates,Fusions,SupportOps,FreqPatterns\n");

            // Ghi log bắt đầu
            String startMsg = "FOM started at " + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SS").format(new Date());
            logWriter.println(startMsg);
            System.out.println(startMsg);

            // In tiêu đề bảng
            System.out.printf(Locale.US, "%-15s\t%-8s\t%-10s\t%-15s\t%-20s\t%-15s\t%-20s\t%-15s%n",
                    "Dataset", "minsup", "Time(s)", "MaxMem(MB)", "Candidates", "Fusions", "SupportOps", "FreqPatterns");
            logWriter.printf(Locale.US, "%-15s\t%-8s\t%-10s\t%-15s\t%-20s\t%-15s\t%-20s\t%-15s%n",
                    "Dataset", "minsup", "Time(s)", "MaxMem(MB)", "Candidates", "Fusions", "SupportOps", "FreqPatterns");

            for (File file : files) {
                for (double currentMinSup : MIN_SUP_ARRAY) {
                    processSingleFile(file, currentMinSup, csvWriter, logWriter);
                    System.gc();
                }
            }

            String endMsg = "FOM finished with exit code 0";
            logWriter.println(endMsg);
            System.out.println(endMsg);
        }
    }

    private static void processSingleFile(File file, double minSup, BufferedWriter csvWriter, PrintWriter logWriter) throws IOException {
        MetricsTracker.reset();
        List<Double> timeSeries = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                for (String token : line.trim().split("\\s+")) {
                    if (!token.isEmpty()) timeSeries.add(Double.parseDouble(token));
                }
            }
        }
        int n = timeSeries.size(); if (n < 2) return;

        double kVal;
        String kProp = System.getProperty("k");
        if (kProp != null && !kProp.isEmpty()) {
            kVal = Double.parseDouble(kProp);
        } else {
            kVal = 1.0 / n;
        }
        ForgettingMechanism forgettingMechanism = new ForgettingMechanism(n, kVal);
        FusionEngine engine = new FusionEngine(forgettingMechanism, timeSeries, minSup, kVal);
        Map<String, Double> canonicalPatterns = CANONICAL_DIR.isEmpty() ? null : new TreeMap<>();

        List<Integer> ascPositions = new ArrayList<>(), descPositions = new ArrayList<>();
        double ascSup = 0.0, descSup = 0.0;
        for (int i = 1; i < n; i++) {
            double prev = timeSeries.get(i - 1), curr = timeSeries.get(i), weight = forgettingMechanism.getWeight(i);
            if (curr > prev) { ascPositions.add(i); ascSup += weight; } 
            else if (curr < prev) { descPositions.add(i); descSup += weight; }
        }

        List<PatternCandidate> currentGeneration = new ArrayList<>();
        if (ascSup >= minSup) { currentGeneration.add(new PatternCandidate(new int[]{1, 2}, SparseBitmap.create(ascPositions), ascSup, 1)); MetricsTracker.frequentPatternsCount++; }
        if (descSup >= minSup) { currentGeneration.add(new PatternCandidate(new int[]{2, 1}, SparseBitmap.create(descPositions), descSup, 2)); MetricsTracker.frequentPatternsCount++; }
        recordCanonical(canonicalPatterns, currentGeneration);
        MetricsTracker.candidatePatternsCount += 2;

        while (!currentGeneration.isEmpty()) {
            MetricsTracker.checkMemory();
            currentGeneration = engine.generateNextGeneration(currentGeneration);
            recordCanonical(canonicalPatterns, currentGeneration);
        }

        MetricsTracker.stopTimer(); MetricsTracker.checkMemory();
        writeCanonicalOutput(file, minSup, canonicalPatterns);

        double timeSec = MetricsTracker.getExecutionTimeSeconds();
        double memMB = MetricsTracker.maxMemoryMB;

        String line = String.format(Locale.US, "%-15s\t%-8.1f\t%-10.6f\t%-15.2f\t%-20d\t%-15d\t%-20d\t%-15d",
                file.getName(), minSup, timeSec, memMB,
                MetricsTracker.candidatePatternsCount,
                MetricsTracker.patternFusionsCount,
                MetricsTracker.supportCalculationsCount,
                MetricsTracker.frequentPatternsCount);
        System.out.println(line);
        logWriter.println(line);

        csvWriter.write(String.format(Locale.US, "%s,%.1f,%.6f,%.2f,%d,%d,%d,%d%n",
                file.getName(), minSup, timeSec, memMB,
                MetricsTracker.candidatePatternsCount,
                MetricsTracker.patternFusionsCount,
                MetricsTracker.supportCalculationsCount,
                MetricsTracker.frequentPatternsCount));
        csvWriter.flush();
    }

    private static void recordCanonical(Map<String, Double> canonicalPatterns, List<PatternCandidate> generation) {
        if (canonicalPatterns == null) return;
        for (PatternCandidate p : generation) {
            canonicalPatterns.put(Arrays.toString(p.getRankSequence()), p.getSupport());
        }
    }

    private static void writeCanonicalOutput(File file, double minSup, Map<String, Double> canonicalPatterns) throws IOException {
        if (canonicalPatterns == null) return;
        File dir = new File(CANONICAL_DIR);
        dir.mkdirs();
        String dataset = file.getName().replaceFirst("\\.txt$", "");
        String minSupLabel = String.format(Locale.US, "%.1f", minSup).replace('.', 'p');
        File out = new File(dir, dataset + "_minsup_" + minSupLabel + ".csv");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            writer.write("pattern,support\n");
            for (Map.Entry<String, Double> entry : canonicalPatterns.entrySet()) {
                writer.write(String.format(Locale.US, "\"%s\",%.10f%n", entry.getKey(), entry.getValue()));
            }
        }
    }
}

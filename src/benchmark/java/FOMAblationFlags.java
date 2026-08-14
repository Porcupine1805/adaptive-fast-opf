/**
 * FOMAblationFlags — Adaptive Fast OPF benchmark entry point.
 *
 * Pipeline (paper direction):
 *   Stage 1: Hash-Indexed Join (HJ) on order-preserving prefix keys
 *   Stage 2: Cheap-Prune Cascade (CPC) — residual / span / card [optional range]
 *   Stage 3: Sorted-list fusion, optionally Gallop under occurrence skew
 *   Policy:  Adaptive staging after HJ (problem quantities only; no wall-clock control)
 *
 * Naming note: system properties still use historical prefixes {@code adaptiveWsb*} for CPC
 * (WSB was replaced by CPC). Bitmap fusion is disabled ({@code SPARSE=false}; prefer
 * {@code -DbitmapPolicy=never}).
 *
 * Modes:
 *   baseline | hash_only | adaptive | full | ... (ablation combinations)
 * Recommended paper configs:
 *   -Dmode=hash_only  -DbitmapPolicy=never -DwsbPolicy=never
 *   -Dmode=adaptive   -DbitmapPolicy=never -DwsbPolicy=cost
 *     (CPC + smart intersect + staged defaults when mode=adaptive)
 */
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FOMAblationFlags {
    private static final String INPUT_DIR = System.getProperty("input", "data/benchmark");
    private static final String OUTPUT_SUMMARY_FILE = System.getProperty("output", "results/runs/FOMAblation_summary.csv");
    private static final String LOG_FILE = System.getProperty("log", "results/runs/FOMAblation.log");
    private static final String CANONICAL_DIR = System.getProperty("canonical", "");
    private static final String GENERATION_OUTPUT_FILE = System.getProperty("generationOutput", "");
    private static final String MODE = System.getProperty("mode", "adaptive");
    private static final boolean COST_ADAPTIVE = MODE.equals("adaptive");
    private static final boolean DIAGNOSTIC = Boolean.parseBoolean(System.getProperty("diagnostic", "false"));
    // Bitmap fusion removed from Adaptive narrative — default never.
    private static final String BITMAP_POLICY = System.getProperty("bitmapPolicy", "never");
    // Adaptive policy (paper): HJ → optional CPC → optional Gallop.
    // Bitmap path is disabled (SPARSE=false). CPC property names retain adaptiveWsb* prefix.
    private static final int BITMAP_MIN_OCCURRENCES = Integer.getInteger("bitmapMinOccurrences",
            COST_ADAPTIVE ? 256 : 512);
    private static final double BITMAP_MIN_DENSITY = Double.parseDouble(
            System.getProperty("bitmapMinDensity", COST_ADAPTIVE ? "0.20" : "0.25"));
    private static final double BITMAP_MIN_FANOUT = Double.parseDouble(
            System.getProperty("bitmapMinFanout", COST_ADAPTIVE ? "1.0" : "1.0"));
    private static final int BITMAP_SAMPLE_SIZE = Integer.getInteger("bitmapSampleSize", 64);
    // "cost"/"adaptive" enable CPC decision path under mode=adaptive; use never for pure HJ.
    private static final String WSB_POLICY = System.getProperty("wsbPolicy",
            COST_ADAPTIVE ? "cost" : MODE.equals("full") ? "cost" : "never");
    private static final int WSB_SAMPLE_SIZE = Integer.getInteger("wsbSampleSize", 8);
    private static final double WSB_MIN_PRUNE_RATE = Double.parseDouble(
            System.getProperty("wsbMinPruneRate", "0.05"));
    /** Soft safety cap only; primary bitmap gate is P-amortization after HJ. */
    private static final long ADAPTIVE_BITMAP_MAX_CONVERSION_UNITS =
            Long.getLong("adaptiveBitmapMaxConversionUnits", 262_144L);
    private static final long ADAPTIVE_WSB_MIN_PAIRS = Long.getLong("adaptiveWsbMinPairs", 32L);
    private static final double ADAPTIVE_WSB_MIN_GAIN = Double.parseDouble(
            System.getProperty("adaptiveWsbMinGain", "1.25"));
    private static final double ADAPTIVE_WSB_MIN_SUPPORT_RATIO = Double.parseDouble(
            System.getProperty("adaptiveWsbMinSupportRatio", "0.0"));
    private static final double ADAPTIVE_WSB_KEEP_PRUNE_RATE = Double.parseDouble(
            System.getProperty("adaptiveWsbKeepPruneRate", "0.10"));
    private static final boolean ADAPTIVE_WSB_USE_RANGE = Boolean.parseBoolean(
            System.getProperty("adaptiveWsbUseRange", "false"));
    /** Range-bound only if supportBound < factor * minSup (tight residual zone). */
    private static final double ADAPTIVE_WSB_TIGHT_FACTOR = Double.parseDouble(
            System.getProperty("adaptiveWsbTightFactor", "2.5"));
    /**
     * Cheap-Prune Cascade (CPC): O(1) card/span bounds before binary range-count,
     * and skip binary when occurrence lists are too long for ROI.
     */
    private static final boolean ADAPTIVE_WSB_CHEAP_PRUNE = Boolean.parseBoolean(
            System.getProperty("adaptiveWsbCheapPrune", COST_ADAPTIVE ? "true" : "false"));
    /** Binary range-count only if supportBound < this factor * minSup (CPC tight gate). */
    private static final double ADAPTIVE_WSB_CPC_TIGHT = Double.parseDouble(
            System.getProperty("adaptiveWsbCpcTight", "1.5"));
    /** Skip binary range-count when |Occ_p| + |Occ_q| exceeds this (fuse is cheaper). */
    private static final int ADAPTIVE_WSB_MAX_OCC_FOR_RANGE = Integer.getInteger(
            "adaptiveWsbMaxOccForRange", 50_000);
    /**
     * Smart sorted-list intersection for Adaptive scalar fuse:
     * galloping (exponential search) when one cursor lags the other.
     */
    private static final boolean ADAPTIVE_SMART_INTERSECT = Boolean.parseBoolean(
            System.getProperty("adaptiveSmartIntersect", COST_ADAPTIVE ? "true" : "false"));
    /**
     * Staged Adaptive: HJ → decide CPC → (if CPC) decide Gallop.
     * When false, legacy global on/off flags apply to every generation.
     */
    private static final boolean ADAPTIVE_STAGED_POLICY = Boolean.parseBoolean(
            System.getProperty("adaptiveStagedPolicy", "true"));
    /** Min compatible pairs (post-HJ) before CPC is considered. */
    private static final long ADAPTIVE_CPC_MIN_PAIRS = Long.getLong("adaptiveCpcMinPairs", 256L);
    /** Pattern support below this×minSup counts as "tight" for CPC enable signal. */
    private static final double ADAPTIVE_CPC_SUPPORT_TIGHT = Double.parseDouble(
            System.getProperty("adaptiveCpcSupportTight", "2.5"));
    /** Min fraction of tight-support patterns to enable CPC. */
    private static final double ADAPTIVE_CPC_TIGHT_FRACTION = Double.parseDouble(
            System.getProperty("adaptiveCpcTightFraction", "0.40"));
    /** Below this N, Adaptive stays HJ-pure (CPC overhead rarely pays). */
    private static final int ADAPTIVE_CPC_MIN_N = Integer.getInteger("adaptiveCpcMinN", 20_000);
    private static final int ADAPTIVE_CPC_PROBE_PAIRS = Integer.getInteger("adaptiveCpcProbePairs", 24);
    private static final double ADAPTIVE_CPC_PROBE_MIN_PRUNE = Double.parseDouble(
            System.getProperty("adaptiveCpcProbeMinPrune", "0.12"));
    /**
     * CPC generation gate family (post-HJ):
     *   A = fail-safe majority O(1) free prunes (no magic ROI %)
     *   B = cost model: enable iff estimated r > c_check/c_fuse
     *   C = structural: N and pairCount floors only (always O(1) CPC when eligible)
     *   D = legacy probe prune-rate threshold (default 0.12)
     */
    private static final String ADAPTIVE_CPC_GATE = System.getProperty("adaptiveCpcGate", "B").trim().toUpperCase();
    /** Relative cost: one O(1) CPC check vs one scalar fuse unit (cost model B). */
    private static final double ADAPTIVE_CPC_CHECK_FUSE_RATIO = Double.parseDouble(
            System.getProperty("adaptiveCpcCheckFuseRatio", "0.08"));
    /**
     * Pair-level length ratio max(|Occ_p|,|Occ_q|)/min(...) required to count as "skewed".
     * Higher → fewer generations enable gallop (more conservative).
     */
    private static final int ADAPTIVE_GALLOP_MIN_RATIO = Integer.getInteger(
            "adaptiveGallopMinRatio", 8);
    /** Min length of the longer list for a pair to be gallop-eligible. */
    private static final int ADAPTIVE_GALLOP_MIN_OCC = Integer.getInteger(
            "adaptiveGallopMinOcc", 256);
    /** Fraction of sampled HJ pairs that must be skewed to enable gallop this generation. */
    private static final double ADAPTIVE_GALLOP_MIN_SKEW_FRACTION = Double.parseDouble(
            System.getProperty("adaptiveGallopMinSkewFraction", "0.35"));
    /** Max pairs to sample when estimating skew (cheap O(sample)). */
    private static final int ADAPTIVE_GALLOP_SAMPLE_PAIRS = Integer.getInteger(
            "adaptiveGallopSamplePairs", 48);
    /** Allow Gallop without CPC (fusion-only path; skewed-list fusion path). */
    private static final boolean ADAPTIVE_GALLOP_WITHOUT_CPC = Boolean.parseBoolean(
            System.getProperty("adaptiveGallopWithoutCpc", COST_ADAPTIVE ? "true" : "false"));
    /**
     * Conversion amortizes when 2N <= P * avgOcc / this divisor.
     * Higher divisor → harder to enable bitmap (more conservative).
     */
    private static final double ADAPTIVE_BITMAP_AMORTIZE_DIVISOR = Double.parseDouble(
            System.getProperty("adaptiveBitmapAmortizeDivisor", "4.0"));
    private static final double[] MIN_SUP_ARRAY = {2.0, 4.0, 6.0, 8.0, 10.0, 12.0};
    private static final String FILE_REGEX = System.getProperty("fileRegex", ".*\\.txt");
    private static final int JIT_WARMUP_RUNS = Integer.getInteger("jitWarmupRuns", 0);
    private static final int CASE_WARMUP_RUNS = Integer.getInteger("caseWarmupRuns", 0);

    private static final boolean HASH = MODE.equals("hash_only") || MODE.equals("hash_sparse") ||
            MODE.equals("hash_wsb") || MODE.equals("full") || COST_ADAPTIVE;
    /** Sparse Bitmap removed — fusion is Sorted List / Gallop only. */
    private static final boolean SPARSE = false;
    private static final boolean WSB = MODE.equals("wsb_only") || MODE.equals("hash_wsb") ||
            MODE.equals("sparse_wsb") || MODE.equals("full") || COST_ADAPTIVE;

    public static final class MetricsTracker {
        public static long startTimeNano = 0, endTimeNano = 0;
        public static double maxMemoryMB = 0.0;
        public static long pairChecksCount = 0, candidatePatternsCount = 0, patternFusionsCount = 0;
        public static long supportCalculationsCount = 0, frequentPatternsCount = 0;
        public static long wsbChecksCount = 0, wsbPrunesCount = 0;
        public static long sparseObjectsCount = 0, sparseWordsAllocatedCount = 0, sparseWordsScannedCount = 0;
        public static long scalarPositionComparisonsCount = 0;
        public static long bitmapFusionPairsCount = 0, scalarFusionPairsCount = 0, rangeBoundPrunesCount = 0;
        public static long adaptiveDecisionTimeNano = 0;
        public static long adaptiveBitmapDecisionTimeNano = 0, adaptiveWsbDecisionTimeNano = 0;
        public static long adaptiveBitmapLevels = 0, adaptiveScalarLevels = 0;
        public static long adaptiveWsbSampledLevels = 0, adaptiveWsbEnabledLevels = 0;
        /** CPC tier prunes (mutually exclusive causes). */
        public static long cpcResidualPrunes = 0, cpcSpanPrunes = 0, cpcCardPrunes = 0, cpcRangePrunes = 0;
        /** Smart intersection diagnostics. */
        public static long smartGallopSteps = 0, smartIntersectFusions = 0;
        /** Staged policy: generations that enabled CPC / Gallop. */
        public static long adaptiveCpcEnabledLevels = 0, adaptiveGallopEnabledLevels = 0;
        public static long adaptiveHjOnlyLevels = 0;

        public static void reset() {
            pairChecksCount = candidatePatternsCount = patternFusionsCount = supportCalculationsCount = frequentPatternsCount = 0;
            wsbChecksCount = wsbPrunesCount = 0;
            sparseObjectsCount = sparseWordsAllocatedCount = sparseWordsScannedCount = 0;
            scalarPositionComparisonsCount = 0;
            bitmapFusionPairsCount = scalarFusionPairsCount = rangeBoundPrunesCount = 0;
            adaptiveDecisionTimeNano = 0;
            adaptiveBitmapDecisionTimeNano = adaptiveWsbDecisionTimeNano = 0;
            adaptiveBitmapLevels = adaptiveScalarLevels = 0;
            adaptiveWsbSampledLevels = adaptiveWsbEnabledLevels = 0;
            cpcResidualPrunes = cpcSpanPrunes = cpcCardPrunes = cpcRangePrunes = 0;
            smartGallopSteps = smartIntersectFusions = 0;
            adaptiveCpcEnabledLevels = adaptiveGallopEnabledLevels = adaptiveHjOnlyLevels = 0;
            maxMemoryMB = 0.0;
            endTimeNano = 0;
            startTimeNano = System.nanoTime();
        }

        public static void stopTimer() { endTimeNano = System.nanoTime(); }

        public static void checkMemory() {
            double used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0;
            if (used > maxMemoryMB) maxMemoryMB = used;
        }

        public static double getExecutionTimeSeconds() {
            return (endTimeNano - startTimeNano) / 1_000_000_000.0;
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

    public static final class PatternCandidate {
        private final int[] rankSequence;
        private final double support;
        private final int group;
        public final int[] prefix, suffix;
        public double prefixSupport, suffixSupport;
        public final List<Integer> occurrences;
        public int[] shiftedOccurrences, suffixOccurrences;

        public PatternCandidate(int[] rankSequence, List<Integer> occurrences, double support, int group) {
            this.rankSequence = rankSequence;
            this.support = support;
            this.group = group;
            this.prefix = OrderPreservingUtils.getPrefix(rankSequence);
            this.suffix = OrderPreservingUtils.getSuffix(rankSequence);
            this.occurrences = occurrences;
            this.prefixSupport = support;
            this.suffixSupport = support;
        }

        public int[] getRankSequence() { return rankSequence; }
        public double getSupport() { return support; }
        public int getGroup() { return group; }

        public int[] getShiftedOccurrences() {
            if (shiftedOccurrences == null) {
                shiftedOccurrences = new int[occurrences.size()];
                for (int i = 0; i < occurrences.size(); i++) shiftedOccurrences[i] = occurrences.get(i) + 1;
            }
            return shiftedOccurrences;
        }

        public int[] getOccurrenceArray() {
            if (suffixOccurrences == null) suffixOccurrences = occurrences.stream().mapToInt(i -> i).toArray();
            return suffixOccurrences;
        }

        public void clearTemp() {
            shiftedOccurrences = null;
            suffixOccurrences = null;
        }
    }

    public static final class IntArrayKey {
        private final int[] array;
        private final int hash;
        public IntArrayKey(int[] array) {
            this.array = array;
            this.hash = Arrays.hashCode(array);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Arrays.equals(array, ((IntArrayKey) o).array);
        }
        @Override public int hashCode() { return hash; }
    }

    public static final class OrderPreservingUtils {
        public static int[] getOrder(int[] origSeq) {
            int[] sq = Arrays.copyOf(origSeq, origSeq.length), o = new int[sq.length], t = Arrays.copyOf(sq, sq.length);
            Arrays.sort(t);
            int mx = t[t.length - 1] + 1;
            for (int j = 0; j < sq.length; j++) {
                int mn = mx, idx = -1;
                for (int i = 0; i < sq.length; i++) {
                    if (sq[i] < mn && sq[i] != Integer.MIN_VALUE) { mn = sq[i]; idx = i; }
                }
                sq[idx] = Integer.MIN_VALUE;
                o[idx] = j + 1;
            }
            return o;
        }
        public static int[] getPrefix(int[] p) { return getOrder(Arrays.copyOfRange(p, 0, p.length - 1)); }
        public static int[] getSuffix(int[] p) { return getOrder(Arrays.copyOfRange(p, 1, p.length)); }
    }

    public static final class FusionEngine {
        private final ForgettingMechanism forgettingMechanism;
        private final List<Double> timeSeries;
        private final double minSup, kVal;
        private final boolean adaptiveBitmapConversionOK;
        private final boolean adaptiveWsbRunEligible;

        public FusionEngine(ForgettingMechanism fm, List<Double> ts, double minSup, double kVal) {
            this.forgettingMechanism = fm;
            this.timeSeries = ts;
            this.minSup = minSup;
            this.kVal = kVal;
            // Conversion budget is only a safety cap for large series. Benefit is
            // decided from observed generation workload (density / occurrences / fanout).
            this.adaptiveBitmapConversionOK = 2L * ts.size() <= ADAPTIVE_BITMAP_MAX_CONVERSION_UNITS;
            this.adaptiveWsbRunEligible = minSup / ts.size() >= ADAPTIVE_WSB_MIN_SUPPORT_RATIO;
        }

        public static final class GenerationDecision {
            public boolean useBitmap;
            public boolean useWsb;
            public long compatiblePairs;
            public long scalarWorkUnits;
            public long bitmapWorkUnits;
            public long conversionWorkUnits;
            public double bitmapGain;
            public int wsbSampleChecks;
            public int wsbSamplePrunes;
            public long wsbBoundWorkUnits;
            public long wsbSavedWorkUnits;
            public long decisionTimeNano;
            public long bitmapDecisionTimeNano;
            public long wsbDecisionTimeNano;
        }

        private GenerationDecision lastDecision = new GenerationDecision();
        private int wsbProbeCooldown = 0;
        private int wsbFailedProbes = 0;
        /** Cached whole-run bitmap decision under cost policy (null = not yet decided). */
        private Boolean cachedCostBitmapDecision = null;
        /** Per-generation staged policy flags (scalar Adaptive path). */
        private boolean useCpcThisGen = false;
        private boolean useGallopThisGen = false;

        public GenerationDecision getLastDecision() {
            return lastDecision;
        }

        /**
         * After HJ: decide whether generation-level CPC pays.
         * Gates A/B/C/D share the same O(1) residual/span/card probe (no range in gate A–C).
         * Mathematical target: enable CPC when expected fuse savings exceed check cost.
         */
        private boolean decideUseCpcAfterHj(List<PatternCandidate> generation,
                                            Map<IntArrayKey, List<PatternCandidate>> prefixMap,
                                            long pairCount) {
            long t0 = System.nanoTime();
            try {
                if (!ADAPTIVE_WSB_CHEAP_PRUNE) return false;
                if (!ADAPTIVE_STAGED_POLICY) return true;
                int n = timeSeries.size();
                if (n < ADAPTIVE_CPC_MIN_N) return false;
                if (pairCount < ADAPTIVE_CPC_MIN_PAIRS) return false;
                if (generation.isEmpty() || prefixMap == null) return false;

                // Gate C: structural only — O(1) CPC whenever mass is large enough.
                // Rationale: residual/span/card are O(1); expected cost is linear in pairs checked,
                // each check is far cheaper than fuse when |Occ| is non-trivial.
                if ("C".equals(ADAPTIVE_CPC_GATE)) return true;

                // Probe O(1) free prunes only (residual, empty, span, card×weight).
                // Range is intentionally excluded from the gate (expensive; stays inside shouldPrune).
                double expK = Math.exp(kVal);
                int checks = 0, freePrunes = 0;
                int stride = Math.max(1, generation.size() / Math.max(1, ADAPTIVE_CPC_PROBE_PAIRS));
                for (int pi = 0; pi < generation.size() && checks < ADAPTIVE_CPC_PROBE_PAIRS; pi += stride) {
                    PatternCandidate p = generation.get(pi);
                    if (p.prefixSupport < minSup) continue;
                    List<PatternCandidate> qs = prefixMap.get(new IntArrayKey(p.suffix));
                    if (qs == null || qs.isEmpty()) continue;
                    PatternCandidate q = qs.get(0);
                    if (q.suffixSupport < minSup) continue;
                    checks++;
                    if (probeFreeO1Prune(p, q, expK)) freePrunes++;
                }
                if (checks < 4) return false;
                double r = (double) freePrunes / checks; // estimated fraction killed by O(1) bounds

                if ("A".equals(ADAPTIVE_CPC_GATE)) {
                    // Fail-safe: only when a majority of probed pairs die for free.
                    // Interpretable: P(O(1) prune) > 1/2 on the HJ sample.
                    return r >= 0.5;
                }
                if ("B".equals(ADAPTIVE_CPC_GATE)) {
                    // Cost model (generation-level):
                    //   cost_HJ  ≈ P * C_fuse
                    //   cost_CPC ≈ P * C_check + (1-r) * P * C_fuse
                    // Enable iff cost_CPC < cost_HJ  ⇔  r > C_check / C_fuse = α
                    // α = adaptiveCpcCheckFuseRatio (default 0.08).
                    return r > ADAPTIVE_CPC_CHECK_FUSE_RATIO;
                }
                // D = legacy: include optional range in a second sense via old threshold on r
                // (still O(1)-only probe here; threshold default 0.12).
                if ("D".equals(ADAPTIVE_CPC_GATE)) {
                    return r >= ADAPTIVE_CPC_PROBE_MIN_PRUNE;
                }
                // Unknown gate name → conservative fail-safe A
                return r >= 0.5;
            } finally {
                long dt = System.nanoTime() - t0;
                MetricsTracker.adaptiveWsbDecisionTimeNano += dt;
                MetricsTracker.adaptiveDecisionTimeNano += dt;
            }
        }

        /** O(1) CPC tiers only: residual, empty occ, span, card×weight vs minSup. */
        private boolean probeFreeO1Prune(PatternCandidate p, PatternCandidate q, double expK) {
            double supportBound = Math.min(p.prefixSupport * expK, q.suffixSupport);
            if (supportBound + 1e-12 < minSup) return true;
            List<Integer> pPos = p.occurrences, qPos = q.occurrences;
            if (pPos.isEmpty() || qPos.isEmpty()) return true;
            int low = Math.max(pPos.get(0) + 1, qPos.get(0));
            int high = Math.min(pPos.get(pPos.size() - 1) + 1, qPos.get(qPos.size() - 1));
            if (low > high) return true;
            double wHigh = forgettingMechanism.getWeight(high);
            return Math.min(pPos.size(), qPos.size()) * wHigh + 1e-12 < minSup;
        }

        /**
         * After CPC is chosen: enable gallop only if enough <b>real HJ pairs</b> have
         * skewed occurrence lengths. Pattern-level max/min alone is too coarse (one long
         * pattern can flip the whole generation even when most pairs are balanced).
         */
        private boolean decideUseGallopAfterCpc(List<PatternCandidate> generation,
                                                Map<IntArrayKey, List<PatternCandidate>> prefixMap) {
            if (!ADAPTIVE_SMART_INTERSECT) return false;
            if (!ADAPTIVE_STAGED_POLICY) return true;
            if (prefixMap == null || generation.isEmpty()) return false;

            int sampled = 0, skewed = 0;
            int stride = Math.max(1, generation.size() / Math.max(1, ADAPTIVE_GALLOP_SAMPLE_PAIRS));
            for (int pi = 0; pi < generation.size() && sampled < ADAPTIVE_GALLOP_SAMPLE_PAIRS; pi += stride) {
                PatternCandidate p = generation.get(pi);
                List<PatternCandidate> qs = prefixMap.get(new IntArrayKey(p.suffix));
                if (qs == null || qs.isEmpty()) continue;
                // Probe up to 2 matching q's for this p (fanout sample).
                int qLimit = Math.min(2, qs.size());
                for (int qi = 0; qi < qLimit && sampled < ADAPTIVE_GALLOP_SAMPLE_PAIRS; qi++) {
                    PatternCandidate q = qs.get(qi);
                    int np = p.occurrences.size();
                    int nq = q.occurrences.size();
                    if (np <= 0 || nq <= 0) continue;
                    sampled++;
                    int lo = Math.min(np, nq), hi = Math.max(np, nq);
                    if (hi >= ADAPTIVE_GALLOP_MIN_OCC
                            && hi >= (long) ADAPTIVE_GALLOP_MIN_RATIO * lo) {
                        skewed++;
                    }
                }
            }
            if (sampled == 0) return false;
            return (double) skewed / sampled >= ADAPTIVE_GALLOP_MIN_SKEW_FRACTION;
        }

        public List<PatternCandidate> generateNextGeneration(List<PatternCandidate> currentGen) {
            // Adaptive: HJ once, then scalar (CPC + Sorted List / Gallop). Bitmap removed.
            if (COST_ADAPTIVE && !ADAPTIVE_WSB_USE_RANGE) {
                prepareGeneration(currentGen);
                Map<IntArrayKey, List<PatternCandidate>> prefixMap = buildPrefixMap(currentGen);
                return generateAdaptiveScalarNoWsb(currentGen, prefixMap, true);
            }
            currentGen.sort((a, b) -> Double.compare(b.getSupport(), a.getSupport()));
            for (PatternCandidate c : currentGen) {
                c.prefixSupport = c.getSupport();
                c.suffixSupport = c.getSupport();
                c.clearTemp();
            }

            Map<IntArrayKey, List<PatternCandidate>> prefixMap = null;
            if (HASH) {
                prefixMap = new HashMap<>();
                for (PatternCandidate c : currentGen) {
                    prefixMap.computeIfAbsent(new IntArrayKey(c.prefix), k -> new ArrayList<>()).add(c);
                }
            }

            List<PatternCandidate> nextGen = new ArrayList<>();
            double expK = Math.exp(kVal);
            double expMinusK = Math.exp(-kVal);
            long decisionStart = COST_ADAPTIVE ? System.nanoTime() : 0L;
            GenerationDecision decision = chooseGenerationPolicy(currentGen, prefixMap);
            boolean useBitmap = false; // Bitmap removed
            if (COST_ADAPTIVE) {
                decision.bitmapDecisionTimeNano = System.nanoTime() - decisionStart;
                decision.decisionTimeNano += decision.bitmapDecisionTimeNano;
                MetricsTracker.adaptiveBitmapDecisionTimeNano += decision.bitmapDecisionTimeNano;
                MetricsTracker.adaptiveDecisionTimeNano += decision.bitmapDecisionTimeNano;
                if (useBitmap) MetricsTracker.adaptiveBitmapLevels++;
                else MetricsTracker.adaptiveScalarLevels++;
            }
            boolean adaptiveWsb = WSB && (WSB_POLICY.equals("adaptive") || WSB_POLICY.equals("cost"));
            boolean costAwareWsb = COST_ADAPTIVE && WSB_POLICY.equals("cost");
            // Fail-safe cost WSB: small probe only. Never scan the whole level just to decide.
            // Enable remainder only if the probe prune rate clears a strict threshold.
            boolean wsbDecisionMade = !adaptiveWsb;
            boolean wsbEnabledForRemainder = WSB && !WSB_POLICY.equals("never");
            int wsbSampleChecks = 0, wsbSamplePrunes = 0;
            long wsbBoundWorkUnits = 0L, wsbSavedWorkUnits = 0L;
            int wsbSampleTarget = WSB_SAMPLE_SIZE;
            if (costAwareWsb) {
                if (!adaptiveWsbRunEligible || decision.compatiblePairs < ADAPTIVE_WSB_MIN_PAIRS) {
                    wsbDecisionMade = true;
                    wsbEnabledForRemainder = false;
                } else if (wsbProbeCooldown > 0) {
                    wsbProbeCooldown--;
                    wsbDecisionMade = true;
                    wsbEnabledForRemainder = false;
                } else {
                    // Probe a handful of pairs; default remainder off until probe succeeds.
                    wsbDecisionMade = false;
                    wsbEnabledForRemainder = false;
                    wsbSampleTarget = (int) Math.min(Math.max((long) WSB_SAMPLE_SIZE, 16L),
                            decision.compatiblePairs);
                }
            } else if (adaptiveWsb) {
                wsbSampleTarget = (int) Math.min((long) WSB_SAMPLE_SIZE,
                        Math.max(1L, decision.compatiblePairs));
            }

            for (PatternCandidate p : currentGen) {
                if (p.prefixSupport < minSup) continue;
                int m = p.getRankSequence().length;
                List<PatternCandidate> matchingQs = HASH
                        ? prefixMap.getOrDefault(new IntArrayKey(p.suffix), Collections.emptyList())
                        : currentGen;
                MetricsTracker.pairChecksCount += matchingQs.size();

                int[] shiftedPArray = null;

                for (PatternCandidate q : matchingQs) {
                    if (p.prefixSupport < minSup) break;
                    if (q.suffixSupport < minSup) continue;
                    if (!HASH && !Arrays.equals(p.suffix, q.prefix)) continue;

                    if (m > 2) {
                        int pG = p.getGroup(), qG = q.getGroup();
                        boolean valid = ((pG == 1 || pG == 3) && (qG == 1 || qG == 2)) ||
                                ((pG == 2 || pG == 4) && (qG == 3 || qG == 4));
                        if (!valid) continue;
                    }

                    boolean checkWsb = WSB && (!wsbDecisionMade || wsbEnabledForRemainder);
                    if (checkWsb) {
                        if (DIAGNOSTIC || COST_ADAPTIVE) MetricsTracker.wsbChecksCount++;
                        double supportBound = Math.min(p.prefixSupport * expK, q.suffixSupport);
                        double rangeBound = rangeIntersectionUpperBound(p.occurrences, q.occurrences);
                        double upperBound = Math.min(supportBound, rangeBound);
                        boolean pruned = upperBound + 1e-12 < minSup;
                        if (adaptiveWsb && !wsbDecisionMade) {
                            wsbSampleChecks++;
                            if (pruned) {
                                wsbSamplePrunes++;
                                if (costAwareWsb) {
                                    wsbBoundWorkUnits += estimateBoundWork(p, q);
                                    wsbSavedWorkUnits += estimateFusionWork(p, q, useBitmap);
                                }
                            } else if (costAwareWsb) {
                                wsbBoundWorkUnits += estimateBoundWork(p, q);
                            }
                            if (wsbSampleChecks >= wsbSampleTarget) {
                                wsbDecisionMade = true;
                                double pruneRate = (double) wsbSamplePrunes / wsbSampleChecks;
                                double keepRate = costAwareWsb ? ADAPTIVE_WSB_KEEP_PRUNE_RATE
                                        : WSB_MIN_PRUNE_RATE;
                                wsbEnabledForRemainder = pruneRate >= keepRate ||
                                        (costAwareWsb && wsbSamplePrunes > 0 &&
                                                wsbSavedWorkUnits >= ADAPTIVE_WSB_MIN_GAIN *
                                                        Math.max(1L, wsbBoundWorkUnits));
                            }
                        }
                        if (pruned) {
                            if (DIAGNOSTIC || COST_ADAPTIVE) {
                                MetricsTracker.wsbPrunesCount++;
                                if (rangeBound < supportBound) MetricsTracker.rangeBoundPrunesCount++;
                            }
                            continue;
                        }
                    }

                    MetricsTracker.patternFusionsCount++;
                    MetricsTracker.candidatePatternsCount += (p.getRankSequence()[0] == q.getRankSequence()[m - 1]) ? 2 : 1;

                    if (DIAGNOSTIC) MetricsTracker.scalarFusionPairsCount++;
                    if (shiftedPArray == null) shiftedPArray = p.getShiftedOccurrences();
                    fuseScalar(p, q, shiftedPArray, nextGen, expMinusK);
                }
            }
            for (PatternCandidate c : currentGen) c.clearTemp();
            decision.useWsb = !adaptiveWsb ? wsbEnabledForRemainder :
                    wsbDecisionMade && wsbEnabledForRemainder;
            if (COST_ADAPTIVE && wsbSampleChecks > 0) {
                MetricsTracker.adaptiveWsbSampledLevels++;
                if (decision.useWsb) {
                    MetricsTracker.adaptiveWsbEnabledLevels++;
                    wsbFailedProbes = 0;
                    wsbProbeCooldown = 0;
                } else {
                    wsbFailedProbes = Math.min(wsbFailedProbes + 1, 4);
                    wsbProbeCooldown = (1 << wsbFailedProbes) - 1;
                }
            }
            decision.wsbSampleChecks = wsbSampleChecks;
            decision.wsbSamplePrunes = wsbSamplePrunes;
            decision.wsbBoundWorkUnits = wsbBoundWorkUnits;
            decision.wsbSavedWorkUnits = wsbSavedWorkUnits;
            lastDecision = decision;
            return nextGen;
        }

        /** Sort + residual init once per generation (shared by Adaptive scalar/bitmap). */
        private void prepareGeneration(List<PatternCandidate> currentGen) {
            currentGen.sort((a, b) -> Double.compare(b.getSupport(), a.getSupport()));
            for (PatternCandidate candidate : currentGen) {
                candidate.prefixSupport = candidate.getSupport();
                candidate.suffixSupport = candidate.getSupport();
                candidate.clearTemp();
            }
        }

        private List<PatternCandidate> generateAdaptiveScalarNoWsb(List<PatternCandidate> currentGen) {
            return generateAdaptiveScalarNoWsb(currentGen, null, false);
        }

        /**
         * Adaptive scalar kernel. When {@code prepared} is true, {@code prefixMap} is reused
         * (no second sort / residual / map build). Small N → HJ-pure fuse, zero CPC decision.
         */
        private List<PatternCandidate> generateAdaptiveScalarNoWsb(List<PatternCandidate> currentGen,
                                                                    Map<IntArrayKey, List<PatternCandidate>> prefixMap,
                                                                    boolean prepared) {
            if (!prepared) {
                prepareGeneration(currentGen);
                prefixMap = buildPrefixMap(currentGen);
            }
            GenerationDecision decision = new GenerationDecision();
            decision.conversionWorkUnits = 2L * timeSeries.size();
            decision.useBitmap = false;

            // Count compatible pairs in one pass only when CPC is eligible (N large enough).
            long pairCount = 0L;
            final boolean cpcEligible = ADAPTIVE_WSB_CHEAP_PRUNE
                    && timeSeries.size() >= ADAPTIVE_CPC_MIN_N;
            if (cpcEligible) {
                for (PatternCandidate candidate : currentGen) {
                    List<PatternCandidate> matches = prefixMap.get(new IntArrayKey(candidate.suffix));
                    if (matches != null) pairCount += matches.size();
                }
            }
            decision.compatiblePairs = pairCount;

            // Staged: HJ → CPC? → Gallop?  Small series force pure HJ fuse (no decision tax).
            boolean useCpc = cpcEligible && decideUseCpcAfterHj(currentGen, prefixMap, pairCount);
            // Gallop is a fusion strategy (like Bitmap): may run without CPC when allowed.
            boolean useGallop = ADAPTIVE_SMART_INTERSECT
                    && (useCpc || ADAPTIVE_GALLOP_WITHOUT_CPC)
                    && decideUseGallopAfterCpc(currentGen, prefixMap);
            decision.useWsb = useCpc;
            this.useGallopThisGen = useGallop;
            this.useCpcThisGen = useCpc;
            MetricsTracker.adaptiveScalarLevels++;
            if (useCpc) MetricsTracker.adaptiveCpcEnabledLevels++;
            else MetricsTracker.adaptiveHjOnlyLevels++;
            if (useGallop) MetricsTracker.adaptiveGallopEnabledLevels++;

            List<PatternCandidate> nextGen = new ArrayList<>();
            double expMinusK = Math.exp(-kVal);
            double expK = Math.exp(kVal);
            // Hot path: no virtual Adaptive tax beyond optional CPC check + direct fuse call.
            for (PatternCandidate p : currentGen) {
                if (p.prefixSupport < minSup) continue;
                int patternLength = p.getRankSequence().length;
                List<PatternCandidate> matchingQs = prefixMap.getOrDefault(
                        new IntArrayKey(p.suffix), Collections.emptyList());
                MetricsTracker.pairChecksCount += matchingQs.size();
                int[] shiftedPArray = null;
                for (PatternCandidate q : matchingQs) {
                    if (p.prefixSupport < minSup) break;
                    if (q.suffixSupport < minSup || !groupsCompatible(p, q, patternLength)) continue;
                    if (useCpc && shouldPruneAdaptivePair(p, q, expK)) continue;
                    MetricsTracker.patternFusionsCount++;
                    MetricsTracker.candidatePatternsCount +=
                            p.getRankSequence()[0] == q.getRankSequence()[patternLength - 1] ? 2 : 1;
                    if (DIAGNOSTIC) MetricsTracker.scalarFusionPairsCount++;
                    if (shiftedPArray == null) shiftedPArray = p.getShiftedOccurrences();
                    if (useGallop) {
                        fuseScalarSmart(p, q, shiftedPArray, nextGen, expMinusK);
                    } else {
                        fuseScalarClassic(p, q, shiftedPArray, q.getOccurrenceArray(), nextGen, expMinusK);
                    }
                }
            }
            finishAdaptiveGeneration(currentGen, decision);
            return nextGen;
        }

        private static Map<IntArrayKey, List<PatternCandidate>> buildPrefixMap(
                List<PatternCandidate> generation) {
            Map<IntArrayKey, List<PatternCandidate>> prefixMap = new HashMap<>();
            for (PatternCandidate candidate : generation) {
                prefixMap.computeIfAbsent(new IntArrayKey(candidate.prefix), key -> new ArrayList<>())
                        .add(candidate);
            }
            return prefixMap;
        }

        private void finishAdaptiveGeneration(List<PatternCandidate> generation,
                                              GenerationDecision decision) {
            for (PatternCandidate candidate : generation) candidate.clearTemp();
            lastDecision = decision;
        }

        private GenerationDecision chooseGenerationPolicy(List<PatternCandidate> generation,
                                                          Map<IntArrayKey, List<PatternCandidate>> prefixMap) {
            GenerationDecision decision = new GenerationDecision();
            // Bitmap removed from Adaptive Fast OPF — always scalar fusion.
            decision.useBitmap = false;
            if (true) return decision;
            if (BITMAP_POLICY.equals("always")) {
                decision.useBitmap = false;
                return decision;
            }
            if (BITMAP_POLICY.equals("never")) return decision;
            if (!BITMAP_POLICY.equals("cost")) {
                decision.useBitmap = false;
                return decision;
            }
            if (prefixMap == null || generation.isEmpty()) return decision;

            // --- Post-HJ signals (exact, not L^2) ---
            long pairCount = 0L;
            long occSum = 0L;
            long coveredSum = 0L;
            int sampleCount = Math.min(BITMAP_SAMPLE_SIZE, generation.size());
            for (PatternCandidate candidate : generation) {
                List<PatternCandidate> matches = prefixMap.get(new IntArrayKey(candidate.suffix));
                if (matches != null) pairCount += matches.size();
            }
            for (int s = 0; s < sampleCount; s++) {
                int idx = (int) ((long) s * generation.size() / sampleCount);
                PatternCandidate c = generation.get(idx);
                List<Integer> pos = c.occurrences;
                if (pos.isEmpty()) continue;
                occSum += pos.size();
                coveredSum += (long) pos.get(pos.size() - 1) - pos.get(0) + 1L;
            }
            double avgOcc = sampleCount == 0 ? 0.0 : (double) occSum / sampleCount;
            double density = coveredSum == 0L ? 0.0 : (double) occSum / coveredSum;
            double avgFanout = generation.isEmpty() ? 0.0 : (double) pairCount / generation.size();

            long conversion = 2L * timeSeries.size();
            decision.compatiblePairs = pairCount;
            decision.conversionWorkUnits = conversion;
            decision.scalarWorkUnits = (long) Math.min(Long.MAX_VALUE / 4.0,
                    pairCount * Math.max(1.0, avgOcc));
            decision.bitmapWorkUnits = (long) Math.min(Long.MAX_VALUE / 4.0,
                    pairCount * Math.max(1.0, avgOcc / 64.0));
            // Gain proxy: scalar mass vs bitmap mass + conversion, using HJ-visible P and density.
            decision.bitmapGain = decision.scalarWorkUnits <= 0 ? 0.0 :
                    (double) (decision.scalarWorkUnits - decision.bitmapWorkUnits - conversion)
                            / decision.scalarWorkUnits;

            // Default scalar. Enable BM only when post-HJ signals say conversion amortizes.
            // Do NOT freeze the decision on tiny early generations (length-2 often has P≈2–4):
            // wait until pairCount is large enough to estimate amortization reliably, then cache.
            if (cachedCostBitmapDecision == null) {
                if (pairCount < Math.max(ADAPTIVE_WSB_MIN_PAIRS, 16L)) {
                    decision.useBitmap = false; // observe more generations under HJ first
                    return decision;
                }
                boolean amortizes = avgOcc > 0.0 && pairCount > 0 &&
                        conversion <= (long) (pairCount * avgOcc / ADAPTIVE_BITMAP_AMORTIZE_DIVISOR);
                boolean softCap = conversion <= ADAPTIVE_BITMAP_MAX_CONVERSION_UNITS;
                boolean workload = avgOcc >= BITMAP_MIN_OCCURRENCES
                        && density >= BITMAP_MIN_DENSITY
                        && avgFanout >= BITMAP_MIN_FANOUT;
                cachedCostBitmapDecision = amortizes && softCap && workload;
            }
            decision.useBitmap = Boolean.TRUE.equals(cachedCostBitmapDecision);
            return decision;
        }

        private static boolean groupsCompatible(PatternCandidate p, PatternCandidate q, int patternLength) {
            if (patternLength <= 2) return true;
            int pGroup = p.getGroup(), qGroup = q.getGroup();
            return ((pGroup == 1 || pGroup == 3) && (qGroup == 1 || qGroup == 2)) ||
                    ((pGroup == 2 || pGroup == 4) && (qGroup == 3 || qGroup == 4));
        }

        private static long estimatedBitmapWords(List<Integer> positions) {
            if (positions.isEmpty()) return 0L;
            long firstBlock = positions.get(0) >>> 6;
            long lastBlock = (positions.get(positions.size() - 1) + 1L) >>> 6;
            long spanWords = lastBlock - firstBlock + 1L;
            return Math.min((long) positions.size(), spanWords);
        }

        private static long estimateBoundWork(PatternCandidate p, PatternCandidate q) {
            return 4L * (ceilLog2(p.occurrences.size() + 1) + ceilLog2(q.occurrences.size() + 1));
        }

        private static long estimateFusionWork(PatternCandidate p, PatternCandidate q, boolean bitmap) {
            if (bitmap) return estimatedBitmapWords(p.occurrences) + estimatedBitmapWords(q.occurrences);
            return (long) p.occurrences.size() + q.occurrences.size();
        }

        private static int ceilLog2(int value) {
            if (value <= 1) return 1;
            return 32 - Integer.numberOfLeadingZeros(value - 1);
        }


        private boolean shouldPruneAdaptivePair(PatternCandidate p, PatternCandidate q, double expK) {
            double supportBound = Math.min(p.prefixSupport * expK, q.suffixSupport);
            if (supportBound + 1e-12 < minSup) {
                MetricsTracker.wsbChecksCount++;
                MetricsTracker.wsbPrunesCount++;
                MetricsTracker.cpcResidualPrunes++;
                return true;
            }

            List<Integer> pPos = p.occurrences;
            List<Integer> qPos = q.occurrences;
            if (pPos.isEmpty() || qPos.isEmpty()) {
                MetricsTracker.wsbChecksCount++;
                MetricsTracker.wsbPrunesCount++;
                MetricsTracker.cpcSpanPrunes++;
                return true;
            }

            if (!ADAPTIVE_WSB_CHEAP_PRUNE) {
                if (supportBound < ADAPTIVE_WSB_TIGHT_FACTOR * minSup) {
                    MetricsTracker.wsbChecksCount++;
                    double rangeBound = rangeIntersectionUpperBound(pPos, qPos);
                    if (rangeBound + 1e-12 < minSup) {
                        MetricsTracker.wsbPrunesCount++;
                        MetricsTracker.rangeBoundPrunesCount++;
                        MetricsTracker.cpcRangePrunes++;
                        return true;
                    }
                }
                return false;
            }

            // ---- Cheap-Prune Cascade (CPC) ----
            int pFirst = pPos.get(0), pLast = pPos.get(pPos.size() - 1);
            int qFirst = qPos.get(0), qLast = qPos.get(qPos.size() - 1);
            int low = Math.max(pFirst + 1, qFirst);
            int high = Math.min(pLast + 1, qLast);

            if (low > high) {
                MetricsTracker.wsbChecksCount++;
                MetricsTracker.wsbPrunesCount++;
                MetricsTracker.cpcSpanPrunes++;
                return true;
            }

            double wHigh = forgettingMechanism.getWeight(high);
            int minOcc = Math.min(pPos.size(), qPos.size());
            if (minOcc * wHigh + 1e-12 < minSup) {
                MetricsTracker.wsbChecksCount++;
                MetricsTracker.wsbPrunesCount++;
                MetricsTracker.cpcCardPrunes++;
                return true;
            }

            boolean tight = supportBound < ADAPTIVE_WSB_CPC_TIGHT * minSup;
            long occMass = (long) pPos.size() + (long) qPos.size();
            if (tight && occMass <= ADAPTIVE_WSB_MAX_OCC_FOR_RANGE) {
                MetricsTracker.wsbChecksCount++;
                double rangeBound = rangeIntersectionUpperBound(pPos, qPos);
                if (rangeBound + 1e-12 < minSup) {
                    MetricsTracker.wsbPrunesCount++;
                    MetricsTracker.rangeBoundPrunesCount++;
                    MetricsTracker.cpcRangePrunes++;
                    return true;
                }
            }
            return false;
        }

        private double rangeIntersectionUpperBound(List<Integer> pPositions, List<Integer> qPositions) {
            if (pPositions.isEmpty() || qPositions.isEmpty()) return 0.0;
            int low = Math.max(pPositions.get(0) + 1, qPositions.get(0));
            int high = Math.min(pPositions.get(pPositions.size() - 1) + 1,
                    qPositions.get(qPositions.size() - 1));
            if (low > high) return 0.0;
            int pCount = countInRange(pPositions, low - 1, high - 1);
            int qCount = countInRange(qPositions, low, high);
            int possibleMatches = Math.min(pCount, qCount);
            return possibleMatches * forgettingMechanism.getWeight(high);
        }

        private static int countInRange(List<Integer> values, int low, int high) {
            return upperBound(values, high) - lowerBound(values, low);
        }

        private static int lowerBound(List<Integer> values, int target) {
            int low = 0, high = values.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (values.get(middle) < target) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        private static int upperBound(List<Integer> values, int target) {
            int low = 0, high = values.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (values.get(middle) <= target) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        private void fuseScalar(PatternCandidate pCand, PatternCandidate qCand, int[] shiftedP,
                                List<PatternCandidate> nextGen, double expMinusK) {
            fuseScalarClassic(pCand, qCand, shiftedP, qCand.getOccurrenceArray(), nextGen, expMinusK);
        }

        /**
         * Pair-local smart fuse: if this pair is not skewed, fall back to classic two-pointer.
         * If skewed, gallop only when advancing the <b>longer</b> list (initial sizes).
         * Same matches as classic fuse — only seeks fewer comparisons on skewed pairs.
         */
        private void fuseScalarSmart(PatternCandidate pCand, PatternCandidate qCand, int[] shiftedP,
                                     List<PatternCandidate> nextGen, double expMinusK) {
            int[] qPos = qCand.getOccurrenceArray();
            int nP = shiftedP.length, nQ = qPos.length;
            int lo = Math.min(nP, nQ), hi = Math.max(nP, nQ);
            boolean pairSkewed = lo > 0
                    && hi >= ADAPTIVE_GALLOP_MIN_OCC
                    && hi >= (long) ADAPTIVE_GALLOP_MIN_RATIO * lo;
            if (!pairSkewed) {
                // Generation may be "gallop-eligible" overall, but this pair is balanced.
                fuseScalarClassic(pCand, qCand, shiftedP, qPos, nextGen, expMinusK);
                return;
            }
            MetricsTracker.smartIntersectFusions++;
            boolean pIsLong = nP >= nQ;
            int[] pRank = pCand.getRankSequence(), qRank = qCand.getRankSequence();
            int m = pRank.length;
            List<Integer> rPositions = new ArrayList<>(), hPositions = new ArrayList<>();
            double rSupport = 0.0, hSupport = 0.0, totalRemovedP = 0.0, totalRemovedQ = 0.0;
            int i = 0, j = 0;
            while (i < nP && j < nQ) {
                MetricsTracker.scalarPositionComparisonsCount++;
                int pv = shiftedP[i], qv = qPos[j];
                if (pv == qv) {
                    MetricsTracker.supportCalculationsCount++;
                    int occ = qv;
                    double weightQ = forgettingMechanism.getWeight(occ);
                    double weightP = weightQ * expMinusK;
                    if (pRank[0] == qRank[m - 1]) {
                        double tFirst = timeSeries.get(occ - m), tLast = timeSeries.get(occ);
                        if (tFirst < tLast) { rPositions.add(occ); rSupport += weightQ; }
                        else if (tFirst > tLast) { hPositions.add(occ); hSupport += weightQ; }
                    } else {
                        rPositions.add(occ);
                        rSupport += weightQ;
                    }
                    totalRemovedP += weightP;
                    totalRemovedQ += weightQ;
                    i++;
                    j++;
                } else if (pv < qv) {
                    // Only gallop on the longer side when it is behind.
                    if (pIsLong) {
                        int ni = gallopLowerBound(shiftedP, i, nP, qv);
                        MetricsTracker.smartGallopSteps++;
                        i = ni;
                    } else {
                        i++;
                    }
                } else {
                    if (!pIsLong) {
                        int nj = gallopLowerBound(qPos, j, nQ, pv);
                        MetricsTracker.smartGallopSteps++;
                        j = nj;
                    } else {
                        j++;
                    }
                }
            }
            pCand.prefixSupport -= totalRemovedP;
            qCand.suffixSupport -= totalRemovedQ;
            emitCandidates(pCand, qCand, rPositions, hPositions, rSupport, hSupport, nextGen);
        }

        private void fuseScalarClassic(PatternCandidate pCand, PatternCandidate qCand,
                                       int[] shiftedP, int[] qPos,
                                       List<PatternCandidate> nextGen, double expMinusK) {
            int[] pRank = pCand.getRankSequence(), qRank = qCand.getRankSequence();
            int m = pRank.length;
            List<Integer> rPositions = new ArrayList<>(), hPositions = new ArrayList<>();
            double rSupport = 0.0, hSupport = 0.0, totalRemovedP = 0.0, totalRemovedQ = 0.0;
            int i = 0, j = 0;
            while (i < shiftedP.length && j < qPos.length) {
                MetricsTracker.scalarPositionComparisonsCount++;
                if (shiftedP[i] == qPos[j]) {
                    MetricsTracker.supportCalculationsCount++;
                    int occ = qPos[j];
                    double weightQ = forgettingMechanism.getWeight(occ);
                    double weightP = weightQ * expMinusK;
                    if (pRank[0] == qRank[m - 1]) {
                        double tFirst = timeSeries.get(occ - m), tLast = timeSeries.get(occ);
                        if (tFirst < tLast) { rPositions.add(occ); rSupport += weightQ; }
                        else if (tFirst > tLast) { hPositions.add(occ); hSupport += weightQ; }
                    } else {
                        rPositions.add(occ);
                        rSupport += weightQ;
                    }
                    totalRemovedP += weightP;
                    totalRemovedQ += weightQ;
                    i++;
                    j++;
                } else if (shiftedP[i] < qPos[j]) i++;
                else j++;
            }
            pCand.prefixSupport -= totalRemovedP;
            qCand.suffixSupport -= totalRemovedQ;
            emitCandidates(pCand, qCand, rPositions, hPositions, rSupport, hSupport, nextGen);
        }

        /** Exponential search then binary search: first index in [lo, hi) with values[idx] >= target. */
        private static int gallopLowerBound(int[] values, int lo, int hi, int target) {
            int step = 1;
            int pos = lo;
            while (pos + step < hi) {
                MetricsTracker.scalarPositionComparisonsCount++;
                if (values[pos + step] < target) {
                    pos += step;
                    step <<= 1;
                } else break;
            }
            int left = pos, right = Math.min(hi, pos + step + 1);
            while (left < right) {
                MetricsTracker.scalarPositionComparisonsCount++;
                int mid = (left + right) >>> 1;
                if (values[mid] < target) left = mid + 1;
                else right = mid;
            }
            return left;
        }

        private void emitCandidates(PatternCandidate pCand, PatternCandidate qCand,
                                    List<Integer> rPositions, List<Integer> hPositions,
                                    double rSupport, double hSupport, List<PatternCandidate> nextGen) {
            int[] pRank = pCand.getRankSequence(), qRank = qCand.getRankSequence();
            int m = pRank.length;
            int newGroup = (m == 2)
                    ? ((pRank[0] < pRank[1] && qRank[0] < qRank[1]) ? 1 :
                    (pRank[0] < pRank[1] && qRank[0] > qRank[1]) ? 2 :
                            (pRank[0] > pRank[1] && qRank[0] < qRank[1]) ? 3 : 4)
                    : pCand.getGroup();

            if (pRank[0] == qRank[m - 1]) {
                if (rSupport >= minSup) {
                    int[] rRank = new int[m + 1];
                    rRank[0] = pRank[0];
                    rRank[m] = qRank[m - 1] + 1;
                    for (int i = 1; i < m; i++) rRank[i] = (qRank[i - 1] < pRank[0]) ? qRank[i - 1] : qRank[i - 1] + 1;
                    nextGen.add(new PatternCandidate(rRank, rPositions, rSupport, newGroup));
                    MetricsTracker.frequentPatternsCount++;
                }
                if (hSupport >= minSup) {
                    int[] hRank = new int[m + 1];
                    hRank[0] = pRank[0] + 1;
                    hRank[m] = qRank[m - 1];
                    for (int i = 1; i < m; i++) hRank[i] = (pRank[i] < qRank[m - 1]) ? pRank[i] : pRank[i] + 1;
                    nextGen.add(new PatternCandidate(hRank, hPositions, hSupport, newGroup));
                    MetricsTracker.frequentPatternsCount++;
                }
            } else if (rSupport >= minSup) {
                int[] rRank = new int[m + 1];
                if (pRank[0] < qRank[m - 1]) {
                    rRank[0] = pRank[0];
                    rRank[m] = qRank[m - 1] + 1;
                    for (int i = 1; i < m; i++) rRank[i] = (qRank[i - 1] < pRank[0]) ? qRank[i - 1] : qRank[i - 1] + 1;
                } else {
                    rRank[0] = pRank[0] + 1;
                    rRank[m] = qRank[m - 1];
                    for (int i = 1; i < m; i++) rRank[i] = (pRank[i] < qRank[m - 1]) ? pRank[i] : pRank[i] + 1;
                }
                nextGen.add(new PatternCandidate(rRank, rPositions, rSupport, newGroup));
                MetricsTracker.frequentPatternsCount++;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Set<String> valid = new HashSet<>(Arrays.asList(
                "baseline", "hash_only", "sparse_only", "wsb_only",
                "hash_sparse", "hash_wsb", "sparse_wsb", "full", "adaptive"));
        if (!valid.contains(MODE)) throw new IllegalArgumentException("Unsupported mode: " + MODE);
        if (!Arrays.asList("always", "adaptive", "cost", "never").contains(BITMAP_POLICY)) {
            throw new IllegalArgumentException("Unsupported bitmapPolicy: " + BITMAP_POLICY);
        }
        if (!Arrays.asList("always", "adaptive", "cost", "never").contains(WSB_POLICY)) {
            throw new IllegalArgumentException("Unsupported wsbPolicy: " + WSB_POLICY);
        }
        if (ADAPTIVE_WSB_MIN_PAIRS < 0 || ADAPTIVE_WSB_MIN_GAIN <= 0.0 ||
                ADAPTIVE_BITMAP_MAX_CONVERSION_UNITS < 0 ||
                ADAPTIVE_WSB_MIN_SUPPORT_RATIO < 0.0) {
            throw new IllegalArgumentException("Adaptive cost thresholds must be non-negative and gains positive.");
        }

        File dir = new File(INPUT_DIR);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt") && name.matches(FILE_REGEX));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        File outFile = new File(OUTPUT_SUMMARY_FILE);
        File outParent = outFile.getParentFile();
        if (outParent != null) outParent.mkdirs();
        File logFile = new File(LOG_FILE);
        File logParent = logFile.getParentFile();
        if (logParent != null) logParent.mkdirs();
        File generationFile = GENERATION_OUTPUT_FILE.isEmpty() ? null : new File(GENERATION_OUTPUT_FILE);
        if (generationFile != null && generationFile.getParentFile() != null) {
            generationFile.getParentFile().mkdirs();
        }

        try (BufferedWriter csvWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(OUTPUT_SUMMARY_FILE), StandardCharsets.UTF_8));
             PrintWriter logWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                     new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8)));
             BufferedWriter generationWriter = generationFile == null ? null : new BufferedWriter(
                     new OutputStreamWriter(new FileOutputStream(generationFile), StandardCharsets.UTF_8))) {
            csvWriter.write("Dataset,minsup,Time_s,MaxMem_MB,PairChecks,Candidates,Fusions,SupportOps,FreqPatterns," +
                    "WSBChecks,WSBPrunes,SparseObjects,SparseWordsAllocated,SparseWordsScanned,ScalarPositionComparisons," +
                    "BitmapFusionPairs,ScalarFusionPairs,RangeBoundPrunes,AdaptiveDecision_ms," +
                    "AdaptiveBitmapDecision_ms,AdaptiveWsbDecision_ms," +
                    "AdaptiveBitmapLevels,AdaptiveScalarLevels,AdaptiveWsbSampledLevels,AdaptiveWsbEnabledLevels," +
                    "CpcResidualPrunes,CpcSpanPrunes,CpcCardPrunes,CpcRangePrunes,SmartGallopSteps,SmartIntersectFusions," +
                    "AdaptiveCpcEnabledLevels,AdaptiveGallopEnabledLevels,AdaptiveHjOnlyLevels\n");
            if (generationWriter != null) {
                generationWriter.write("Dataset,minsup,PatternLength,InputPatterns,InputOccurrences,OccurrenceDensity," +
                        "OutputPatterns,KernelTime_s,PairChecks,Fusions,SupportOps,WSBChecks,WSBPrunes," +
                        "SparseWordsAllocated,SparseWordsScanned,ScalarPositionComparisons,BitmapFusionPairs," +
                        "ScalarFusionPairs,CompatiblePairsEstimate,ScalarWorkEstimate,BitmapWorkEstimate," +
                        "BitmapConversionEstimate,BitmapGainEstimate,SelectedBitmap,SelectedWsb," +
                        "WsbSampleChecks,WsbSamplePrunes,WsbBoundWorkEstimate,WsbSavedWorkEstimate," +
                        "DecisionTime_ms,BitmapDecisionTime_ms,WsbDecisionTime_ms\n");
            }
            String startMsg = "FOMAblationFlags mode=" + MODE + " hash=" + HASH + " sparse=" + SPARSE +
                    " wsb=" + WSB + " bitmapPolicy=" + BITMAP_POLICY + " wsbPolicy=" + WSB_POLICY +
                    " diagnostic=" + DIAGNOSTIC +
                    (COST_ADAPTIVE ? " bitmapMaxConversionUnits=" + ADAPTIVE_BITMAP_MAX_CONVERSION_UNITS +
                             " wsbMinGain=" + ADAPTIVE_WSB_MIN_GAIN +
                             " wsbMinPairs=" + ADAPTIVE_WSB_MIN_PAIRS +
                             " wsbMinSupportRatio=" + ADAPTIVE_WSB_MIN_SUPPORT_RATIO : "") + " started";
            logWriter.println(startMsg);
            System.out.println(startMsg);
            if (JIT_WARMUP_RUNS > 0 && CANONICAL_DIR.isEmpty()) {
                double warmupMinSup = selectedMinSups()[0];
                for (int warmup = 0; warmup < JIT_WARMUP_RUNS; warmup++) {
                    for (File warmupFile : files) {
                        BufferedWriter sinkCsv = new BufferedWriter(new StringWriter());
                        PrintWriter sinkLog = new PrintWriter(new StringWriter());
                        processSingleFile(warmupFile, warmupMinSup, sinkCsv, sinkLog, null);
                    }
                }
                System.gc();
            }
            for (File file : files) {
                for (double minSup : selectedMinSups()) {
                    if (CASE_WARMUP_RUNS > 0 && CANONICAL_DIR.isEmpty()) {
                        for (int warmup = 0; warmup < CASE_WARMUP_RUNS; warmup++) {
                            BufferedWriter sinkCsv = new BufferedWriter(new StringWriter());
                            PrintWriter sinkLog = new PrintWriter(new StringWriter());
                            processSingleFile(file, minSup, sinkCsv, sinkLog, null);
                        }
                        System.gc();
                    }
                    processSingleFile(file, minSup, csvWriter, logWriter, generationWriter);
                    System.gc();
                }
            }
            String endMsg = "FOMAblationFlags mode=" + MODE + " finished";
            logWriter.println(endMsg);
            System.out.println(endMsg);
        }
    }

    private static double[] selectedMinSups() {
        String property = System.getProperty("minsupList", "").trim();
        if (property.isEmpty()) return MIN_SUP_ARRAY;
        String[] tokens = property.split(",");
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Double.parseDouble(tokens[i].trim());
            if (!Double.isFinite(values[i]) || values[i] <= 0.0) {
                throw new IllegalArgumentException("minSup must be finite and greater than zero: " + values[i]);
            }
        }
        return values;
    }

    private static void processSingleFile(File file, double minSup, BufferedWriter csvWriter,
                                          PrintWriter logWriter, BufferedWriter generationWriter) throws IOException {
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
        int n = timeSeries.size();
        if (n < 2) return;

        String kProp = System.getProperty("k");
        double kVal = (kProp != null && !kProp.isEmpty()) ? Double.parseDouble(kProp) : 1.0 / n;
        ForgettingMechanism forgetting = new ForgettingMechanism(n, kVal);
        FusionEngine engine = new FusionEngine(forgetting, timeSeries, minSup, kVal);
        Map<String, Double> canonicalPatterns = CANONICAL_DIR.isEmpty() ? null : new TreeMap<>();

        List<Integer> ascPositions = new ArrayList<>(), descPositions = new ArrayList<>();
        double ascSup = 0.0, descSup = 0.0;
        for (int i = 1; i < n; i++) {
            double prev = timeSeries.get(i - 1), curr = timeSeries.get(i), weight = forgetting.getWeight(i);
            if (curr > prev) { ascPositions.add(i); ascSup += weight; }
            else if (curr < prev) { descPositions.add(i); descSup += weight; }
        }

        List<PatternCandidate> currentGeneration = new ArrayList<>();
        if (ascSup >= minSup) {
            currentGeneration.add(new PatternCandidate(new int[]{1, 2}, ascPositions, ascSup, 1));
            MetricsTracker.frequentPatternsCount++;
        }
        if (descSup >= minSup) {
            currentGeneration.add(new PatternCandidate(new int[]{2, 1}, descPositions, descSup, 2));
            MetricsTracker.frequentPatternsCount++;
        }
        recordCanonical(canonicalPatterns, currentGeneration);
        MetricsTracker.candidatePatternsCount += 2;

        while (!currentGeneration.isEmpty()) {
            MetricsTracker.checkMemory();
            int patternLength = currentGeneration.get(0).getRankSequence().length;
            int inputPatterns = currentGeneration.size();
            long inputOccurrences = 0L, coveredPositions = 0L;
            if (generationWriter != null) {
                for (PatternCandidate candidate : currentGeneration) {
                    List<Integer> positions = candidate.occurrences;
                    inputOccurrences += positions.size();
                    if (!positions.isEmpty()) {
                        coveredPositions += (long) positions.get(positions.size() - 1) - positions.get(0) + 1L;
                    }
                }
            }
            double occurrenceDensity = coveredPositions == 0L ? 0.0 :
                    (double) inputOccurrences / coveredPositions;
            long pairChecksBefore = MetricsTracker.pairChecksCount;
            long fusionsBefore = MetricsTracker.patternFusionsCount;
            long supportOpsBefore = MetricsTracker.supportCalculationsCount;
            long wsbChecksBefore = MetricsTracker.wsbChecksCount;
            long wsbPrunesBefore = MetricsTracker.wsbPrunesCount;
            long wordsAllocatedBefore = MetricsTracker.sparseWordsAllocatedCount;
            long wordsScannedBefore = MetricsTracker.sparseWordsScannedCount;
            long scalarComparisonsBefore = MetricsTracker.scalarPositionComparisonsCount;
            long bitmapPairsBefore = MetricsTracker.bitmapFusionPairsCount;
            long scalarPairsBefore = MetricsTracker.scalarFusionPairsCount;
            long kernelStart = System.nanoTime();
            List<PatternCandidate> nextGeneration = engine.generateNextGeneration(currentGeneration);
            double kernelTime = (System.nanoTime() - kernelStart) / 1_000_000_000.0;
            FusionEngine.GenerationDecision decision = engine.getLastDecision();
            if (generationWriter != null) {
                generationWriter.write(String.format(Locale.US,
                        "%s,%s,%d,%d,%d,%.10f,%d,%.9f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d," +
                                "%d,%d,%d,%d,%.8f,%s,%s,%d,%d,%d,%d,%.6f,%.6f,%.6f%n",
                        file.getName(), formatMinSup(minSup), patternLength, inputPatterns, inputOccurrences,
                        occurrenceDensity, nextGeneration.size(), kernelTime,
                        MetricsTracker.pairChecksCount - pairChecksBefore,
                        MetricsTracker.patternFusionsCount - fusionsBefore,
                        MetricsTracker.supportCalculationsCount - supportOpsBefore,
                        MetricsTracker.wsbChecksCount - wsbChecksBefore,
                        MetricsTracker.wsbPrunesCount - wsbPrunesBefore,
                        MetricsTracker.sparseWordsAllocatedCount - wordsAllocatedBefore,
                        MetricsTracker.sparseWordsScannedCount - wordsScannedBefore,
                        MetricsTracker.scalarPositionComparisonsCount - scalarComparisonsBefore,
                        MetricsTracker.bitmapFusionPairsCount - bitmapPairsBefore,
                        MetricsTracker.scalarFusionPairsCount - scalarPairsBefore,
                        decision.compatiblePairs, decision.scalarWorkUnits, decision.bitmapWorkUnits,
                        decision.conversionWorkUnits, decision.bitmapGain,
                        decision.useBitmap, decision.useWsb,
                        decision.wsbSampleChecks, decision.wsbSamplePrunes,
                        decision.wsbBoundWorkUnits, decision.wsbSavedWorkUnits,
                        decision.decisionTimeNano / 1_000_000.0,
                        decision.bitmapDecisionTimeNano / 1_000_000.0,
                        decision.wsbDecisionTimeNano / 1_000_000.0));
            }
            currentGeneration = nextGeneration;
            recordCanonical(canonicalPatterns, currentGeneration);
        }

        MetricsTracker.stopTimer();
        MetricsTracker.checkMemory();
        writeCanonicalOutput(file, minSup, canonicalPatterns);
        double timeSec = MetricsTracker.getExecutionTimeSeconds();
        double memMB = MetricsTracker.maxMemoryMB;

        String line = String.format(Locale.US, "%s\t%s\t%.6f\t%.2f\t%d\t%d\t%d\t%d\t%d",
                file.getName(), formatMinSup(minSup), timeSec, memMB,
                MetricsTracker.pairChecksCount,
                MetricsTracker.candidatePatternsCount,
                MetricsTracker.patternFusionsCount,
                MetricsTracker.supportCalculationsCount,
                MetricsTracker.frequentPatternsCount);
        System.out.println(line);
        logWriter.println(line);
            csvWriter.write(String.format(Locale.US, "%s,%s,%.6f,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d," +
                            "%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    file.getName(), formatMinSup(minSup), timeSec, memMB,
                    MetricsTracker.pairChecksCount,
                    MetricsTracker.candidatePatternsCount,
                    MetricsTracker.patternFusionsCount,
                    MetricsTracker.supportCalculationsCount,
                    MetricsTracker.frequentPatternsCount,
                    MetricsTracker.wsbChecksCount,
                    MetricsTracker.wsbPrunesCount,
                    MetricsTracker.sparseObjectsCount,
                    MetricsTracker.sparseWordsAllocatedCount,
                    MetricsTracker.sparseWordsScannedCount,
                    MetricsTracker.scalarPositionComparisonsCount,
                    MetricsTracker.bitmapFusionPairsCount,
                    MetricsTracker.scalarFusionPairsCount,
                    MetricsTracker.rangeBoundPrunesCount,
                    MetricsTracker.adaptiveDecisionTimeNano / 1_000_000.0,
                    MetricsTracker.adaptiveBitmapDecisionTimeNano / 1_000_000.0,
                    MetricsTracker.adaptiveWsbDecisionTimeNano / 1_000_000.0,
                    MetricsTracker.adaptiveBitmapLevels,
                    MetricsTracker.adaptiveScalarLevels,
                    MetricsTracker.adaptiveWsbSampledLevels,
                    MetricsTracker.adaptiveWsbEnabledLevels,
                    MetricsTracker.cpcResidualPrunes,
                    MetricsTracker.cpcSpanPrunes,
                    MetricsTracker.cpcCardPrunes,
                    MetricsTracker.cpcRangePrunes,
                    MetricsTracker.smartGallopSteps,
                    MetricsTracker.smartIntersectFusions,
                    MetricsTracker.adaptiveCpcEnabledLevels,
                    MetricsTracker.adaptiveGallopEnabledLevels,
                    MetricsTracker.adaptiveHjOnlyLevels));
        csvWriter.flush();
        if (generationWriter != null) generationWriter.flush();
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
        String minSupLabel = formatMinSup(minSup).replace('-', 'm').replace('.', 'p');
        File out = new File(dir, dataset + "_minsup_" + minSupLabel + ".csv");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            writer.write("pattern,support\n");
            for (Map.Entry<String, Double> entry : canonicalPatterns.entrySet()) {
                writer.write(String.format(Locale.US, "\"%s\",%.10f%n", entry.getKey(), entry.getValue()));
            }
        }
    }

    private static String formatMinSup(double minSup) {
        return BigDecimal.valueOf(minSup).stripTrailingZeros().toPlainString();
    }
}

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OPF_Miner gốc (giữ nguyên thuật toán)
 * Chỉ bổ sung metric, đo thời gian, bộ nhớ, xuất CSV.
 * Không thay đổi logic.
 */
public class OPF_Miner_Original {

    public static double minsup;
    private static final String CANONICAL_DIR = System.getProperty("canonical", "");
    private static Map<String, List<List<Double>>> Fmap = new LinkedHashMap<>();
    public static double k;
    public static double e = Math.E;
    static List<Double> S = new ArrayList<>();
    public static int len;
    public static int fre_num = 0;
    public static int fre_number = 0;
    private static int candNum = 2;
    public static int element_num = 0;
    public static int contrast_num = 0;
    
    // Metric bổ sung
    private static long patternFusionsCount = 0;

    static List<Double> forget_mech = new ArrayList<>();
    static Map<String, Double> allfrepattern = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        String inputDir = System.getProperty("input", "data/benchmark");
                String outputCsv = System.getProperty("output");
        if (outputCsv == null || outputCsv.isEmpty()) {
            outputCsv = "results/OPF_Miner_Original_summary.csv";
        }

        File dir = new File(inputDir);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        String fileRegex = System.getProperty("fileRegex", "");
        if (files == null || files.length == 0) {
            System.err.println("Không tìm thấy file .txt trong " + inputDir);
            return;
        }
        if (fileRegex != null && !fileRegex.isEmpty()) {
            files = Arrays.stream(files)
                    .filter(file -> file.getName().matches(fileRegex))
                    .toArray(File[]::new);
            if (files.length == 0) {
                System.err.println("No .txt files matched fileRegex=" + fileRegex + " in " + inputDir);
                return;
            }
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        double[] minsupList = parseMinSupList(System.getProperty("minsupList", ""));

        System.out.printf("%-12s %-8s %-10s %-12s %-15s %-15s %-20s %-15s%n",
                "Dataset", "minsup", "Time(s)", "MaxMem(MB)", "Candidates", "Fusions", "SupportOps", "FreqPatterns");

        File outFile = new File(outputCsv);
        File outParent = outFile.getParentFile();
        if (outParent != null) outParent.mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputCsv), StandardCharsets.UTF_8))) {
            writer.write("Dataset,minsup,Time_s,MaxMem_MB,Candidates,Fusions,SupportOps,FreqPatterns\n");

            for (File file : files) {
                for (double ms : minsupList) {
                    processFile(file, ms, writer);
                }
            }
        }
        System.out.println("Done. Kết quả lưu tại: " + outputCsv);
    }

    private static double[] parseMinSupList(String prop) {
        if (prop == null || prop.trim().isEmpty()) {
            return new double[]{2.0, 4.0, 6.0, 8.0, 10.0, 12.0};
        }
        String[] tokens = prop.split(",");
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Double.parseDouble(tokens[i].trim());
            if (!Double.isFinite(values[i]) || values[i] <= 0.0) {
                throw new IllegalArgumentException("minSup must be finite and greater than zero: " + values[i]);
            }
        }
        return values;
    }

    private static void processFile(File file, double minSup, BufferedWriter writer) throws IOException {
        // Reset toàn bộ trạng thái
        S.clear();
        Fmap.clear();
        allfrepattern.clear();
        forget_mech.clear();
        fre_num = 0;
        fre_number = 0;
        candNum = 2;
        element_num = 0;
        contrast_num = 0;
        patternFusionsCount = 0;
        minsup = minSup;

        // Đọc toàn bộ dữ liệu từ file
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                for (String token : tokens) {
                    if (!token.isEmpty()) S.add(Double.parseDouble(token));
                }
            }
        }

        len = S.size();
        if (len < 2) return;

        String kProp = System.getProperty("k");
        if (kProp != null && !kProp.isEmpty()) {
            k = Double.parseDouble(kProp);
        } else {
            k = 1.0 / len;
        }
        forgetting_mechanism();

        // Peak memory polling (consistent with FOM variants)
        Runtime rt = Runtime.getRuntime();
        rt.gc();
        double maxMemMB = 0.0;
        long startTime = System.nanoTime();

        find();
        maxMemMB = Math.max(maxMemMB, (rt.totalMemory() - rt.freeMemory()) / 1048576.0);
        calculate();
        maxMemMB = Math.max(maxMemMB, (rt.totalMemory() - rt.freeMemory()) / 1048576.0);
        writeCanonicalOutput(file, minsup);

        long endTime = System.nanoTime();
        double timeSec = (endTime - startTime) / 1e9;

        System.out.printf(Locale.US, "%-12s %-8s %-10.6f %-12.2f %-15d %-15d %-20d %-15d%n",
                file.getName(), formatMinSup(minsup), timeSec, maxMemMB,
                candNum, patternFusionsCount, element_num, fre_num);

        writer.write(String.format(Locale.US, "%s,%s,%.6f,%.2f,%d,%d,%d,%d%n",
                file.getName(), formatMinSup(minsup), timeSec, maxMemMB,
                candNum, patternFusionsCount, element_num, fre_num));
        writer.flush();
    }

    // ================== CÁC HÀM GỐC (GIỮ NGUYÊN) ==================

    private static void writeCanonicalOutput(File file, double minSup) throws IOException {
        if (CANONICAL_DIR == null || CANONICAL_DIR.isEmpty()) return;
        File dir = new File(CANONICAL_DIR);
        dir.mkdirs();
        String dataset = file.getName().replaceFirst("\\.txt$", "");
        String minSupLabel = formatMinSup(minSup).replace('-', 'm').replace('.', 'p');
        File out = new File(dir, dataset + "_minsup_" + minSupLabel + ".csv");
        TreeMap<String, Double> sorted = new TreeMap<>(allfrepattern);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            writer.write("pattern,support\n");
            for (Map.Entry<String, Double> entry : sorted.entrySet()) {
                writer.write(String.format(Locale.US, "\"%s\",%.10f%n", entry.getKey(), entry.getValue()));
            }
        }
    }

    private static String formatMinSup(double minSup) {
        return BigDecimal.valueOf(minSup).stripTrailingZeros().toPlainString();
    }

    public static void forgetting_mechanism() {
        forget_mech.add(0.0);
        for (int i = 1; i <= len; i++) {
            double f = Math.pow(e, -k * (len - i));
            forget_mech.add(f);
        }
    }

    public static void find() {
        candNum = 2;
        List<Double> Z = new ArrayList<>();
        List<Double> Z2 = new ArrayList<>();
        int i = 0, j = 1;
        Integer[] Cd = {1, 2};
        Integer[] Cd2 = {2, 1};
        double f1 = 0.0, f2 = 0.0;
        while (j < len) {
            if (S.get(j) > S.get(i)) {
                Z.add((double) (j + 1));
                f1 += forget_mech.get(j + 1);
            } else if (S.get(j) < S.get(i)) {
                Z2.add((double) (j + 1));
                f2 += forget_mech.get(j + 1);
            }
            i++;
            j++;
        }
        judge_fre(Cd, Z, f1, 1);
        judge_fre(Cd2, Z2, f2, 3);
    }

    public static void judge_fre(Integer[] Cd, List<Double> Z, double sup_num, int group) {
        if (sup_num >= minsup) {
            List<List<Double>> content = new ArrayList<>();
            fre_number++;
            content.add(Z);
            content.add(List.of(sup_num));
            content.add(List.of((double) group));
            Fmap.put(Arrays.toString(Cd), content);
            allfrepattern.put(Arrays.toString(Cd), sup_num);
        }
    }

    private static void calculate() {
        if (fre_number > 0) {
            fre_num += fre_number;
            fre_number = 0;
            Comparator<Map.Entry<String, List<List<Double>>>> comparator = (entry1, entry2) -> {
                double v1 = entry1.getValue().get(1).get(0);
                double v2 = entry2.getValue().get(1).get(0);
                return Double.compare(v2, v1);
            };
            List<Map.Entry<String, List<List<Double>>>> sortedData = new ArrayList<>(Fmap.entrySet());
            Collections.sort(sortedData, comparator);

            // Null-safe: either base pattern may be absent when below minsup
            List<List<Double>> entry12 = Fmap.get("[1, 2]");
            List<List<Double>> entry21 = Fmap.get("[2, 1]");
            boolean flag = (entry12 != null && entry21 != null)
                    ? entry12.get(1).get(0) > entry21.get(1).get(0)
                    : (entry12 != null);

            Fmap = new LinkedHashMap<>();
            List<LNode> Lb1 = new ArrayList<>();
            List<Double> suffset = new ArrayList<>();
            for (Map.Entry<String, List<List<Double>>> entry : sortedData) {
                Lb1.add(getLNode(entry.getValue().get(0)));
                suffset.add(entry.getValue().get(1).get(0));
            }

            if (flag) {
                for (Map.Entry<String, List<List<Double>>> entry1 : sortedData) {
                    Integer[] P = stringToArray(entry1.getKey());
                    LNode PNode = getLNode(entry1.getValue().get(0));
                    int group = entry1.getValue().get(2).get(0).intValue();
                    int i = 0;
                    for (Map.Entry<String, List<List<Double>>> entry2 : sortedData) {
                        if (PNode.data >= minsup && suffset.get(i) >= minsup) {
                            patternFusionsCount++;
                            LNode QNode = Lb1.get(i);
                            Integer[] Q = stringToArray(entry2.getKey());
                            patternFusion(P, PNode, Q, QNode, group, suffset, i);
                        }
                        group++;
                        i++;
                    }
                }
            } else {
                for (Map.Entry<String, List<List<Double>>> entry1 : sortedData) {
                    Integer[] P = stringToArray(entry1.getKey());
                    LNode PNode = getLNode(entry1.getValue().get(0));
                    int group = entry1.getValue().get(2).get(0).intValue() + 1;
                    int i = 0;
                    for (Map.Entry<String, List<List<Double>>> entry2 : sortedData) {
                        if (PNode.data >= minsup && suffset.get(i) >= minsup) {
                            patternFusionsCount++;
                            LNode QNode = Lb1.get(i);
                            Integer[] Q = stringToArray(entry2.getKey());
                            patternFusion(P, PNode, Q, QNode, group, suffset, i);
                        }
                        group--;
                        i++;
                    }
                }
            }
        }

        while (fre_number > 0) {
            fre_num += fre_number;
            fre_number = 0;
            Comparator<Map.Entry<String, List<List<Double>>>> comparator = (entry1, entry2) -> {
                double v1 = entry1.getValue().get(1).get(0);
                double v2 = entry2.getValue().get(1).get(0);
                return Double.compare(v2, v1);
            };
            List<Map.Entry<String, List<List<Double>>>> sortedData = new ArrayList<>(Fmap.entrySet());
            Collections.sort(sortedData, comparator);

            List<Map.Entry<String, List<List<Double>>>> G1 = new LinkedList<>();
            List<Map.Entry<String, List<List<Double>>>> G2 = new LinkedList<>();
            for (Map.Entry<String, List<List<Double>>> entry : sortedData) {
                int g = entry.getValue().get(2).get(0).intValue();
                if (g == 1 || g == 2) G1.add(entry);
                else G2.add(entry);
            }

            Fmap = new LinkedHashMap<>();
            List<LNode> Lb1 = new ArrayList<>();
            List<LNode> Lb2 = new ArrayList<>();
            List<Double> suffset1 = new ArrayList<>();
            List<Double> suffset2 = new ArrayList<>();
            for (Map.Entry<String, List<List<Double>>> e : G1) {
                Lb1.add(getLNode(e.getValue().get(0)));
                suffset1.add(e.getValue().get(1).get(0));
            }
            for (Map.Entry<String, List<List<Double>>> e : G2) {
                Lb2.add(getLNode(e.getValue().get(0)));
                suffset2.add(e.getValue().get(1).get(0));
            }

            for (Map.Entry<String, List<List<Double>>> entry1 : sortedData) {
                Integer[] P = stringToArray(entry1.getKey());
                Integer[] PSuf = Arrays.copyOfRange(P, 1, P.length);
                LNode PNode = getLNode(entry1.getValue().get(0));
                int group = entry1.getValue().get(2).get(0).intValue();
                int i = 0;
                if (group == 1 || group == 3) {
                    for (Map.Entry<String, List<List<Double>>> entry2 : G1) {
                        if (PNode.data >= minsup && suffset1.get(i) >= minsup) {
                            patternFusionsCount++;
                            Integer[] Q = stringToArray(entry2.getKey());
                            Integer[] QPre = Arrays.copyOfRange(Q, 0, Q.length - 1);
                            contrast_num++;
                            if (Arrays.equals(getOrder(PSuf), getOrder(QPre))) {
                                LNode QNode = Lb1.get(i);
                                patternFusion(P, PNode, Q, QNode, group, suffset1, i);
                            }
                        }
                        i++;
                    }
                } else {
                    for (Map.Entry<String, List<List<Double>>> entry2 : G2) {
                        if (PNode.data >= minsup && suffset2.get(i) >= minsup) {
                            patternFusionsCount++;
                            Integer[] Q = stringToArray(entry2.getKey());
                            Integer[] QPre = Arrays.copyOfRange(Q, 0, Q.length - 1);
                            contrast_num++;
                            if (Arrays.equals(getOrder(PSuf), getOrder(QPre))) {
                                LNode QNode = Lb2.get(i);
                                patternFusion(P, PNode, Q, QNode, group, suffset2, i);
                            }
                        }
                        i++;
                    }
                }
            }
        }
    }

    private static void patternFusion(Integer[] P, LNode PNode, Integer[] Q, LNode QNode,
                                      int group, List<Double> suffset, int index) {
        int slen = P.length;
        if (P[0] == Q[Q.length - 1]) {
            Integer[] Cd = new Integer[slen + 1];
            Integer[] Cd2 = new Integer[slen + 1];
            Cd[0] = P[0]; Cd2[0] = P[0] + 1;
            Cd[slen] = Cd2[0]; Cd2[slen] = Cd[0];
            for (int t = 1; t < slen; t++) {
                if (P[t] > Q[slen - 1]) {
                    Cd[t] = P[t] + 1;
                    Cd2[t] = P[t] + 1;
                } else {
                    Cd[t] = P[t];
                    Cd2[t] = P[t];
                }
            }
            candNum += 2;
            grow_BaseP2(slen, QNode, PNode, Cd, Cd2, group, suffset, index);
        } else if (P[0] < Q[Q.length - 1]) {
            Integer[] Cd = new Integer[slen + 1];
            Cd[0] = P[0];
            Cd[slen] = Q[slen - 1] + 1;
            for (int t = 1; t < slen; t++) {
                Cd[t] = (P[t] > Q[slen - 1]) ? P[t] + 1 : P[t];
            }
            candNum++;
            grow_BaseP1(QNode, PNode, Cd, group, suffset, index);
        } else {
            Integer[] Cd = new Integer[slen + 1];
            Cd[0] = P[0] + 1;
            Cd[slen] = Q[slen - 1];
            for (int t = 0; t < slen - 1; t++) {
                Cd[t + 1] = (Q[t] > P[0]) ? Q[t] + 1 : Q[t];
            }
            candNum++;
            grow_BaseP1(QNode, PNode, Cd, group, suffset, index);
        }
    }

    private static void grow_BaseP2(int slen, LNode qNode, LNode pNode,
                                    Integer[] Cd, Integer[] Cd2, int group,
                                    List<Double> suffset, int index) {
        List<Double> Z = new ArrayList<>();
        List<Double> Z2 = new ArrayList<>();
        LNode p = pNode;
        LNode q = qNode;
        double f1 = 0.0, f2 = 0.0;
        while (p.next != null && q.next != null) {
            if (q.next.data == p.next.data + 1) {
                int lst = q.next.data.intValue();
                int fri = lst - slen;
                if (S.get(lst - 1) > S.get(fri - 1)) {
                    Z.add(q.next.data);
                    f1 += forget_mech.get(q.next.data.intValue());
                } else if (S.get(lst - 1) < S.get(fri - 1)) {
                    Z2.add(q.next.data);
                    f2 += forget_mech.get(q.next.data.intValue());
                }
                p.next = p.next.next;
                q.next = q.next.next;
            } else if (p.next.data < q.next.data) {
                p = p.next;
            } else {
                q = q.next;
            }
            element_num++;
        }
        pNode.data = pNode.data - Z.size() - Z2.size();
        suffset.set(index, suffset.get(index) - f1 - f2);
        judge_fre(Cd, Z, f1, group);
        judge_fre(Cd2, Z2, f2, group);
    }

    private static void grow_BaseP1(LNode qNode, LNode pNode, Integer[] Cd,
                                    int group, List<Double> suffset, int index) {
        List<Double> Z = new ArrayList<>();
        LNode p = pNode;
        LNode q = qNode;
        double f = 0.0;
        while (p.next != null && q.next != null) {
            if (q.next.data == p.next.data + 1) {
                Z.add(q.next.data);
                f += forget_mech.get(q.next.data.intValue());
                p.next = p.next.next;
                q.next = q.next.next;
            } else if (p.next.data < q.next.data) {
                p = p.next;
            } else {
                q = q.next;
            }
            element_num++;
        }
        pNode.data = pNode.data - Z.size();
        suffset.set(index, suffset.get(index) - f);
        judge_fre(Cd, Z, f, group);
    }

    private static Integer[] getOrder(Integer[] seq) {
        Integer[] arr = Arrays.copyOf(seq, seq.length);
        Integer[] order = new Integer[arr.length];
        Integer[] temp = Arrays.copyOf(arr, arr.length);
        Arrays.sort(temp);
        for (int j = 0; j < arr.length; j++) {
            int min = temp[temp.length - 1] + 1;
            int idx = 0;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] < min && arr[i] != Integer.MIN_VALUE) {
                    min = arr[i];
                    idx = i;
                }
            }
            arr[idx] = Integer.MIN_VALUE;
            order[idx] = j + 1;
        }
        return order;
    }

    private static Integer[] stringToArray(String key) {
        key = key.substring(1, key.length() - 1);
        String[] parts = key.split(",");
        Integer[] arr = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
        return arr;
    }

    private static LNode getLNode(List<Double> list) {
        LNode head = new LNode();
        head.data = (double) list.size();
        LNode cur = head;
        for (Double v : list) {
            LNode node = new LNode();
            node.data = v;
            cur.next = node;
            cur = node;
        }
        cur.next = null;
        return head;
    }

    static class LNode {
        Double data;
        LNode next = null;
    }
}

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

public final class GenerateFOMHypothesisDatasets {
    private GenerateFOMHypothesisDatasets() {}

    public static void main(String[] args) throws IOException {
        File outputDir = new File(args.length == 0
                ? "data/synthetic/fom_hypothesis_probe"
                : args[0]);
        outputDir.mkdirs();

        writeSeries(new File(outputDir, "SYN_RANDOM_4096.txt"), 4096, randomSeries(20260812L));
        writeSeries(new File(outputDir, "SYN_ALTERNATING_4096.txt"), 4096,
                i -> (i % 2 == 0 ? 0.0 : 1.0) + i * 1e-7);
        writeSeries(new File(outputDir, "SYN_MONOTONIC_2048.txt"), 2048, i -> i);
        writeSeries(new File(outputDir, "SYN_SAWTOOTH_4096.txt"), 4096,
                i -> (i % 8) + i * 1e-7);
        writeSeries(new File(outputDir, "SYN_RANDOM_1024.txt"), 1024, randomSeries(20260812L));
        writeSeries(new File(outputDir, "SYN_ALTERNATING_1024.txt"), 1024,
                i -> (i % 2 == 0 ? 0.0 : 1.0) + i * 1e-7);
        writeSeries(new File(outputDir, "SYN_MONOTONIC_1024.txt"), 1024, i -> i);
        writeSeries(new File(outputDir, "SYN_SAWTOOTH_1024.txt"), 1024,
                i -> (i % 8) + i * 1e-7);
        writeSeries(new File(outputDir, "SYN_RANDOM_128.txt"), 128, randomSeries(20260812L));
        writeSeries(new File(outputDir, "SYN_ALTERNATING_128.txt"), 128,
                i -> (i % 2 == 0 ? 0.0 : 1.0) + i * 1e-7);
        writeSeries(new File(outputDir, "SYN_MONOTONIC_128.txt"), 128, i -> i);

        System.out.println("Generated hypothesis datasets in " + outputDir.getCanonicalPath());
    }

    private static IntToDoubleFunction randomSeries(long seed) {
        Random random = new Random(seed);
        double[] values = new double[4096];
        for (int i = 0; i < values.length; i++) values[i] = random.nextDouble();
        return i -> values[i];
    }

    private static void writeSeries(File file, int length, IntToDoubleFunction valueAt) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < length; i++) {
                if (i > 0) writer.write(' ');
                writer.write(String.format(Locale.US, "%.10f", valueAt.applyAsDouble(i)));
            }
            writer.newLine();
        }
    }
}

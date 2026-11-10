import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Summary {

    /// Percentile with linear interpolation between the closest values
    /// (same method as R's default and numpy's default)
    ///
    /// @param sorted values sorted ascending
    /// @param p      between 0 and 1, e.g. 0.25 for the first quartile
    static double percentile(long[] sorted, double p) {
        double index = p * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        double fraction = index - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Path.of("results", "raw_times.csv"));

        Map<String, List<Long>> groups = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split(",");
            String key = parts[0] + "," + parts[1] + "," + parts[2] + "," + parts[3];
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(Long.parseLong(parts[6]));
        }

        // rows grouped by condition so the three algorithms of a condition are next to each other
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of("results", "summary.csv"))) {
            writer.write("data_type,input_order,size,algorithm,n,min_us,q1_us,median_us,q3_us,max_us");
            writer.newLine();

            for (String type : Plots.TYPES) {
                for (String order : Plots.ORDERS) {
                    for (int size : Plots.SIZES) {
                        for (String algorithm : Plots.ALGORITHMS) {
                            List<Long> group = groups.get(algorithm + "," + type + "," + order + "," + size);
                            long[] times = group.stream().mapToLong(Long::longValue).sorted().toArray();

                            // Locale.ROOT so the decimal separator is always a dot
                            writer.write(String.format(Locale.ROOT, "%s,%s,%d,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f",
                                    type, order, size, algorithm,
                                    times.length,
                                    times[0] / 1000.0,
                                    percentile(times, 0.25) / 1000,
                                    percentile(times, 0.5) / 1000,
                                    percentile(times, 0.75) / 1000,
                                    times[times.length - 1] / 1000.0));
                            writer.newLine();
                        }
                    }
                }
            }
        }
        System.out.println("wrote results/summary.csv (" + groups.size() + " conditions)");

        Plots.createPlots(groups);
    }
}

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.BoxChart;
import org.knowm.xchart.BoxChartBuilder;
import org.knowm.xchart.BoxSeries;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.BoxStyler;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.Marker;
import org.knowm.xchart.style.markers.SeriesMarkers;

/// Creates the plots for the report with XChart:
/// for each input order one box plot (n = 10,000, all types) and one scaling chart (all sizes and types)
public class Plots {

    static final String[] ALGORITHMS = {"BubbleSortWhileNeeded", "BubbleSortUntilNoChange", "BubbleSortPassPerItem"};
    static final String[] TYPES = {"Integer", "Double", "Float", "String"};
    static final String[] ORDERS = {"RANDOM", "SORTED", "REVERSED"};
    static final int[] SIZES = {100, 1000, 5000, 10000};

    /// box plots only for the biggest size, the other sizes are in summary.csv
    static final int BOX_PLOT_SIZE = 10000;

    // one colour per algorithm, one marker per data type
    static final Color[] COLORS = {new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a)};
    static final Marker[] MARKERS = {SeriesMarkers.CIRCLE, SeriesMarkers.SQUARE, SeriesMarkers.DIAMOND, SeriesMarkers.TRIANGLE_UP};

    static final Path PLOTS_DIR = Path.of("results", "plots");

    static String shortName(String algorithm) {
        return algorithm.replace("BubbleSort", "");
    }

    static void style(AxesChartStyler styler) {
        styler.setChartBackgroundColor(Color.WHITE);
        styler.setPlotBackgroundColor(Color.WHITE);
        styler.setPlotBorderVisible(false);
        styler.setPlotGridLinesColor(new Color(0xe0e0e0));
        styler.setPlotGridVerticalLinesVisible(false);
        styler.setLegendBorderColor(Color.WHITE);
        styler.setAntiAlias(true);
        // labels only at powers of 10 on log axes, and 10,000 instead of 1E4
        styler.setXAxisLogarithmicDecadeOnly(true);
        styler.setYAxisLogarithmicDecadeOnly(true);
        styler.setXAxisDecimalPattern("#,##0.####");
        styler.setYAxisDecimalPattern("#,##0.####");
    }

    /// Box plot for one input order with n = 10,000: one box per data type and algorithm (12 boxes)
    static BoxChart boxChart(Map<String, List<Long>> groups, String order) {
        // sorted input: PassPerItem is thousands of times slower, so log scale or the other two are not visible
        boolean logScale = order.equals("SORTED");

        BoxChart chart = new BoxChartBuilder().width(1200).height(700)
                .title(order.charAt(0) + order.substring(1).toLowerCase() + " input, n = " + String.format("%,d", BOX_PLOT_SIZE)
                        + " (100 measurements per box)")
                .yAxisTitle("time in ms" + (logScale ? " (log)" : ""))
                .build();
        style(chart.getStyler());
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setYAxisLogarithmic(logScale);
        chart.getStyler().setXAxisLabelRotation(40);
        // same quartile method as Summary.percentile (linear interpolation)
        chart.getStyler().setBoxplotCalCulationMethod(BoxStyler.BoxplotCalCulationMethod.N_LESS_1);

        for (String type : TYPES) {
            for (int a = 0; a < ALGORITHMS.length; a++) {
                List<Double> times = new ArrayList<>();
                for (long nanos : groups.get(ALGORITHMS[a] + "," + type + "," + order + "," + BOX_PLOT_SIZE)) {
                    times.add(nanos / 1_000_000.0);
                }
                BoxSeries series = chart.addSeries(type + ": " + shortName(ALGORITHMS[a]), times);
                series.setMarkerColor(COLORS[a]);
            }
        }
        return chart;
    }

    /// Median time against array size for one input order, both axes logarithmic.
    /// Colour = algorithm, marker = data type
    static XYChart scalingChart(Map<String, List<Long>> groups, String order) {
        XYChart chart = new XYChartBuilder().width(1200).height(700)
                .title(order.charAt(0) + order.substring(1).toLowerCase() + " input: median time against array size")
                .xAxisTitle("array size (log)")
                .yAxisTitle("median time in ms (log)")
                .build();
        style(chart.getStyler());
        chart.getStyler().setXAxisLogarithmic(true);
        chart.getStyler().setYAxisLogarithmic(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setMarkerSize(9);

        for (int a = 0; a < ALGORITHMS.length; a++) {
            for (int t = 0; t < TYPES.length; t++) {
                double[] sizes = new double[SIZES.length];
                double[] medians = new double[SIZES.length];
                for (int i = 0; i < SIZES.length; i++) {
                    List<Long> times = groups.get(ALGORITHMS[a] + "," + TYPES[t] + "," + order + "," + SIZES[i]);
                    long[] sorted = times.stream().mapToLong(Long::longValue).sorted().toArray();
                    sizes[i] = SIZES[i];
                    medians[i] = Summary.percentile(sorted, 0.5) / 1_000_000;
                }
                XYSeries series = chart.addSeries(shortName(ALGORITHMS[a]) + ", " + TYPES[t], sizes, medians);
                series.setLineColor(COLORS[a]);
                series.setMarkerColor(COLORS[a]);
                series.setMarker(MARKERS[t]);
                // strings are the only type that is clearly slower, dashed so they stand out
                if (TYPES[t].equals("String")) {
                    series.setLineStyle(SeriesLines.DASH_DASH);
                }
            }
        }
        return chart;
    }

    /// Saves boxplot_<order>.png and scaling_<order>.png for each input order (6 images)
    ///
    /// @param groups raw times in ns, key is "algorithm,data_type,input_order,size"
    static void createPlots(Map<String, List<Long>> groups) throws IOException {
        Files.createDirectories(PLOTS_DIR);

        for (String order : ORDERS) {
            String name = order.toLowerCase() + ".png";
            // 144 dpi = twice the normal resolution, so the text stays sharp in the report
            BitmapEncoder.saveBitmapWithDPI(boxChart(groups, order), PLOTS_DIR.resolve("boxplot_" + name).toString(), BitmapFormat.PNG, 144);
            BitmapEncoder.saveBitmapWithDPI(scalingChart(groups, order), PLOTS_DIR.resolve("scaling_" + name).toString(), BitmapFormat.PNG, 144);
        }

        System.out.println("wrote plots to " + PLOTS_DIR);
    }
}

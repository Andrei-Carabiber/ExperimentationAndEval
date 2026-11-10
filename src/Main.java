import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class Main {

    static final Random random = new Random(1231);

    static final int[] SIZES = {100, 1000, 5000, 10000};
    static final int INPUTS_PER_CONDITION = 5;
    static final int REPETITIONS = 20;

    static final int WARMUP_RUNS = 200;
    static final int WARMUP_SIZE = 1000;

    static final int STRING_LENGTH = 10;

    static final Path RESULTS_DIR = Path.of("results");

    enum InputOrder {
        RANDOM,
        SORTED,
        REVERSED
    }

    /// One type of element to sort
    ///
    /// @param name         name written in the csv
    /// @param generator    makes one random value of the type from a Random
    /// @param arrayFactory makes an empty array of the type
    record DataType<T extends Comparable<T>>(String name, Function<Random, T> generator, IntFunction<T[]> arrayFactory) {
    }

    static final List<DataType<?>> DATA_TYPES = List.of(
            new DataType<>("Integer", Random::nextInt, Integer[]::new),
            new DataType<>("Double", Random::nextDouble, Double[]::new),
            new DataType<>("Float", Random::nextFloat, Float[]::new),
            new DataType<>("String", Main::randomString, String[]::new));

    /// Random string of lowercase letters a-z
    static String randomString(Random random) {
        StringBuilder builder = new StringBuilder(STRING_LENGTH);
        for (int i = 0; i < STRING_LENGTH; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }

    static <T extends Comparable<T>> List<Sorter<T>> createSorters() {
        return new ArrayList<>(List.of(
                new BubbleSortWhileNeeded<>(),
                new BubbleSortPassPerItem<>(),
                new BubbleSortUntilNoChange<>()));
    }

    /// Sorts a copy of the data and returns how long the sort took.
    ///
    /// @return elapsed time in nanoseconds
    static <T extends Comparable<T>> long testSorter(Sorter<T> sorter, T[] data) {
        T[] copy = Arrays.copyOf(data, data.length);
        long start = System.nanoTime();
        sorter.sort(copy);
        long elapsed = System.nanoTime() - start;

        if (!isSorted(copy)) {
            throw new IllegalStateException(sorter.getClass().getSimpleName() + " did not sort the array");
        }
        return elapsed;
    }

    static <T extends Comparable<T>> boolean isSorted(T[] array) {
        for (int i = 1; i < array.length; i++) {
            if (array[i - 1].compareTo(array[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    /// Generate an array of a type either random, sorted, or reversed
    ///
    /// @param size      size of the array
    /// @param generator A function that generates an object of the type
    /// @param order     order of the elements in the returned array
    static <T extends Comparable<T>> T[] generateArray(
            int size,
            Supplier<T> generator,
            IntFunction<T[]> arrayFactory,
            InputOrder order) {

        T[] array = arrayFactory.apply(size);
        for (int i = 0; i < size; i++) {
            array[i] = generator.get();
        }

        if (order == InputOrder.SORTED) {
            Arrays.sort(array);
        } else if (order == InputOrder.REVERSED) {
            Arrays.sort(array, Collections.reverseOrder());
        }

        return array;
    }

    /// Run every sort algo many times with one data type
    static <T extends Comparable<T>> void warmUp(DataType<T> type) {
        List<Sorter<T>> sorters = createSorters();
        Random warmupRandom = new Random(7283161);
        InputOrder[] orders = InputOrder.values();

        for (int i = 0; i < WARMUP_RUNS; i++) {
            InputOrder order = orders[i % orders.length];
            T[] data = generateArray(WARMUP_SIZE, () -> type.generator().apply(warmupRandom), type.arrayFactory(), order);
            for (Sorter<T> sorter : sorters) {
                testSorter(sorter, data);
            }
        }
    }

    /// Runs all sizes, orders, inputs and repetitions for one data type and writes every time to the csv
    static <T extends Comparable<T>> void runExperiment(DataType<T> type, BufferedWriter writer) throws IOException {
        List<Sorter<T>> sorters = createSorters();

        for (int size : SIZES) {
            for (InputOrder order : InputOrder.values()) {
                System.out.println("Running " + type.name() + " order " + order + " with size " + size);
                for (int seed = 1; seed <= INPUTS_PER_CONDITION; seed++) {
                    Random inputRandom = new Random(seed);
                    T[] data = generateArray(size, () -> type.generator().apply(inputRandom), type.arrayFactory(), order);

                    for (int rep = 0; rep < REPETITIONS; rep++) {
                        Collections.shuffle(sorters, random);

                        for (Sorter<T> sorter : sorters) {
                            long nanos = testSorter(sorter, data);
                            writer.write(sorter.getClass().getSimpleName() + "," + type.name() + "," + order + ","
                                    + size + "," + seed + "," + rep + "," + nanos);
                            writer.newLine();
                        }
                    }
                }
                writer.flush();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(RESULTS_DIR);

        // warm up with all types before measuring anything, otherwise the JIT recompiles
        // the sort methods every time it sees a new type in the middle of the measurements
        System.out.println("Warmup");
        for (DataType<?> type : DATA_TYPES) {
            warmUp(type);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(RESULTS_DIR.resolve("raw_times.csv"))) {
            writer.write("algorithm,data_type,input_order,size,input_seed,repetition,nanos");
            writer.newLine();

            for (DataType<?> type : DATA_TYPES) {
                runExperiment(type, writer);
            }
        }
    }
}

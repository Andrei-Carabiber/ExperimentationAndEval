import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class Main {

    static final Random random = new Random(1231);

    static <T extends Comparable<T>> long testSorter(Sorter<T> sorter, T[] data) {
        T[] copy = Arrays.copyOf(data, data.length);

        long start = System.nanoTime();
        sorter.sort(copy);
        long elapsed = System.nanoTime() - start;

        return elapsed;
    }

    /// Runs a benchmark for a select sorter
    ///
    /// @param iterations number of times it runs
    static <T extends Comparable<T>> double runBenchmarkSameType(Sorter<T> sorter, T[] data, int iterations) {
        long[] times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            times[i] = testSorter(sorter, data);
        }
        Arrays.sort(times);
        return Arrays.stream(times).average().orElse(0);
    }

    /// Generate an array of a type either sorted, reversed, or random
    ///
    /// @param generator A function that generates an object of the type
    /// @param variation 1 -> random 2 -> sorted 3 -> reversed
    /// @param size      size of the array
    static <T extends Comparable<? super T>> T[] generateArray(
            int size,
            Supplier<T> generator,
            IntFunction<T[]> arrayFactory,
            int variation) {

        T[] array = arrayFactory.apply(size);
        for (int i = 0; i < size; i++) {
            array[i] = generator.get();
        }

        if (variation == 2) {
            Arrays.sort(array);
        } else if (variation == 3) {
            Arrays.sort(array, Collections.reverseOrder());
        }

        return array;
    }

    public static void main(String[] args) {

        Sorter whileNeeded = new BubbleSortWhileNeeded<>();
        Sorter passPerItem = new BubbleSortPassPerItem<>();
        Sorter untilNoChange = new BubbleSortUntilNoChange<>();

    }
}
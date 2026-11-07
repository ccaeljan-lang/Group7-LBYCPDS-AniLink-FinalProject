
package ph.edu.dlsu.anilink.util;

import ph.edu.dlsu.anilink.model.DepartureSchedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * feat: Sort departure schedules chronologically using Bubble Sort.
 *
 * Average and worst-case complexity: O(n^2)
 * Best-case complexity: O(n) with early termination
 */

public final class ScheduleBubbleSort {

    private ScheduleBubbleSort() {
    }

    public static List<DepartureSchedule> byDepartureTime(
            List<DepartureSchedule> schedules) {

        return sort(schedules, Comparator.comparing(
                DepartureSchedule::getDepartureTime,
                Comparator.nullsLast(
                        Comparator.<LocalTime>naturalOrder()
                )
        ));
    }

    // Implement reusable Bubble Sort using adjacent comparisons.
    public static <T> List<T> sort(
            List<T> source,
            Comparator<? super T> comparator) {

        List<T> result = new ArrayList<>(source);

        for (int pass = 0; pass < result.size() - 1; pass++) {

            boolean swapped = false;

            for (int i = 0; i < result.size() - 1 - pass; i++) {

                // Compare two adjacent elements.
                if (comparator.compare(
                        result.get(i),
                        result.get(i + 1)) > 0) {

                    // Swap elements if they are out of order.
                    T temp = result.get(i);

                    result.set(i, result.get(i + 1));
                    result.set(i + 1, temp);

                    swapped = true;
                }
            }

            // Stop early if the list is already sorted.
            if (!swapped) {
                break;
            }
        }

        return result;
    }
}

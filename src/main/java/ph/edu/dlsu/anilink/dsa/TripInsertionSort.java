package ph.edu.dlsu.anilink.dsa;

import ph.edu.dlsu.anilink.controller.MyReservationsController.ReservationItem;

import java.util.List;

/**
 * Sort reservation items by their user-facing trip number using Insertion Sort.
 *
 * <p>This is a hand-written Insertion Sort, not {@link java.util.Collections#sort},
 * {@link java.util.List#sort}, stream sorting, or a database {@code ORDER BY}.
 * It satisfies the DSA requirement of showing an explicit, instructor-visible
 * sorting algorithm.</p>
 *
 * <ul>
 *   <li>Outer loop: {@code for (int i = 1; i &lt; list.size(); i++)}</li>
 *   <li>Save the current item, then shift earlier items with a greater trip
 *       number one position to the right while moving backwards.</li>
 *   <li>Insert the saved item at {@code list.set(j + 1, current)}.</li>
 * </ul>
 *
 * Sorting is numeric ascending (so trip #2 precedes trip #10, not lexicographic),
 * stable for equal trip numbers (only strictly greater keys shift), with a
 * deterministic secondary order of travel date/time then reservation ID.
 *
 * @see ph.edu.dlsu.anilink.controller.MyReservationsController#loadReservationsAsync()
 */
public final class TripInsertionSort {

    private TripInsertionSort() {
    }

    /**
     * Sorts {@code items} in place by trip number ascending.
     *
     * @param items the reservation items to sort; may be null or empty
     */
    public static void sortByTripNumber(List<ReservationItem> items) {
        if (items == null || items.size() < 2) {
            return;
        }

        for (int i = 1; i < items.size(); i++) {
            ReservationItem current = items.get(i);
            int j = i - 1;

            // Shift items whose trip number is greater than the current one
            // one position to the right. Strictly greater => stable for equal keys.
            while (j >= 0 && compareTripNumber(items.get(j), current) > 0) {
                items.set(j + 1, items.get(j));
                j--;
            }

            items.set(j + 1, current);
        }
    }

    /**
     * Compares two reservation items by trip number, then travel date/time,
     * then reservation ID as a deterministic tie-breaker.
     *
     * @return negative if a sorts before b, positive if after, zero if equal
     */
    private static int compareTripNumber(ReservationItem a, ReservationItem b) {
        int na = tripNumber(a);
        int nb = tripNumber(b);

        // Unknown trip numbers (-1) sort after all known numbers.
        if (na < 0 && nb < 0) {
            return 0;
        }
        if (na < 0) {
            return 1;
        }
        if (nb < 0) {
            return -1;
        }

        if (na != nb) {
            return Integer.compare(na, nb);
        }

        // Same trip number (same trip): order by travel date/time, then ID.
        int c = compareText(a.getTravelDate(), b.getTravelDate());
        if (c != 0) {
            return c;
        }
        c = compareText(a.getDepartureTime(), b.getDepartureTime());
        if (c != 0) {
            return c;
        }
        return Long.compare(reservationId(a), reservationId(b));
    }

    private static int tripNumber(ReservationItem item) {
        return item == null ? -1 : item.getTripNumber();
    }

    private static long reservationId(ReservationItem item) {
        return item == null || item.getReservationId() == null ? 0L : item.getReservationId();
    }

    private static int compareText(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }
}

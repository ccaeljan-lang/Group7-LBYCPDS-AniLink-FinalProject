package ph.edu.dlsu.anilink.dsa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ph.edu.dlsu.anilink.controller.MyReservationsController.ReservationItem;

/**
 * Unit tests for {@link TripInsertionSort}. These are pure in-memory DSA tests
 * (no Supabase, no network). Execute with: mvn -q test
 */
public class TripInsertionSortTest {

    private static ReservationItem item(int tripNumber, String travelDate, Long reservationId) {
        return new ReservationItem(
                reservationId,
                "RES-" + reservationId,
                "Manila ↔ Laguna",
                travelDate,
                "08:00",
                "CONFIRMED",
                (long) tripNumber,
                1L,
                tripNumber);
    }

    @Test
    public void sortsNumericAscendingNotLexicographic() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(10, "2026-11-25", 10L));
        list.add(item(2, "2026-11-25", 2L));
        list.add(item(7, "2026-11-25", 7L));
        list.add(item(1, "2026-11-25", 1L));

        TripInsertionSort.sortByTripNumber(list);

        assertEquals(List.of(1L, 2L, 7L, 10L), tripIds(list));
    }

    @Test
    public void alreadyAscendingStaysUnchanged() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(1, "2026-11-25", 1L));
        list.add(item(2, "2026-11-25", 2L));
        list.add(item(3, "2026-11-25", 3L));

        TripInsertionSort.sortByTripNumber(list);

        assertEquals(List.of(1L, 2L, 3L), tripIds(list));
    }

    @Test
    public void descendingIsSorted() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(9, "2026-11-25", 9L));
        list.add(item(4, "2026-11-25", 4L));
        list.add(item(2, "2026-11-25", 2L));

        TripInsertionSort.sortByTripNumber(list);

        assertEquals(List.of(2L, 4L, 9L), tripIds(list));
    }

    @Test
    public void emptyAndSingleton() {
        List<ReservationItem> empty = new ArrayList<>();
        TripInsertionSort.sortByTripNumber(empty);
        assertEquals(0, empty.size());

        List<ReservationItem> single = new ArrayList<>();
        single.add(item(5, "2026-11-25", 5L));
        TripInsertionSort.sortByTripNumber(single);
        assertEquals(List.of(5L), tripIds(single));
    }

    @Test
    public void equalTripNumbersAreStable() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(3, "2026-11-25", 100L)); // first
        list.add(item(1, "2026-11-25", 200L));
        list.add(item(3, "2026-11-25", 300L)); // second with same number

        TripInsertionSort.sortByTripNumber(list);

        List<Long> ids = tripIds(list);
        assertEquals(List.of(200L, 100L, 300L), ids);
        // The two trip #3 items keep their original relative order (100 before 300).
        assertTrue(ids.indexOf(100L) < ids.indexOf(300L));
    }

    @Test
    public void unknownTripNumberSortsAfterKnown() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(-1, null, 1L));   // legacy / unknown
        list.add(item(2, "2026-11-25", 2L));
        list.add(item(1, "2026-11-25", 3L));

        TripInsertionSort.sortByTripNumber(list);

        assertEquals(List.of(3L, 2L, 1L), tripIds(list));
    }

    @Test
    public void sameTripNumberOrderedByDateThenId() {
        List<ReservationItem> list = new ArrayList<>();
        list.add(item(3, "2026-11-27", 30L));
        list.add(item(3, "2026-11-25", 10L));
        list.add(item(3, "2026-11-25", 20L));

        TripInsertionSort.sortByTripNumber(list);

        assertEquals(List.of(10L, 20L, 30L), tripIds(list));
    }

    private static List<Long> tripIds(List<ReservationItem> list) {
        List<Long> ids = new ArrayList<>();
        for (ReservationItem it : list) {
            ids.add(it.getReservationId());
        }
        return ids;
    }
}

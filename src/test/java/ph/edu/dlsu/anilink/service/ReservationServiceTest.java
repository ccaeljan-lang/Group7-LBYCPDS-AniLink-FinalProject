package ph.edu.dlsu.anilink.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.WaitingList;

/**
 * Unit tests for ReservationService cancellation/waitlist promotion (no Supabase,
 * no network). Execute with: mvn -q test
 */
public class ReservationServiceTest {

    private SupabaseService supabaseService;
    private BookingValidationService bookingValidationService;
    private WaitingListService waitingListService;
    private ReservationService service;

    @BeforeEach
    public void setUp() {
        supabaseService = mock(SupabaseService.class);
        bookingValidationService = mock(BookingValidationService.class);
        waitingListService = mock(WaitingListService.class);
        service = new ReservationService(supabaseService, bookingValidationService, waitingListService);

        when(bookingValidationService.validateBooking(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BookingValidationService.ValidationResult(true, "ok"));
    }

    @Test
    public void rejectAlreadyCancelledReservation() {
        when(supabaseService.getReservationById(1L))
                .thenReturn("[{\"id\":1,\"status\":\"CANCELLED\",\"trip\":{\"id\":100}}]");

        assertThrows(IllegalStateException.class, () -> service.cancelReservationAndPromote(1L));
    }

    @Test
    public void rejectCompletedReservation() {
        when(supabaseService.getReservationById(2L))
                .thenReturn("[{\"id\":2,\"status\":\"COMPLETED\",\"trip\":{\"id\":100}}]");

        assertThrows(IllegalStateException.class, () -> service.cancelReservationAndPromote(2L));
    }

    @Test
    public void cancelAndPromoteCreatesConfirmedReservationThenConfirmsWaitlist() throws Exception {
        // Existing confirmed reservation on trip 100.
        when(supabaseService.getReservationById(1L))
                .thenReturn("[" +
                        "{\"id\":1,\"status\":\"CONFIRMED\",\"trip\":{\"id\":100,\"seatsTaken\":5}," +
                        "\"passenger\":{\"id\":1,\"name\":\"A\",\"email\":\"a@dlsu.edu.ph\",\"password\":\"x\"}}" +
                        "]");
        when(supabaseService.getTripDetails(100L))
                .thenReturn("[" +
                        "{\"id\":100,\"seatsTaken\":5,\"capacity\":10,\"status\":\"SCHEDULED\"," +
                        "\"route\":{\"id\":1,\"origin\":\"A\",\"destination\":\"B\"}," +
                        "\"schedule\":{\"id\":1,\"departure_time\":\"08:00\",\"departure_date\":\"2026-11-25\",\"capacity\":10}" +
                        "}");
        // The promoted reservation row is persisted (CONFIRMED) with a QR payload.
        when(supabaseService.postReservation(anyLong(), anyLong(), anyString()))
                .thenReturn("[" +
                        "{\"id\":99,\"status\":\"CONFIRMED\",\"qr_payload\":\"ANILINK-RES-TEST1234\"," +
                        "\"trip\":{\"id\":100},\"passenger\":{\"id\":2}}" +
                        "]");
        // Passenger B is first in the FIFO waitlist and is a real PASSENGER.
        WaitingList candidate = new WaitingList(50L, 100L, 2L, "B", null, WaitingList.STATUS_WAITING);
        when(waitingListService.promoteNext(100L)).thenReturn(candidate);
        when(supabaseService.getUserById(2L))
                .thenReturn("{\"id\":2,\"name\":\"B\",\"email\":\"b@dlsu.edu.ph\",\"password\":\"x\",\"role\":\"PASSENGER\"}");
        when(waitingListService.confirmPromotion(50L)).thenReturn(true);

        assertDoesNotThrow(() -> service.cancelReservationAndPromote(1L));

        // Seat decremented then restored by the promoted reservation.
        verify(supabaseService).updateReservationStatus(1L, Reservation.CANCELLED);
        verify(supabaseService).updateTripSeatsTaken(100L, 4);
        verify(supabaseService).updateTripSeatsTaken(100L, 5);
        // Waitlist confirmed ONLY after the reservation was created.
        verify(waitingListService).confirmPromotion(50L);
    }

    @Test
    public void noWaitingPassengerMeansNoPromotionButCancellationStillCompletes() throws Exception {
        when(supabaseService.getReservationById(3L))
                .thenReturn("[{\"id\":3,\"status\":\"CONFIRMED\",\"trip\":{\"id\":100,\"seatsTaken\":5}}]");
        when(supabaseService.getTripDetails(100L))
                .thenReturn("[{\"id\":100,\"seatsTaken\":5,\"capacity\":10,\"status\":\"SCHEDULED\"}]");
        when(waitingListService.promoteNext(100L)).thenReturn(null);

        assertDoesNotThrow(() -> service.cancelReservationAndPromote(3L));

        verify(waitingListService, never()).confirmPromotion(org.mockito.ArgumentMatchers.anyLong());
        verify(supabaseService).updateTripSeatsTaken(100L, 4);
    }

    @Test
    public void promoteIntoCompletedTripIsSkipped() throws Exception {
        when(supabaseService.getReservationById(4L))
                .thenReturn("[{\"id\":4,\"status\":\"CONFIRMED\",\"trip\":{\"id\":100,\"seatsTaken\":5}}]");
        when(supabaseService.getTripDetails(100L))
                .thenReturn("[{\"id\":100,\"seatsTaken\":5,\"capacity\":10,\"status\":\"COMPLETED\"}]");

        assertDoesNotThrow(() -> service.cancelReservationAndPromote(4L));
        verify(waitingListService, never()).promoteNext(org.mockito.ArgumentMatchers.anyLong());
        verify(waitingListService, never()).confirmPromotion(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void existingCancelReservationDelegatesToSharedFlow() throws Exception {
        when(supabaseService.getReservationById(6L))
                .thenReturn("[{\"id\":6,\"status\":\"CONFIRMED\",\"trip\":{\"id\":100,\"seatsTaken\":5}}]");
        when(supabaseService.getTripDetails(100L))
                .thenReturn("[{\"id\":100,\"seatsTaken\":5,\"capacity\":10,\"status\":\"SCHEDULED\"}]");
        when(waitingListService.promoteNext(100L)).thenReturn(null);

        Reservation cancelled = service.cancelReservation(6L);
        assertEquals(Reservation.CANCELLED, cancelled.getStatus());
    }
}

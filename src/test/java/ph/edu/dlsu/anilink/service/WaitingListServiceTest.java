package ph.edu.dlsu.anilink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ph.edu.dlsu.anilink.model.WaitingList;

/**
 * Unit tests for WaitingListService FIFO promotion semantics (no Supabase,
 * no network). Execute with: mvn -q test
 */
public class WaitingListServiceTest {

    private SupabaseService supabaseService;
    private WaitingListService service;

    @BeforeEach
    public void setUp() {
        supabaseService = mock(SupabaseService.class);
        service = new WaitingListService(supabaseService);
    }

    @Test
    public void promoteNextReturnsHeadWithoutConfirmingSupabase() {
        when(supabaseService.getWaitingListByTrip(100L))
                .thenReturn("[" +
                        "{\"id\":50,\"trip_id\":100,\"passenger_id\":2,\"passenger_name\":\"B\",\"status\":\"WAITING\"}," +
                        "{\"id\":51,\"trip_id\":100,\"passenger_id\":3,\"passenger_name\":\"C\",\"status\":\"WAITING\"}" +
                        "]");

        WaitingList promoted = service.promoteNext(100L);

        assertEquals(2L, promoted.getPassengerId());
        // The entry must remain WAITING until the reservation is really persisted.
        assertEquals(WaitingList.STATUS_WAITING, promoted.getStatus());
        // No Supabase confirmation happened yet.
        verify(supabaseService, never()).updateWaitingListStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void promoteNextReturnsNullForEmptyQueue() {
        when(supabaseService.getWaitingListByTrip(100L)).thenReturn("[]");

        assertNull(service.promoteNext(100L));
    }

    @Test
    public void confirmPromotionFlipsWaitingToConfirmed() {
        when(supabaseService.markWaitingListConfirmed(50L)).thenReturn(true);

        assertTrue(service.confirmPromotion(50L));
        verify(supabaseService).markWaitingListConfirmed(50L);
    }

    @Test
    public void confirmPromotionReturnsFalseWhenUpdateFails() {
        when(supabaseService.markWaitingListConfirmed(50L)).thenReturn(false);

        assertTrue(!service.confirmPromotion(50L));
    }
}

package ph.edu.dlsu.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ph.edu.dlsu.anilink.model.WaitingList;
import ph.edu.dlsu.anilink.queue.WaitingListQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing the first-in-first-out (FIFO) waiting list for full trips.
 *
 * <p>Each trip maintains its own simple queue of waiting passengers, backed by
 * the {@code waiting_lists} table in Supabase. Queue order is determined by
 * {@code joined_at} (earliest joined = first in line).
 * </p>
 *
 * <p>The queue itself is implemented by {@link WaitingListQueue}, a plain Java
 * FIFO queue with no external library dependencies and no concurrency utilities.</p>
 */
@Service
public class WaitingListService {

    private final Map<Long, WaitingListQueue> queues = new HashMap<>();
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WaitingListService(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    /**
     * Returns the 1-based position of the passenger in the waiting list for a trip,
     * or -1 if the passenger is not on the list.
     */
    public int getPosition(Long tripId, Long passengerId) {
        WaitingListQueue queue = loadQueue(tripId);
        return queue.positionOf(passengerId);
    }

    /**
     * Adds a passenger to the waiting list for a trip.
     *
     * @return true if added, false if already on the list
     */
    public boolean joinWaitlist(Long tripId, Long passengerId, String passengerName) {
        if (getPosition(tripId, passengerId) != -1) {
            return false;
        }

        try {
            supabaseService.createWaitingListEntry(tripId, passengerId);

            WaitingList entry = new WaitingList();
            entry.setTripId(tripId);
            entry.setPassengerId(passengerId);
            entry.setPassengerName(passengerName);
            entry.setStatus(WaitingList.STATUS_WAITING);

            queues.computeIfAbsent(tripId, k -> new WaitingListQueue()).enqueue(entry);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes a passenger from the waiting list for a trip.
     *
     * @return true if removed, false if not found
     */
    public boolean cancelWaitlist(Long tripId, Long passengerId) {
        WaitingListQueue queue = queues.get(tripId);
        if (queue == null) {
            return false;
        }

        WaitingList toRemove = null;
        for (WaitingList entry : queue.toList()) {
            if (entry.getPassengerId() != null
                    && entry.getPassengerId().equals(passengerId)
                    && WaitingList.STATUS_WAITING.equals(entry.getStatus())) {
                toRemove = entry;
                break;
            }
        }

        if (toRemove == null) {
            return false;
        }

        boolean removed = queue.removePassenger(passengerId);
        if (removed && toRemove.getId() != null) {
            supabaseService.updateWaitingListStatus(toRemove.getId(), WaitingList.STATUS_CANCELLED);
        }
        return removed;
    }

    /**
     * Promotes the next passenger in line.
     *
     * <p>Removes the head of the FIFO queue, marks the entry as CONFIRMED in
     * Supabase, and returns the promoted {@link WaitingList} entry. The caller
     * is responsible for creating an actual reservation via
     * {@link ph.edu.dlsu.anilink.service.ReservationService#createReservation}.
     * </p>
     *
     * @return the promoted entry, or null if the queue is empty
     */
    public WaitingList promoteNext(Long tripId) {
        WaitingListQueue queue = queues.get(tripId);
        if (queue == null) {
            return null;
        }

        WaitingList next = queue.dequeue();
        if (next == null) {
            return null;
        }

        next.setStatus(WaitingList.STATUS_CONFIRMED);
        if (next.getId() != null) {
            supabaseService.updateWaitingListStatus(next.getId(), WaitingList.STATUS_CONFIRMED);
        }
        return next;
    }

    /**
     * Returns the full waiting list for a trip, ordered by position (FIFO).
     */
    public List<WaitingList> getWaitingList(Long tripId) {
        return new ArrayList<>(loadQueue(tripId).toList());
    }

    /**
     * Loads (or reloads) the queue for a trip from Supabase.
     * Entries are ordered by joined_at ascending (oldest first).
     */
    private WaitingListQueue loadQueue(Long tripId) {
        return queues.computeIfAbsent(tripId, k -> {
            WaitingListQueue queue = new WaitingListQueue();
            try {
                String json = supabaseService.getWaitingListByTrip(tripId);
                if (json == null || json.isBlank()) {
                    return queue;
                }

                JsonNode root = objectMapper.readTree(json);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        WaitingList entry = new WaitingList();
                        entry.setId(node.path("id").asLong());
                        entry.setTripId(node.path("trip_id").asLong());
                        entry.setPassengerId(node.path("passenger_id").asLong());

                        JsonNode passenger = node.path("passenger");
                        if (!passenger.isMissingNode() && !passenger.isNull()) {
                            entry.setPassengerName(passenger.path("name").asText("Unknown"));
                        }

                        entry.setStatus(node.path("status").asText(WaitingList.STATUS_WAITING));
                        queue.enqueue(entry);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return queue;
        });
    }
}

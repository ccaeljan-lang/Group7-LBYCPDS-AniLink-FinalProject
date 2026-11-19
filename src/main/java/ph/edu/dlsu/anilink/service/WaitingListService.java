package ph.edu.dlsu.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ph.edu.dlsu.anilink.model.WaitingList;
import ph.edu.dlsu.anilink.dsa.WaitingListQueue;

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
     *
     * <p>Always reloads from Supabase to ensure position reflects the current
     * state of the waiting list after promotions or cancellations.</p>
     */
    public int getPosition(Long tripId, Long passengerId) {
        System.out.println("DEBUG [WaitingListService] getPosition tripId=" + tripId
                + ", passengerId=" + passengerId);
        WaitingListQueue queue = loadQueue(tripId);
        int pos = queue.positionOf(passengerId);
        System.out.println("DEBUG [WaitingListService] getPosition result=" + pos
                + ", queueSize=" + queue.size());
        return pos;
    }

    /**
     * Adds a passenger to the waiting list for a trip.
     *
     * @return true if added, false if already on the list
     */
    public boolean joinWaitlist(Long tripId, Long passengerId, String passengerName) {
        System.out.println("DEBUG [WaitingListService] joinWaitlist tripId=" + tripId
                + ", passengerId=" + passengerId + ", name=" + passengerName);

        if (getPosition(tripId, passengerId) != -1) {
            System.out.println("DEBUG [WaitingListService] joinWaitlist: already on list, returning false");
            return false;
        }

        try {
            supabaseService.createWaitingListEntry(tripId, passengerId);
            System.out.println("DEBUG [WaitingListService] joinWaitlist: Supabase entry created");

            WaitingList entry = new WaitingList();
            entry.setTripId(tripId);
            entry.setPassengerId(passengerId);
            entry.setPassengerName(passengerName);
            entry.setStatus(WaitingList.STATUS_WAITING);

            queues.computeIfAbsent(tripId, k -> new WaitingListQueue()).enqueue(entry);
            System.out.println("DEBUG [WaitingListService] joinWaitlist: added to queue, size="
                    + queues.get(tripId).size());
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
        System.out.println("DEBUG [WaitingListService] cancelWaitlist tripId=" + tripId
                + ", passengerId=" + passengerId);

        WaitingListQueue queue = loadQueue(tripId);
        if (queue == null || queue.isEmpty()) {
            System.out.println("DEBUG [WaitingListService] cancelWaitlist: queue empty or null");
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
            System.out.println("DEBUG [WaitingListService] cancelWaitlist: entry not found");
            return false;
        }

        boolean removed = queue.removePassenger(passengerId);
        if (removed && toRemove.getId() != null) {
            supabaseService.updateWaitingListStatus(toRemove.getId(), WaitingList.STATUS_CANCELLED);
            System.out.println("DEBUG [WaitingListService] cancelWaitlist: removed and updated Supabase");
        }
        return removed;
    }

    /**
     * Promotes the next passenger in line.
     *
     * <p>Loads the queue from Supabase, removes the head of the FIFO queue,
     * marks the entry as CONFIRMED in Supabase, and returns the promoted
     * {@link WaitingList} entry. The caller is responsible for creating an
     * actual reservation via
     * {@link ph.edu.dlsu.anilink.service.ReservationService#createReservation}.
     * </p>
     *
     * @return the promoted entry, or null if the queue is empty
     */
    public WaitingList promoteNext(Long tripId) {
        System.out.println("DEBUG [WaitingListService] promoteNext tripId=" + tripId);

        WaitingListQueue queue = loadQueue(tripId);
        if (queue == null || queue.isEmpty()) {
            System.out.println("DEBUG [WaitingListService] promoteNext: queue empty, no one to promote");
            return null;
        }

        WaitingList next = queue.dequeue();
        if (next == null) {
            System.out.println("DEBUG [WaitingListService] promoteNext: dequeue returned null");
            return null;
        }

        System.out.println("DEBUG [WaitingListService] promoteNext: promoting passengerId="
                + next.getPassengerId() + " name=" + next.getPassengerName());

        next.setStatus(WaitingList.STATUS_CONFIRMED);
        if (next.getId() != null) {
            supabaseService.updateWaitingListStatus(next.getId(), WaitingList.STATUS_CONFIRMED);
            System.out.println("DEBUG [WaitingListService] promoteNext: Supabase status updated to CONFIRMED");
        }
        return next;
    }

    /**
     * Returns the full waiting list for a trip, ordered by position (FIFO).
     *
     * <p>Only returns entries with status {@code WAITING}.</p>
     */
    public List<WaitingList> getWaitingList(Long tripId) {
        WaitingListQueue queue = loadQueue(tripId);
        List<WaitingList> result = new ArrayList<>();
        for (WaitingList entry : queue.toList()) {
            if (WaitingList.STATUS_WAITING.equals(entry.getStatus())) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Loads (or reloads) the queue for a trip from Supabase.
     *
     * <p>Always reloads from Supabase (no in-memory caching) to ensure the
     * queue reflects the current state after promotions or cancellations.
     * Only entries with status {@code WAITING} are loaded.</p>
     */
    private WaitingListQueue loadQueue(Long tripId) {
        System.out.println("DEBUG [WaitingListService] loadQueue: loading tripId=" + tripId
                + " from Supabase");

        WaitingListQueue queue = new WaitingListQueue();
        try {
            String json = supabaseService.getWaitingListByTrip(tripId);
            if (json == null || json.isBlank()) {
                System.out.println("DEBUG [WaitingListService] loadQueue: empty response from Supabase");
                return queue;
            }

            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                System.out.println("DEBUG [WaitingListService] loadQueue: found "
                        + root.size() + " entries in Supabase");
                for (JsonNode node : root) {
                    String status = node.path("status").asText(WaitingList.STATUS_WAITING);
                    // Only load WAITING entries - CONFIRMED/CANCELLED entries are no longer waiting
                    if (!WaitingList.STATUS_WAITING.equals(status)) {
                        continue;
                    }

                    WaitingList entry = new WaitingList();
                    entry.setId(node.path("id").asLong());
                    entry.setTripId(node.path("trip_id").asLong());
                    entry.setPassengerId(node.path("passenger_id").asLong());

                    JsonNode passenger = node.path("passenger");
                    if (!passenger.isMissingNode() && !passenger.isNull()) {
                        entry.setPassengerName(passenger.path("name").asText("Unknown"));
                    }

                    entry.setStatus(status);
                    queue.enqueue(entry);
                    System.out.println("DEBUG [WaitingListService] loadQueue: loaded entry passengerId="
                            + entry.getPassengerId() + " status=" + entry.getStatus());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return queue;
    }
}

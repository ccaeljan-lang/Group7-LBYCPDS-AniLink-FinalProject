package ph.edu.dlsu.anilink.queue;

import ph.edu.dlsu.anilink.model.WaitingList;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple first-in-first-out (FIFO) queue for waiting list entries.
 *
 * <p>This is a plain Java queue with no external library dependencies and no
 * concurrency utilities. It uses an {@link ArrayList} internally to preserve
 * insertion order (oldest entry at index 0).
 * </p>
 */
public class WaitingListQueue {

    private final List<WaitingList> entries = new ArrayList<>();

    /**
     * Adds an entry to the back of the queue.
     */
    public void enqueue(WaitingList entry) {
        entries.add(entry);
    }

    /**
     * Removes and returns the entry at the front of the queue.
     *
     * @return the front entry, or null if the queue is empty
     */
    public WaitingList dequeue() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.remove(0);
    }

    /**
     * Returns the entry at the front of the queue without removing it.
     *
     * @return the front entry, or null if the queue is empty
     */
    public WaitingList peek() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(0);
    }

    /**
     * Returns the number of entries in the queue.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns true if the queue contains no entries.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the 1-based position of a passenger in the queue, or -1 if not found.
     */
    public int positionOf(Long passengerId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getPassengerId() != null
                    && entries.get(i).getPassengerId().equals(passengerId)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Removes the entry for a specific passenger from the queue.
     *
     * @return true if an entry was removed, false if the passenger was not found
     */
    public boolean removePassenger(Long passengerId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getPassengerId() != null
                    && entries.get(i).getPassengerId().equals(passengerId)) {
                entries.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a copy of all entries in queue order (front to back).
     */
    public List<WaitingList> toList() {
        return new ArrayList<>(entries);
    }

    /**
     * Removes all entries from the queue.
     */
    public void clear() {
        entries.clear();
    }
}

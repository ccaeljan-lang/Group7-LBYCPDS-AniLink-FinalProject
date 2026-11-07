package ph.edu.dlsu.anilink.model;

/**
 * Thrown when a passenger attempts to book a trip that has reached full capacity.
 * The UI layer can catch this exception and offer the passenger the option to
 * join the waiting list instead.
 */
public class TripFullException extends IllegalStateException {

    public TripFullException(String message) {
        super(message);
    }
}

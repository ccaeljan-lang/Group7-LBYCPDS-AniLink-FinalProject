package ph.edu.dlsu.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ph.edu.dlsu.anilink.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Core service interacting directly with the Supabase PostgREST API.
 *
 * <p>Key components:
 * <ul>
 *   <li><b>REST Integration:</b> Uses Spring's {@link RestClient} to execute HTTP operations against Supabase endpoints.</li>
 *   <li><b>User Deserialization:</b> Maps raw JSON records into concrete {@link User} domain instances based on role types.</li>
 *   <li><b>Domain Operations:</b> Provides CRUD operations across users, routes, schedules, trips, and reservations.</li>
 * </ul>
 * </p>
 */
@Service
public class SupabaseService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupabaseService(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.secret-key}") String secretKey) {
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("apikey", secretKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
    }

    public User findUserByEmail(String email) {
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users")
                            .queryParam("email", "eq." + email.trim())
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .body(String.class);

            System.out.println("Supabase query response: " + response);

            JsonNode users = objectMapper.readTree(response);

            if (!users.isArray() || users.isEmpty()) {
                return null;
            }

            JsonNode user = users.get(0);

            // Safely parse ID whether Supabase returns a Number or a UUID String
            Long userId = 1L;
            if (user.has("id") && !user.get("id").isNull()) {
                if (user.get("id").isNumber()) {
                    userId = user.get("id").asLong();
                } else {
                    // If ID is a UUID string, hash it to a positive Long for Model compatibility
                    userId = Math.abs((long) user.get("id").asText().hashCode());
                }
            }

            String name = user.has("name") ? user.get("name").asText() : "";
            String userEmail = user.has("email") ? user.get("email").asText() : "";
            String password = user.has("password") ? user.get("password").asText() : "";
            String role = user.has("role") ? user.get("role").asText() : "PASSENGER";

            switch (role.toUpperCase()) {
                case "PASSENGER":
                    return new Passenger(
                            userId,
                            name,
                            userEmail,
                            password,
                            getOptionalText(user, "category")
                    );
                case "DRIVER":
                    return new Driver(
                            userId,
                            name,
                            userEmail,
                            password,
                            getOptionalText(user, "license_number")
                    );
                case "ADMINISTRATOR":
                    return new Administrator(
                            userId,
                            name,
                            userEmail,
                            password,
                            getOptionalText(user, "admin_level")
                    );
                default:
                    throw new IllegalArgumentException("Unknown user role: " + role);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error fetching user: " + e.getMessage(), e);
        }
    }

    public String getUsers() {
        return restClient.get()
                .uri("/users?select=*")
                .retrieve()
                .body(String.class);
    }

    public String getUsersByRole(String role) {
        return restClient.get()
                .uri("/users?role=eq." + role + "&select=*")
                .retrieve()
                .body(String.class);
    }

    private String getOptionalText(
            JsonNode node,
            String fieldName) {
        JsonNode field = node.get(fieldName);

        if (field == null || field.isNull()) {
            return "";
        }

        return field.asText();
    }

    public void updateRoute(Route route) {
        restClient.patch()
                .uri("/routes?id=eq." + route.getRouteId())
                .body(
                        java.util.Map.of(
                                "origin", route.getOrigin(),
                                "destination", route.getDestination()
                        )
                )
                .retrieve()
                .toBodilessEntity();
    }

    public String createReservation(Reservation reservation) {
        return restClient.post()
                .uri("/reservations")
                .body(
                        java.util.Map.of(
                                "id", reservation.getReservationId(),
                                "passenger_id", reservation.getPassenger().getUserId(),
                                "trip_id", reservation.getTrip().getTripId(),
                                "status", reservation.getStatus(),
                                "qr_payload", reservation.getQrPayload()
                        )
                )
                .retrieve()
                .body(String.class);
    }

    public String getReservations() {
        return restClient.get()
                .uri("/reservations?select=*")
                .retrieve()
                .body(String.class);
    }

    public String getReservationsByPassenger(Long passengerId) {
        return restClient.get()
                .uri("/reservations?passenger_id=eq." + passengerId + "&select=*,trip:trips(*,route:routes(*),schedule:departure_schedules(*))")
                .retrieve()
                .body(String.class);
    }

    public void cancelReservation(Long reservationId) {
        restClient.patch()
                .uri("/reservations?id=eq." + reservationId)
                .body(
                        java.util.Map.of(
                                "status", "CANCELLED"
                        )
                )
                .retrieve()
                .toBodilessEntity();
    }

    public String createSchedule(DepartureSchedule schedule) {
        return restClient.post()
                .uri("/departure_schedules")
                .body(
                        java.util.Map.of(
                                "id", schedule.getScheduleId(),
                                "route_id", schedule.getRoute().getRouteId(),
                                "departure_time", schedule.getDepartureTime().toString(),
                                "capacity", schedule.getCapacity()
                        )
                )
                .retrieve()
                .body(String.class);
    }

    public String getRoutes() {
        return restClient.get()
                .uri("/routes?select=*")
                .retrieve()
                .body(String.class);
    }

    // REPLACES THE OLD registerPassenger METHOD
    public String registerAccount(String name, String email, String password, String role, String extraDetail) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("name", name);
        payload.put("email", email);
        payload.put("password", password);
        payload.put("role", role);

        // Map the extra detail to the correct column so findUserByEmail doesn't crash later
        if ("PASSENGER".equalsIgnoreCase(role)) {
            payload.put("category", "PASSENGER"); // Students and faculty have the same category
        } else if ("DRIVER".equalsIgnoreCase(role)) {
            payload.put("license_number", "PENDING-" + System.currentTimeMillis()); // Default placeholder license
        } else if ("ADMINISTRATOR".equalsIgnoreCase(role)) {
            payload.put("admin_level", "STANDARD"); // Default admin level
        }

        return restClient.post()
                .uri("/users")
                .header("Prefer", "return=representation")
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String getTrips() {
        return restClient.get()
                .uri("/trips?select=*")
                .retrieve()
                .body(String.class);
    }

    public void updateTripStatus(Long tripId, String status) {
        restClient.patch()
                .uri("/trips?id=eq." + tripId)
                .body(java.util.Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }

    public String getTripsByDriver(Long driverId) throws Exception {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trips")
                        .queryParam("driver_id", "eq." + driverId)
                        .queryParam("limit", "1")
                        .queryParam("select", "*,route:routes(*),schedule:departure_schedules(*)")
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String getTripDetails(Long tripId) throws Exception {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trips")
                        .queryParam("id", "eq." + tripId)
                        .queryParam("select", "*,route:routes(*),schedule:departure_schedules(*)")
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String getReservationByQrPayload(String qrPayload) {
        return restClient.get()
                .uri("/reservations?qr_payload=eq." + qrPayload + "&select=*,passenger:users(*)")
                .retrieve()
                .body(String.class);
    }

    public void updateReservationStatus(Long reservationId, String status) {
        restClient.patch()
                .uri("/reservations?id=eq." + reservationId)
                .body(java.util.Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }




    public String getSchedulesByRoute(Long routeId) {
        return restClient.get()
                .uri("/departure_schedules?route_id=eq." + routeId + "&select=*,routes(*)")
                .retrieve()
                .body(String.class);
    }




    public String getTripBySchedule(Long scheduleId) {
        return restClient.get()
                .uri("/trips?schedule_id=eq." + scheduleId + "&status=neq.COMPLETED&select=*")
                .retrieve()
                .body(String.class);
    }

    // 1. Updated createTrip to accept driverId and status dynamically
    public String createTrip(Long routeId, Long scheduleId, Long driverId, int capacity, String status) {
        java.util.Map<String, Object> bodyMap = new java.util.HashMap<>();
        bodyMap.put("route_id", routeId);
        bodyMap.put("schedule_id", scheduleId);
        bodyMap.put("capacity", capacity);
        bodyMap.put("seats_taken", 0);
        bodyMap.put("status", (status != null && !status.isBlank()) ? status : "SCHEDULED");

        if (driverId != null) {
            bodyMap.put("driver_id", driverId);
        }

        return restClient.post()
                .uri("/trips")
                .header("Prefer", "return=representation")
                .body(bodyMap)
                .retrieve()
                .body(String.class);
    }

    // 2. Assign or re-assign a driver to an existing trip
    public String assignDriverToTrip(Long tripId, Long driverId) {
        return restClient.patch()
                .uri("/trips?trip_id=eq." + tripId)
                .header("Prefer", "return=representation")
                .body(java.util.Map.of("driver_id", driverId))
                .retrieve()
                .body(String.class);
    }

    // 3. Fetch users with role 'DRIVER' to populate the driver dropdowns
    public String getDrivers() {
        return restClient.get()
                .uri("/users?role=eq.DRIVER")
                .retrieve()
                .body(String.class);
    }

    public void updateTripSeatsTaken(Long tripId, int newSeatsCount) {
        restClient.patch()
                .uri("/trips?id=eq." + tripId)
                .body(java.util.Map.of("seats_taken", newSeatsCount))
                .retrieve()
                .toBodilessEntity();
    }

    public String getSchedules() {
        return restClient.get()
                .uri("/departure_schedules?select=*,route:routes(*)")
                .retrieve()
                .body(String.class);
    }

    public String createSchedule(Long routeId, String departureTime, int capacity) {
        return restClient.post()
                .uri("/departure_schedules")
                .header("Prefer", "return=representation")
                .body(java.util.Map.of(
                        "route_id", routeId,
                        "departure_time", departureTime,
                        "capacity", capacity
                ))
                .retrieve()
                .body(String.class);
    }

    public void deleteSchedule(Long scheduleId) {
        restClient.delete()
                .uri("/departure_schedules?id=eq." + scheduleId)
                .retrieve()
                .toBodilessEntity();
    }

    public String getTripsWithDetails() {
        return restClient.get()
                .uri("/trips?select=*,route:routes(*),schedule:departure_schedules(*)")
                .retrieve()
                .body(String.class);
    }


    public String createTrip(Long routeId, Long scheduleId, int capacity, String status) {
        return restClient.post()
                .uri("/trips")
                .header("Prefer", "return=representation")
                .body(java.util.Map.of(
                        "route_id", routeId,
                        "schedule_id", scheduleId,
                        "capacity", capacity,
                        "seats_taken", 0,
                        "status", status != null ? status : "SCHEDULED"
                ))
                .retrieve()
                .body(String.class);
    }


    public void deleteTrip(Long tripId) {
        restClient.delete()
                .uri("/trips?id=eq." + tripId)
                .retrieve()
                .toBodilessEntity();
    }


    public String getActiveReservationsByPassenger(Long passengerId) {
        return restClient.get()
                .uri("/reservations?passenger_id=eq." + passengerId + "&status=neq.CANCELLED&status=neq.COMPLETED")
                .retrieve()
                .body(String.class);
    }


    public String getActiveReservationsByPassengerAndRoute(Long passengerId, Long routeId) {
        return restClient.get()
                .uri("/reservations?passenger_id=eq." + passengerId +
                        "&status=neq.CANCELLED&status=neq.COMPLETED" +
                        "&trip.route_id=eq." + routeId +
                        "&select=*,trip:trips!inner(*)")
                .retrieve()
                .body(String.class);
    }


    public String getAllReservations() {
        return restClient.get()
                .uri("/reservations?select=*,passenger:users(*),trip:trips(*)")
                .retrieve()
                .body(String.class);
    }

    public String getReservationById(Long id) {
        return restClient.get()
                .uri("/reservations?id=eq." + id + "&select=*,passenger:users(*),trip:trips(*)")
                .retrieve()
                .body(String.class);
    }

    public String getRouteById(Long id) {
        return restClient.get()
                .uri("/routes?id=eq." + id)
                .retrieve()
                .body(String.class);
    }

    public String createRoute(String origin, String destination) {
        return restClient.post()
                .uri("/routes")
                .header("Prefer", "return=representation")
                .body(java.util.Map.of(
                        "origin", origin,
                        "destination", destination
                ))
                .retrieve()
                .body(String.class);
    }

    public void deleteRoute(Long routeId) {
        restClient.delete()
                .uri("/routes?id=eq." + routeId)
                .retrieve()
                .toBodilessEntity();
    }

    public void updateTripLocation(Long tripId, double lat, double lng) {
        restClient.patch()
                .uri("/trips?id=eq." + tripId)
                .body(java.util.Map.of("current_lat", lat, "current_lng", lng))
                .retrieve()
                .toBodilessEntity();
    }

    public String getAllUsers() {
        return restClient.get()
                .uri("/users")
                .retrieve()
                .body(String.class);
    }

    public String getUserById(Long id) {
        return restClient.get()
                .uri("/users?id=eq." + id)
                .retrieve()
                .body(String.class);
    }

    public String getUserByEmail(String email) {
        return restClient.get()
                .uri("/users?email=eq." + email)
                .retrieve()
                .body(String.class);
    }

    public String createUser(java.util.Map<String, Object> userData) {
        return restClient.post()
                .uri("/users")
                .header("Prefer", "return=representation")
                .body(userData)
                .retrieve()
                .body(String.class);
    }

    public void deleteUser(Long id) {
        restClient.delete()
                .uri("/users?id=eq." + id)
                .retrieve()
                .toBodilessEntity();
    }

    public String getTripsByRouteAndDate(Long routeId, String dateStr) {
        String startOfDay = dateStr + "T00:00:00";
        String endOfDay = dateStr + "T23:59:59";

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trips")
                        .queryParam("route_id", "eq." + routeId)
                        .queryParam("schedule.departure_time", "gte." + startOfDay)
                        .queryParam("schedule.departure_time", "lt." + endOfDay)
                        .queryParam("select", "*,schedule:departure_schedules(*)")
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String postReservation(Long passengerId, Long tripId, String qrPayload) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("passenger_id", passengerId);
        body.put("trip_id", tripId);
        body.put("qr_payload", qrPayload);
        body.put("status", "WAITLISTED"); // Adjust status string as needed

        return restClient.post()
                .uri("/reservations")
                .header("Prefer", "return=representation") // Tells PostgREST to return the inserted record
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class); // Returns the JSON array string containing the created record
    }

    public String getTripsByRoute(Long routeId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trips")
                        .queryParam("route_id", "eq." + routeId)
                        .queryParam("select", "*,schedule:departure_schedules(*)")
                        .build())
                .retrieve()
                .body(String.class);
    }
    public String getReservationsByTripId(Long tripId) {
        return restClient.get()
                .uri("/reservations?trip_id=eq." + tripId + "&select=*,passenger:users(*)")
                .retrieve()
                .body(String.class);
    }
}
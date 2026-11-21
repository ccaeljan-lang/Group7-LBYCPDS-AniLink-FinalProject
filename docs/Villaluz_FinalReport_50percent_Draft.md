# Villaluz Final Report — 50% Draft (Assignment C)

**Contributor:** Froilene Nicole A. Villaluz  
**Project:** AniLink: DLSU Shuttle Reservation System (Group 7, LBYCPDS)  
**Status:** DRAFT — Villaluz-relevant sections only.  
**Date:** November 21, 2026 (Philippine time)  
**Code baseline:** Git branch `master`, commit `454b201` (HEAD).  
**IMPORTANT:** This is only Villaluz's individual draft for the first 50% of the final report. It does NOT represent the complete group report, and it does not claim the group report is 50% complete. Teammates' sections (Cristobal, Sze) are not overwritten or represented here.

---

## 1. Project Overview

AniLink is a DLSU shuttle reservation system built with **Java 21**, **JavaFX 21**, **Spring Boot 3.5.5**, and **Supabase (PostgREST)** as the data layer. Passengers reserve shuttle seats, drivers verify passengers with QR codes, and administrators manage routes, schedules, trips, and drivers. The system is organized as Category → Line → Route → Departure Schedule → Trip → Reservation, with a first-in-first-out (FIFO) waiting list that promotes passengers when a confirmed seat is cancelled.

This draft covers the work assigned to Villaluz: (A) waitlist promotion through to a usable reservation, (B) trip numbers with a real Insertion Sort and Supabase trip synchronization, and (C) this 50% report contribution.

## 2. Problem and Objectives

**Problems observed in the codebase (commit `454b201` baseline):**

1. **Waitlist promotion did not produce a usable reservation.** When a passenger cancelled, `ReservationService.cancelReservation()` promoted the next waiting passenger by marking the waiting-list entry `CONFIRMED` *before* creating the reservation, and it built a half-populated `Trip` (no route/schedule) that `RouteLimitRule` rejected. Promotion exceptions were silently swallowed. Result: a waitlisted passenger could show as `CONFIRMED` without an actual reservation, QR, or seat.
2. **Persistence semantics were wrong.** `SupabaseService.postReservation()` inserted an actual seat reservation with `status="WAITLISTED"`, mixing waitlist queue status with booked-seat status.
3. **API/UI divergence.** `backend/ReservationController` (`PUT /api/reservations/{id}/cancel`) duplicated the JavaFX cancellation flow with its own stale `Trip`, so the two paths could behave differently.
4. **Misleading queue position `-1`.** `WaitingListController.loadWaitlistsAsync()` called `getPosition()` for every status, including `CONFIRMED`, so promoted rows showed `-1`.
5. **False download success.** `BookingSummaryService.saveToFile()` returned void and `MyReservationsController.handleDownloadSummary()` always announced success, even when the file chooser was cancelled or the PNG write failed.
6. **Driver assignment silently updated zero rows.** `SupabaseService.assignDriverToTrip()` filtered on `trip_id=eq.` while every other trip query and the `Trip` model use the primary key `id`.
7. **No user-facing trip number.** `Trip.toString()` labeled the immutable Supabase `id` as `TRIP #...`, which is not a reusable sequence number; completed trips could inflate visible numbers; `MyReservations` showed booking `created_at` instead of the travel date and was unsorted.

**Objectives of this contribution:**
- Make cancellation promote the next FIFO waitlist passenger into exactly one persisted, `CONFIRMED` reservation with a unique QR payload, and mark the waitlist entry `CONFIRMED` only after the reservation exists.
- Share one cancellation/promotion workflow between JavaFX and the REST API.
- Show a numeric trip number and the travel date in My Reservations, sorted ascending by a hand-written Insertion Sort.
- Retire completed/cancelled trips from active listings without deleting history.

## 3. System Architecture

- **Client/UI:** JavaFX 21 with FXML views (`src/main/resources/fxml`) and Spring-managed controllers (`src/main/java/ph/edu/dlsu/anilink/controller`). `UserSession` holds the logged-in user; `ViewNavigator` switches views.
- **Service layer:** Spring `@Service` classes (`ReservationService`, `WaitingListService`, `SupabaseService`, `BookingSummaryService`, `BookingValidationService`, `QRService`) coordinate business logic and Supabase calls.
- **Validation (Strategy pattern):** `BookingRule` interface with `RouteLimitRule`, `CategoryLimitRule`, `CapacityBookingRule`, `PassengerRoleBookingRule`, run by `BookingValidationService`.
- **DSA:** `dsa/WaitingListQueue` (FIFO), `dsa/ScheduleBubbleSort`, `dsa/ScheduleBinarySearch`, and the new `dsa/TripInsertionSort`.
- **API:** `backend/ReservationController` REST endpoints under `/api/reservations`.
- **Data:** Supabase PostgREST tables `users`, `routes`, `departure_schedules`, `trips`, `reservations`, `waiting_lists`. Accessed via `SupabaseService` using Spring `RestClient` with `apikey`/`Authorization` headers. No credentials are stored in the repository.

## 4. Data Flow (Villaluz-relevant)

**Booking:** Passenger selects route/date/schedule in `ReservationController` (JavaFX) → builds a `Trip` with route attached → `ReservationService.createReservation(user, trip)` → `SupabaseService.postReservation()` inserts status `CONFIRMED` with UUID QR payload → seat counted only after the row exists → reservation appears in My Reservations.

**Waitlist when full:** `ReservationController.showJoinWaitlistDialog()` → `ReservationService.joinWaitingList()` → `SupabaseService.createWaitingListEntry()` (status `WAITING`) → `WaitingListQueue` FIFO ordered by `joined_at`.

**Cancellation + promotion (the core fix):**
1. `MyReservationsController.handleCancelReservation()` or `backend/ReservationController.cancelReservation()` → `ReservationService.cancelReservationAndPromote(reservationId)`.
2. Reject if `null`, already `CANCELLED`, or `COMPLETED` (no double decrement/promotion).
3. Mark reservation `CANCELLED` in Supabase.
4. Decrement `seats_taken` using **fresh** trip details from `SupabaseService.getTripDetails(tripId)`.
5. If the trip is still active (`SCHEDULED`/`ARRIVING`/`BOARDING`/`IN_TRANSIT`/`DEPARTED`), call `promoteAndReserve(tripId)`:
   - `WaitingListService.promoteNext(tripId)` returns the FIFO head but leaves it `WAITING` in Supabase.
   - Fetch passenger via `getUserById`, require role `PASSENGER`.
   - Fetch fresh complete `Trip` (route + schedule) — never a half-populated trip.
   - Validate via `canBook()` (role, route quota, duplicate, seat, capacity).
   - `createReservation(passenger, bookingTrip)` inserts a `CONFIRMED` reservation with UUID QR payload and counts the seat.
   - **Only then** `WaitingListService.confirmPromotion(waitingListId)` conditionally flips `WAITING → CONFIRMED` (only if still `WAITING`, guarding against races).
6. On any failure, the entry stays `WAITING` and the cancellation is not rolled back, so a retry can promote again.

**My Reservations:** `MyReservationsController.loadReservationsAsync()` → `SupabaseService.getReservationsByPassenger()` (nested trip/route/schedule) → travel date from `schedule.departure_date` (fallback to booking `created_at`) → trip number from the active-trip ordinal → `TripInsertionSort.sortByTripNumber()` → displayed sorted ascending. Download uses `BookingSummaryService.generateSummaryImage()` + `saveToFile()` (returns `boolean`; success announced only if PNG written). Cancel uses the shared `cancelReservationAndPromote()`.

## 5. DSA Descriptions and Pseudocode

Existing DSA is preserved: `dsa/ScheduleBubbleSort` (departure schedules by date/time) and `dsa/ScheduleBinarySearch` (sorted time lookup). New: `dsa/TripInsertionSort`.

### Insertion Sort (new, `dsa/TripInsertionSort.java`, commit `c0b00c5`)

Sorts reservation items by user-facing trip number ascending, stable, numeric (so #2 precedes #10).

```
procedure sortByTripNumber(list):
    for i from 1 to list.size - 1:
        current = list[i]
        j = i - 1
        while j >= 0 and tripNumber(list[j]) > tripNumber(current):
            list[j + 1] = list[j]        // shift right
            j = j - 1
        list[j + 1] = current            // insert
```

Secondary tie-break for equal trip numbers: travel date/time, then reservation ID. Unknown trip numbers (-1) sort last. Called in `MyReservationsController.loadReservationsAsync()` before `reservationList.setAll()`. This is a hand-written Insertion Sort — not `Collections.sort`, `List.sort`, streams, or SQL `ORDER BY`.

### FIFO Waiting List (`dsa/WaitingListQueue`)
Plain Java `ArrayList`-backed queue; `enqueue`/`dequeue` preserve insertion order; `positionOf` returns 1-based position; ordered by `joined_at` (Supabase `order=joined_at.asc`) with ID as stable tie-break implicitly via reload.

### Bubble Sort (existing, preserved)
`dsa/ScheduleBubbleSort.sort(source, comparator)` — classic bubble sort with early termination; O(n²) average/worst, O(n) best.

### Binary Search (existing, preserved)
`dsa/ScheduleBinarySearch.search(sortedTimes, targetTime)` — standard binary search on sorted time strings; returns index or -1.

## 6. Validation and Exception Handling

- `BookingValidationService` chains `PassengerRoleBookingRule`, `CapacityBookingRule`, `RouteLimitRule`, `CategoryLimitRule`; short-circuits on first failure.
- `ReservationService.cancelReservationAndPromote()` rejects `null` IDs, `CANCELLED` ("already cancelled") and `COMPLETED` ("completed and cannot be cancelled") with `IllegalStateException`; never decrements seats or promotes twice.
- Promotion into a `CANCELLED`/`COMPLETED` trip is skipped (`isActiveTrip`).
- `promoteAndReserve` validates the promoter is a real `PASSENGER` and that the fresh `Trip` passes `canBook()`; ineligible passengers stay `WAITING` (non-destructive, never silently dropped).
- `createReservation` inserts the reservation row *before* counting the seat, so a seat is never counted without a reservation; if the seat update fails the reservation remains valid and `SupabaseService.syncSeatsTaken()` can reconcile.
- All Supabase calls are wrapped in try/catch with stack traces printed and descriptive `RuntimeException`s propagated to the UI as alert dialogs.
- No credentials are committed; Supabase URL/key come from configuration (`app.supabase.url`, `app.supabase.secret-key`), not present in the repository.

## 7. Real Debugging Root Causes and Fixes

| # | Root cause | Fix | Commit |
|---|------------|-----|--------|
| 1 | `postReservation()` wrote `status="WAITLISTED"` for an actual seat reservation | Write `CONFIRMED`; `WAITLISTED` reserved for waiting_lists queue entries | `488082c` |
| 2 | `assignDriverToTrip()` filtered `trip_id=eq.` (not a trips column) → zero rows updated | Filter on primary key `id=eq.` | `488082c` |
| 3 | `cancelReservation()` promoted by marking waitlist `CONFIRMED` before the reservation existed, and passed a half-populated `Trip` (no route/schedule) that `RouteLimitRule` rejected; exceptions swallowed | Central `cancelReservationAndPromote()` + `promoteAndReserve()`: fresh complete trip, validate, create `CONFIRMED` reservation, then conditionally confirm waitlist; cancellation not rolled back on promotion failure | `3d1177b` |
| 4 | REST cancel endpoint duplicated the UI flow with a stale `Trip` | `backend/ReservationController.cancelReservation()` delegates to `ReservationService.cancelReservationAndPromote()` | `a11bc6f` |
| 5 | `WaitingListController` showed queue position `-1` for promoted/CONFIRMED rows | `queuePos` is now a display string: number for `WAITING`, "Promoted" for confirmed, "—" for cancelled | `a5c9e74` |
| 6 | Download always announced success even on cancelled dialog or write failure | `BookingSummaryService.saveToFile()` returns `boolean`; UI announces success only when PNG written | `767cb98` |
| 7 | No user-facing trip number; My Reservations showed booking date, unsorted | New `tripNumber` column, travel date from `schedule.departure_date`, `TripInsertionSort` sort, completed trips excluded from numbering | `c0b00c5`, `12d1d75`, `ceb6ed5`, `454b201` |

## 8. Test Matrix

Tests were written but **could not be executed** in this environment because Maven is not installed and no dependency JARs are available (see §11). They are committed as source and should be run with `mvn -q test` once Maven and Supabase credentials are configured.

| Test class | Cases | Status |
|---|---|---|
| `dsa/TripInsertionSortTest` | [10,2,7,1]→[1,2,7,10]; ascending; descending; empty; singleton; equal keys stable; unknown (-1) last; same-trip date/ID tie-break | NOT RUN (see §11) |
| `service/ReservationServiceTest` | reject already-cancelled; reject completed; happy cancel→promote→confirm waitlist; no waiting passenger; promote into completed trip skipped; `cancelReservation()` delegates | NOT RUN |
| `service/WaitingListServiceTest` | `promoteNext` returns head without confirming Supabase (stays WAITING); null for empty queue; `confirmPromotion` flips WAITING→CONFIRMED | NOT RUN |
| Existing `AppTest` | placeholder `assertTrue(true)` | NOT RUN |

**GUI/manual verification scenario (not yet executed):** Register/login as passenger A → book the last seat on a trip → passenger B joins the waitlist (WAITING, #1) → A cancels → B gets exactly one CONFIRMED reservation for the same trip with a QR payload → seat count correct → B's booking appears in My Reservations after reload → B downloads the PNG summary with QR → B cancels → next passenger C promotes. Insertion Sort demonstration: open My Reservations and confirm trip numbers increase by numeric value (#2 before #10) after reload.

## 9. Limitations

- **No multi-call atomicity.** Cancellation + seat decrement + reservation insert + waitlist confirmation are separate Supabase REST calls; a true atomic transaction requires a server-side PostgreSQL function/RPC with row locks, which could not be deployed because schema access and production approval are unavailable. A safe fallback is implemented: the reservation is created before the waitlist is confirmed, the waitlist update is conditional (`WAITING` only), and failures leave the entry `WAITING` for retry. Concurrency double-booking is mitigated but not eliminated without the RPC.
- **Not tested against live Supabase.** No Supabase URL/key is available in this environment, so integration behavior (nested JSON deserialization, PostgREST `order`/`in.` filters) could not be verified end to end.
- **Trip number is a display ordinal.** It is computed from active trips ordered by travel date/time then `id`, is not persisted, and may shift when trips are added/removed. Historical reservations and FKs always use immutable `trips.id`; no primary/foreign keys were rewritten and no trips were deleted.
- **Admin/driver pages were not changed** to display the trip number (Sze's/admin areas are owned by teammates); only My Reservations shows it, using a documented shared mapping.
- **Tests are unexecuted** due to missing Maven/dependency JARs (see §11).

## 10. Migration / External Actions Still Required

No schema change is required for the code fixes (they use existing columns: `reservations.status`, `waiting_lists.status`, `trips.seats_taken`/`status`).

An **optional, NOT APPLIED** migration is provided at `docs/migrations/001_waitlist_promotion_safe_fallback.sql`. It documents (a) an optional PostgreSQL function `promote_waitlist_passenger()` for atomic cancellation/promotion with row locks, and (b) a unique index to prevent duplicate active reservations per user/trip. It is explicitly labeled **NOT APPLIED** and must be reviewed and approved before any production application.

Still required externally:
1. Install Maven and run `mvn -q test` then `mvn -q package -DskipTests` (see §11).
2. Configure Supabase credentials (`app.supabase.url`, `app.supabase.secret-key`) without committing them.
3. Review and optionally apply `docs/migrations/001_waitlist_promotion_safe_fallback.sql` only with DBA approval.
4. Execute the GUI/manual verification scenario and capture screenshots for the final report (placeholders below).

## 11. Environment and Build Notes

- Maven (`mvn`) is **not installed** in this environment; the project's dependency JARs (Spring Boot, JavaFX, Jackson, ZXing) are not present locally. Therefore `mvn -q test` and `mvn -q package -DskipTests` **could not be run**, and no tests were executed. Do not interpret committed tests as passed.
- JDK 25 (`javac 25.0.3`) is present, but the project targets Java 21; compiling individual files with `javac` is not sufficient without the dependency classpath.
- Once Maven is available: `mvn -q test` (runs JUnit 5 tests; note `src/test/.../AppTest.java` is a pre-existing JUnit 4 placeholder) and `mvn -q package -DskipTests`.
- Supabase credentials must be supplied via configuration/environment and never committed.

## 12. Evidence / Screenshot Placeholders

Do not invent screenshots. Capture these after the environment is set up:

- [ ] Waitlist promotion: A's confirmed reservation, B at #1 WAITING, A cancels, B's CONFIRMED reservation with QR in My Reservations.
- [ ] Seat count before/after cancellation and promotion (correct; no overbooking).
- [ ] Waiting List view: promoted row shows "Promoted" / "—", not `-1`.
- [ ] My Reservations: Trip # column numeric (#2 before #10), travel date from schedule, Download produces PNG+QR, Cancel works, cancelled/completed rows hide buttons.
- [ ] Insertion Sort demonstration: reservation rows re-sort numerically by trip number on reload.
- [ ] Driver assignment updates the correct trip row (`id=eq.`).

## 13. Remaining Final-Report Sections (not yet drafted)

- Group member roles and contribution breakdown (Villaluz portion only).
- Full system architecture diagram.
- Complete test results (PASS/FAIL) after `mvn -q test`.
- Full test evidence and screenshots.
- Integration with teammates' work (admin refactors, repeat booking, vehicle selection, driver camera, trip-status controls, seat visualization).
- Conclusion and references.

---

**Commit log (Villaluz, chronological):**

1. `488082c` fix(persistence): persist seat reservations as confirmed, fix driver assignment column
2. `3d1177b` fix(waitlist): create confirmed booking before promoting waitlist entry
3. `a11bc6f` fix(reservations): share cancellation flow across api and ui
4. `a5c9e74` fix(waitlist-ui): show promoted status instead of queue minus one
5. `767cb98` fix(reservations): announce download only when png is saved
6. `c0b00c5` feat(dsa): add stable insertion sort for reservation trips
7. `12d1d75` fix(trips): show display trip number and travel date in my reservations
8. `ceb6ed5` fix(trips): sync passenger trip listing by date and exclude completed trips
9. `454b201` fix(trips): add travel date accessor for reservation sort comparator
10. `38a3f21` test(waitlist): cover fifo promotion and cancellation failures
11. `0ef10f2` test(trips): verify insertion sort numbering edge cases

**Files edited (Villaluz):**
- `src/main/java/ph/edu/dlsu/anilink/service/SupabaseService.java`
- `src/main/java/ph/edu/dlsu/anilink/service/ReservationService.java`
- `src/main/java/ph/edu/dlsu/anilink/service/WaitingListService.java`
- `src/main/java/ph/edu/dlsu/anilink/service/BookingSummaryService.java`
- `src/main/java/ph/edu/dlsu/anilink/backend/ReservationController.java`
- `src/main/java/ph/edu/dlsu/anilink/controller/ReservationController.java`
- `src/main/java/ph/edu/dlsu/anilink/controller/WaitingListController.java`
- `src/main/java/ph/edu/dlsu/anilink/controller/MyReservationsController.java`
- `src/main/resources/fxml/MyReservations.fxml`
- `src/main/java/ph/edu/dlsu/anilink/dsa/TripInsertionSort.java` (new)
- `src/test/java/ph/edu/dlsu/anilink/service/ReservationServiceTest.java` (new)
- `src/test/java/ph/edu/dlsu/anilink/service/WaitingListServiceTest.java` (new)
- `src/test/java/ph/edu/dlsu/anilink/dsa/TripInsertionSortTest.java` (new)

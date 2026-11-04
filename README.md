# PROJECT TITLE:
AniLink: DLSU Shuttle Service Reservation

## TEAM MEMBERS:<br>
Caeljan D. Cristobal - ccaeljan-lang<br>
Froilene Nicole A. Villaluz - froileneeeee<br>
Timothy Emmanuel U. Sze - TimothySze1254

## PROBLEM STATEMENT & GOALS:<br>
### Goals:
To develop a centralized shuttle management platform that improves the efficiency, accessibility, and reliability of the DLSU shuttle service.
1. To provide a digital platform for shuttle scheduling and seat reservations.
2. To enable real-time shuttle tracking and QR code-based/ticket/card passenger verification.
3. To simplify shuttle operations by improving coordination among passengers, drivers, and administrators.

#### Problem Statement:
1. Repeated Manual Data Entry: Passengers are required to repeatedly provide the same personal and trip information for every reservation, making the process time-consuming and increasing the likelihood of incomplete or incorrect submissions.
2. Inefficient Reservation Management: Because reservations are handled through standalone online forms, the system lacks proper conflict detection and schedule management, making it difficult for users to organize multiple trips and for administrators to monitor seat availability accurately.
3. Time-Consuming Passenger Verification: Boarding relies on manually checking passenger names against reservation lists, which slows down the boarding process, creates queues at pickup locations, and increases the risk of verification errors.


## TARGET USER:

AniLink is intended to help serve the community of De La Salle University (DLSU). The platform has three specific categories for its target users.

### 1. Passengers

- DLSU Students (Undergraduate and Graduate): Students with Inter-campus enrolled subjects or in thesis using Inter-campus facilities. They will use the platform to reserve seats, view departure schedules, monitor their status, and generate QR codes/tickets/cards for a seamless boarding.

- DLSU Faculty and Staffs: Faculty with Inter-campus assignments.

### 2. Shuttle Drivers and Administrators

- Assigned DLSU Drivers and Facilitators: They are responsible for monitoring and operating the DLSU Shuttle. They will use the platform to ensure a smooth boarding process by scanning the QR codes or validating the passengers' tickets or cards. They will also use the platform to monitor and update real-time data such as the time and status of their trip.


## BRIEF DESCRIPTION:
AniLink is a mobile application which is a platform for shuttle management which is designed to manage the DLSU shuttle service for students and faculty traveling between the Laguna and Manila campuses. This application replaces the manual (google form-based) reservation system with a structured and organized shuttle operations through an hierarchy of: Category → Line → Route → Departure Schedule → Trip → Reservation.

Through the platform, passengers can browse available routes and departure schedules, reserve seats in real time, and receive a QR code/digital card or ticket for a fast and contactless passenger verification. The application contains built-in validation checks such as route limit and category limit checks to ensure fair seat distribution and the prevention of duplicate same day bookings, while a first-in, first-out (FIFO) waitlist automatically promotes passengers when confirmed reservations are cancelled.

For the administration, staff can manage Lines, Routes, and Departure Schedules, monitor seat capacity, and track each trip as it passes through the operational states: Scheduled, Arriving, Boarding, Departed, and Completed. Through this, AniLink reduces the manual reservation effort, minimizes booking errors, and provides a faster, improved shuttle service for the whole DLSU community.

## CORE OOP CONCEPTS:
- Encapsulation: Classes like Reservation, Trip, and Passenger keep their data private and expose only necessary methods (e.g., reserveSeat(), cancelReservation()). <br>
- Inheritance: Passenger, Driver, and Administrator inherit from a common User class.<br>
- Polymorphism: Different user types override methods such as displayDashboard() or performAction().<br>
- Abstraction: Interfaces or abstract classes such as Validator, Repository, or User hide implementation details from the GUI.<br>

## INITIAL CLASS IDEAS:

1. User (Abstract Class): Parent class responsible for encapsulating shared identity attributes. Attributes include userID, firstName, lastName, dlsuEmail, and accountStatus. Methods include login(), logout(), and updateProfile().
2. Passenger (Inherits User): Represents DLSU Students and Faculty booking the shuttles. Attributes include dlsuIDNumber, passengerCategory, and reservationHistory. Methods include requestBooking(), cancelBooking(), and viewActiveReservations().
3. Driver (Inherits User): Represents the assigned shuttle operators verifying passengers. Attributes include licenseNumber, assignedVehicle, and currentTrip. Methods include scanBoardingPass(), updateTripStatus(), and viewPassengerManifest().
4. Administrator (Inherits User): Represents the campus transport managers configuring the system. Attributes include adminRole and department. Methods include manageRoutes(), manageSchedules(), generateUtilizationReports(), and overrideWaitlist().
5. Route: Represents the physical travel path. Attributes include routeID, origin, destination, and departureSchedules. Methods include addSchedule(), getSchedulesForDay(), and validateDailyRouteLimit().
6. DepartureSchedule: Represents the planned master timeslots. Attributes include scheduleID, departureTime, operatingDays, and isActive. Methods include generateDailyTrips().
7. Vehicle: Represents the physical shuttle bus. Attributes include plateNumber, capacity, and vehicleStatus. Methods include updateStatus() and getCapacity().
8. Trip: Represents a specific travel event for a given day. Attributes include tripID, date, route, schedule, assignedVehicle, assignedDriver, and tripStatus. Methods include getAvailableSeats() and getWaitlistQueue().
9. Reservation: Represents the booking transaction linking a passenger to a trip. Attributes include reservationID, passenger, trip, timestamp, status, and qrCodePayload. Methods include generateQRCode() and updateReservationStatus().
10. ReservationManager (Controller): Acts as the central logic handler enforcing business rules. Attributes include maxDailyReservationsPerUser and categorySeatAllocation. Methods include processBookingRequest(), handleCancellation(), processFIFO_Waitlist(), and enforceCategoryLimit().


## USER STORIES (Recommended):
- As a DLSU student, I want to see the current seat reservation progress so I know if a certain bus schedule is full or not.
- As a regular shuttle user going to Laguna and back, I want to know the actual schedules of each bus so that I can know the time a bus would come or go.
- As a new user of the shuttle service reservation, I want the platform to be well organized so that I am given the correct and overall information I need to get on the bus.

## CORE FEATURES (Recommended):
- Automated Reservation and Waitlist
- The system does real-time bookings and sorts users by category. If someone cancels their confirmed seat, it automatically gives it to the next person on the waitlist using FIFO. It also blocks duplicate bookings by checking the daily route limit.
- Contactless QR Code Verification
- Every confirmed booking gets its own QR code. Drivers just scan it from their account to verify passengers. No more checking names on a list, so boarding is way faster.
- Dynamic Trip Status Tracking
- Passengers can check routes, schedules, and how many seats are left in real time. Drivers update the trip status like Boarding, Departed, or Completed as it happens. This way everyone knows what's going on with the shuttle.
- Centralized Profile and History
- The app saves passenger info in their profile so they don't have to type it in every time they book. Users can also see their current bookings and past trips in one place.

## SAMPLE IMAGES<br>
### Login
<img width="1024" height="768" alt="Heading (1)" src="https://github.com/user-attachments/assets/d96bc114-9224-449c-9854-7277c9fda86a" /><br>
### Dashboard (Passenger)
<img width="1024" height="768" alt="Heading (2)" src="https://github.com/user-attachments/assets/8374261d-321f-4830-a3e4-84b47940a300" />


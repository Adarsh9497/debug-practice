# 🎮 LEVEL 3: CareSlot Appointment Booking API

## 1. System Description

CareSlot is a clinic scheduling service that lets intake clients create and retrieve patient appointments. The Spring Boot API must enforce clinician availability and return stable HTTP contracts for successful, invalid, missing, and conflicting requests.

## 2. Business Requirements

* `POST /api/v1/appointments` creates an appointment.
* `patientId` and `clinicianId` are required and must not be blank.
* `startsAt` is required. `durationMinutes` is required and must be between 15 and 120 inclusive.
* A successful creation returns HTTP `201`, a `Location` header, and the stored appointment.
* An appointment occupies the half-open interval `[startsAt, endsAt)`.
* Two appointments for the same clinician may meet at a boundary, but their occupied intervals must not overlap.
* A genuine clinician conflict returns HTTP `409` with problem code `APPOINTMENT_CONFLICT`.
* Invalid request data returns HTTP `400` with problem code `VALIDATION_ERROR`.
* `GET /api/v1/appointments/{appointmentId}` returns HTTP `200` for an existing appointment and HTTP `404` with code `APPOINTMENT_NOT_FOUND` for an unknown one.
* Appointments are stored in memory for this exercise.

## 3. Environment and Architecture

* Java: 21
* Spring Boot: 3.2.4
* Build tool: Maven 3.9+
* Database: none; in-memory service storage
* Web stack: Spring MVC and Bean Validation
* Tests: JUnit 5 and MockMvc
* Configuration: `src/main/resources/application.yml`
* Application entry point: `CareSlotApplication`

```text
level-3/
├── README.md
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/amazon/careslot/
    │   │   ├── CareSlotApplication.java
    │   │   ├── appointment/
    │   │   │   ├── Appointment.java
    │   │   │   ├── AppointmentConflictException.java
    │   │   │   ├── AppointmentController.java
    │   │   │   ├── AppointmentNotFoundException.java
    │   │   │   ├── AppointmentResponse.java
    │   │   │   ├── AppointmentService.java
    │   │   │   └── CreateAppointmentRequest.java
    │   │   └── web/
    │   │       └── ApiExceptionHandler.java
    │   └── resources/
    │       └── application.yml
    └── test/java/com/amazon/careslot/appointment/
        └── AppointmentApiTest.java
```

Run from the repository root:

```bash
cd level-3
mvn test
```

## 4. Incident Report

**Incident ID:** CARESLOT-API-317
**Severity:** Sev-3

Clinic quality control found zero-minute appointments in the booking ledger. The API returned HTTP `201`, and the response showed identical `startsAt` and `endsAt` values.

Clinician operations also reported that a valid back-to-back appointment was rejected even though the second appointment began exactly when the first ended. Investigators confirmed the clinician had no overlapping patient commitment.

The mobile client team separately reported that a real clinician conflict returned HTTP `400`, but the published API contract requires HTTP `409`. Their conflict-recovery workflow never runs because it classifies the response as request validation failure.

## 5. Test Execution Report

Executed with `mvn test` on 2026-11-02: **6 tests run, 3 passed, 3 failed**.

### Passing Scenarios

**Valid appointment creation**

* Request: A nonblank patient and clinician, `startsAt=2026-11-10T09:00:00Z`, and `durationMinutes=30`.
* Expected: HTTP `201`, a location header, normalized response fields, and `endsAt=2026-11-10T09:30:00Z`.
* Observed: Matches expected.

**Retrieve an appointment**

* Initial state: A valid appointment has been created.
* Expected: `GET` by appointment ID returns HTTP `200` with the stored clinician.
* Observed: Matches expected.

**Unknown appointment**

* Request: `GET /api/v1/appointments/APT-missing`.
* Expected: HTTP `404` and code `APPOINTMENT_NOT_FOUND`.
* Observed: Matches expected.

### Failing Scenarios

**Scenario 1: Unsupported duration**

* Request: A valid patient and clinician with `durationMinutes=0`.
* Expected: HTTP `400` and code `VALIDATION_ERROR`.
* Actual: HTTP `201`; an appointment with equal start and end times is created.

**Scenario 2: Boundary-adjacent appointment**

* Initial state: Clinician `clinician-adjacent` is booked from `09:00Z` to `09:30Z`.
* Request: Book the same clinician from `09:30Z` to `10:00Z`.
* Expected: HTTP `201`.
* Actual: HTTP `400` with `APPOINTMENT_CONFLICT`.

**Scenario 3: Genuine overlap**

* Initial state: Clinician `clinician-overlap` is booked from `09:00Z` to `10:00Z`.
* Request: Book the same clinician from `09:15Z` to `09:45Z`.
* Expected: HTTP `409` and code `APPOINTMENT_CONFLICT`.
* Actual: HTTP `400` and code `APPOINTMENT_CONFLICT`.

## 6. Debugging Constraints

* Maximum alteration: 6 changed production lines.
* Preserve all public endpoints, request and response fields, exception contracts, and status codes.
* Do not rewrite the controller, service, or error-handling architecture.
* Do not add dependencies or persistence.
* Additional focused tests are allowed.
* Do not hardcode test values or bypass Spring validation.
* The final patch should address the production contracts rather than weaken the tests.

## 7. Your Task

Identify each defect and submit findings incrementally or together using this format:

```text
Bug #1
* File:
* Class and method:
* Exact faulty statement or line:
* Root cause:
* Triggering conditions:
* Proposed minimal fix:
* Regression tests:
```

Your investigation starts now. Find the root cause before changing the code.

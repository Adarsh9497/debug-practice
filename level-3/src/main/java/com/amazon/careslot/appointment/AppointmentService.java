package com.amazon.careslot.appointment;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AppointmentService {

    private final Map<String, Appointment> appointments = new ConcurrentHashMap<>();
    private final Map<String, List<Appointment>> appointmentsByClinician = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1_000);

    public Appointment create(CreateAppointmentRequest request) {
        Instant candidateStart = request.startsAt();
        Instant candidateEnd = candidateStart.plus(request.durationMinutes(), ChronoUnit.MINUTES);
        List<Appointment> existingAppointments =
                appointmentsByClinician.getOrDefault(request.clinicianId(), List.of());

        for (Appointment existing : existingAppointments) {
            if (overlaps(existing, candidateStart, candidateEnd)) {
                throw new AppointmentConflictException(
                        "Clinician " + request.clinicianId() + " is already booked");
            }
        }

        Appointment appointment = new Appointment(
                "APT-" + sequence.getAndIncrement(),
                request.patientId(),
                request.clinicianId(),
                candidateStart,
                candidateEnd,
                request.durationMinutes()
        );
        appointments.put(appointment.appointmentId(), appointment);
        appointmentsByClinician
                .computeIfAbsent(request.clinicianId(), ignored -> new ArrayList<>())
                .add(appointment);
        return appointment;
    }

    public Appointment get(String appointmentId) {
        Appointment appointment = appointments.get(appointmentId);
        if (appointment == null) {
            throw new AppointmentNotFoundException(appointmentId);
        }
        return appointment;
    }

    private boolean overlaps(Appointment existing, Instant candidateStart, Instant candidateEnd) {
        return !candidateStart.isAfter(existing.endsAt())
                && !existing.startsAt().isAfter(candidateEnd);
    }
}

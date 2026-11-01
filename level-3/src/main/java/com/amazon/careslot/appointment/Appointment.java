package com.amazon.careslot.appointment;

import java.time.Instant;

public record Appointment(
        String appointmentId,
        String patientId,
        String clinicianId,
        Instant startsAt,
        Instant endsAt,
        int durationMinutes
) {}

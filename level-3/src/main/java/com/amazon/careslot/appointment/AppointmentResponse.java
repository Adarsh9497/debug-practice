package com.amazon.careslot.appointment;

import java.time.Instant;

public record AppointmentResponse(
        String appointmentId,
        String patientId,
        String clinicianId,
        Instant startsAt,
        Instant endsAt,
        int durationMinutes
) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.appointmentId(),
                appointment.patientId(),
                appointment.clinicianId(),
                appointment.startsAt(),
                appointment.endsAt(),
                appointment.durationMinutes()
        );
    }
}

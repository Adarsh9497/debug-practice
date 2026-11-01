package com.amazon.careslot.appointment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateAppointmentRequest(
        @NotBlank String patientId,
        @NotBlank String clinicianId,
        @NotNull Instant startsAt,
        @NotNull @Min(15) @Max(120) Integer durationMinutes
) {}

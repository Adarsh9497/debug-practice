package com.amazon.careslot.appointment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @RequestBody CreateAppointmentRequest request
    ) {
        Appointment appointment = appointmentService.create(request);
        URI location = URI.create("/api/v1/appointments/" + appointment.appointmentId());
        return ResponseEntity.created(location).body(AppointmentResponse.from(appointment));
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@PathVariable String appointmentId) {
        return AppointmentResponse.from(appointmentService.get(appointmentId));
    }
}

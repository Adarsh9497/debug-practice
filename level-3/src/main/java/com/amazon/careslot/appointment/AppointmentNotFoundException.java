package com.amazon.careslot.appointment;

public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(String appointmentId) {
        super("Appointment not found: " + appointmentId);
    }
}

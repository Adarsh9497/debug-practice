package com.amazon.careslot.web;

import com.amazon.careslot.appointment.AppointmentConflictException;
import com.amazon.careslot.appointment.AppointmentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid appointment request");
        problem.setProperty("code", "VALIDATION_ERROR");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(AppointmentNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Appointment not found");
        problem.setProperty("code", "APPOINTMENT_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(AppointmentConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(AppointmentConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Appointment conflict");
        problem.setProperty("code", "APPOINTMENT_CONFLICT");
        return ResponseEntity.badRequest().body(problem);
    }
}

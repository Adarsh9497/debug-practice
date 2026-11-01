package com.amazon.careslot.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Instant startsAt;

    @BeforeEach
    void setUp() {
        startsAt = Instant.parse("2026-11-10T09:00:00Z");
    }

    @Test
    @DisplayName("PASSING: create a valid appointment")
    void createsAppointment() throws Exception {
        String body = request("patient-1", "clinician-create", startsAt, 30);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/appointments/APT-")))
                .andExpect(jsonPath("$.appointmentId").isNotEmpty())
                .andExpect(jsonPath("$.patientId").value("patient-1"))
                .andExpect(jsonPath("$.clinicianId").value("clinician-create"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.endsAt").value("2026-11-10T09:30:00Z"));
    }

    @Test
    @DisplayName("PASSING: retrieve an existing appointment")
    void retrievesAppointment() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-2", "clinician-get", startsAt, 45)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(createResponse);

        mockMvc.perform(get("/api/v1/appointments/{id}", json.get("appointmentId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicianId").value("clinician-get"));
    }

    @Test
    @DisplayName("PASSING: unknown appointment returns 404")
    void unknownAppointmentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/APT-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("FAILING: duration below the supported range is rejected")
    void rejectsDurationBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-3", "clinician-short", startsAt, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FAILING: adjacent appointments can share a clinician boundary")
    void allowsAdjacentAppointments() throws Exception {
        String clinicianId = "clinician-adjacent";
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-4", clinicianId, startsAt, 30)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-5", clinicianId, startsAt.plusSeconds(1_800), 30)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FAILING: overlapping appointments return a conflict status")
    void overlappingAppointmentsReturnConflict() throws Exception {
        String clinicianId = "clinician-overlap";
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-6", clinicianId, startsAt, 60)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("patient-7", clinicianId, startsAt.plusSeconds(900), 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
    }

    private String request(
            String patientId,
            String clinicianId,
            Instant appointmentStart,
            int durationMinutes
    ) throws Exception {
        return objectMapper.writeValueAsString(new CreateAppointmentRequest(
                patientId, clinicianId, appointmentStart, durationMinutes));
    }
}

package com.example.meeting_service.dto.request;


import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateMeetingRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private Long groupId;  // Optionnel : si réunion pour un groupe

    @NotNull(message = "Scheduled start time is required")
    @Future(message = "Start time must be in the future")
    private LocalDateTime scheduledStartTime;

    private LocalDateTime scheduledEndTime;

    private List<String> participantIds;  // Liste de userIds Keycloak

    private Integer maxParticipants = 50;

    private Boolean isRecorded = false;
}
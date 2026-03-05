package com.example.meeting_service.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateMeetingRequest {
    private String title;
    private String description;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private Integer maxParticipants;
    private Boolean isRecorded;
}
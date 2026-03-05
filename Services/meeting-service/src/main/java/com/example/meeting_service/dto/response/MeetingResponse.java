package com.example.meeting_service.dto.response;

import com.example.meeting_service.enums.MeetingStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MeetingResponse {
    private Long id;
    private String title;
    private String description;
    private String organizerId;
    private Long groupId;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private MeetingStatus status;
    private Boolean isRecorded;
    private Integer participantCount;
    private LocalDateTime createdAt;
}
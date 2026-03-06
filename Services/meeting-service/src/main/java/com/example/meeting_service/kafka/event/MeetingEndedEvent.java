package com.example.meeting_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingEndedEvent {
    private String eventId;
    private String eventType = "MEETING_ENDED";
    private LocalDateTime timestamp;
    private Long meetingId;
    private String title;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Long durationMinutes;
    private Boolean isRecorded;
}
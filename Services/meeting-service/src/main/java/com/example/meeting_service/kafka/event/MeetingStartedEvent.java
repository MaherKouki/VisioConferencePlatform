package com.example.meeting_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingStartedEvent {
    private String eventId;
    private String eventType = "MEETING_STARTED";
    private LocalDateTime timestamp;
    private Long meetingId;
    private String title;
    private String meetingUrl;
    private LocalDateTime actualStartTime;
    private List<String> participants;
    private Boolean isRecorded;
}
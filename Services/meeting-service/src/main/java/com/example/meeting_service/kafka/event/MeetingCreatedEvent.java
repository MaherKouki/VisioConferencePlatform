package com.example.meeting_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingCreatedEvent {
    private String eventId;
    private String eventType = "MEETING_CREATED";
    private LocalDateTime timestamp;
    private Long meetingId;
    private String title;
    private String organizerId;
    private Long groupId;
    private LocalDateTime scheduledStartTime;
    private List<ParticipantInfo> participants;
    private Boolean isRecorded;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String userId;
        private String email;
    }
}

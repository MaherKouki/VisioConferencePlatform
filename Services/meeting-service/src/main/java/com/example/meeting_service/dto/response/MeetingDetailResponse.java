package com.example.meeting_service.dto.response;

import com.example.meeting_service.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class MeetingDetailResponse {
    private Long id;
    private String title;
    private String description;
    private OrganizerInfo organizer;
    private GroupInfo group;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private MeetingStatus status;
    private String meetingUrl;
    private Integer maxParticipants;
    private Boolean isRecorded;
    private List<ParticipantResponse> participants;
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    public static class OrganizerInfo {
        private String userId;
        private String username;
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class GroupInfo {
        private Long id;
        private String name;
    }
}

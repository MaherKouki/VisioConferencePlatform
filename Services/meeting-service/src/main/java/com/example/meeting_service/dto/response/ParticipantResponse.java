package com.example.meeting_service.dto.response;

import com.example.meeting_service.enums.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ParticipantResponse {
    private String userId;
    private String username;
    private String email;
    private ParticipantStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
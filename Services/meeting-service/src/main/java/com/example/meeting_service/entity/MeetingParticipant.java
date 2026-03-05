package com.example.meeting_service.entity;

import com.example.meeting_service.enums.ParticipantStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_participants")
@Data
public class MeetingParticipant {

    @EmbeddedId
    private MeetingParticipantId id;

    @ManyToOne
    @MapsId("meetingId")
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status = ParticipantStatus.INVITED;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;
}
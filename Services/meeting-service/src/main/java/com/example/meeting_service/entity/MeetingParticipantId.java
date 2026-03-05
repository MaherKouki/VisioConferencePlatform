package com.example.meeting_service.entity;

import java.io.Serializable;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantId implements Serializable {
    private Long meetingId;
    private String userId;

}

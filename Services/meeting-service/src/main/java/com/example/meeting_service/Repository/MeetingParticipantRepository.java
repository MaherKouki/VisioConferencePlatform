package com.example.meeting_service.Repository;

//import com.example.meeting_service.entity.MeetingParticipant;
//import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.entity.MeetingParticipant;
import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant , MeetingParticipantId> {
    // Trouver participants par réunion
    List<MeetingParticipant> findByIdMeetingId(Long meetingId);

    // Trouver réunions d'un utilisateur
    List<MeetingParticipant> findByIdUserId(String userId);

    // Compter participants par statut
    int countByIdMeetingIdAndStatus(Long meetingId, ParticipantStatus status);

    // Compter participants actifs (pas encore partis)
    int countByIdMeetingIdAndLeftAtIsNull(Long meetingId);

    // Vérifier si un utilisateur est participant
    boolean existsByIdMeetingIdAndIdUserId(Long meetingId, String userId);

    int countByIdMeetingId(Long id);
}

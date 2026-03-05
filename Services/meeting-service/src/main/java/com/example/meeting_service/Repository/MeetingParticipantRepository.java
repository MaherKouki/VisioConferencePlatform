package com.example.meeting_service.Repository;

//import com.example.meeting_service.entity.MeetingParticipant;
//import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.entity.MeetingParticipant;
import com.example.meeting_service.entity.MeetingParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant , MeetingParticipantId> {
}

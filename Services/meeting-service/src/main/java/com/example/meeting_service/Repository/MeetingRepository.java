package com.example.meeting_service.Repository;

import com.example.meeting_service.entity.Meeting;
import com.example.meeting_service.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    // Trouver réunions par organisateur
    List<Meeting> findByOrganizerId(String organizerId);

    // Trouver réunions par statut
    List<Meeting> findByStatus(MeetingStatus status);

    // Trouver réunions par organisateur et statut
    List<Meeting> findByOrganizerIdAndStatus(String organizerId, MeetingStatus status);

    // Trouver réunions par groupe
    List<Meeting> findByGroupId(Long groupId);

    // Trouver réunions entre deux dates
    List<Meeting> findByScheduledStartTimeBetween(LocalDateTime start, LocalDateTime end);


    // Trouver réunions d'un utilisateur (organisateur OU participant)
    @Query("SELECT DISTINCT m FROM Meeting m " +
            "LEFT JOIN m.participants p " +
            "WHERE m.organizerId = :userId OR p.id.userId = :userId")
    List<Meeting> findByUserIdAsOrganizerOrParticipant(String userId);


}

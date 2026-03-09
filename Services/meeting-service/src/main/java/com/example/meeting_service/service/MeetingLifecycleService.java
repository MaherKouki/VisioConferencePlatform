package com.example.meeting_service.service;

import com.example.meeting_service.Repository.MeetingParticipantRepository;
import com.example.meeting_service.Repository.MeetingRepository;
import com.example.meeting_service.entity.Meeting;
import com.example.meeting_service.entity.MeetingParticipant;
import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.enums.MeetingStatus;
import com.example.meeting_service.enums.ParticipantStatus;
import com.example.meeting_service.kafka.event.MeetingEndedEvent;
import com.example.meeting_service.kafka.event.MeetingStartedEvent;
import com.example.meeting_service.kafka.producer.MeetingEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEventProducer eventProducer;
    private final MeetingSessionService sessionService;


    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public Map<String, Object> startMeeting(Long meetingId, String userId) {
        log.info("Starting meeting {} by user {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier que c'est l'organisateur
        if (!meeting.getOrganizerId().equals(userId)) {
            log.warn("User {} tried to start meeting {} but is not organizer", userId, meetingId);
            throw new RuntimeException("Only organizer can start meeting");
        }

        // Vérifier statut
        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new RuntimeException("Meeting is not in SCHEDULED status (current: " + meeting.getStatus() + ")");
        }

        // Vérifier heure (tolérance ±15 minutes)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = meeting.getScheduledStartTime();
        long minutesDifference = Duration.between(scheduledTime, now).toMinutes();

        if (Math.abs(minutesDifference) > 15) {
            log.warn("Trying to start meeting {} outside time window (diff: {} min)", meetingId, minutesDifference);
            throw new RuntimeException("Meeting can only be started within 15 minutes of scheduled time");
        }

        // Générer URL + Mettre à jour
        String meetingUrl = generateMeetingUrl(meetingId);

        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setActualStartTime(now);
        meeting.setMeetingUrl(meetingUrl);
        meeting.setUpdatedAt(now);
        meetingRepository.save(meeting);

        // Marquer organisateur comme actif
        sessionService.markParticipantActive(meetingId, userId);
        log.info("Organizer {} marked as active in Redis session", userId);

        // Publier événement Kafka
        publishMeetingStartedEvent(meeting);

        log.info("Meeting {} started successfully at {}", meetingId, now);

        return Map.of(
                "message", "Meeting started successfully",
                "meetingUrl", meetingUrl,
                "status", MeetingStatus.LIVE.toString(),
                "actualStartTime", now.toString()
        );
    }




    @Transactional
    public Map<String, Object> joinMeeting(Long meetingId, String userId) {
        log.info("User {} joining meeting {}", userId, meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier que réunion est LIVE
        if (meeting.getStatus() != MeetingStatus.LIVE) {
            throw new RuntimeException("Meeting is not live (current status: " + meeting.getStatus() + ")");
        }

        // Vérifier que utilisateur est invité
        if (!meeting.getOrganizerId().equals(userId) &&
                !participantRepository.existsByIdMeetingIdAndIdUserId(meetingId, userId)) {
            log.warn("User {} tried to join meeting {} without invitation", userId, meetingId);
            throw new RuntimeException("You are not invited to this meeting");
        }

        // Mettre à jour participant (si pas organisateur)
        if (!meeting.getOrganizerId().equals(userId)) {
            MeetingParticipant participant = participantRepository.findById(
                    new MeetingParticipantId(meetingId, userId)
            ).orElseThrow(() -> new RuntimeException("Participant not found"));

            participant.setStatus(ParticipantStatus.ATTENDED);
            participant.setJoinedAt(LocalDateTime.now());
            participantRepository.save(participant);
        }

        // Marquer participant comme actif
        sessionService.markParticipantActive(meetingId, userId);

        // Compter participants en ligne
        long activeCount = sessionService.countActiveParticipants(meetingId);
        log.info("User {} joined meeting {} (total active: {})", userId, meetingId, activeCount);

        return Map.of(
                "meetingUrl", meeting.getMeetingUrl(),
                "meetingId", meetingId,
                "title", meeting.getTitle(),
                "isRecorded", meeting.getIsRecorded(),
                "activeParticipants", activeCount
        );
    }


    @Transactional
    public void leaveMeeting(Long meetingId, String userId) {
        log.info("User {} leaving meeting {}", userId, meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Si organisateur quitte → doit terminer la réunion
        if (meeting.getOrganizerId().equals(userId)) {
            log.info("Organizer attempted to leave - should use end meeting instead");
            return;
        }

        // Mettre à jour participant en DB
        MeetingParticipant participant = participantRepository.findById(
                new MeetingParticipantId(meetingId, userId)
        ).orElse(null);

        if (participant != null) {
            participant.setLeftAt(LocalDateTime.now());
            participantRepository.save(participant);
        }

        //Retirer du Set actifs
        sessionService.removeParticipant(meetingId, userId);

        //Vérifier si dernier participant
        long activeCount = sessionService.countActiveParticipants(meetingId);

        if (activeCount == 0 && meeting.getStatus() == MeetingStatus.LIVE) {
            log.info("Last participant left (Redis count: 0) - auto-ending meeting {}", meetingId);
            endMeeting(meetingId, meeting.getOrganizerId());
        } else {
            log.info("User {} left meeting {} ({} participants remaining)", userId, meetingId, activeCount);
        }
    }



    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public void endMeeting(Long meetingId, String userId) {
        log.info("Ending meeting {} by user {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier que c'est l'organisateur
        if (!meeting.getOrganizerId().equals(userId)) {
            throw new RuntimeException("Only organizer can end meeting");
        }

        // Vérifier le statut
        if (meeting.getStatus() != MeetingStatus.LIVE) {
            throw new RuntimeException("Meeting is not live (current: " + meeting.getStatus() + ")");
        }

        LocalDateTime now = LocalDateTime.now();
        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setActualEndTime(now);
        meeting.setUpdatedAt(now);
        meetingRepository.save(meeting);

        //Nettoyer session (supprime Set actifs)
        sessionService.clearMeetingSession(meetingId);
        log.info("Redis session cleared for meeting {}", meetingId);

        // Publier événement Kafka
        publishMeetingEndedEvent(meeting);

        log.info("Meeting {} ended successfully at {}", meetingId, now);
    }


    private String generateMeetingUrl(Long meetingId) {
        String roomToken = UUID.randomUUID().toString();
        return String.format("https://meet.example.com/room/%d/%s", meetingId, roomToken);
    }


    private void publishMeetingStartedEvent(Meeting meeting) {
        List<String> participantIds = participantRepository.findByIdMeetingId(meeting.getId())
                .stream()
                .map(p -> p.getId().getUserId())
                .collect(Collectors.toList());

        MeetingStartedEvent event = new MeetingStartedEvent(
                UUID.randomUUID().toString(),
                "MEETING_STARTED",
                LocalDateTime.now(),
                meeting.getId(),
                meeting.getTitle(),
                meeting.getMeetingUrl(),
                meeting.getActualStartTime(),
                participantIds,
                meeting.getIsRecorded()
        );

        eventProducer.publishMeetingStarted(event);
        log.info("MeetingStartedEvent published for meeting {}", meeting.getId());
    }


    private void publishMeetingEndedEvent(Meeting meeting) {
        long durationMinutes = 0;
        if (meeting.getActualStartTime() != null && meeting.getActualEndTime() != null) {
            durationMinutes = Duration.between(
                    meeting.getActualStartTime(),
                    meeting.getActualEndTime()
            ).toMinutes();
        }

        MeetingEndedEvent event = new MeetingEndedEvent(
                UUID.randomUUID().toString(),
                "MEETING_ENDED",
                LocalDateTime.now(),
                meeting.getId(),
                meeting.getTitle(),
                meeting.getActualStartTime(),
                meeting.getActualEndTime(),
                durationMinutes,
                meeting.getIsRecorded()
        );

        eventProducer.publishMeetingEnded(event);
        log.info("MeetingEndedEvent published for meeting {}", meeting.getId());
    }
}
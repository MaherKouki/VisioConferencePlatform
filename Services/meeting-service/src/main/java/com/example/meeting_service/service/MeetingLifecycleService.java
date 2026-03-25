// CHEMIN : meeting-service/src/main/java/com/example/meeting_service/service/MeetingLifecycleService.java

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
import com.example.meeting_service.websocket.MeetingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final MeetingRepository            meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEventProducer         eventProducer;
    private final MeetingSessionService        sessionService;
    private final SimpMessagingTemplate        messagingTemplate;

    // ══════════════════════════════════════════════════════
    // START MEETING
    // ══════════════════════════════════════════════════════
    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public Map<String, Object> startMeeting(Long meetingId, String userId) {
        log.info("Starting meeting {} by user {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerId().equals(userId)) {
            throw new RuntimeException("Only organizer can start meeting");
        }

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new RuntimeException(
                    "Meeting is not in SCHEDULED status (current: " + meeting.getStatus() + ")"
            );
        }

        LocalDateTime now           = LocalDateTime.now();
        LocalDateTime scheduledTime = meeting.getScheduledStartTime();
        long minutesBefore          = Duration.between(now, scheduledTime).toMinutes();

        if (minutesBefore > 30) {
            throw new RuntimeException(
                    "Meeting can only be started 30 minutes before scheduled time "
                            + "(currently " + minutesBefore + " min early)"
            );
        }

        String meetingUrl = generateMeetingUrl(meetingId);

        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setActualStartTime(now);
        meeting.setMeetingUrl(meetingUrl);
        meeting.setUpdatedAt(now);
        meetingRepository.save(meeting);

        sessionService.markParticipantActive(meetingId, userId);
        log.info("Organizer {} marked as active in Redis session", userId);

        // Kafka event
        publishMeetingStartedEvent(meeting);

        // ── WebSocket room : badge SCHEDULED → LIVE ──────────────
        messagingTemplate.convertAndSend(
                "/topic/meeting/" + meeting.getId(),
                new MeetingEvent("MEETING_STARTED", meeting.getId(), meeting.getTitle())
        );

        // ── Toast organisateur : "X is now live" ─────────────────
        messagingTemplate.convertAndSend(
                "/topic/user/" + userId,
                new MeetingEvent("MEETING_LIVE_NOTIFY", meetingId,
                        Map.of("meetingTitle", meeting.getTitle()))
        );

        // ── Toast chaque participant ACCEPTED : "X is now live" ──
        List<MeetingParticipant> acceptedParticipants = participantRepository
                .findByIdMeetingId(meetingId)
                .stream()
                .filter(p -> p.getStatus() == ParticipantStatus.ACCEPTED)
                .collect(Collectors.toList());

        for (MeetingParticipant p : acceptedParticipants) {
            messagingTemplate.convertAndSend(
                    "/topic/user/" + p.getId().getUserId(),
                    new MeetingEvent("MEETING_LIVE_NOTIFY", meetingId,
                            Map.of("meetingTitle", meeting.getTitle()))
            );
        }

        log.info("Meeting {} started successfully at {}", meetingId, now);

        return Map.of(
                "message",         "Meeting started successfully",
                "meetingUrl",      meetingUrl,
                "status",          MeetingStatus.LIVE.toString(),
                "actualStartTime", now.toString()
        );
    }

    // ══════════════════════════════════════════════════════
    // JOIN MEETING
    // ══════════════════════════════════════════════════════
    @Transactional
    public Map<String, Object> joinMeeting(Long meetingId, String userId) {
        log.info("User {} joining meeting {}", userId, meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getStatus() != MeetingStatus.LIVE) {
            throw new RuntimeException(
                    "Meeting is not live (current status: " + meeting.getStatus() + ")"
            );
        }

        if (!meeting.getOrganizerId().equals(userId) &&
                !participantRepository.existsByIdMeetingIdAndIdUserId(meetingId, userId)) {
            throw new RuntimeException("You are not invited to this meeting");
        }

        // Mettre à jour statut participant → ATTENDED
        if (!meeting.getOrganizerId().equals(userId)) {
            MeetingParticipant participant = participantRepository.findById(
                    new MeetingParticipantId(meetingId, userId)
            ).orElseThrow(() -> new RuntimeException("Participant not found"));

            participant.setStatus(ParticipantStatus.ATTENDED);
            participant.setJoinedAt(LocalDateTime.now());
            participantRepository.save(participant);
        }

        sessionService.markParticipantActive(meetingId, userId);

        long activeCount = sessionService.countActiveParticipants(meetingId);
        log.info("User {} joined meeting {} (total active: {})", userId, meetingId, activeCount);

        messagingTemplate.convertAndSend(
                "/topic/meeting/" + meetingId,
                new MeetingEvent("PARTICIPANT_JOINED", meetingId, (int) activeCount)
        );

        return Map.of(
                "meetingUrl",         meeting.getMeetingUrl(),
                "meetingId",          meetingId,
                "title",              meeting.getTitle(),
                "isRecorded",         meeting.getIsRecorded(),
                "activeParticipants", activeCount
        );
    }

    // ══════════════════════════════════════════════════════
    // LEAVE MEETING
    // retourne true  → organisateur (controller → 400)
    // retourne false → quitte normalement
    // ══════════════════════════════════════════════════════
    @Transactional
    public boolean leaveMeeting(Long meetingId, String userId) {
        log.info("User {} leaving meeting {}", userId, meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getOrganizerId().equals(userId)) {
            log.info("Organizer {} attempted to leave - should use end meeting instead", userId);
            return true;
        }

        MeetingParticipant participant = participantRepository.findById(
                new MeetingParticipantId(meetingId, userId)
        ).orElse(null);

        if (participant != null) {
            participant.setLeftAt(LocalDateTime.now());
            participantRepository.save(participant);
        }

        sessionService.removeParticipant(meetingId, userId);

        long activeCount = sessionService.countActiveParticipants(meetingId);

        if (activeCount == 0 && meeting.getStatus() == MeetingStatus.LIVE) {
            log.info("Last participant left - auto-ending meeting {}", meetingId);
            endMeeting(meetingId, meeting.getOrganizerId());
        } else {
            log.info("User {} left meeting {} ({} remaining)", userId, meetingId, activeCount);
            messagingTemplate.convertAndSend(
                    "/topic/meeting/" + meetingId,
                    new MeetingEvent("PARTICIPANT_LEFT", meetingId, (int) activeCount)
            );
        }

        return false;
    }

    // ══════════════════════════════════════════════════════
    // END MEETING
    //
    // FIX : double émission WebSocket
    //   1. /topic/meeting/{id}  → MEETING_ENDED
    //      → meeting-room reçoit → TOUS redirigés (organisateur compris)
    //   2. /topic/user/{userId} → MEETING_ENDED_NOTIFY
    //      → toast "X has ended" pour chaque participant
    //
    // IMPORTANT : l'organisateur NE navigue PAS dans le next: callback
    // côté Angular → c'est le WS qui gère la redirection pour tout le monde
    // ══════════════════════════════════════════════════════
    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public void endMeeting(Long meetingId, String userId) {
        log.info("Ending meeting {} by user {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerId().equals(userId)) {
            throw new RuntimeException("Only organizer can end meeting");
        }

        if (meeting.getStatus() != MeetingStatus.LIVE) {
            throw new RuntimeException(
                    "Meeting is not live (current: " + meeting.getStatus() + ")"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setActualEndTime(now);
        meeting.setUpdatedAt(now);
        meetingRepository.save(meeting);

        sessionService.clearMeetingSession(meetingId);
        log.info("Redis session cleared for meeting {}", meetingId);

        // Kafka event
        publishMeetingEndedEvent(meeting);

        // ── 1. Redirection de la room pour TOUS les actifs ───────
        // (organisateur + participants dans meeting-room)
        messagingTemplate.convertAndSend(
                "/topic/meeting/" + meeting.getId(),
                new MeetingEvent("MEETING_ENDED", meeting.getId(), null)
        );

        // ── 2. Toast individuel pour chaque participant ──────────
        List<MeetingParticipant> allParticipants = participantRepository.findByIdMeetingId(meetingId);
        for (MeetingParticipant p : allParticipants) {
            messagingTemplate.convertAndSend(
                    "/topic/user/" + p.getId().getUserId(),
                    new MeetingEvent("MEETING_ENDED_NOTIFY", meetingId,
                            Map.of("meetingTitle", meeting.getTitle()))
            );
        }

        // ── 3. Toast pour l'organisateur ─────────────────────────
        messagingTemplate.convertAndSend(
                "/topic/user/" + meeting.getOrganizerId(),
                new MeetingEvent("MEETING_ENDED_NOTIFY", meetingId,
                        Map.of("meetingTitle", meeting.getTitle()))
        );

        log.info("Meeting {} ended successfully at {}", meetingId, now);
    }

    // ══════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════

    private String generateMeetingUrl(Long meetingId) {
        String roomToken = UUID.randomUUID().toString();
        return String.format("https://meet.example.com/room/%d/%s", meetingId, roomToken);
    }

    private void publishMeetingStartedEvent(Meeting meeting) {
        List<String> participantIds = participantRepository
                .findByIdMeetingId(meeting.getId())
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
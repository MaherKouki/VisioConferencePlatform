package com.example.meeting_service.controller;

import com.example.meeting_service.service.MeetingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════
 * MEETING LIFECYCLE CONTROLLER - Cycle de Vie Réunions
 * ═══════════════════════════════════════════════════════════
 *
 * Gestion des transitions d'état (PostgreSQL) :
 * SCHEDULED → LIVE → ENDED
 *
 * Endpoints :
 * - POST /api/meetings/{id}/start   → Démarrer réunion
 * - POST /api/meetings/{id}/join    → Rejoindre réunion
 * - POST /api/meetings/{id}/leave   → Quitter réunion
 * - POST /api/meetings/{id}/end     → Terminer réunion
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleController {

    private final MeetingLifecycleService lifecycleService;

    /**
     * ═══════════════════════════════════════════════════════════
     * DÉMARRER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{id}/start
     *
     * Transition : SCHEDULED → LIVE
     *
     * Règles :
     * - Seul l'organisateur peut démarrer
     * - Tolérance ±15 minutes heure planifiée
     *
     * Actions :
     * 1. PostgreSQL : status → LIVE, actualStartTime, meetingUrl
     * 2. Redis : Marquer organisateur actif
     * 3. Kafka : Publier MeetingStartedEvent
     *
     * Response :
     * {
     *   "message": "Meeting started successfully",
     *   "meetingUrl": "https://meet.example.com/room/123/...",
     *   "status": "LIVE",
     *   "actualStartTime": "2026-03-10T14:02:30"
     * }
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Starting meeting {} by user {}", id, userId);

        Map<String, Object> response = lifecycleService.startMeeting(id, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * REJOINDRE UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{id}/join
     *
     * Règles :
     * - Réunion doit être LIVE
     * - Utilisateur invité ou organisateur
     *
     * Actions :
     * 1. PostgreSQL : status participant → ATTENDED, joinedAt
     * 2. Redis : Marquer participant actif
     * 3. Frontend : Redirection vers meetingUrl
     *
     * Response :
     * {
     *   "meetingUrl": "https://meet.example.com/room/123/...",
     *   "meetingId": 123,
     *   "title": "Sprint Planning",
     *   "isRecorded": true,
     *   "activeParticipants": 5  ← Nombre en ligne (Redis)
     * }
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> joinMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("User {} joining meeting {}", userId, id);

        Map<String, Object> response = lifecycleService.joinMeeting(id, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * QUITTER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{id}/leave
     *
     * Actions :
     * 1. PostgreSQL : leftAt = NOW()
     * 2. Redis : Retirer du Set actifs
     * 3. Si dernier participant (Redis count = 0) → Auto-terminer
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<Map<String, String>> leaveMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("User {} leaving meeting {}", userId, id);

        lifecycleService.leaveMeeting(id, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Left meeting successfully",
                "meetingId", id.toString()
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * TERMINER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{id}/end
     *
     * Transition : LIVE → ENDED
     *
     * Règles :
     * - Seul l'organisateur peut terminer
     *
     * Actions :
     * 1. PostgreSQL : status → ENDED, actualEndTime
     * 2. Redis : Nettoyer session (supprimer Set actifs)
     * 3. Kafka : Publier MeetingEndedEvent
     */
    @PostMapping("/{id}/end")
    public ResponseEntity<Map<String, String>> endMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Ending meeting {} by user {}", id, userId);

        lifecycleService.endMeeting(id, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Meeting ended successfully",
                "meetingId", id.toString()
        ));
    }
}
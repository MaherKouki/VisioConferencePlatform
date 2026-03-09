package com.example.meeting_service.controller;

import com.example.meeting_service.dto.request.CreateMeetingRequest;
import com.example.meeting_service.dto.request.UpdateMeetingRequest;
import com.example.meeting_service.dto.response.MeetingDetailResponse;
import com.example.meeting_service.dto.response.MeetingResponse;
import com.example.meeting_service.enums.MeetingStatus;
import com.example.meeting_service.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════
 * MEETING CONTROLLER - CRUD Réunions
 * ═══════════════════════════════════════════════════════════
 *
 * Endpoints :
 * - POST   /api/meetings                    → Créer réunion
 * - GET    /api/meetings                    → Mes réunions
 * - GET    /api/meetings?status=LIVE        → Mes réunions filtrées
 * - GET    /api/meetings/{id}               → Détails réunion
 * - PUT    /api/meetings/{id}               → Modifier réunion
 * - DELETE /api/meetings/{id}               → Annuler réunion
 *
 * Authentification : JWT Keycloak (Bearer Token)
 * User ID extrait de : jwt.getSubject()
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * ═══════════════════════════════════════════════════════════
     * CRÉER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings
     *
     * Body :
     * {
     *   "title": "Sprint Planning Q2",
     *   "description": "Planification sprint 12",
     *   "groupId": 1,
     *   "scheduledStartTime": "2026-03-10T14:00:00",
     *   "scheduledEndTime": "2026-03-10T15:00:00",
     *   "participantIds": ["bob-uuid", "charlie-uuid"],
     *   "maxParticipants": 50,
     *   "isRecorded": true
     * }
     *
     * Response 201 :
     * {
     *   "id": 123,
     *   "title": "Sprint Planning Q2",
     *   "status": "SCHEDULED",
     *   "participantCount": 5,
     *   ...
     * }
     */
    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String organizerId = jwt.getSubject();  // UUID Keycloak

        log.info("Creating meeting '{}' by organizer {}", request.getTitle(), organizerId);

        MeetingResponse response = meetingService.createMeeting(request, organizerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * LISTER MES RÉUNIONS
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/meetings
     * GET /api/meetings?status=LIVE
     * GET /api/meetings?status=SCHEDULED
     *
     * Retourne TOUTES les réunions où je suis :
     * - Organisateur
     * - OU Participant
     *
     * Query Params :
     * - status (optionnel) : SCHEDULED, LIVE, ENDED, CANCELLED
     */
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMyMeetings(
            @RequestParam(required = false) MeetingStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        List<MeetingResponse> meetings;

        if (status != null) {
            log.debug("Fetching meetings for user {} with status {}", userId, status);
            meetings = meetingService.getMyMeetingsByStatus(userId, status);
        } else {
            log.debug("Fetching all meetings for user {}", userId);
            meetings = meetingService.getMyMeetings(userId);
        }

        return ResponseEntity.ok(meetings);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * DÉTAILS D'UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/meetings/{id}
     *
     * Response 200 :
     * {
     *   "id": 123,
     *   "title": "Sprint Planning Q2",
     *   "organizer": {
     *     "userId": "alice-uuid",
     *     "username": "alice",
     *     "email": "alice@example.com"
     *   },
     *   "group": {
     *     "groupId": 1,
     *     "groupName": "Équipe Dev"
     *   },
     *   "participants": [
     *     {
     *       "userId": "bob-uuid",
     *       "username": "bob",
     *       "status": "ACCEPTED",
     *       "joinedAt": "2026-03-10T14:05:00"
     *     }
     *   ],
     *   "meetingUrl": "https://meet.example.com/room/123/...",
     *   ...
     * }
     *
     * CACHE REDIS :
     * - 1er appel : Query DB + Appels Auth Service
     * - Appels suivants (< 15 min) : Lecture Redis (< 10ms)
     */
    @GetMapping("/{id}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.debug("Fetching details for meeting {} by user {}", id, userId);

        MeetingDetailResponse details = meetingService.getMeetingDetails(id, userId);

        return ResponseEntity.ok(details);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * MODIFIER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * PUT /api/meetings/{id}
     *
     * Body (tous champs optionnels) :
     * {
     *   "title": "Sprint Planning Q2 - UPDATED",
     *   "description": "Nouvelle description",
     *   "scheduledStartTime": "2026-03-10T15:00:00",
     *   "maxParticipants": 100,
     *   "isRecorded": false
     * }
     *
     * Restrictions :
     * - Seul l'organisateur peut modifier
     * - Impossible si ENDED ou CANCELLED
     *
     * REDIS :
     * - @CacheEvict : Invalide cache getMeetingDetails()
     */
    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Updating meeting {} by user {}", id, userId);

        MeetingResponse response = meetingService.updateMeeting(id, request, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * ANNULER UNE RÉUNION
     * ═══════════════════════════════════════════════════════════
     *
     * DELETE /api/meetings/{id}
     *
     * Restrictions :
     * - Seul l'organisateur peut annuler
     * - Change status → CANCELLED
     *
     * REDIS :
     * - Nettoie session si réunion était LIVE
     * - Invalide cache
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> cancelMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Cancelling meeting {} by user {}", id, userId);

        meetingService.cancelMeeting(id, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Meeting cancelled successfully",
                "meetingId", id.toString()
        ));
    }
}
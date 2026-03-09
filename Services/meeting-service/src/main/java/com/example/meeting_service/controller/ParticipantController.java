package com.example.meeting_service.controller;

import com.example.meeting_service.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════
 * PARTICIPANT CONTROLLER - Gestion Participants
 * ═══════════════════════════════════════════════════════════
 *
 * Endpoints :
 * - POST   /api/meetings/{meetingId}/participants/{userId}    → Inviter
 * - DELETE /api/meetings/{meetingId}/participants/{userId}    → Retirer
 * - PUT    /api/meetings/{meetingId}/participants/accept      → Accepter invitation
 * - PUT    /api/meetings/{meetingId}/participants/decline     → Refuser invitation
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController {

    private final ParticipantService participantService;

    /**
     * ═══════════════════════════════════════════════════════════
     * INVITER UN PARTICIPANT
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{meetingId}/participants/{userId}
     *
     * Restrictions :
     * - Seul l'organisateur peut inviter
     * - Utilisateur doit exister (vérifié via Auth Service)
     * - Réunion pas ENDED/CANCELLED
     * - Limite max_participants respectée
     *
     * REDIS :
     * - userCacheService.userExists() : Vérif cache
     * - @CacheEvict : Invalide cache détails réunion
     */
    @PostMapping("/{meetingId}/participants/{userId}")
    public ResponseEntity<Map<String, String>> inviteParticipant(
            @PathVariable Long meetingId,
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {

        String organizerId = jwt.getSubject();

        log.info("Inviting user {} to meeting {} by organizer {}", userId, meetingId, organizerId);

        participantService.InviteParticpant(meetingId, userId, organizerId);

        return ResponseEntity.ok(Map.of(
                "message", "Participant invited successfully",
                "userId", userId,
                "meetingId", meetingId.toString()
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * RETIRER UN PARTICIPANT
     * ═══════════════════════════════════════════════════════════
     *
     * DELETE /api/meetings/{meetingId}/participants/{userId}
     *
     * Restrictions :
     * - Seul l'organisateur peut retirer
     */
    @DeleteMapping("/{meetingId}/participants/{userId}")
    public ResponseEntity<Map<String, String>> removeParticipant(
            @PathVariable Long meetingId,
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {

        String organizerId = jwt.getSubject();

        log.info("Removing user {} from meeting {} by organizer {}", userId, meetingId, organizerId);

        participantService.removeParticipant(meetingId, userId, organizerId);

        return ResponseEntity.ok(Map.of(
                "message", "Participant removed successfully",
                "userId", userId,
                "meetingId", meetingId.toString()
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * ACCEPTER UNE INVITATION
     * ═══════════════════════════════════════════════════════════
     *
     * PUT /api/meetings/{meetingId}/participants/accept
     *
     * Participant change son statut : INVITED → ACCEPTED
     */
    @PutMapping("/{meetingId}/participants/accept")
    public ResponseEntity<Map<String, String>> acceptInvitation(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("User {} accepting invitation to meeting {}", userId, meetingId);

        participantService.acceptInvitation(meetingId, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Invitation accepted",
                "meetingId", meetingId.toString()
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * REFUSER UNE INVITATION
     * ═══════════════════════════════════════════════════════════
     *
     * PUT /api/meetings/{meetingId}/participants/decline
     *
     * Participant change son statut : INVITED → DECLINED
     */
    @PutMapping("/{meetingId}/participants/decline")
    public ResponseEntity<Map<String, String>> declineInvitation(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("User {} declining invitation to meeting {}", userId, meetingId);

        participantService.declineInvitation(meetingId, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Invitation declined",
                "meetingId", meetingId.toString()
        ));
    }
}
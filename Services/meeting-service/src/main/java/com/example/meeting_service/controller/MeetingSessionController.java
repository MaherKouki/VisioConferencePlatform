package com.example.meeting_service.controller;

import com.example.meeting_service.service.MeetingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════
 * MEETING SESSION CONTROLLER - Tracking Temps Réel (Redis)
 * ═══════════════════════════════════════════════════════════
 *
 * Gestion des participants EN LIGNE (Redis Set avec TTL 30s)
 *
 * Endpoints :
 * - POST /api/meetings/{id}/heartbeat         → Heartbeat participant
 * - GET  /api/meetings/{id}/active-count      → Nombre en ligne
 * - GET  /api/meetings/{id}/active-participants → Liste en ligne
 *
 * SYSTÈME HEARTBEAT :
 * - Frontend envoie heartbeat toutes les 10 secondes
 * - Redis TTL = 30 secondes
 * - Si pas de heartbeat pendant 30s → Auto-retiré
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingSessionController {

    private final MeetingSessionService sessionService;

    /**
     * ═══════════════════════════════════════════════════════════
     * HEARTBEAT - Signaler "Je suis toujours là"
     * ═══════════════════════════════════════════════════════════
     *
     * POST /api/meetings/{id}/heartbeat
     *
     * Appelé automatiquement par le frontend toutes les 10 secondes
     *
     * Flow Frontend (Angular) :
     * ```typescript
     * ngOnInit() {
     *   // Heartbeat toutes les 10 secondes
     *   interval(10000).subscribe(() => {
     *     this.http.post(`/api/meetings/${meetingId}/heartbeat`, {})
     *       .subscribe();
     *   });
     * }
     *
     * ngOnDestroy() {
     *   // Arrêter heartbeat quand composant détruit
     * }
     * ```
     *
     * REDIS :
     * - SADD meeting:active:{id} userId
     * - EXPIRE meeting:active:{id} 30
     *
     * Si participant ferme onglet :
     * → Plus de heartbeat
     * → Après 30s → Redis supprime automatiquement
     */
    @PostMapping("/{meetingId}/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        // Marquer participant actif + Refresh TTL à 30s
        sessionService.markParticipantActive(meetingId, userId);

        // Pas de log DEBUG pour éviter spam (appelé toutes les 10s)

        return ResponseEntity.ok(Map.of(
                "status", "active",
                "userId", userId
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * COMPTER PARTICIPANTS EN LIGNE
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/meetings/{id}/active-count
     *
     * Utilisé pour afficher badge "12 participants en ligne"
     *
     * ULTRA RAPIDE : Redis SCARD (< 1ms)
     *
     * Response :
     * {
     *   "meetingId": 123,
     *   "activeParticipants": 12
     * }
     */
    @GetMapping("/{meetingId}/active-count")
    public ResponseEntity<Map<String, Object>> getActiveCount(
            @PathVariable Long meetingId) {

        long count = sessionService.countActiveParticipants(meetingId);

        return ResponseEntity.ok(Map.of(
                "meetingId", meetingId,
                "activeParticipants", count
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * LISTER PARTICIPANTS EN LIGNE
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/meetings/{id}/active-participants
     *
     * Retourne liste des userId connectés MAINTENANT
     *
     * Response :
     * {
     *   "meetingId": 123,
     *   "participants": [
     *     "alice-uuid",
     *     "bob-uuid",
     *     "charlie-uuid"
     *   ]
     * }
     *
     * Frontend peut ensuite enrichir avec infos users
     * (appel Auth Service ou cache local)
     */
    @GetMapping("/{meetingId}/active-participants")
    public ResponseEntity<Map<String, Object>> getActiveParticipants(
            @PathVariable Long meetingId) {

        Set<Object> participants = sessionService.getActiveParticipants(meetingId);

        log.debug("Meeting {} has {} active participants", meetingId, participants.size());

        return ResponseEntity.ok(Map.of(
                "meetingId", meetingId,
                "participants", participants
        ));
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * VÉRIFIER SI UN PARTICIPANT EST EN LIGNE
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/meetings/{id}/active-participants/{userId}
     *
     * Response :
     * {
     *   "userId": "alice-uuid",
     *   "isActive": true
     * }
     */
    @GetMapping("/{meetingId}/active-participants/{userId}")
    public ResponseEntity<Map<String, Object>> isParticipantActive(
            @PathVariable Long meetingId,
            @PathVariable String userId) {

        boolean isActive = sessionService.isParticipantActive(meetingId, userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "meetingId", meetingId,
                "isActive", isActive
        ));
    }
}
package com.example.meeting_service.controller;

import com.example.meeting_service.service.MeetingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleController {

    private final MeetingLifecycleService lifecycleService;

    /**
     * POST /api/meetings/{id}/start
     * Démarrer une réunion — seul l'organisateur peut démarrer
     * ROLE_USER ✅ — MeetingLifecycleService vérifie que c'est l'organisateur
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> startMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("Starting meeting {} by user {}", id, userId);
        Map<String, Object> response = lifecycleService.startMeeting(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/meetings/{id}/join
     * Rejoindre une réunion
     * ROLE_USER ✅ — MeetingLifecycleService vérifie l'invitation
     */
    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> joinMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("User {} joining meeting {}", userId, id);
        Map<String, Object> response = lifecycleService.joinMeeting(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/meetings/{id}/leave
     * Quitter une réunion
     * ROLE_USER ✅
     *
     * ✅ FIX : retourne un message d'erreur clair si l'organisateur tente de quitter
     * AVANT : return silencieux → frontend ne savait pas que l'action avait échoué
     */
    @PostMapping("/{id}/leave")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> leaveMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("User {} leaving meeting {}", userId, id);

        // FIX : on vérifie si l'organisateur tente de quitter
        // et on retourne une erreur claire au lieu d'un return silencieux
        try {
            boolean wasOrganizer = lifecycleService.leaveMeeting(id, userId);
            if (wasOrganizer) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Organizer cannot leave. Use 'End Meeting' instead.",
                        "meetingId", id.toString()
                ));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "meetingId", id.toString()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Left meeting successfully",
                "meetingId", id.toString()
        ));
    }

    /**
     * POST /api/meetings/{id}/end
     * Terminer une réunion — seul l'organisateur peut terminer
     * ROLE_USER ✅ — MeetingLifecycleService vérifie que c'est l'organisateur
     */
    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('ROLE_USER')")
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
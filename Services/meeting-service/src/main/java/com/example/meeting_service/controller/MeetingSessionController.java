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


@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingSessionController {

    private final MeetingSessionService sessionService;


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


    @GetMapping("/{meetingId}/active-count")
    public ResponseEntity<Map<String, Object>> getActiveCount(
            @PathVariable Long meetingId) {

        long count = sessionService.countActiveParticipants(meetingId);

        return ResponseEntity.ok(Map.of(
                "meetingId", meetingId,
                "activeParticipants", count
        ));
    }


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
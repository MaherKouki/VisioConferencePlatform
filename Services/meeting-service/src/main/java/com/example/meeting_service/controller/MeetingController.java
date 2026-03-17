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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * POST /api/meetings
     * Créer une réunion — tout utilisateur connecté peut créer une réunion
     * ROLE_USER ✅ — action utilisateur normale
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String organizerId = jwt.getSubject();
        log.info("Creating meeting '{}' by organizer {}", request.getTitle(), organizerId);
        MeetingResponse response = meetingService.createMeeting(request, organizerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/meetings
     * Voir mes réunions (organisateur ou participant)
     * ROLE_USER ✅ — chaque user voit ses propres réunions
     */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_USER')")
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
     * GET /api/meetings/{id}
     * Voir les détails d'une réunion
     * ROLE_USER ✅ — accès vérifié dans MeetingService.canAccessMeeting()
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("Fetching details for meeting {} by user {}", id, userId);
        MeetingDetailResponse details = meetingService.getMeetingDetails(id, userId);
        return ResponseEntity.ok(details);
    }

    /**
     * PUT /api/meetings/{id}
     * Modifier une réunion
     * ROLE_USER ✅ — MeetingService vérifie que c'est l'organisateur
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
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
     * DELETE /api/meetings/{id}
     * Annuler une réunion
     * ROLE_USER ✅ — MeetingService vérifie que c'est l'organisateur
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
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
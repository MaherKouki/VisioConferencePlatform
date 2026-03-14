package com.example.meeting_service.controller;

import com.example.meeting_service.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController {

    private final ParticipantService participantService;


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
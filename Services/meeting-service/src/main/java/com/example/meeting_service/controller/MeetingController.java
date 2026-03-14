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


@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final MeetingService meetingService;


    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String organizerId = jwt.getSubject();  // UUID Keycloak

        log.info("Creating meeting '{}' by organizer {}", request.getTitle(), organizerId);

        MeetingResponse response = meetingService.createMeeting(request, organizerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


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


    @GetMapping("/{id}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.debug("Fetching details for meeting {} by user {}", id, userId);

        MeetingDetailResponse details = meetingService.getMeetingDetails(id, userId);

        return ResponseEntity.ok(details);
    }


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
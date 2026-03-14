package com.example.meeting_service.controller;

import com.example.meeting_service.service.MeetingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


///api/meetings/{id}/start
///api/meetings/{id}/join
///api/meetings/{id}/leave
// /api/meetings/{id}/end

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleController {

    private final MeetingLifecycleService lifecycleService;


    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Starting meeting {} by user {}", id, userId);

        Map<String, Object> response = lifecycleService.startMeeting(id, userId);

        return ResponseEntity.ok(response);
    }


    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> joinMeeting(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("User {} joining meeting {}", userId, id);

        Map<String, Object> response = lifecycleService.joinMeeting(id, userId);

        return ResponseEntity.ok(response);
    }


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
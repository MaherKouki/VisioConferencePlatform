package com.example.meeting_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingSessionService {


    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ACTIVE_PARTICIPANTS_PREFIX = "meeting:active:";

    private static final long HEARBEAT_TIMEOUT_SECONDS = 30;


    public void markParticipantActive(Long meetingId , String userId) {
        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key , HEARBEAT_TIMEOUT_SECONDS , TimeUnit.SECONDS);
        log.debug("User {} heartbeat received for meeting {} (TTL reset to 30 s)" , userId ,meetingId);
    }

    public void removeParticipant(Long meetingId , String userId) {
        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        redisTemplate.opsForSet().remove(key, userId);
        log.info("User {} removed from active participant in meeting {}", userId, meetingId);
    }

    public long countActiveParticipants(Long meetingId) {

        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        Long count = redisTemplate.opsForSet().size(key);

        log.debug("Meeting {} has {} active participant " , meetingId, count != null ? count : 0);
        return count != null ? count : 0;
    }

    public Set<Object> getActiveParticipants(Long meetingId) {
        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        Set<Object> participants = redisTemplate.opsForSet().members(key);
        log.debug("active paticipants in meeting {} : {} " , meetingId, participants);

        return participants;
    }

    public boolean isParticipantActive(Long meetingId , String userId) {
        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);
        return Boolean.TRUE.equals(isMember);
    }

    public void clearMeetingSession(Long meetingId) {
        String key = ACTIVE_PARTICIPANTS_PREFIX + meetingId;

        redisTemplate.delete(key);
        log.info("Session is cleared for meeting {}", meetingId);
    }


}

package com.example.meeting_service.service;


import com.example.meeting_service.Repository.MeetingParticipantRepository;
import com.example.meeting_service.Repository.MeetingRepository;
import com.example.meeting_service.client.AuthServiceClient;
import com.example.meeting_service.dto.UserInfo;
import com.example.meeting_service.dto.request.CreateMeetingRequest;
import com.example.meeting_service.dto.request.UpdateMeetingRequest;
import com.example.meeting_service.dto.response.MeetingDetailResponse;
import com.example.meeting_service.dto.response.MeetingResponse;
import com.example.meeting_service.dto.response.ParticipantResponse;
import com.example.meeting_service.entity.Meeting;
import com.example.meeting_service.entity.MeetingParticipant;
import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.enums.MeetingStatus;
import com.example.meeting_service.enums.ParticipantStatus;
import com.example.meeting_service.kafka.event.MeetingCreatedEvent;
import com.example.meeting_service.kafka.producer.MeetingEventProducer;
import com.example.meeting_service.mapper.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {


    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final AuthServiceClient authServiceClient;
    private final UserCacheService userCacheService;
    private final MeetingSessionService sessionService;
    private final MeetingEventProducer eventProducer;
    private final MeetingMapper meetingMapper;


    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, String organizerId) {
        log.info("Creating meeting: {} by organizer: {}", request.getTitle(), organizerId);

        Meeting meeting = new Meeting();
        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        meeting.setOrganizerId(organizerId);
        meeting.setGroupId(request.getGroupId());
        meeting.setScheduledStartTime(request.getScheduledStartTime());
        meeting.setScheduledEndTime(request.getScheduledEndTime());
        meeting.setIsRecorded(request.getIsRecorded());
        meeting.setMaxParticipants(request.getMaxParticipants());
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());

        meeting = meetingRepository.save(meeting);
        log.debug("Meeting saved with id: {}", meeting.getId());

        Set<String> participantIds = new HashSet<>();

        // if meeting for a groupe , it willll take meembers from auth Service
        if (request.getGroupId() != null) {
            try {
                log.debug("Fetching members for group: {}", request.getGroupId());
                List<String> groupMembers = authServiceClient.getGroupMembers(request.getGroupId());
                participantIds.addAll(groupMembers);
                log.info("Added {} group members to meeting", groupMembers.size());
            } catch (Exception e) {
                log.error("Failed to get group members", e);
                throw new RuntimeException("Failed to retrieve group members: " + e.getMessage());
            }
        }

        // Ajouter participants manuels (si fournis)
        if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            participantIds.addAll(request.getParticipantIds());
            log.debug("Added {} manual participants", request.getParticipantIds().size());
        }


        for (String userId : participantIds) {
            // not inviting organizer
            if (!userId.equals(organizerId)) {
                MeetingParticipant participant = new MeetingParticipant();
                participant.setId(new MeetingParticipantId(meeting.getId(), userId));
                participant.setMeeting(meeting);
                participant.setStatus(ParticipantStatus.INVITED);
                participantRepository.save(participant);
            }
        }
        log.info("Created {} participants for meeting {}", participantIds.size(), meeting.getId());

        //publish event for kafkaa
        publishMeetingCreatedEvent(meeting, participantIds);

        log.info("Meeting created successfully: {}", meeting.getId());
        return meetingMapper.toResponse(meeting);
    }





    public List<MeetingResponse> getMyMeetings(String userId) {

        log.debug("Fetching meetings for user: {}", userId);

        List<Meeting> meetings = meetingRepository.findByUserIdAsOrganizerOrParticipant(userId);
        log.info("Found {} meetings for user {}", meetings.size(), userId);

        return meetings.stream()
                .map(meetingMapper::toResponse)
                .collect(Collectors.toList());
    }




    public List<MeetingResponse> getMyMeetingsByStatus(String userId, MeetingStatus status) {
        log.debug("Fetching meetings for user: {} with status {} ", userId ,  status);

        List<Meeting> allMeetings = meetingRepository.findByUserIdAsOrganizerOrParticipant(userId);

        List<MeetingResponse> filtred = allMeetings.stream()
                .filter(m->m.getStatus() == status)
                .map(meetingMapper::toResponse)
                .toList();

        log.info("Found {} meetings for user {}", filtred.size(), userId);

        return filtred;
    }


    @Cacheable(value = "meetingDetails", key = "#meetingId")
    public MeetingDetailResponse getMeetingDetails(Long meetingId, String userId) {
        log.debug("⚠️ Cache MISS - Fetching details for meeting: {}", meetingId);

        // Récupérer la réunion
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier accès
        if (!canAccessMeeting(meeting, userId)) {
            log.warn("User {} tried to access meeting {} without permission", userId, meetingId);
            throw new RuntimeException("Access denied");
        }

        // Récupérer infos organisateur (cache 15 min)
        UserInfo organizerInfo = userCacheService.getUserInfo(meeting.getOrganizerId());
        if (organizerInfo == null) {
            organizerInfo = new UserInfo();
            organizerInfo.setUserId(meeting.getOrganizerId());
            organizerInfo.setUsername("Unknown");
            organizerInfo.setEmail("unknown@example.com");
        }

        // Récupérer infos groupe (si applicable)
        MeetingDetailResponse.GroupInfo groupInfo = null;
        if (meeting.getGroupId() != null) {
            groupInfo = new MeetingDetailResponse.GroupInfo(
                    meeting.getGroupId(),
                    "Group #" + meeting.getGroupId()
            );
        }

        // Récupérer participants avec infos (cache 15 min)
        List<MeetingParticipant> participants = participantRepository.findByIdMeetingId(meetingId);
        List<ParticipantResponse> participantResponses = new ArrayList<>();

        for (MeetingParticipant participant : participants) {
            // userCacheService.getUserInfo() utilise cache Redis
            UserInfo userInfo = userCacheService.getUserInfo(participant.getId().getUserId());
            participantResponses.add(meetingMapper.toParticipantResponse(participant, userInfo));
        }

        log.info("Retrieved details for meeting {} with {} participants (cached 15min)",
                meetingId, participantResponses.size());

        return meetingMapper.toDetailResponse(meeting, organizerInfo, groupInfo, participantResponses);
    }

    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public MeetingResponse updateMeeting(Long meetingId, UpdateMeetingRequest request, String userId) {
        log.info("Updating meeting: {} by user: {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier que c'est l'organisateur
        if (!meeting.getOrganizerId().equals(userId)) {
            log.warn("User {} tried to update meeting {} but is not organizer", userId, meetingId);
            throw new RuntimeException("Only organizer can update meeting");
        }

        // Vérifier que la réunion n'est pas déjà terminée
        if (meeting.getStatus() == MeetingStatus.ENDED ||
                meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new RuntimeException("Cannot update ended or cancelled meeting");
        }

        // Mettre à jour les champs
        if (request.getTitle() != null) {
            meeting.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            meeting.setDescription(request.getDescription());
        }
        if (request.getScheduledStartTime() != null) {
            meeting.setScheduledStartTime(request.getScheduledStartTime());
        }
        if (request.getScheduledEndTime() != null) {
            meeting.setScheduledEndTime(request.getScheduledEndTime());
        }
        if (request.getMaxParticipants() != null) {
            meeting.setMaxParticipants(request.getMaxParticipants());
        }
        if (request.getIsRecorded() != null) {
            meeting.setIsRecorded(request.getIsRecorded());
        }

        meeting.setUpdatedAt(LocalDateTime.now());
        meeting = meetingRepository.save(meeting);

        log.info("Meeting {} updated successfully (cache evicted)", meetingId);

        return meetingMapper.toResponse(meeting);
    }


    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public void cancelMeeting(Long meetingId, String userId) {
        log.info("Cancelling meeting: {} by user: {}", meetingId, userId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Vérifier que c'est l'organisateur
        if (!meeting.getOrganizerId().equals(userId)) {
            throw new RuntimeException("Only organizer can cancel meeting");
        }

        // Vérifier le statut
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            throw new RuntimeException("Cannot cancel ended meeting");
        }

        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.save(meeting);

        // Nettoyer session si réunion était LIVE
        if (meeting.getStatus() == MeetingStatus.LIVE) {
            sessionService.clearMeetingSession(meetingId);
            log.info("Redis session cleared for cancelled meeting {}", meetingId);
        }

        log.info("Meeting {} cancelled successfully", meetingId);
    }

    private boolean canAccessMeeting(Meeting meeting, String userId) {
        // Organisateur peut toujours accéder
        if (meeting.getOrganizerId().equals(userId)) {
            return true;
        }
        // Vérifier si participant
        return participantRepository.existsByIdMeetingIdAndIdUserId(meeting.getId(), userId);
    }



    private void publishMeetingCreatedEvent(Meeting meeting, Set<String> participantIds) {
        log.debug("Publishing MeetingCreatedEvent for meeting: {}", meeting.getId());

        List<MeetingCreatedEvent.ParticipantInfo> participantInfos = new ArrayList<>();

        // Récupérer emails (cache 15 min)
        for (String userId : participantIds) {
            UserInfo userInfo = userCacheService.getUserInfo(userId);
            if (userInfo != null) {
                participantInfos.add(new MeetingCreatedEvent.ParticipantInfo(
                        userId,
                        userInfo.getEmail()
                ));
            }
        }

        MeetingCreatedEvent event = new MeetingCreatedEvent(
                UUID.randomUUID().toString(),
                "MEETING_CREATED",
                LocalDateTime.now(),
                meeting.getId(),
                meeting.getTitle(),
                meeting.getOrganizerId(),
                meeting.getGroupId(),
                meeting.getScheduledStartTime(),
                participantInfos,
                meeting.getIsRecorded()
        );

        eventProducer.publishMeetingCreated(event);

        log.info("MeetingCreatedEvent published for meeting: {}", meeting.getId());
    }




}

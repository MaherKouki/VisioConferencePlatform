package com.example.meeting_service.mapper;

import com.example.meeting_service.Repository.MeetingParticipantRepository;
import com.example.meeting_service.dto.UserInfo;
import com.example.meeting_service.dto.response.MeetingDetailResponse;
import com.example.meeting_service.dto.response.MeetingResponse;
import com.example.meeting_service.dto.response.ParticipantResponse;
import com.example.meeting_service.entity.Meeting;
import com.example.meeting_service.entity.MeetingParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MeetingMapper {

    private final MeetingParticipantRepository participantRepository;


    public MeetingResponse toResponse(Meeting meeting) {
        if (meeting == null) return null;

        int participantCount = participantRepository.countByIdMeetingId(meeting.getId());

        return new MeetingResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getOrganizerId(),
                meeting.getGroupId(),
                meeting.getScheduledStartTime(),
                meeting.getScheduledEndTime(),
                meeting.getStatus(),
                meeting.getIsRecorded(),
                participantCount,
                meeting.getCreatedAt()
        );
    }


    public MeetingDetailResponse toDetailResponse(
            Meeting meeting,
            UserInfo organizerInfo,
            MeetingDetailResponse.GroupInfo groupInfo,
            List<ParticipantResponse> participants) {

        if (meeting == null) return null;

        MeetingDetailResponse.OrganizerInfo organizer = new MeetingDetailResponse.OrganizerInfo(
                organizerInfo.getUserId(),
                organizerInfo.getUsername(),
                organizerInfo.getEmail()
        );

        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                organizer,
                groupInfo,
                meeting.getScheduledStartTime(),
                meeting.getScheduledEndTime(),
                meeting.getActualStartTime(),
                meeting.getActualEndTime(),
                meeting.getStatus(),
                meeting.getMeetingUrl(),
                meeting.getMaxParticipants(),
                meeting.getIsRecorded(),
                participants,
                meeting.getCreatedAt()
        );
    }


    public ParticipantResponse toParticipantResponse(MeetingParticipant participant, UserInfo userInfo) {
        return new ParticipantResponse(
                participant.getId().getUserId(),
                userInfo != null ? userInfo.getUsername() : "Unknown",
                userInfo != null ? userInfo.getEmail() : "unknown@example.com",
                participant.getStatus(),
                participant.getJoinedAt(),
                participant.getLeftAt()
        );
    }
}
package com.example.meeting_service.service;


import com.example.meeting_service.Repository.MeetingParticipantRepository;
import com.example.meeting_service.Repository.MeetingRepository;
import com.example.meeting_service.entity.Meeting;
import com.example.meeting_service.entity.MeetingParticipant;
import com.example.meeting_service.entity.MeetingParticipantId;
import com.example.meeting_service.enums.MeetingStatus;
import com.example.meeting_service.enums.ParticipantStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final UserCacheService userCacheService;




    @Transactional
    @CacheEvict(value = "meetingDetails" , key = "#meetingId")
    public void InviteParticpant (Long meetingId, String userIdToInvite ,  String organizerId ){

        log.info("Inviting user{} to meeting {} by organizer {}", userIdToInvite, meetingId, organizerId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(()-> new RuntimeException("meeting not found"));

        //to veirfyy the organizer
        if(!meeting.getOrganizerId().equals(organizerId)){
            log.warn("User {} is not organizer of meeting {}",organizerId , meetingId);
            throw new RuntimeException("Only organizer can invite participants");
        }

        if(meeting.getStatus() == MeetingStatus.CANCELLED ||
        meeting.getStatus() == MeetingStatus.ENDED){
            throw new RuntimeException("Cannot invite to ended or cancelled meeting");
        }

        //veriify the user to invite (using cache)
        if(!userCacheService.userExists(userIdToInvite)){
            log.warn("Tried to invite a user {} does not exist", userIdToInvite);
            throw new RuntimeException("User not found");
        }

        //verify user iss not participant
        if(participantRepository.existsByIdMeetingIdAndIdUserId( meetingId, userIdToInvite )){
            throw new RuntimeException("User is already participant");
        }


        //max participants
        int currentCount = participantRepository.countByIdMeetingId(meetingId);
        if(currentCount >= meeting.getMaxParticipants()){
            throw new RuntimeException("Max participants exceeded : " + meeting.getMaxParticipants());
        }


        MeetingParticipant participant = new MeetingParticipant();
        participant.setId(new MeetingParticipantId(meetingId, userIdToInvite));
        participant.setMeeting(meeting);
        participant.setStatus(ParticipantStatus.INVITED);
        participantRepository.save(participant);

        log.info("Participant {} invited to meeting {} ", userIdToInvite, meetingId);
    }


    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public void removeParticipant(Long meetingId, String userIdToRemove, String organizerId) {
        log.info("Removing user {} from meeting {} by organizer {}", userIdToRemove, meetingId, organizerId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerId().equals(organizerId)) {
            throw new RuntimeException("Only organizer can remove participants");
        }

        MeetingParticipant participant = participantRepository.findById(
                new MeetingParticipantId(meetingId, userIdToRemove)
        ).orElseThrow(() -> new RuntimeException("User is not a participant"));

        participantRepository.delete(participant);

        log.info("Participant {} removed from meeting {} (cache evicted)", userIdToRemove, meetingId);
    }


    @Transactional
    @CacheEvict(value = "meetingDetails" , key ="#meetingId")
    public void acceptInvitation(Long meetingId , String userIdToAccept){
        log.info("Accepting user {} from meeting {} ", userIdToAccept, meetingId);

        MeetingParticipant participant = participantRepository.findById(
                new MeetingParticipantId(meetingId , userIdToAccept)
        ).orElseThrow(() -> new RuntimeException("Invitation not found "));

        participant.setStatus(ParticipantStatus.ACCEPTED);
        participantRepository.save(participant);

    }


    @Transactional
    @CacheEvict(value = "meetingDetails", key = "#meetingId")
    public void declineInvitation(Long meetingId, String userId) {
        log.info("User {} declining invitation to meeting {}", userId, meetingId);

        MeetingParticipant participant = participantRepository.findById(
                new MeetingParticipantId(meetingId, userId)
        ).orElseThrow(() -> new RuntimeException("Invitation not found"));

        participant.setStatus(ParticipantStatus.DECLINED);
        participantRepository.save(participant);

        log.info("Invitation declined by {} for meeting {} (cache evicted)", userId, meetingId);
    }
}

package com.example.meeting_service.kafka.producer;

import com.example.meeting_service.kafka.event.MeetingCreatedEvent;
import com.example.meeting_service.kafka.event.MeetingEndedEvent;
import com.example.meeting_service.kafka.event.MeetingStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishMeetingCreated(MeetingCreatedEvent event) {
        log.info("Publishing MeetingCreatedEvent for meeting: {}", event.getMeetingId());
        kafkaTemplate.send("meeting.created", event.getMeetingId().toString(), event);
    }

    public void publishMeetingStarted(MeetingStartedEvent event) {
        log.info("Publishing MeetingStartedEvent for meeting: {}", event.getMeetingId());
        kafkaTemplate.send("meeting.started", event.getMeetingId().toString(), event);
    }

    public void publishMeetingEnded(MeetingEndedEvent event) {
        log.info("Publishing MeetingEndedEvent for meeting: {}", event.getMeetingId());
        kafkaTemplate.send("meeting.ended", event.getMeetingId().toString(), event);
    }
}
package com.example.meeting_service.websocket;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeetingEvent {

    private String type;

    private Long meetingId;

    private Object data;

}

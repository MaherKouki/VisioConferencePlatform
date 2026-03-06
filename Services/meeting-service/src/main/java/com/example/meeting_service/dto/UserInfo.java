package com.example.meeting_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private String userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
}
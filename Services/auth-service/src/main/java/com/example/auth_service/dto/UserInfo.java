package com.example.auth_service.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfo {
    private String userId;           // UUID Keycloak
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;      // ["ROLE_USER", "ROLE_ADMIN"]
    private Boolean emailVerified;
    private LocalDateTime createdAt;
}

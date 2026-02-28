package com.example.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String ownerId;
    private LocalDateTime createdAt;
    private int memberCount;
}

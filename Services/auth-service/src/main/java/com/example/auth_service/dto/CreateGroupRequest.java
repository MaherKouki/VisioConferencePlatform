package com.example.auth_service.dto;

import lombok.Data;

@Data
public class CreateGroupRequest {
    private String name;
    private String description;
}

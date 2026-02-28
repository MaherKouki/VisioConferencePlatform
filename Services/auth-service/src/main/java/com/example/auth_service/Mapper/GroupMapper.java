package com.example.auth_service.Mapper;

import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.entity.Group;
import org.springframework.stereotype.Component;

@Component
public class GroupMapper {

    public GroupResponse toResponse(Group group) {
        if (group == null) return null;

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getOwnerId(),
                group.getCreatedAt(),
                group.getMembers() != null ? group.getMembers().size() : 0
        );
    }

    public Group toEntity(CreateGroupRequest request, String ownerId) {
        if (request == null) return null;

        Group group = new Group();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setOwnerId(ownerId);
        return group;
    }
}
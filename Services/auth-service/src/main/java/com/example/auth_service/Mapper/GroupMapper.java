package com.example.auth_service.Mapper;

import com.example.auth_service.Repository.GroupMemberRepository;
import com.example.auth_service.Repository.GroupRepository;
import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.entity.Group;
import com.example.auth_service.entity.GroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupMapper {

    private final GroupMemberRepository groupMemberRepository;

    public GroupResponse toResponse(Group group) {
        if (group == null) return null;

        int memberCount = groupMemberRepository.countByIdGroupId(group.getId());

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getOwnerId(),
                group.getCreatedAt(),
                memberCount
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
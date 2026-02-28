package com.example.auth_service.service;


import com.example.auth_service.Mapper.GroupMapper;
import com.example.auth_service.Repository.GroupMemberRepository;
import com.example.auth_service.Repository.GroupRepository;
import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.entity.Group;
import com.example.auth_service.entity.GroupMember;
import com.example.auth_service.entity.GroupMemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMapper groupMapper;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String ownerId) {
        Group group = groupMapper.toEntity(request, ownerId);
        group = groupRepository.save(group);

        GroupMember ownerMember = new GroupMember();
        ownerMember.setId(new GroupMemberId(group.getId(), ownerId));
        ownerMember.setGroup(group);
        ownerMember.setRole("OWNER");



        groupMemberRepository.save(ownerMember);


        return groupMapper.toResponse(group);
    }

    public List<GroupResponse> getMyGroups(String userId) {
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);
        return memberships.stream()
                .map(gm -> groupMapper.toResponse(gm.getGroup()))
                .collect(Collectors.toList());
    }

    public GroupResponse getGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        return groupMapper.toResponse(group);
    }


}
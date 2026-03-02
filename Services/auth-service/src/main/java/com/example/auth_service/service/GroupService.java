package com.example.auth_service.service;


import com.example.auth_service.Mapper.GroupMapper;
import com.example.auth_service.Repository.GroupMemberRepository;
import com.example.auth_service.Repository.GroupRepository;
import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.entity.Group;
import com.example.auth_service.entity.GroupMember;
import com.example.auth_service.entity.GroupMemberId;
import org.keycloak.admin.client.Keycloak;


import lombok.RequiredArgsConstructor;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {


    private final String realm = "visioconference";


    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMapper groupMapper;
    private final Keycloak keycloak;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String ownerId) {
        Group group = groupMapper.toEntity(request, ownerId);
        group = groupRepository.save(group);

        GroupMember ownerMember = new GroupMember();
        ownerMember.setId(new GroupMemberId(group.getId(), ownerId));
        ownerMember.setGroup(group);
        ownerMember.setRole("OWNER");

        groupMemberRepository.save(ownerMember);

        //group.getMembers().add(ownerMember);
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

    @Transactional
    public void addMember(Long groupId, String role , String userToAdd , String adminUserId) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(()-> new RuntimeException("groupe invalide"));

        GroupMember adminMember = groupMemberRepository.findById(
                new GroupMemberId(groupId, adminUserId)
        ).orElseThrow(()-> new RuntimeException("You are already a member of this group"));

        if(!adminMember.getRole().equals("ADMIN") &&
        !adminMember.getRole().equals("OWNER")){
            throw new RuntimeException("Only OWNER or ADMIN can add members");
        }

        try {
            UserRepresentation user = keycloak.realm(realm)
                    .users()
                    .get(userToAdd)
                    .toRepresentation();
        }
        catch (Exception e){
            throw new RuntimeException("User not found" +  userToAdd);
        }

        boolean alreadyMember = groupMemberRepository.existsByIdGroupIdAndUserId(groupId, userToAdd);


        if(!alreadyMember){
            throw new RuntimeException("User" + userToAdd + " is already a member of the group" + groupId );
        }

        if (role == null || role.isEmpty()) {
            role = "MEMBER";
        }

        if(role.equals("OWNER") && !adminMember.getRole().equals("OWNER")){
            throw new RuntimeException("Only OWNER or ADMIN can add other OWNERS");
        }

        GroupMember newMember = new GroupMember() ;
        newMember.setId(new GroupMemberId(groupId, userToAdd));
        newMember.setRole(role);
        newMember.setGroup(group);
        groupMemberRepository.save(newMember);



    }


}
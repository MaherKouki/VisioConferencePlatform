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
    public void addMember(Long groupId, String role, String userToAdd, String adminUserId) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Vérifier que celui qui ajoute est membre du groupe
        GroupMember adminMember = groupMemberRepository.findById(
                new GroupMemberId(groupId, adminUserId)
        ).orElseThrow(() -> new RuntimeException("You are not a member of this group"));

        // Vérifier que celui qui ajoute est OWNER ou ADMIN
        String adminRole = adminMember.getRole().toUpperCase();
        if (!adminRole.equals("OWNER") && !adminRole.equals("ADMIN")) {
            throw new RuntimeException("Only OWNER or ADMIN can add members");
        }

        // Vérifier que l'user existe dans Keycloak
        try {
            keycloak.realm(realm).users().get(userToAdd).toRepresentation();
        } catch (Exception e) {
            throw new RuntimeException("User not found: " + userToAdd);
        }

        // BUG FIX 2 : normaliser le rôle EN PREMIER
        if (role == null || role.isEmpty()) {
            role = "MEMBER";
        }

        // BUG FIX 1 : condition corrigée
        boolean alreadyMember = groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userToAdd);
        if (alreadyMember) {
            throw new RuntimeException("User " + userToAdd + " is already a member of group " + groupId);
        }

        // Seul OWNER peut assigner le rôle OWNER
        if (role.toUpperCase().equals("OWNER") && !adminRole.equals("OWNER")) {
            throw new RuntimeException("Only OWNER can assign OWNER role");
        }

        GroupMember newMember = new GroupMember();
        newMember.setId(new GroupMemberId(groupId, userToAdd));
        newMember.setRole(role.toUpperCase()); // ✅ toujours en majuscules
        newMember.setGroup(group);
        groupMemberRepository.save(newMember);
    }


    @Transactional
    public void removeMember(Long groupId, String userIdToRemove, String adminUserId) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(()-> new RuntimeException("group invalide"));

        GroupMember adminMember = groupMemberRepository.findById(
                new GroupMemberId(groupId, adminUserId)
        ).orElseThrow(()-> new RuntimeException("You are already a member of this group"));

        if(!adminMember.getRole().equals("ADMIN") &&
                !adminMember.getRole().equals("OWNER")){
            throw new RuntimeException("Only OWNER or ADMIN can remove members");
        }

        GroupMember memberToRemove = groupMemberRepository.findById(
                new GroupMemberId(groupId , userIdToRemove)
        ).orElseThrow(()-> new RuntimeException("User is not a member of this group"));


        if(memberToRemove.getRole().equals("OWNER")
        && !adminMember.getRole().equals("OWNER")){
            throw new RuntimeException("Only OWNER or ADMIN can remove members");
        }

        if(userIdToRemove.equals(group.getOwnerId())){
            int memberCount = groupMemberRepository.countByIdGroupId(groupId);
            if(memberCount == 1){
                throw new RuntimeException("OWNER can't leave as the only member of the group");
            }
        }
        groupMemberRepository.delete(memberToRemove);
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        return groupMemberRepository.findByIdGroupId(groupId);
    }



    }
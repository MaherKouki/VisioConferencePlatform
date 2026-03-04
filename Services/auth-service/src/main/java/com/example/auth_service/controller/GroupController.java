package com.example.auth_service.controller;

import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        GroupResponse group = groupService.createGroup(request, userId);
        return ResponseEntity.ok(group);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getMyGroups(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<GroupResponse> groups = groupService.getMyGroups(userId);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        GroupResponse group = groupService.getGroup(id);
        return ResponseEntity.ok(group);
    }

    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> addMember(
            @PathVariable Long groupId,
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ){
        String role = body != null ? body.getOrDefault("role" , "MEMBER") : "MEMBER";
        String adminUserId = jwt.getSubject();  // id of the admin

        groupService.addMember(groupId,role, userId ,adminUserId );
        //addMember(Long groupId, String role , String userToAdd , String adminUserId) {

        return ResponseEntity.ok().build();
    }


    //removeMember(Long groupId, String userIdToRemove, String adminUserId)


    @DeleteMapping("")
    public ResponseEntity<?> removeMember(
            @PathVariable Long groupId ,
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt
            ) {
        String adminUserId = jwt.getSubject();
        groupService.removeMember(groupId,userId ,adminUserId );
        return ResponseEntity.ok().build();
    }



}
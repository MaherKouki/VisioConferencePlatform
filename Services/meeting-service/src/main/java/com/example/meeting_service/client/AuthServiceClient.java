package com.example.meeting_service.client;

import com.example.meeting_service.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${auth-service.url}")
    private String authServiceUrl;


    private HttpEntity<?> createAuthHeaders() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.getTokenValue()); // propager le token
        return new HttpEntity<>(headers);
    }

    public List<String> getGroupMembers(Long groupId) {
        try {
            String url = authServiceUrl + "/api/groups/" + groupId + "/members";
            log.debug("Calling Auth Service: GET {}", url);

            ResponseEntity<List<LinkedHashMap>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createAuthHeaders(),
                    new ParameterizedTypeReference<List<LinkedHashMap>>() {}
            );

            List<LinkedHashMap> members = response.getBody();
            if (members == null) {
                log.warn("No members returned for group {}", groupId);
                return new ArrayList<>();
            }

            // Extraire uniquement les userId
            List<String> userIds = members.stream()
                    .map(member -> {
                        LinkedHashMap id = (LinkedHashMap) member.get("id");
                        return (String) id.get("userId");
                    })
                    .collect(Collectors.toList());

            log.info("Retrieved {} members from group {}", userIds.size(), groupId);
            return userIds;

        } catch (Exception e) {
            log.error("Failed to get group members for groupId: {}", groupId, e);
            throw new RuntimeException("Failed to retrieve group members: " + e.getMessage());
        }
    }

    public UserInfo getUserInfo(String userId) {
        try {
            String url = authServiceUrl + "/api/auth/users/" + userId;
            log.debug("Calling Auth Service: GET {}", url);

            ResponseEntity<UserInfo> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createAuthHeaders(), // ✅ avec token
                    UserInfo.class
            );

            UserInfo userInfo = response.getBody();
            if (userInfo == null) {
                log.warn("User not found: {}", userId);
                return null;
            }

            log.debug("Retrieved user info for {}: {}", userId, userInfo.getUsername());
            return userInfo;

        } catch (Exception e) {
            log.error("Failed to get user info for userId: {}", userId, e);
            return null;
        }
    }


    public boolean userExists(String userId) {
        try {
            return getUserInfo(userId) != null;
        } catch (Exception e) {
            log.error("Error checking if user exists: {}", userId, e);
            return false;
        }
    }


    public List<UserInfo> getUserInfoBatch(List<String> userIds) {
        List<UserInfo> users = new ArrayList<>();
        for (String userId : userIds) {
            UserInfo userInfo = getUserInfo(userId);
            if (userInfo != null) {
                users.add(userInfo);
            }
        }
        return users;
    }
}
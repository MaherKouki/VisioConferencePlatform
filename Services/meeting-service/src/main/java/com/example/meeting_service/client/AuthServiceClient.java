package com.example.meeting_service.client;

import com.example.meeting_service.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;  // Injecté par Spring (RestTemplateConfig)

    @Value("${auth-service.url}")
    private String authServiceUrl;  // http://localhost:8081


    public List<String> getGroupMembers(Long groupId) {
        try {

            String url = authServiceUrl + "/api/groups/" + groupId + "/members";

            log.debug("Calling Auth Service: GET {}", url);


            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);


            List<String> members = response.getBody();

            if (members == null) {
                log.warn("No members returned for group {}", groupId);
                return new ArrayList<>();
            }

            log.info("Retrieved {} members from group {}", members.size(), groupId);

            return members;

        } catch (Exception e) {
            log.error("Failed to get group members for groupId: {}", groupId, e);
            throw new RuntimeException("Failed to retrieve group members: " + e.getMessage());
        }
    }


    public UserInfo getUserInfo(String userId) {
        try {
            String url = authServiceUrl + "/api/users/" + userId;

            log.debug("Calling Auth Service: GET {}", url);

            ResponseEntity<UserInfo> response = restTemplate.getForEntity(url, UserInfo.class);

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
            UserInfo userInfo = getUserInfo(userId);
            return userInfo != null;
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